package pl.duckmc.core;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RankManager implements Listener, CommandExecutor, TabCompleter {
    private final DuckMC plugin;
    private final Map<String, Rank> ranks = new LinkedHashMap<>();

    public RankManager(DuckMC plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        ranks.clear();
        YamlConfiguration y = plugin.yaml("ranks.yml");
        ConfigurationSection s = y.getConfigurationSection("ranks");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection c = s.getConfigurationSection(id);
                if (c == null) continue;
                ranks.put(id.toLowerCase(), new Rank(id.toLowerCase(),
                        c.getString("name", id),
                        c.getString("prefix", "[" + id + "] "),
                        c.getString("name-color", "<white>"),
                        c.getString("chat-color", "<gray>"),
                        c.getInt("weight", 1)));
            }
        }
        if (!ranks.containsKey("gracz")) {
            ranks.put("gracz", new Rank("gracz", "<gray>Gracz", "<dark_gray>[<gray>Gracz<dark_gray>] ", "<gray>", "<gray>", 1));
        }
    }

    public Rank get(String id) {
        return id == null ? null : ranks.get(id.toLowerCase());
    }

    public Rank rank(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        Rank r = get(d.rank);
        return r != null ? r : ranks.get("gracz");
    }

    public Iterable<Rank> all() {
        return ranks.values();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Rank r = rank(p);
        e.renderer(ChatRenderer.viewerUnaware((source, name, msg) ->
                Text.raw(r.prefix())
                        .append(Text.raw(r.nameColor() + p.getName()))
                        .append(Text.raw("<dark_gray> » "))
                        .append(Text.raw(r.chatColor()).append(msg))));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("info")) {
            if (s instanceof Player p) plugin.msg(s, "Twoja ranga: " + rank(p).name());
            return true;
        }
        if (a[0].equalsIgnoreCase("lista")) {
            StringBuilder sb = new StringBuilder("Dostepne rangi: ");
            for (Rank r : ranks.values()) sb.append(r.name()).append("<gray>, ");
            plugin.msg(s, sb.toString());
            return true;
        }
        if (!s.hasPermission("duckmc.admin")) {
            plugin.msg(s, "<red>Brak uprawnien.");
            return true;
        }
        if (a[0].equalsIgnoreCase("set") && a.length >= 3) {
            Rank r = get(a[2]);
            if (r == null) {
                plugin.msg(s, "<red>Nie ma takiej rangi. Uzyj /ranga lista");
                return true;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayer(a[1]);
            plugin.data().get(op.getUniqueId()).rank = r.id();
            plugin.msg(s, "Ustawiono range " + r.name() + "<gray> dla <white>" + a[1]);
            Player online = op.getPlayer();
            if (online != null) plugin.refresh(online);
            plugin.boards().syncAll();
            return true;
        }
        plugin.msg(s, "<yellow>/ranga set <gracz> <ranga> <gray>| <yellow>/ranga lista");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.add("lista");
            out.add("info");
            if (s.hasPermission("duckmc.admin")) out.add("set");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("set")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("set")) {
            out.addAll(ranks.keySet());
        }
        return out;
    }
}
