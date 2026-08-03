package net.mentalsmp.killenchant;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UniversalEnchantListener implements Listener {

    private static final long RIPTIDE_COOLDOWN_MILLIS = 1000L;

    private final KillEnchant plugin;
    private final Map<UUID, Long> lastRiptideUse = new HashMap<>();

    public UniversalEnchantListener(KillEnchant plugin) {
        this.plugin = plugin;
    }

    /*
     * RIPTIDE AUF JEDEM ITEM
     *
     * Paper markiert Rechtsklicks in die Luft bei normalen Items teilweise
     * bereits als cancelled. Deshalb ignorieren wir cancelled Events hier nicht.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onRiptideUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR
                && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.AIR) {
            return;
        }

        // Auf einem echten Dreizack übernimmt Minecraft den Effekt.
        if (item.getType() == Material.TRIDENT) {
            return;
        }

        int riptideLevel = item.getEnchantmentLevel(Enchantment.RIPTIDE);

        if (riptideLevel <= 0) {
            return;
        }

        // Für dieses Projekt funktioniert Riptide ausschließlich im Wasser.
        if (!player.isInWater()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long previousUse = lastRiptideUse.getOrDefault(
                player.getUniqueId(),
                0L
        );

        if (currentTime - previousUse < RIPTIDE_COOLDOWN_MILLIS) {
            return;
        }

        lastRiptideUse.put(player.getUniqueId(), currentTime);
        event.setCancelled(true);

        double strength = 1.15D + (0.55D * riptideLevel);

        Vector velocity = player.getEyeLocation()
                .getDirection()
                .normalize()
                .multiply(strength)
                .add(new Vector(0.0D, 0.12D, 0.0D));

        player.setVelocity(velocity);
        player.setFallDistance(0.0F);

        Sound riptideSound = switch (riptideLevel) {
            case 1 -> Sound.ITEM_TRIDENT_RIPTIDE_1;
            case 2 -> Sound.ITEM_TRIDENT_RIPTIDE_2;
            default -> Sound.ITEM_TRIDENT_RIPTIDE_3;
        };

        World world = player.getWorld();

        world.playSound(
                player.getLocation(),
                riptideSound,
                1.0F,
                1.0F
        );

        world.spawnParticle(
                Particle.SPLASH,
                player.getLocation().add(0.0D, 1.0D, 0.0D),
                35,
                0.6D,
                0.8D,
                0.6D,
                0.15D
        );
    }

    /*
     * DENSITY, BREACH UND WIND BURST AUF JEDEM ITEM
     *
     * Density: zusätzlicher höhenabhängiger Smash-Schaden.
     * Breach: teilweise Rüstungsdurchdringung.
     * Wind Burst: schleudert den Angreifer hoch, gibt aber KEINEN
     * zusätzlichen höhenabhängigen Schaden.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUniversalMaceHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.AIR) {
            return;
        }

        // Eine echte Mace besitzt diese Mechaniken bereits durch Minecraft.
        if (item.getType() == Material.MACE) {
            return;
        }

        int densityLevel = item.getEnchantmentLevel(Enchantment.DENSITY);
        int breachLevel = item.getEnchantmentLevel(Enchantment.BREACH);
        int windBurstLevel = item.getEnchantmentLevel(Enchantment.WIND_BURST);

        if (densityLevel <= 0
                && breachLevel <= 0
                && windBurstLevel <= 0) {
            return;
        }

        double damage = event.getDamage();

        // Breach wirkt bei jedem Treffer, aber skaliert nicht mit der Fallhöhe.
        damage += calculateBreachBonus(
                event.getEntity() instanceof LivingEntity livingEntity
                        ? livingEntity
                        : null,
                damage,
                breachLevel
        );

        float fallDistance = player.getFallDistance();

        // Ohne richtigen Fall bleibt nur Breach aktiv.
        if (fallDistance <= 1.5F) {
            event.setDamage(damage);
            return;
        }

        /*
         * Nur Density gibt den Mace-artigen Fallschaden.
         * Wind Burst allein verändert den verursachten Schaden nicht.
         */
        if (densityLevel > 0) {
            double maceSmashBonus = calculateMaceSmashBonus(fallDistance);
            double densityBonus = fallDistance * 0.5D * densityLevel;
            damage += maceSmashBonus + densityBonus;
        }

        event.setDamage(damage);
        player.setFallDistance(0.0F);

        Location impact = event.getEntity()
                .getLocation()
                .add(0.0D, 0.6D, 0.0D);

        World world = impact.getWorld();

        if (world != null) {
            Sound smashSound = fallDistance >= 5.0F
                    ? Sound.ITEM_MACE_SMASH_GROUND_HEAVY
                    : Sound.ITEM_MACE_SMASH_GROUND;

            Particle gustEmitter = fallDistance >= 5.0F
                    ? Particle.GUST_EMITTER_LARGE
                    : Particle.GUST_EMITTER_SMALL;

            world.playSound(
                    impact,
                    smashSound,
                    1.25F,
                    1.0F
            );

            world.spawnParticle(
                    gustEmitter,
                    impact,
                    1
            );

            world.spawnParticle(
                    Particle.GUST,
                    impact,
                    14,
                    1.0D,
                    0.45D,
                    1.0D,
                    0.08D
            );

            world.spawnParticle(
                    Particle.EXPLOSION,
                    impact,
                    1
            );
        }

        // Wind Burst schleudert nur hoch und erhöht nicht den Treffer-Schaden.
        if (windBurstLevel > 0) {
            plugin.getServer().getScheduler().runTask(
                    plugin,
                    () -> {
                        if (!player.isOnline()) {
                            return;
                        }

                        Vector currentVelocity = player.getVelocity();
                        double launchHeight = 0.75D + (0.35D * windBurstLevel);

                        currentVelocity.setY(
                                Math.max(
                                        currentVelocity.getY(),
                                        launchHeight
                                )
                        );

                        player.setVelocity(currentVelocity);
                        player.setFallDistance(0.0F);
                    }
            );
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastRiptideUse.remove(event.getPlayer().getUniqueId());
    }

    private double calculateBreachBonus(
            LivingEntity target,
            double baseDamage,
            int breachLevel
    ) {
        if (target == null || breachLevel <= 0) {
            return 0.0D;
        }

        AttributeInstance armorAttribute = target.getAttribute(Attribute.ARMOR);

        if (armorAttribute == null) {
            return 0.0D;
        }

        double armorPoints = Math.max(0.0D, armorAttribute.getValue());
        double armorReductionEstimate = Math.min(0.80D, armorPoints * 0.04D);
        double bypassFraction = Math.min(0.60D, 0.15D * breachLevel);

        return baseDamage * armorReductionEstimate * bypassFraction;
    }

    private double calculateMaceSmashBonus(float fallDistance) {
        if (fallDistance <= 3.0F) {
            return 4.0D * fallDistance;
        }

        if (fallDistance <= 8.0F) {
            return 12.0D
                    + (2.0D * (fallDistance - 3.0F));
        }

        return 22.0D + fallDistance - 8.0F;
    }
}
