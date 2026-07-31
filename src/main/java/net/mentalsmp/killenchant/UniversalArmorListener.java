package net.mentalsmp.killenchant;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.concurrent.ThreadLocalRandom;

public final class UniversalArmorListener implements Listener {

    private final KillEnchant plugin;

    private final NamespacedKey aquaAffinityKey;
    private final NamespacedKey depthStriderKey;
    private final NamespacedKey soulSpeedKey;
    private final NamespacedKey swiftSneakKey;

    private boolean applyingThornsDamage;

    public UniversalArmorListener(KillEnchant plugin) {
        this.plugin = plugin;

        this.aquaAffinityKey =
                new NamespacedKey(plugin, "universal_aqua_affinity");

        this.depthStriderKey =
                new NamespacedKey(plugin, "universal_depth_strider");

        this.soulSpeedKey =
                new NamespacedKey(plugin, "universal_soul_speed");

        this.swiftSneakKey =
                new NamespacedKey(plugin, "universal_swift_sneak");

        plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::updateMovementEnchantments,
                1L,
                5L
        );
    }

    /*
     * PROTECTION, FIRE PROTECTION, BLAST PROTECTION,
     * PROJECTILE PROTECTION UND FEATHER FALLING
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProtection(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        ItemStack item =
                player.getInventory().getItemInMainHand();

        if (item.getType() == Material.AIR) {
            return;
        }

        int protectionPoints =
                item.getEnchantmentLevel(Enchantment.PROTECTION);

        switch (event.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR ->
                    protectionPoints += 2 * item.getEnchantmentLevel(
                            Enchantment.FIRE_PROTECTION
                    );

            case BLOCK_EXPLOSION, ENTITY_EXPLOSION ->
                    protectionPoints += 2 * item.getEnchantmentLevel(
                            Enchantment.BLAST_PROTECTION
                    );

            case PROJECTILE ->
                    protectionPoints += 2 * item.getEnchantmentLevel(
                            Enchantment.PROJECTILE_PROTECTION
                    );

            case FALL ->
                    protectionPoints += 2 * item.getEnchantmentLevel(
                            Enchantment.FEATHER_FALLING
                    );

            default -> {
            }
        }

        protectionPoints = Math.min(20, protectionPoints);

        if (protectionPoints <= 0) {
            return;
        }

        double damageMultiplier =
                1.0D - (protectionPoints * 0.04D);

        event.setDamage(
                Math.max(
                        0.0D,
                        event.getDamage() * damageMultiplier
                )
        );
    }

    /*
     * THORNS AUF JEDEM GEHALTENEN ITEM
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onThorns(EntityDamageByEntityEvent event) {
        if (applyingThornsDamage) {
            return;
        }

        if (!(event.getEntity() instanceof Player defender)) {
            return;
        }

        ItemStack item =
                defender.getInventory().getItemInMainHand();

        int thornsLevel =
                item.getEnchantmentLevel(Enchantment.THORNS);

        if (thornsLevel <= 0) {
            return;
        }

        LivingEntity attacker =
                findLivingAttacker(event.getDamager());

        if (attacker == null) {
            return;
        }

        double chance =
                Math.min(1.0D, thornsLevel * 0.15D);

        if (ThreadLocalRandom.current().nextDouble() > chance) {
            return;
        }

        double thornsDamage = thornsLevel > 10
                ? thornsLevel - 10
                : ThreadLocalRandom.current().nextInt(1, 5);

        applyingThornsDamage = true;

        try {
            attacker.damage(thornsDamage, defender);
        } finally {
            applyingThornsDamage = false;
        }
    }

    /*
     * RESPIRATION AUF JEDEM ITEM
     */
    @EventHandler(ignoreCancelled = true)
    public void onAirChange(EntityAirChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (event.getAmount() >= player.getRemainingAir()) {
            return;
        }

        ItemStack item =
                player.getInventory().getItemInMainHand();

        int respirationLevel =
                item.getEnchantmentLevel(Enchantment.RESPIRATION);

        if (respirationLevel <= 0) {
            return;
        }

        double keepAirChance =
                respirationLevel / (respirationLevel + 1.0D);

        if (ThreadLocalRandom.current().nextDouble()
                < keepAirChance) {
            event.setCancelled(true);
        }
    }

    private LivingEntity findLivingAttacker(Entity damager) {
        if (damager instanceof LivingEntity livingEntity) {
            return livingEntity;
        }

        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();

            if (shooter instanceof LivingEntity livingEntity) {
                return livingEntity;
            }
        }

        return null;
    }

    /*
     * AQUA AFFINITY, DEPTH STRIDER, SOUL SPEED,
     * SWIFT SNEAK UND FROST WALKER
     */
    private void updateMovementEnchantments() {
        for (Player player :
                plugin.getServer().getOnlinePlayers()) {

            ItemStack item =
                    player.getInventory().getItemInMainHand();

            int aquaAffinityLevel =
                    item.getEnchantmentLevel(
                            Enchantment.AQUA_AFFINITY
                    );

            int depthStriderLevel =
                    item.getEnchantmentLevel(
                            Enchantment.DEPTH_STRIDER
                    );

            int soulSpeedLevel =
                    item.getEnchantmentLevel(
                            Enchantment.SOUL_SPEED
                    );

            int swiftSneakLevel =
                    item.getEnchantmentLevel(
                            Enchantment.SWIFT_SNEAK
                    );

            int frostWalkerLevel =
                    item.getEnchantmentLevel(
                            Enchantment.FROST_WALKER
                    );

            updateAttribute(
                    player,
                    Attribute.SUBMERGED_MINING_SPEED,
                    aquaAffinityKey,
                    aquaAffinityLevel > 0 ? 0.8D : 0.0D,
                    AttributeModifier.Operation.ADD_NUMBER
            );

            updateAttribute(
                    player,
                    Attribute.WATER_MOVEMENT_EFFICIENCY,
                    depthStriderKey,
                    Math.min(
                            1.0D,
                            depthStriderLevel / 3.0D
                    ),
                    AttributeModifier.Operation.ADD_NUMBER
            );

            boolean standingOnSoulBlock =
                    isStandingOnSoulBlock(player);

            updateAttribute(
                    player,
                    Attribute.MOVEMENT_SPEED,
                    soulSpeedKey,
                    soulSpeedLevel > 0 && standingOnSoulBlock
                            ? 0.0405D * (soulSpeedLevel + 1)
                            : 0.0D,
                    AttributeModifier.Operation.ADD_SCALAR
            );

            updateAttribute(
                    player,
                    Attribute.SNEAKING_SPEED,
                    swiftSneakKey,
                    swiftSneakLevel > 0
                            ? 0.15D * swiftSneakLevel
                            : 0.0D,
                    AttributeModifier.Operation.ADD_NUMBER
            );

            if (frostWalkerLevel > 0
                    && player.isOnGround()) {
                freezeNearbyWater(
                        player,
                        frostWalkerLevel
                );
            }
        }
    }

    private void updateAttribute(
            Player player,
            Attribute attribute,
            NamespacedKey modifierKey,
            double amount,
            AttributeModifier.Operation operation
    ) {
        AttributeInstance instance =
                player.getAttribute(attribute);

        if (instance == null) {
            return;
        }

        instance.removeModifier(modifierKey);

        if (amount > 0.0D) {
            instance.addTransientModifier(
                    new AttributeModifier(
                            modifierKey,
                            amount,
                            operation
                    )
            );
        }
    }

    private boolean isStandingOnSoulBlock(Player player) {
        Material blockType = player.getLocation()
                .getBlock()
                .getRelative(BlockFace.DOWN)
                .getType();

        return blockType == Material.SOUL_SAND
                || blockType == Material.SOUL_SOIL;
    }

    private void freezeNearbyWater(
            Player player,
            int frostWalkerLevel
    ) {
        int radius =
                Math.min(16, 2 + frostWalkerLevel);

        Block center = player.getLocation()
                .getBlock()
                .getRelative(BlockFace.DOWN);

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if ((x * x) + (z * z)
                        > radius * radius) {
                    continue;
                }

                Block water =
                        center.getRelative(x, 0, z);

                Block above =
                        water.getRelative(BlockFace.UP);

                if (water.getType() != Material.WATER
                        || above.getType() != Material.AIR) {
                    continue;
                }

                if (!(water.getBlockData()
                        instanceof Levelled levelled)
                        || levelled.getLevel() != 0) {
                    continue;
                }

                water.setType(
                        Material.FROSTED_ICE,
                        false
                );
            }
        }
    }
}
