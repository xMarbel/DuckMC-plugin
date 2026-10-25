package pl.duckmc.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Wlasny, prosty system NPC (bez Citizens) - latwa konfiguracja komendami /npc. */
public final class NpcManager implements Listener, CommandExecutor, TabCompleter {
    private static final class Npc {
        String id;
        Location loc;
        EntityType type = EntityType.VILLAGER;
        String command = "";
        String head = "";
        List<String> lines = new ArrayList<>();
        Entity entity;
        Entity holo;
    }

    private final DuckMC plugin;
    private final NamespacedKey key;
    private final File file;
    private final Map<String, Npc> npcs = new LinkedHashMap<>();
    private final Map<UUID, Long> last = new HashMap<>();
    private BukkitTask task;

    public NpcManager(DuckMC plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "npc_id");
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
        load();
    }

    // ------------------------------------------------------------ zapis / odczyt

    private void load() {
        npcs.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("npcs");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) continue;
            World w = Bukkit.getWorld(c.getString("world", "world"));
            if (w == null) continue;
            Npc n = new Npc();
            n.id = id;
            n.loc = new Location(w, c.getDouble("x"), c.getDouble("y"), c.getDouble("z"),
                    (float) c.getDouble("yaw"), (float) c.getDouble("pitch"));
            try {
                n.type = EntityType.valueOf(c.getString("type", "VILLAGER").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
            n.command = c.getString("command", "");
            n.head = c.getString("head", "");
            n.lines = new ArrayList<>(c.getStringList("lines"));
            npcs.put(id, n);
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Npc n : npcs.values()) {
            String p = "npcs." + n.id + ".";
            y.set(p + "world", n.loc.getWorld().getName());
            y.set(p + "x", n.loc.getX());
            y.set(p + "y", n.loc.getY());
            y.set(p + "z", n.loc.getZ());
            y.set(p + "yaw", n.loc.getYaw());
            y.set(p + "pitch", n.loc.getPitch());
            y.set(p + "type", n.type.name());
            y.set(p + "command", n.command);
            y.set(p + "head", n.head);
            y.set(p + "lines", n.lines);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Nie mozna zapisac npcs.yml: " + ex.getMessage());
        }
    }

    public void reload() {
        shutdown();
        load();
        start();
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Npc n : npcs.values()) {
                boolean ok = n.entity != null && n.entity.isValid()
                        && (n.lines.isEmpty() || (n.holo != null && n.holo.isValid()));
                if (!ok) spawn(n);
            }
        }, 20L, 40L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Npc n : npcs.values()) despawn(n);
    }

    // ------------------------------------------------------------ spawn

    private void despawn(Npc n) {
        if (n.entity != null && n.entity.isValid()) n.entity.remove();
        if (n.holo != null && n.holo.isValid()) n.holo.remove();
        n.entity = null;
        n.holo = null;
    }

    private void spawn(Npc n) {
        despawn(n);
        World w = n.loc.getWorld();
        if (w == null || !w.isChunkLoaded(n.loc.getBlockX() >> 4, n.loc.getBlockZ() >> 4)) return;
        Entity e = w.spawnEntity(n.loc, n.type, CreatureSpawnEvent.SpawnReason.CUSTOM, ent -> {
            ent.setPersistent(false);
            ent.setInvulnerable(true);
            ent.setSilent(true);
            ent.getPersistentDataContainer().set(key, PersistentDataType.STRING, n.id);
            if (ent instanceof LivingEntity le) {
                le.setRemoveWhenFarAway(false);
                le.setCollidable(false);
                le.setAI(false);
            }
            if (ent instanceof ArmorStand as) {
                as.setBasePlate(false);
                as.setArms(true);
                if (!n.head.isBlank()) {
                    Material m = Material.matchMaterial(n.head);
                    if (m != null) as.getEquipment().setHelmet(new ItemStack(m));
                }
            }
        });
        n.entity = e;
        if (!n.lines.isEmpty()) {
            List<Component> comps = new ArrayList<>();
            for (String l : n.lines) comps.add(Text.raw(l));
            Component text = Component.join(JoinConfiguration.newlines(), comps);
            Location hl = n.loc.clone().add(0, e.getHeight() + 0.2 + 0.13 * n.lines.size(), 0);
            n.holo = w.spawn(hl, TextDisplay.class, td -> {
                td.setPersistent(false);
                td.text(text);
                td.setBillboard(Display.Billboard.CENTER);
                td.setAlignment(TextDisplay.TextAlignment.CENTER);
                td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                td.setShadowed(true);
                td.getPersistentDataContainer().set(key, PersistentDataType.STRING, n.id);
            });
        }
    }

    private void respawn(Npc n) {
        save();
        spawn(n);
    }

    // ------------------------------------------------------------ klikniecia

    private void trigger(Player p, Entity clicked) {
        String id = clicked.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return;
        Npc n = npcs.get(id);
        if (n == null || n.command.isBlank()) return;
        long now = System.currentTimeMillis();
        if (now - last.getOrDefault(p.getUniqueId(), 0L) < 400) return;
        last.put(p.getUniqueId(), now);
        Bukkit.dispatchCommand(p, n.command.replace("%player%", p.getName()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;
        e.setCancelled(true);
        trigger(e.getPlayer(), e.getRightClicked());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;
        e.setCancelled(true);
        if (e.getDamager() instanceof Player p) trigger(p, e.getEntity());
    }

    // ------------------------------------------------------------ komendy

    private void help(CommandSender s) {
        plugin.msg(s, "<yellow>Komendy NPC:");
        String[] h = {
                "/npc create <id> [typ]  <dark_gray>- stworz NPC tu gdzie stoisz (np. VILLAGER, ARMOR_STAND, ZOMBIE)",
                "/npc remove <id>",
                "/npc list",
                "/npc addline <id> <tekst>  <dark_gray>- dodaj linie nad glowa (MiniMessage)",
                "/npc setline <id> <nr> <tekst>",
                "/npc clearlines <id>",
                "/npc command <id> <komenda>  <dark_gray>- komenda po kliknieciu (bez /), np. patron",
                "/npc type <id> <typ>",
                "/npc head <id> <MATERIAL>  <dark_gray>- (armor stand) glowa",
                "/npc move <id>  <dark_gray>- przenies tu",
                "/npc tp <id>",
                "/npc reload"};
        for (String l : h) s.sendMessage(Text.raw("<gray>" + l));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("duckmc.admin")) {
            plugin.msg(s, "<red>Brak uprawnien.");
            return true;
        }
        if (a.length == 0) {
            help(s);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            plugin.msg(s, "NPC: <white>" + String.join("<gray>, <white>", npcs.keySet()));
            return true;
        }
        if (sub.equals("reload")) {
            reload();
            plugin.msg(s, "Przeladowano NPC.");
            return true;
        }
        if (a.length < 2) {
            help(s);
            return true;
        }
        String id = a[1].toLowerCase(Locale.ROOT);
        if (sub.equals("create")) {
            if (!(s instanceof Player p)) {
                plugin.msg(s, "Tylko dla graczy.");
                return true;
            }
            if (npcs.containsKey(id)) {
                plugin.msg(s, "<red>NPC o takim id juz istnieje.");
                return true;
            }
            Npc n = new Npc();
            n.id = id;
            n.loc = p.getLocation();
            if (a.length >= 3) {
                EntityType t = parseType(a[2]);
                if (t == null) {
                    plugin.msg(s, "<red>Zly typ encji.");
                    return true;
                }
                n.type = t;
            }
            n.lines.add("<yellow><bold>" + id.toUpperCase(Locale.ROOT));
            n.lines.add("<gray>Kliknij we mnie!");
            npcs.put(id, n);
            respawn(n);
            plugin.msg(s, "Stworzono NPC <white>" + id + "<gray>. Ustaw komende: <yellow>/npc command " + id + " <komenda>");
            return true;
        }
        Npc n = npcs.get(id);
        if (n == null) {
            plugin.msg(s, "<red>Nie ma NPC o id " + id);
            return true;
        }
        switch (sub) {
            case "remove" -> {
                despawn(n);
                npcs.remove(id);
                save();
                plugin.msg(s, "Usunieto NPC.");
            }
            case "addline" -> {
                if (a.length < 3) return help2(s);
                n.lines.add(String.join(" ", Arrays.copyOfRange(a, 2, a.length)));
                respawn(n);
                plugin.msg(s, "Dodano linie.");
            }
            case "setline" -> {
                if (a.length < 4) return help2(s);
                try {
                    int i = Integer.parseInt(a[2]) - 1;
                    String t = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
                    if (i < 0 || i >= n.lines.size()) {
                        plugin.msg(s, "<red>Nie ma takiej linii.");
                        return true;
                    }
                    n.lines.set(i, t);
                    respawn(n);
                    plugin.msg(s, "Zmieniono linie.");
                } catch (NumberFormatException ex) {
                    plugin.msg(s, "<red>Podaj numer linii.");
                }
            }
            case "clearlines" -> {
                n.lines.clear();
                respawn(n);
                plugin.msg(s, "Wyczyszczono linie.");
            }
            case "command" -> {
                if (a.length < 3) return help2(s);
                String c = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
                n.command = c.startsWith("/") ? c.substring(1) : c;
                save();
                plugin.msg(s, "Ustawiono komende: <white>/" + n.command);
            }
            case "type" -> {
                if (a.length < 3) return help2(s);
                EntityType t = parseType(a[2]);
                if (t == null) {
                    plugin.msg(s, "<red>Zly typ encji.");
                    return true;
                }
                n.type = t;
                respawn(n);
                plugin.msg(s, "Zmieniono typ.");
            }
            case "head" -> {
                if (a.length < 3) return help2(s);
                n.head = a[2].toUpperCase(Locale.ROOT);
                respawn(n);
                plugin.msg(s, "Ustawiono glowe (dziala dla ARMOR_STAND).");
            }
            case "move" -> {
                if (s instanceof Player p) {
                    n.loc = p.getLocation();
                    respawn(n);
                    plugin.msg(s, "Przeniesiono NPC.");
                }
            }
            case "tp" -> {
                if (s instanceof Player p) p.teleport(n.loc);
            }
            default -> help(s);
        }
        return true;
    }

    private boolean help2(CommandSender s) {
        help(s);
        return true;
    }

    private EntityType parseType(String s) {
        try {
            EntityType t = EntityType.valueOf(s.toUpperCase(Locale.ROOT));
            if (t == EntityType.PLAYER || !t.isSpawnable() || !t.isAlive()) return null;
            return t;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("create", "remove", "list", "addline", "setline", "clearlines", "command", "type", "head", "move", "tp", "reload"));
        } else if (a.length == 2 && !a[0].equalsIgnoreCase("create")) {
            out.addAll(npcs.keySet());
        } else if (a.length == 3 && (a[0].equalsIgnoreCase("create") || a[0].equalsIgnoreCase("type"))) {
            out.addAll(List.of("VILLAGER", "ARMOR_STAND", "ZOMBIE", "SKELETON", "IRON_GOLEM", "WITCH", "PIGLIN", "ALLAY", "WANDERING_TRADER"));
        }
        String last = a.length > 0 ? a[a.length - 1].toLowerCase(Locale.ROOT) : "";
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
