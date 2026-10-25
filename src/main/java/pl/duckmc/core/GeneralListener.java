package pl.duckmc.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class GeneralListener implements Listener {
    private final DuckMC plugin;

    public GeneralListener(DuckMC plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.data().get(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.refresh(p);
            plugin.boards().syncAll();
        }, 5L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendPack(p), 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.boards().remove(e.getPlayer());
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.boards().syncAll(), 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.refresh(p), 2L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        PlayerData vd = plugin.data().get(victim.getUniqueId());
        vd.deaths++;
        vd.points = Math.max(0, vd.points + plugin.getConfig().getInt("points.per-death", -5));
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            PlayerData kd = plugin.data().get(killer.getUniqueId());
            kd.kills++;
            kd.points += plugin.getConfig().getInt("points.per-kill", 10);
            kd.money += plugin.getConfig().getDouble("points.kill-money", 25.0);
        }
    }

    public void sendPack(Player p) {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("resource-pack.enabled", false)) return;
        String url = cfg.getString("resource-pack.url", "");
        if (url == null || url.isBlank()) return;
        byte[] hash = null;
        String sha = cfg.getString("resource-pack.sha1", "");
        if (sha != null && sha.length() == 40) {
            hash = new byte[20];
            for (int i = 0; i < 20; i++) {
                hash[i] = (byte) Integer.parseInt(sha.substring(i * 2, i * 2 + 2), 16);
            }
        }
        Component prompt = Text.raw(cfg.getString("resource-pack.prompt", "Paczka tekstur DuckMC"));
        p.setResourcePack(url, hash, prompt, cfg.getBoolean("resource-pack.force", false));
    }
}
