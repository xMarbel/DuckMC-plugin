package pl.duckmc.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/** Efekty patronow (perki) oraz efekty Godziny Patrona. */
public final class PatronListener implements Listener {
    private final DuckMC plugin;
    private final PatronManager pm;
    private final Random rnd = new Random();
    private final Set<UUID> busy = new HashSet<>();
    private final Set<Location> webs = new HashSet<>();

    public PatronListener(DuckMC plugin) {
        this.plugin = plugin;
        this.pm = plugin.patrons();
    }

    private boolean chance(double c) {
        return rnd.nextDouble() < c;
    }

    private ConfigurationSection v(String id) {
        return pm.values(id);
    }

    // ------------------------------------------------------------------ walka

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player a) attacker = a;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player a2) attacker = a2;
        if (attacker == null || attacker.equals(victim) || busy.contains(attacker.getUniqueId())) return;
        final Player atk = attacker;

        // Hefajstos (patron ofiary): szansa na zablokowanie uderzenia
        if (pm.has(victim, "hefajstos") && chance(v("hefajstos").getDouble("block-chance", 0.10))) {
            e.setCancelled(true);
            victim.sendActionBar(Text.raw("<gold>Zablokowano uderzenie! <gray>(Hefajstos)"));
            return;
        }

        // Perki patrona atakujacego
        String own = pm.patronOf(atk.getUniqueId());
        if (own != null) {
            switch (own) {
                case "cyklop" -> {
                    ConfigurationSection c = v("cyklop");
                    if (chance(c.getDouble("slow-chance", 0.15))) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                                c.getInt("slow-seconds", 3) * 20, c.getInt("slow-level", 1)));
                    }
                }
                case "kratos" -> {
                    if (chance(v("kratos").getDouble("web-chance", 0.10))) placeWeb(victim);
                }
                case "tanatos" -> {
                    if (chance(v("tanatos").getDouble("helmet-chance", 0.05))) removeHelmet(victim);
                }
                case "hades" -> {
                    ConfigurationSection c = v("hades");
                    if (atk.getHealth() <= c.getDouble("low-hp", 10.0)) {
                        e.setDamage(e.getDamage() * c.getDouble("damage-multiplier", 1.25));
                    }
                }
                default -> {
                }
            }
        }

        // Efekty Godziny Patrona (dzialaja dla wszystkich graczy)
        String hour = pm.hourPatron();
        if (hour == null) return;
        switch (hour) {
            case "hades" -> {
                ConfigurationSection c = v("hades");
                if (chance(c.getDouble("hour-extra-chance", 0.15))) {
                    double extra = e.getFinalDamage() * c.getDouble("hour-extra-damage-percent", 0.5);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        busy.add(atk.getUniqueId());
                        try {
                            victim.setNoDamageTicks(0);
                            victim.damage(extra, atk);
                        } finally {
                            busy.remove(atk.getUniqueId());
                        }
                    });
                }
            }
            case "hefajstos" -> {
                ConfigurationSection c = v("hefajstos");
                if (chance(c.getDouble("hour-weakness-chance", 0.15))) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                            c.getInt("weakness-seconds", 5) * 20, 0));
                }
            }
            case "helios" -> {
                ConfigurationSection c = v("helios");
                if (chance(c.getDouble("hour-glow-chance", 0.20))) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                            c.getInt("glow-seconds", 5) * 20, 0));
                }
            }
            default -> {
            }
        }
    }

    private void removeHelmet(Player victim) {
        ItemStack h = victim.getInventory().getHelmet();
        if (h == null || h.getType().isAir()) return;
        victim.getInventory().setHelmet(null);
        victim.getInventory().addItem(h).values()
                .forEach(i -> victim.getWorld().dropItemNaturally(victim.getLocation(), i));
        victim.sendActionBar(Text.raw("<light_purple>Zdjeto Ci helm! <gray>(Tanatos)"));
    }

    private void placeWeb(Player victim) {
        Block b = victim.getLocation().getBlock();
        if (!b.getType().isAir()) return;
        Location key = b.getLocation();
        b.setType(Material.COBWEB);
        webs.add(key);
        long ticks = v("kratos").getInt("web-seconds", 4) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (webs.remove(key) && key.getBlock().getType() == Material.COBWEB) key.getBlock().setType(Material.AIR);
        }, ticks);
    }

    public void clearWebs() {
        for (Location l : new ArrayList<>(webs)) {
            if (l.getBlock().getType() == Material.COBWEB) l.getBlock().setType(Material.AIR);
        }
        webs.clear();
    }

    // ------------------------------------------------------------------ zlote jablka

    @EventHandler
    public void onEat(PlayerItemConsumeEvent e) {
        if (e.getItem().getType() != Material.GOLDEN_APPLE) return;
        Player p = e.getPlayer();

        if (pm.has(p, "helios") && chance(v("helios").getDouble("coconut-chance", 0.10))) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 400, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 6000, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 6000, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 3));
                p.sendActionBar(Text.raw("<gold>Kokos! <gray>(Helios)"));
            });
        }
        if ("cyklop".equals(pm.hourPatron())) {
            ConfigurationSection c = v("cyklop");
            Bukkit.getScheduler().runTask(plugin, () -> p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                    c.getInt("hour-regen-seconds", 10) * 20, c.getInt("hour-regen-level", 2))));
        }
    }

    // ------------------------------------------------------------------ pajeczyny / fajerwerki

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if ("kratos".equals(pm.hourPatron()) && e.getBlockPlaced().getType() == Material.COBWEB) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Text.raw("<red>Trwa Godzina Patrona Kratosa - pajeczyny sa zablokowane!"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFirework(PlayerInteractEvent e) {
        if (!"tanatos".equals(pm.hourPatron())) return;
        if (!e.getAction().isRightClick()) return;
        ItemStack it = e.getItem();
        if (it == null || it.getType() != Material.FIREWORK_ROCKET) return;
        Player p = e.getPlayer();
        if (p.hasCooldown(Material.FIREWORK_ROCKET)) return;
        int secs = v("tanatos").getInt("hour-firework-cooldown-seconds", 10);
        p.setCooldown(Material.FIREWORK_ROCKET, secs * 20);
    }

    // ------------------------------------------------------------------ drop (Artemida)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || !e.isDropItems()) return;
        ConfigurationSection c = v("artemida");
        Block b = e.getBlock();
        String mat = b.getType().name();
        int sets = 0;
        if (pm.has(p, "artemida") && c.getStringList("drop-blocks").contains(mat)
                && chance(c.getDouble("drop-chance", 0.20))) sets++;
        if ("artemida".equals(pm.hourPatron()) && c.getStringList("generator-blocks").contains(mat)
                && chance(c.getDouble("turbo-multiplier", 1.3) - 1.0)) sets++;
        if (sets == 0) return;
        Collection<ItemStack> drops = b.getDrops(p.getInventory().getItemInMainHand(), p);
        Location at = b.getLocation().add(0.5, 0.5, 0.5);
        for (int i = 0; i < sets; i++) {
            for (ItemStack d : drops) b.getWorld().dropItemNaturally(at, d.clone());
        }
    }
}
