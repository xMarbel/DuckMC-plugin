package pl.duckmc.core;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.*;

public final class SwordManager {
    private final DuckMC plugin;
    private final Map<Integer, SwordDef> defs = new TreeMap<>();

    public SwordManager(DuckMC plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        defs.clear();
        YamlConfiguration y = plugin.yaml("swords.yml");
        ConfigurationSection s = y.getConfigurationSection("levels");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                int lvl = Integer.parseInt(k);
                ConfigurationSection c = s.getConfigurationSection(k);
                if (c == null) continue;
                String color = c.getString("color", "#FFFFFF");
                defs.put(lvl, new SwordDef(lvl, c.getString("sword", "Miecz " + lvl),
                        c.getString("genitive", "Poziomu " + lvl), color,
                        c.getString("armor-color", color), c.getDouble("price", 1000)));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public SwordDef def(int level) { return defs.get(level); }
    public Collection<SwordDef> all() { return defs.values(); }

    private static Color rgb(String hex) {
        try {
            return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (NumberFormatException e) {
            return Color.WHITE;
        }
    }

    private ItemStack base(Material m, String name, SwordDef d, List<String> extraLore, Map<Enchantment, Integer> ench) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.c("<" + d.color() + "><bold>" + name.toUpperCase(Locale.ROOT) + " <gold>★<yellow>" + d.level()));
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(extraLore);
        lore.add("");
        lore.add("<dark_gray>✖ <white>Poziom seta: <yellow>" + d.level());
        lore.add("<dark_gray>✖ <white>Cena: <green>$" + Text.money(d.price()) + " <gray>($" + Text.full(d.price()) + ")");
        meta.lore(Text.c(lore));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DYE);
        if (meta instanceof LeatherArmorMeta lm) lm.setColor(rgb(d.armorColor()));
        for (var e : ench.entrySet()) meta.addEnchant(e.getKey(), e.getValue(), true);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack sword(int level) {
        SwordDef d = defs.get(level);
        if (d == null) return null;
        int sharp = level * 2 + 1;
        int unb = level * 2;
        ItemStack it = base(Material.NETHERITE_SWORD, d.sword(), d, List.of(
                "<dark_gray>[<red>⚔<dark_gray>] <red>Ostrość <gold>" + sharp,
                "<dark_gray>[<green>❤<dark_gray>] <green>Niezniszczalność <green>" + unb,
                "<dark_gray>──«─«─»─»──"),
                Map.of(Enchantment.SHARPNESS, sharp, Enchantment.UNBREAKING, 3));
        ItemMeta m = it.getItemMeta();
        m.setItemModel(Items.model("sword_" + level));
        it.setItemMeta(m);
        return it;
    }

    public List<ItemStack> set(int level) {
        SwordDef d = defs.get(level);
        if (d == null) return List.of();
        int prot = Math.min(level, 10);
        String g = d.genitive();
        List<String> armorLore = List.of("<dark_gray>[<aqua>🛡<dark_gray>] <aqua>Ochrona <white>" + prot,
                "<dark_gray>──«─«─»─»──");
        Map<Enchantment, Integer> pe = Map.of(Enchantment.PROTECTION, prot, Enchantment.UNBREAKING, 3);
        List<ItemStack> out = new ArrayList<>();
        out.add(base(Material.LEATHER_HELMET, "Hełm " + g, d, armorLore, pe));
        out.add(base(Material.LEATHER_CHESTPLATE, "Napierśnik " + g, d, armorLore, pe));
        out.add(base(Material.LEATHER_LEGGINGS, "Spodnie " + g, d, armorLore, pe));
        out.add(base(Material.LEATHER_BOOTS, "Buty " + g, d, armorLore, pe));
        out.add(sword(level));
        out.add(base(Material.NETHERITE_AXE, "Topór " + g, d, List.of("<dark_gray>──«─«─»─»──"),
                Map.of(Enchantment.SHARPNESS, level * 2 + 1, Enchantment.EFFICIENCY, Math.min(level * 2, 50))));
        out.add(base(Material.NETHERITE_SHOVEL, "Łopata " + g, d, List.of("<dark_gray>──«─«─»─»──"),
                Map.of(Enchantment.EFFICIENCY, Math.min(level * 2, 50))));
        return out;
    }
}
