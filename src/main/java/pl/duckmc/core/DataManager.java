package pl.duckmc.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DataManager {
    private final DuckMC plugin;
    private final File file;
    private final Map<UUID, PlayerData> map = new HashMap<>();

    public DataManager(DuckMC plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("players");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection c = s.getConfigurationSection(key);
                if (c == null) continue;
                PlayerData d = new PlayerData();
                d.rank = c.getString("rank", "gracz");
                d.patron = c.getString("patron", null);
                d.patronExpire = c.getLong("patron-expire", 0);
                d.lastDraw = c.getLong("last-draw", 0);
                d.money = c.getDouble("money", 0);
                d.kills = c.getInt("kills", 0);
                d.deaths = c.getInt("deaths", 0);
                d.points = c.getInt("points", 0);
                d.essence = c.getInt("essence", 0);
                d.silver = c.getInt("silver", 0);
                d.gold = c.getInt("gold", 0);
                d.clan = c.getString("clan", "-");
                map.put(id, d);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public PlayerData get(UUID id) {
        return map.computeIfAbsent(id, k -> new PlayerData());
    }

    public Map<UUID, PlayerData> all() {
        return map;
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerData> e : map.entrySet()) {
            String p = "players." + e.getKey() + ".";
            PlayerData d = e.getValue();
            y.set(p + "rank", d.rank);
            y.set(p + "patron", d.patron);
            y.set(p + "patron-expire", d.patronExpire);
            y.set(p + "last-draw", d.lastDraw);
            y.set(p + "money", d.money);
            y.set(p + "kills", d.kills);
            y.set(p + "deaths", d.deaths);
            y.set(p + "points", d.points);
            y.set(p + "essence", d.essence);
            y.set(p + "silver", d.silver);
            y.set(p + "gold", d.gold);
            y.set(p + "clan", d.clan);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Nie mozna zapisac players.yml: " + ex.getMessage());
        }
    }
}
