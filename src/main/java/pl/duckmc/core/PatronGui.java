package pl.duckmc.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public final class PatronGui implements Listener {
    public static final class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // Y=zolty, O=pomaranczowy, L=jasnoszary, G/. = ciemnoszary
    private static final String[] GRID = {
            "YOLLGLLOY",
            "O.......O",
            "L.......L",
            "L.......L",
            "O.......O",
            "YOLLGLLOY"};

    private final DuckMC plugin;

    public PatronGui(DuckMC plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        PatronManager pm = plugin.patrons();
        YamlConfiguration c = pm.cfg();
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, Text.raw("<gold><bold>Patron"));
        h.inv = inv;

        for (int r = 0; r < 6; r++) {
            for (int col = 0; col < 9; col++) {
                Material m = switch (GRID[r].charAt(col)) {
                    case 'Y' -> Material.YELLOW_STAINED_GLASS_PANE;
                    case 'O' -> Material.ORANGE_STAINED_GLASS_PANE;
                    case 'L' -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
                    default -> Material.GRAY_STAINED_GLASS_PANE;
                };
                inv.setItem(r * 9 + col, Items.pane(m));
            }
        }

        List<Integer> slots = c.getIntegerList("gui.patron-slots");
        String mine = pm.patronOf(p.getUniqueId());
        int i = 0;
        for (PatronDef d : pm.all()) {
            if (i >= slots.size()) break;
            inv.setItem(slots.get(i++), patronItem(d, d.id().equals(mine)));
        }
        inv.setItem(c.getInt("gui.dice-slot", 12), dice(p));
        inv.setItem(c.getInt("gui.info-slot", 14), info(p));
        p.openInventory(inv);
    }

    private ItemStack patronItem(PatronDef d, boolean mine) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.c(d.colored()));
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<dark_gray>✖ <yellow>Dla Patronów: " + d.perk());
        lore.add("");
        lore.add("<dark_gray>✖ <green>Godzina Patrona: " + d.hourPerk());
        if (mine) {
            lore.add("");
            lore.add("<green>✔ To jest Twój aktualny patron");
            m.setEnchantmentGlintOverride(true);
        }
        m.lore(Text.c(lore));
        m.setItemModel(Items.model("patron_" + d.id()));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack dice(Player p) {
        PatronManager pm = plugin.patrons();
        YamlConfiguration c = pm.cfg();
        PlayerData d = plugin.data().get(p.getUniqueId());
        long cd = c.getLong("settings.free-draw-days", 3) * 86_400_000L;
        long left = cd - (System.currentTimeMillis() - d.lastDraw);
        boolean free = left <= 0;
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<dark_gray>✖ <gray>Patrona możesz za darmo losować co <yellow>" + c.getLong("settings.free-draw-days", 3) + " dni");
        lore.add("<dark_gray>✖ <gray>Koszt szybkiego wylosowania: <white>x" + c.getInt("settings.quick-cost", 2)
                + " <bold><gradient:#5B2BFF:#C400FF>" + pm.shardName());
        lore.add("");
        lore.add("<dark_gray>✖ <gray>Darmowe losowanie: " + (free ? "<green><bold>AKTYWNE" : "<red>za " + Text.duration(left)));
        lore.add("");
        lore.add("<green>Kliknij <yellow>LPM <green>aby wylosować za darmo!");
        lore.add("<green>Kliknij <yellow>PPM <green>aby wylosować szybko!");
        Material m = Material.matchMaterial(c.getString("gui.dice-material", "CRAFTER"));
        ItemStack it = Items.named(m != null ? m : Material.CRAFTER, "<aqua>♻ <bold>LOSOWANIE PATRONA <aqua>♻", lore);
        return it;
    }

    private ItemStack info(Player p) {
        PatronManager pm = plugin.patrons();
        YamlConfiguration c = pm.cfg();
        PlayerData d = plugin.data().get(p.getUniqueId());
        String mine = pm.patronOf(p.getUniqueId());
        PatronDef def = pm.def(mine);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<dark_gray>✖ <gray>Twój Patron: " + (def != null ? def.colored() : "<red>Brak"));
        if (def != null) lore.add("<dark_gray>✖ <gray>Wygasa za: <white>" + Text.duration(d.patronExpire - System.currentTimeMillis()));
        lore.add("");
        lore.add("<dark_gray>✖ <gray>Nowy Patron wspiera nas w grze przez <white>" + c.getLong("settings.duration-days", 5) + " dni");
        lore.add("");
        lore.add("<dark_gray>✖ <green>Godziny Patronów <gray>dostępne są codziennie od <yellow>" + pm.hoursText(false) + "</yellow>");
        lore.add("<dark_gray>✖ <gray>W weekendy są one przedłużone od <yellow>" + pm.hoursText(true) + "</yellow>");
        lore.add("<dark_gray>✖ <gray>Losowanie <green>Godziny Patrona <gray>zaczyna się zawsze o równej");
        lore.add("<dark_gray>✖ <gray>godzinie (<yellow>15:00, 16:00 <gray>itd.) a jej czas trwania wynosi <gold>"
                + c.getInt("settings.hour-duration-minutes", 30) + " minut");
        String hour = pm.hourPatron();
        if (hour != null) {
            lore.add("");
            lore.add("<dark_gray>✖ <green>Teraz trwa Godzina Patrona: " + pm.def(hour).colored());
        }
        return Items.named(Material.OAK_SIGN, "<aqua>❄ <bold>INFORMACJE <aqua>❄", lore);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int dice = plugin.patrons().cfg().getInt("gui.dice-slot", 12);
        if (e.getSlot() != dice) return;
        plugin.patrons().draw(p, e.getClick().isRightClick());
        Bukkit.getScheduler().runTask(plugin, () -> open(p));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
