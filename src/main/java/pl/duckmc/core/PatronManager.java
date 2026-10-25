package pl.duckmc.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

public final class PatronManager {
    private final DuckMC plugin;
    private YamlConfiguration cfg;
    private final Map<String, PatronDef> defs = new LinkedHashMap<>();
    private final NamespacedKey hpKey;
    private final NamespacedKey shardKey;
    private final Random rnd = new Random();
    private String hourPatron = null;
    private long lastSlot = -1;
    private long manualUntil = 0;
    private BukkitTask task;

    public PatronManager(DuckMC plugin) {
        this.plugin = plugin;
        this.hpKey = new NamespacedKey(plugin, "afrodyta");
        this.shardKey = new NamespacedKey(plugin, "dragon_shard");
        load();
    }

    public void load() {
        cfg = plugin.yaml("patrons.yml");
        defs.clear();
        ConfigurationSection s = cfg.getConfigurationSection("patrons");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) continue;
            ConfigurationSection v = c.getConfigurationSection("values");
            if (v == null) v = new MemoryConfiguration();
            defs.put(id.toLowerCase(), new PatronDef(id.toLowerCase(), c.getString("name", id.toUpperCase()),
                    c.getString("color", "#FFFFFF"), c.getString("perk", ""), c.getString("hour-perk", ""), v));
        }
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public YamlConfiguration cfg() { return cfg; }
    public Collection<PatronDef> all() { return defs.values(); }
    public PatronDef def(String id) { return id == null ? null : defs.get(id.toLowerCase()); }

    public ConfigurationSection values(String id) {
        PatronDef d = def(id);
        return d != null ? d.values() : new MemoryConfiguration();
    }

    public String shardName() {
        return cfg.getString("settings.shard-name", "Smoczy Odłamek");
    }

    // ------------------------------------------------------------ patron gracza

    public String patronOf(UUID id) {
        PlayerData d = plugin.data().get(id);
        if (d.patron == null) return null;
        if (d.patronExpire < System.currentTimeMillis()) {
            d.patron = null;
            return null;
        }
        return d.patron;
    }

    public boolean has(Player p, String id) {
        return id.equals(patronOf(p.getUniqueId()));
    }

    // ------------------------------------------------------------ godzina patrona

    public String hourPatron() { return hourPatron; }

    public String hoursText(boolean weekend) {
        String base = "settings.hours." + (weekend ? "weekend" : "weekday");
        return cfg.getString(base + ".start", "13:00") + "</yellow> do <yellow>" + cfg.getString(base + ".end", "22:30");
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (manualUntil > 0) {
            if (now > manualUntil) {
                manualUntil = 0;
                endHour();
            }
            return;
        }
        ZoneId z = ZoneId.of(cfg.getString("settings.timezone", "Europe/Warsaw"));
        ZonedDateTime n = ZonedDateTime.now(z);
        boolean weekend = n.getDayOfWeek() == DayOfWeek.SATURDAY || n.getDayOfWeek() == DayOfWeek.SUNDAY;
        String base = "settings.hours." + (weekend ? "weekend" : "weekday");
        LocalTime start = LocalTime.parse(cfg.getString(base + ".start", "13:00"));
        LocalTime end = LocalTime.parse(cfg.getString(base + ".end", "22:30"));
        LocalTime t = n.toLocalTime();
        boolean inWindow = !t.isBefore(start) && t.isBefore(end);
        int dur = cfg.getInt("settings.hour-duration-minutes", 30);
        boolean inSlot = inWindow && n.getMinute() < dur;
        long slot = n.toLocalDate().toEpochDay() * 24 + n.getHour();
        if (inSlot) {
            if (hourPatron == null && slot != lastSlot && !defs.isEmpty()) {
                lastSlot = slot;
                List<PatronDef> list = new ArrayList<>(defs.values());
                beginHour(list.get(rnd.nextInt(list.size())).id());
            }
        } else if (hourPatron != null) {
            endHour();
        }
    }

    /** Reczne wlaczenie godziny patrona (komenda admina). */
    public boolean startManual(String id, int minutes) {
        if (def(id) == null) return false;
        if (hourPatron != null) endHour();
        manualUntil = System.currentTimeMillis() + minutes * 60_000L;
        beginHour(id.toLowerCase());
        return true;
    }

    public void stopManual() {
        manualUntil = 0;
        endHour();
    }

    private void beginHour(String id) {
        hourPatron = id;
        PatronDef d = def(id);
        Bukkit.broadcast(Text.raw("<dark_gray>[<green>Godzina Patrona<dark_gray>] <gray>Wylosowano patrona: " + d.colored()
                + "<gray>! " + d.hourPerk()));
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateHealth(p);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
    }

    private void endHour() {
        if (hourPatron == null) return;
        PatronDef d = def(hourPatron);
        hourPatron = null;
        Bukkit.broadcast(Text.raw("<dark_gray>[<green>Godzina Patrona<dark_gray>] <gray>Godzina patrona "
                + (d != null ? d.colored() : "") + "<gray> dobiegla konca."));
        for (Player p : Bukkit.getOnlinePlayers()) updateHealth(p);
    }

    // ------------------------------------------------------------ Afrodyta (zycie)

    public void updateHealth(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a == null) return;
        for (AttributeModifier m : new ArrayList<>(a.getModifiers())) {
            if (hpKey.equals(m.getKey())) a.removeModifier(m);
        }
        double extra = 0;
        if (has(p, "afrodyta")) extra += values("afrodyta").getDouble("patron-hp", 1.0);
        if ("afrodyta".equals(hourPatron)) extra += values("afrodyta").getDouble("hour-hp", 2.0);
        if (extra > 0) {
            a.addModifier(new AttributeModifier(hpKey, extra, AttributeModifier.Operation.ADD_NUMBER));
        }
        if (p.getHealth() > a.getValue()) p.setHealth(a.getValue());
    }

    // ------------------------------------------------------------ losowanie

    public void draw(Player p, boolean quick) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        long now = System.currentTimeMillis();
        long cd = cfg.getLong("settings.free-draw-days", 3) * 86_400_000L;
        if (defs.isEmpty()) return;
        if (quick) {
            int cost = cfg.getInt("settings.quick-cost", 2);
            if (!takeShards(p, cost)) {
                plugin.msg(p, "<red>Potrzebujesz <white>x" + cost + " " + shardName() + "<red>!");
                return;
            }
        } else if (now - d.lastDraw < cd) {
            plugin.msg(p, "<red>Darmowe losowanie bedzie dostepne za <yellow>" + Text.duration(cd - (now - d.lastDraw)));
            return;
        } else {
            d.lastDraw = now;
        }
        List<PatronDef> list = new ArrayList<>(defs.values());
        PatronDef n = list.get(rnd.nextInt(list.size()));
        d.patron = n.id();
        d.patronExpire = now + cfg.getLong("settings.duration-days", 5) * 86_400_000L;
        updateHealth(p);
        p.showTitle(Title.title(Text.raw(n.colored()), Text.raw("<gray>Twoj nowy patron!")));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        plugin.msg(p, "Wylosowano patrona: " + n.colored());
    }

    public void setPatron(Player p, String id, int days) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (id == null) {
            d.patron = null;
            d.patronExpire = 0;
        } else {
            d.patron = id.toLowerCase();
            d.patronExpire = System.currentTimeMillis() + days * 86_400_000L;
        }
        updateHealth(p);
    }

    // ------------------------------------------------------------ smoczy odlamek

    public ItemStack shard(int amount) {
        ItemStack it = new ItemStack(Material.AMETHYST_SHARD, amount);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.c("<bold><gradient:#5B2BFF:#C400FF>" + shardName()));
        m.lore(Text.c(List.of("", "<gray>Uzywany do szybkiego losowania patrona.")));
        m.setItemModel(Items.model("dragon_shard"));
        m.getPersistentDataContainer().set(shardKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private boolean isShard(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(shardKey, PersistentDataType.BYTE);
    }

    public int countShards(Player p) {
        int have = 0;
        for (ItemStack it : p.getInventory().getContents()) if (isShard(it)) have += it.getAmount();
        return have;
    }

    private boolean takeShards(Player p, int need) {
        if (countShards(p) < need) return false;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize() && need > 0; i++) {
            ItemStack it = inv.getItem(i);
            if (!isShard(it)) continue;
            int take = Math.min(need, it.getAmount());
            it.setAmount(it.getAmount() - take);
            need -= take;
            if (it.getAmount() <= 0) inv.setItem(i, null);
        }
        return true;
    }
}
