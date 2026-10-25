package pl.duckmc.core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public final class SetsGui implements Listener {
    public static final class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final DuckMC plugin;

    public SetsGui(DuckMC plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, Text.raw("<dark_gray><bold>Do ulepszenia"));
        h.inv = inv;
        for (int i = 0; i < 54; i++) inv.setItem(i, Items.pane(Material.GRAY_STAINED_GLASS_PANE));
        double money = plugin.data().get(p.getUniqueId()).money;
        for (SwordDef d : plugin.swords().all()) {
            int slot = 9 + (d.level() - 1) + ((d.level() - 1) / 7) * 2;
            if (slot >= 54) continue;
            ItemStack it = plugin.swords().sword(d.level());
            ItemMeta m = it.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>(m.lore() != null ? m.lore() : List.of());
            lore.add(Text.c(""));
            lore.add(Text.c(money >= d.price() ? "<green>Kliknij aby kupić cały zestaw!" : "<red>Nie stać Cię na ten zestaw."));
            m.lore(lore);
            it.setItemMeta(m);
            inv.setItem(slot, it);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        for (SwordDef d : plugin.swords().all()) {
            int slot = 9 + (d.level() - 1) + ((d.level() - 1) / 7) * 2;
            if (slot != e.getSlot()) continue;
            PlayerData pd = plugin.data().get(p.getUniqueId());
            if (pd.money < d.price()) {
                plugin.msg(p, "<red>Nie masz wystarczająco pieniędzy! Potrzebujesz <green>$" + Text.full(d.price()));
                return;
            }
            pd.money -= d.price();
            giveSet(p, d.level());
            plugin.msg(p, "Kupiono zestaw poziomu <yellow>" + d.level() + "<gray>!");
            Bukkit.getScheduler().runTask(plugin, () -> open(p));
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    public void giveSet(Player p, int level) {
        for (ItemStack it : plugin.swords().set(level)) {
            p.getInventory().addItem(it).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
        }
    }
}
