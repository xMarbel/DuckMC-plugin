package pl.duckmc.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class Items {
    private Items() {}

    /** Klucz modelu z paczki tekstur: duckmc:nazwa */
    public static NamespacedKey model(String name) {
        return NamespacedKey.fromString("duckmc:" + name);
    }

    public static ItemStack named(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.c(name));
        if (lore != null && !lore.isEmpty()) meta.lore(Text.c(lore));
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(" "));
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }
}
