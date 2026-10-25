package pl.duckmc.core;

import org.bukkit.configuration.ConfigurationSection;

public record PatronDef(String id, String name, String color, String perk, String hourPerk,
                        ConfigurationSection values) {
    public String colored() {
        return "<" + color + ">" + name;
    }
}
