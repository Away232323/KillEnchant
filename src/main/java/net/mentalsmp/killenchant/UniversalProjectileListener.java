package net.mentalsmp.killenchant;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UniversalProjectileListener implements Listener {

    private final Map<UUID, Long> lastShot = new HashMap<>();
    private boolean applyingChannelingDamage;

    /*
     * BOGEN- UND ARMBRUST-ENCHANTMENTS AUF JEDEM ITEM
     *
     * Das Event darf cancelled Events nicht ignorieren, weil Paper einen
     * Rechtsklick in die Luft bei normalen Items oft bereits als cancelled
     * markiert. Dadurch funktioniert die Fähigkeit auch in der Luft.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onUniversalBowUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (weapon.getType() == Material.AIR) {
            return;
        }

        // Unter Wasser erhält Riptide Vorrang.
        if (weapon.getEnchantmentLevel(Enchantment.RIPTIDE) > 0
                && player.isInWater()) {
            return;
        }

        // Schleichen + Rechtsklick mit Angel-Enchantments gehört zur Angel-Fähigkeit.
        if (player.isSneaking()
                && (weapon.getEnchantmentLevel(Enchantment.LURE) > 0
                || weapon.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA) > 0)) {
            return;
        }

        if (isNativeRightClickWeapon(weapon.getType())) {
            return;
        }

        if (!hasProjectileEnchantment(weapon)) {
            return;
        }

        int quickChargeLevel = weapon.getEnchantmentLevel(Enchantment.QUICK_CHARGE);

        long cooldown = Math.max(
                250L,
                1000L - (quickChargeLevel * 250L)
        );

        long currentTime = System.currentTimeMillis();
        long previousShot = lastShot.getOrDefault(player.getUniqueId(), 0L);

        if (currentTime - previousShot < cooldown) {
            return;
        }

        if (!takeArrow(player, weapon)) {
            player.sendActionBar(
                    Component.text(
                            "Du brauchst einen Pfeil!",
                            NamedTextColor.RED
                    )
            );
            return;
        }

        lastShot.put(player.getUniqueId(), currentTime);
        event.setCancelled(true);

        int multishotLevel = weapon.getEnchantmentLevel(Enchantment.MULTISHOT);

        if (multishotLevel > 0) {
            shootArrow(player, weapon, -10.0D);
            shootArrow(player, weapon, 0.0D);
            shootArrow(player, weapon, 10.0D);
        } else {
            shootArrow(player, weapon, 0.0D);
        }

        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_ARROW_SHOOT,
                1.0F,
                1.0F
        );
    }

    /*
     * PIERCING/DURCHSCHUSS AUF JEDEM NAHKAMPF-ITEM
     *
     * Ein Treffer mit einem Piercing-Item geht durch ein hochgehaltenes
     * Schild, bleibt ansonsten aber ein normaler Treffer.
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUniversalPiercingMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (!(event.getEntity() instanceof Player defender)) {
            return;
        }

        if (!defender.isBlocking()) {
            return;
        }

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        int piercingLevel = weapon.getEnchantmentLevel(Enchantment.PIERCING);

        if (piercingLevel <= 0) {
            return;
        }

        if (event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)) {
            event.setDamage(
                    EntityDamageEvent.DamageModifier.BLOCKING,
                    0.0D
            );
        }
    }

    /*
     * IMPALING UND CHANNELING AUF JEDEM ITEM
     */
    @EventHandler(
            priority = EventPriority.HIGH,
            ignoreCancelled = true
    )
    public void onUniversalTridentHit(EntityDamageByEntityEvent event) {
        // Verhindert, dass der manuell verursachte Channeling-Schaden
        // dieses Event erneut endlos auslöst.
        if (applyingChannelingDamage) {
            return;
        }

        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        ItemStack weapon = player.getInventory().getItemInMainHand();

        // Beim richtigen Dreizack übernimmt Minecraft.
        if (weapon.getType() == Material.TRIDENT) {
            return;
        }

        int impalingLevel = weapon.getEnchantmentLevel(Enchantment.IMPALING);

        if (impalingLevel > 0 && target instanceof WaterMob) {
            event.setDamage(
                    event.getDamage() + (2.5D * impalingLevel)
            );
        }

        int channelingLevel = weapon.getEnchantmentLevel(Enchantment.CHANNELING);

        if (channelingLevel > 0
                && canUseChanneling(target.getLocation())) {

            /*
             * Nur der optische Blitz wird gespawnt. Der echte Blitz würde
             * alle nahen Entities treffen und dadurch auch den Angreifer.
             */
            target.getWorld().strikeLightningEffect(target.getLocation());

            applyingChannelingDamage = true;

            try {
                // 5 Schaden = 2,5 Herzen, entsprechend einem Blitztreffer.
                target.damage(5.0D, player);
                target.setFireTicks(
                        Math.max(target.getFireTicks(), 160)
                );
            } finally {
                applyingChannelingDamage = false;
            }
        }
    }

    private void shootArrow(
            Player player,
            ItemStack weapon,
            double yawOffset
    ) {
        Vector direction = player.getEyeLocation()
                .getDirection()
                .normalize()
                .rotateAroundY(Math.toRadians(yawOffset))
                .multiply(3.0D);

        Arrow arrow = player.launchProjectile(
                Arrow.class,
                direction
        );

        arrow.setWeapon(weapon.clone());
        arrow.setCritical(true);

        arrow.setPickupStatus(
                AbstractArrow.PickupStatus.DISALLOWED
        );

        int powerLevel = weapon.getEnchantmentLevel(Enchantment.POWER);
        int flameLevel = weapon.getEnchantmentLevel(Enchantment.FLAME);
        int piercingLevel = weapon.getEnchantmentLevel(Enchantment.PIERCING);

        double damage = 2.0D;

        if (powerLevel > 0) {
            damage += 0.5D * (powerLevel + 1);
        }

        arrow.setDamage(damage);
        arrow.setPierceLevel(piercingLevel);

        if (flameLevel > 0) {
            arrow.setFireTicks(100);
        }
    }

    private boolean takeArrow(
            Player player,
            ItemStack weapon
    ) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }

        if (weapon.getEnchantmentLevel(Enchantment.INFINITY) > 0) {
            return true;
        }

        for (int slot = 0; slot < 36; slot++) {
            ItemStack inventoryItem = player.getInventory().getItem(slot);

            if (inventoryItem == null
                    || inventoryItem.getType() != Material.ARROW) {
                continue;
            }

            if (inventoryItem.getAmount() <= 1) {
                player.getInventory().setItem(slot, null);
            } else {
                inventoryItem.setAmount(inventoryItem.getAmount() - 1);
            }

            return true;
        }

        return false;
    }

    private boolean hasProjectileEnchantment(ItemStack item) {
        return item.getEnchantmentLevel(Enchantment.POWER) > 0
                || item.getEnchantmentLevel(Enchantment.PUNCH) > 0
                || item.getEnchantmentLevel(Enchantment.FLAME) > 0
                || item.getEnchantmentLevel(Enchantment.INFINITY) > 0
                || item.getEnchantmentLevel(Enchantment.MULTISHOT) > 0
                || item.getEnchantmentLevel(Enchantment.QUICK_CHARGE) > 0
                || item.getEnchantmentLevel(Enchantment.PIERCING) > 0;
    }

    private boolean isNativeRightClickWeapon(Material material) {
        return material == Material.BOW
                || material == Material.CROSSBOW
                || material == Material.TRIDENT
                || material == Material.FISHING_ROD;
    }

    private boolean canUseChanneling(Location location) {
        World world = location.getWorld();

        if (world == null || !world.isThundering()) {
            return false;
        }

        int highestBlock = world.getHighestBlockYAt(
                location.getBlockX(),
                location.getBlockZ()
        );

        return highestBlock <= location.getBlockY();
    }
}
