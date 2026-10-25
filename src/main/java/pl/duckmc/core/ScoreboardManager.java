package pl.duckmc.core;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;

public final class ScoreboardManager {
    private static final class Board {
        Scoreboard sb;
        Objective obj;
        int n = 0;
        final List<Team> lineTeams = new ArrayList<>();
        final List<String> entries = new ArrayList<>();
    }

    private final DuckMC plugin;
    private YamlConfiguration cfg;
    private final Map<UUID, Board> boards = new HashMap<>();
    private final Map<UUID, Integer> pos = new HashMap<>();
    private BukkitTask task;
    private int tick = 0;

    public ScoreboardManager(DuckMC plugin) {
        this.plugin = plugin;
        this.cfg = plugin.yaml("scoreboard.yml");
    }

    public void start() {
        int period = Math.max(1, cfg.getInt("update-ticks", 10));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, period);
    }

    public void stop() {
        if (task != null) task.cancel();
        boards.clear();
    }

    public void reload() {
        stop();
        cfg = plugin.yaml("scoreboard.yml");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        start();
    }

    public void remove(Player p) {
        boards.remove(p.getUniqueId());
    }

    private void updateAll() {
        tick++;
        if (tick % 60 == 1) calcRanks();
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }

    private void calcRanks() {
        List<Map.Entry<UUID, PlayerData>> l = new ArrayList<>(plugin.data().all().entrySet());
        l.sort((a, b) -> Integer.compare(b.getValue().points, a.getValue().points));
        pos.clear();
        int i = 1;
        for (var e : l) pos.put(e.getKey(), i++);
    }

    private Component title() {
        String t = cfg.getString("title", "DUCKMC.PL");
        List<String> cols = cfg.getStringList("title-colors");
        if (cols.size() < 2) cols = List.of("#FFD700", "#FF8C00", "#FF3CAC");
        double speed = cfg.getDouble("title-speed", 0.04);
        double phase = ((tick * speed) % 2.0) - 1.0;
        String mm = "<bold><gradient:" + String.join(":", cols) + ":" + String.format(Locale.US, "%.2f", phase) + ">" + t;
        return Text.raw(mm);
    }

    private void update(Player p) {
        Board b = boards.get(p.getUniqueId());
        if (b == null) {
            b = new Board();
            b.sb = Bukkit.getScoreboardManager().getNewScoreboard();
            b.obj = b.sb.registerNewObjective("duck", Criteria.DUMMY, Component.text("DuckMC"));
            b.obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            boards.put(p.getUniqueId(), b);
            p.setScoreboard(b.sb);
            sync(b);
        }
        if (p.getScoreboard() != b.sb) p.setScoreboard(b.sb);

        List<String> lines = cfg.getStringList("lines");
        int n = Math.min(lines.size(), 15);
        if (n != b.n) build(b, n);
        b.obj.displayName(title());

        Map<String, String> ph = placeholders(p);
        for (int i = 0; i < n; i++) {
            String s = lines.get(i);
            for (Map.Entry<String, String> e : ph.entrySet()) s = s.replace(e.getKey(), e.getValue());
            b.lineTeams.get(i).prefix(Text.raw(s));
        }

        if (tick % 20 == 0) {
            String on = String.valueOf(Bukkit.getOnlinePlayers().size());
            String h = cfg.getString("tab-header", "").replace("%online%", on);
            String f = cfg.getString("tab-footer", "").replace("%online%", on);
            p.sendPlayerListHeaderAndFooter(Text.raw(h), Text.raw(f));
        }
    }

    private void build(Board b, int n) {
        for (String e : b.entries) b.sb.resetScores(e);
        for (Team t : b.lineTeams) t.unregister();
        b.entries.clear();
        b.lineTeams.clear();
        for (int i = 0; i < n; i++) {
            String entry = "\u00a7" + Integer.toHexString(i) + "\u00a7r";
            Team t = b.sb.registerNewTeam("l" + i);
            t.addEntry(entry);
            Score s = b.obj.getScore(entry);
            s.setScore(n - i);
            s.numberFormat(NumberFormat.blank());
            b.entries.add(entry);
            b.lineTeams.add(t);
        }
        b.n = n;
    }

    private Map<String, String> placeholders(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        Rank r = plugin.ranks().rank(p);
        String patronId = plugin.patrons().patronOf(p.getUniqueId());
        String patron = "<gray>Brak";
        if (patronId != null) {
            PatronDef def = plugin.patrons().def(patronId);
            if (def != null) patron = def.colored();
        }
        Map<String, String> m = new HashMap<>();
        m.put("%kanal%", cfg.getString("channel", plugin.getConfig().getString("channel", "main1")));
        m.put("%nick%", p.getName());
        m.put("%ranga%", r.name());
        m.put("%patron%", patron);
        m.put("%stan%", Text.money(d.money));
        m.put("%kills%", String.valueOf(d.kills));
        m.put("%deaths%", String.valueOf(d.deaths));
        m.put("%points%", String.valueOf(d.points));
        m.put("%pos%", String.valueOf(pos.getOrDefault(p.getUniqueId(), 0)));
        m.put("%czas%", Text.playtime(p.getStatistic(Statistic.PLAY_ONE_MINUTE)));
        m.put("%esencje%", String.valueOf(d.essence));
        m.put("%srebrne%", String.valueOf(d.silver));
        m.put("%zlote%", String.valueOf(d.gold));
        m.put("%klan%", d.clan == null ? "-" : d.clan);
        m.put("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        return m;
    }

    /** Synchronizuje druzyny z prefixami rang (nametag + kolejnosc w tabie). */
    public void syncAll() {
        for (Board b : boards.values()) sync(b);
    }

    private void sync(Board b) {
        Set<String> online = new HashSet<>();
        for (Player q : Bukkit.getOnlinePlayers()) {
            Rank r = plugin.ranks().rank(q);
            String tn = "z" + String.format("%02d", Math.max(0, 99 - r.weight())) + r.id();
            if (tn.length() > 40) tn = tn.substring(0, 40);
            Team t = b.sb.getTeam(tn);
            if (t == null) t = b.sb.registerNewTeam(tn);
            t.prefix(Text.raw(r.prefix()));
            Team cur = b.sb.getEntryTeam(q.getName());
            if (cur != t) {
                if (cur != null) cur.removeEntry(q.getName());
                t.addEntry(q.getName());
            }
            online.add(q.getName());
        }
        for (Team t : b.sb.getTeams()) {
            if (!t.getName().startsWith("z")) continue;
            for (String en : new HashSet<>(t.getEntries())) {
                if (!online.contains(en)) t.removeEntry(en);
            }
        }
    }
}
