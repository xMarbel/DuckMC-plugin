package pl.duckmc.core;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class DuckMC extends JavaPlugin {
    private static final String[] FILES = {"ranks.yml", "scoreboard.yml", "patrons.yml", "swords.yml", "npcs.yml"};

    private static DuckMC instance;
    private DataManager data;
    private RankManager ranks;
    private PatronManager patrons;
    private ScoreboardManager boards;
    private NpcManager npcs;
    private SwordManager swords;
    private PatronListener patronListener;

    public static DuckMC get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        for (String f : FILES) {
            if (!new File(getDataFolder(), f).exists()) saveResource(f, false);
        }

        data = new DataManager(this);
        ranks = new RankManager(this);
        patrons = new PatronManager(this);
        swords = new SwordManager(this);
        boards = new ScoreboardManager(this);
        npcs = new NpcManager(this);
        patronListener = new PatronListener(this);

        PatronGui patronGui = new PatronGui(this);
        SetsGui setsGui = new SetsGui(this);

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(ranks, this);
        pm.registerEvents(patronGui, this);
        pm.registerEvents(setsGui, this);
        pm.registerEvents(patronListener, this);
        pm.registerEvents(npcs, this);
        pm.registerEvents(new GeneralListener(this), this);

        getCommand("patron").setExecutor((s, c, l, a) -> {
            if (s instanceof Player p) patronGui.open(p);
            else msg(s, "Tylko dla graczy.");
            return true;
        });
        getCommand("sety").setExecutor((s, c, l, a) -> {
            if (s instanceof Player p) setsGui.open(p);
            else msg(s, "Tylko dla graczy.");
            return true;
        });
        getCommand("ranga").setExecutor(ranks);
        getCommand("ranga").setTabCompleter(ranks);
        getCommand("npc").setExecutor(npcs);
        getCommand("npc").setTabCompleter(npcs);
        DuckCommand dc = new DuckCommand(this);
        getCommand("duck").setExecutor(dc);
        getCommand("duck").setTabCompleter(dc);

        Bukkit.getScheduler().runTaskTimer(this, () -> data.save(), 6000L, 6000L);
        patrons.start();
        boards.start();
        npcs.start();
        getLogger().info("DuckMC wlaczony.");
    }

    @Override
    public void onDisable() {
        if (boards != null) boards.stop();
        if (patrons != null) patrons.stop();
        if (npcs != null) npcs.shutdown();
        if (patronListener != null) patronListener.clearWebs();
        if (data != null) data.save();
    }

    public void reloadAll() {
        reloadConfig();
        ranks.load();
        patrons.load();
        swords.load();
        boards.reload();
        npcs.reload();
        for (Player p : Bukkit.getOnlinePlayers()) refresh(p);
        boards.syncAll();
    }

    /** Odswieza efekty zalezne od rangi/patrona (zycie od Afrodyty itd.). */
    public void refresh(Player p) {
        patrons.updateHealth(p);
    }

    public YamlConfiguration yaml(String name) {
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), name));
    }

    public void msg(CommandSender s, String text) {
        s.sendMessage(Text.raw(getConfig().getString("prefix", "") + text));
    }

    public DataManager data() { return data; }
    public RankManager ranks() { return ranks; }
    public PatronManager patrons() { return patrons; }
    public ScoreboardManager boards() { return boards; }
    public NpcManager npcs() { return npcs; }
    public SwordManager swords() { return swords; }
}
