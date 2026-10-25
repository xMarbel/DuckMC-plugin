package pl.duckmc.core;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DuckCommand implements CommandExecutor, TabCompleter {
    private final DuckMC plugin;

    public DuckCommand(DuckMC plugin) {
        this.plugin = plugin;
    }

    private void help(CommandSender s) {
        plugin.msg(s, "<yellow>Komendy DuckMC:");
        String[] h = {
                "/duck reload",
                "/duck zestaw <gracz> <poziom>",
                "/duck miecz <gracz> <poziom>",
                "/duck odlamek <gracz> <ile>",
                "/duck kasa <give|take|set> <gracz> <kwota>",
                "/duck stat <gracz> <esencje|srebrne|zlote|punkty|klan> <wartosc>",
                "/duck patron <gracz> <id|none> [dni]",
                "/duck godzina <start <patron> [min]|stop|status>",
                "/duck paczka [gracz]"};
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
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadAll();
                plugin.msg(s, "Przeladowano konfiguracje.");
            }
            case "zestaw", "miecz" -> {
                if (a.length < 3) return usage(s);
                Player t = Bukkit.getPlayerExact(a[1]);
                Integer lvl = parseInt(a[2]);
                if (t == null || lvl == null || plugin.swords().def(lvl) == null) {
                    plugin.msg(s, "<red>Zly gracz lub poziom.");
                    return true;
                }
                List<ItemStack> items = a[0].equalsIgnoreCase("miecz") ? List.of(plugin.swords().sword(lvl)) : plugin.swords().set(lvl);
                for (ItemStack it : items) {
                    t.getInventory().addItem(it).values().forEach(r -> t.getWorld().dropItemNaturally(t.getLocation(), r));
                }
                plugin.msg(s, "Dano graczowi " + t.getName() + " przedmioty poziomu " + lvl);
            }
            case "odlamek" -> {
                if (a.length < 3) return usage(s);
                Player t = Bukkit.getPlayerExact(a[1]);
                Integer n = parseInt(a[2]);
                if (t == null || n == null || n < 1) {
                    plugin.msg(s, "<red>Zly gracz lub ilosc.");
                    return true;
                }
                t.getInventory().addItem(plugin.patrons().shard(n)).values()
                        .forEach(r -> t.getWorld().dropItemNaturally(t.getLocation(), r));
                plugin.msg(s, "Dano " + n + "x odlamek graczowi " + t.getName());
            }
            case "kasa" -> {
                if (a.length < 4) return usage(s);
                var op = Bukkit.getOfflinePlayer(a[2]);
                PlayerData d = plugin.data().get(op.getUniqueId());
                double v;
                try {
                    v = Double.parseDouble(a[3]);
                } catch (NumberFormatException e) {
                    plugin.msg(s, "<red>Zla kwota.");
                    return true;
                }
                switch (a[1].toLowerCase(Locale.ROOT)) {
                    case "give" -> d.money += v;
                    case "take" -> d.money = Math.max(0, d.money - v);
                    case "set" -> d.money = Math.max(0, v);
                    default -> {
                        return usage(s);
                    }
                }
                plugin.msg(s, "Stan konta " + a[2] + ": <green>$" + Text.money(d.money));
            }
            case "stat" -> {
                if (a.length < 4) return usage(s);
                var op = Bukkit.getOfflinePlayer(a[1]);
                PlayerData d = plugin.data().get(op.getUniqueId());
                String f = a[2].toLowerCase(Locale.ROOT);
                if (f.equals("klan")) {
                    d.clan = a[3];
                } else {
                    Integer v = parseInt(a[3]);
                    if (v == null) {
                        plugin.msg(s, "<red>Zla wartosc.");
                        return true;
                    }
                    switch (f) {
                        case "esencje" -> d.essence = v;
                        case "srebrne" -> d.silver = v;
                        case "zlote" -> d.gold = v;
                        case "punkty" -> d.points = v;
                        default -> {
                            return usage(s);
                        }
                    }
                }
                plugin.msg(s, "Ustawiono.");
            }
            case "patron" -> {
                if (a.length < 3) return usage(s);
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) {
                    plugin.msg(s, "<red>Gracz offline.");
                    return true;
                }
                if (a[2].equalsIgnoreCase("none")) {
                    plugin.patrons().setPatron(t, null, 0);
                    plugin.msg(s, "Usunieto patrona.");
                } else {
                    if (plugin.patrons().def(a[2]) == null) {
                        plugin.msg(s, "<red>Nie ma takiego patrona.");
                        return true;
                    }
                    Integer days = a.length >= 4 ? parseInt(a[3]) : Integer.valueOf(5);
                    plugin.patrons().setPatron(t, a[2], days == null ? 5 : days);
                    plugin.msg(s, "Ustawiono patrona.");
                }
            }
            case "godzina" -> {
                if (a.length < 2) return usage(s);
                switch (a[1].toLowerCase(Locale.ROOT)) {
                    case "start" -> {
                        if (a.length < 3) return usage(s);
                        Integer min = a.length >= 4 ? parseInt(a[3]) : Integer.valueOf(30);
                        if (!plugin.patrons().startManual(a[2], min == null ? 30 : min))
                            plugin.msg(s, "<red>Nie ma takiego patrona.");
                    }
                    case "stop" -> plugin.patrons().stopManual();
                    default -> {
                        String h = plugin.patrons().hourPatron();
                        plugin.msg(s, h == null ? "Brak aktywnej godziny patrona." : "Aktywna: " + plugin.patrons().def(h).colored());
                    }
                }
            }
            case "paczka" -> {
                GeneralListener gl = new GeneralListener(plugin);
                if (a.length >= 2) {
                    Player t = Bukkit.getPlayerExact(a[1]);
                    if (t != null) gl.sendPack(t);
                } else {
                    for (Player p : Bukkit.getOnlinePlayers()) gl.sendPack(p);
                }
                plugin.msg(s, "Wyslano paczke tekstur.");
            }
            default -> help(s);
        }
        return true;
    }

    private boolean usage(CommandSender s) {
        help(s);
        return true;
    }

    private Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("reload", "zestaw", "miecz", "odlamek", "kasa", "stat", "patron", "godzina", "paczka"));
        } else if (a.length == 2 && List.of("zestaw", "miecz", "odlamek", "stat", "patron", "paczka").contains(a[0].toLowerCase())) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("kasa")) {
            out.addAll(List.of("give", "take", "set"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("godzina")) {
            out.addAll(List.of("start", "stop", "status"));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("godzina") && a[1].equalsIgnoreCase("start")) {
            plugin.patrons().all().forEach(d -> out.add(d.id()));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("patron")) {
            plugin.patrons().all().forEach(d -> out.add(d.id()));
            out.add("none");
        } else if (a.length == 3 && a[0].equalsIgnoreCase("stat")) {
            out.addAll(List.of("esencje", "srebrne", "zlote", "punkty", "klan"));
        }
        String last = a.length > 0 ? a[a.length - 1].toLowerCase() : "";
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
