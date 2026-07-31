package net.mentalsmp.killenchant;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
     * Rechtsklick mit dem Item, während man sich im Wasser
     * oder im Regen befindet.
     */
    @EventHandler(ignoreCancelled = true)
    public void onRiptideUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Auf einem echten Dreizack übernimmt Minecraft den Effekt.
        if (item.getType() == Material.TRIDENT) {
            return;
        }

        int riptideLevel = item.getEnchantmentLevel(Enchantment.RIPTIDE);

        if (riptideLevel <= 0) {
            return;
        }

        // Riptide funktioniert nur im Wasser oder Regen.
        if (!player.isInWater() && !player.isInRain()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long previousUse = lastRiptideUse.getOrDefault(
                player.getUniqueId(),
                0L
        );

        // Verhindert unendliches Rechtsklick-Spammen.
        if (currentTime - previousUse < RIPTIDE_COOLDOWN_MILLIS) {
            return;
        }

        lastRiptideUse.put(player.getUniqueId(), currentTime);
        event.setCancelled(true);

        double strength = 0.75D * (riptideLevel + 1);

        Vector velocity = player.getEyeLocation()
                .getDirection()
                .normalize()
                .multiply(strength);

        player.setVelocity(velocity);
        player.setFallDistance(0.0F);

        Sound riptideSound = switch (riptideLevel) {
            case 1 -> Sound.ITEM_TRIDENT_RIPTIDE_1;
            case 2 -> Sound.ITEM_TRIDENT_RIPTIDE_2;
            default -> Sound.ITEM_TRIDENT_RIPTIDE_3;
        };

        player.getWorld().playSound(
                player.getLocation(),
                riptideSound,
                1.0F,
                1.0F
        );
    }

    /*
     * DENSITY, BREACH UND WIND BURST AUF JEDEM ITEM
     *
     * Wenn der Spieler von oben auf einen Gegner schlägt,
     * funktioniert das Item wie eine Mace.
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

        // Eine echte Mace besitzt den Effekt bereits durch Minecraft.
        if (item.getType() == Material.MACE) {
            return;
        }

        int densityLevel =
                item.getEnchantmentLevel(Enchantment.DENSITY);

        int breachLevel =
                item.getEnchantmentLevel(Enchantment.BREACH);

        int windBurstLevel =
                item.getEnchantmentLevel(Enchantment.WIND_BURST);

        if (densityLevel <= 0
                && breachLevel <= 0
                && windBurstLevel <= 0) {
            return;
        }

        float fallDistance = player.getFallDistance();

        // Mace-Smash beginnt erst nach einem richtigen Fall.
        if (fallDistance <= 1.5F) {
            return;
        }

        double maceSmashBonus =
                calculateMaceSmashBonus(fallDistance);

        double densityBonus =
                fallDistance * 0.5D * densityLevel;

        event.setDamage(
                event.getDamage()
                        + maceSmashBonus
                        + densityBonus
        );

        player.setFallDistance(0.0F);

        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_STRONG,
                1.0F,
                0.7F
        );

        /*
         * Wind Burst schleudert den Angreifer nach dem
         * erfolgreichen Smash wieder nach oben.
         */
        if (windBurstLevel > 0) {
            plugin.getServer().getScheduler().runTask(
                    plugin,
                    () -> {
                        Vector velocity = player.getVelocity();

                        velocity.setY(
                                0.8D + (0.35D * windBurstLevel)
                        );

                        player.setVelocity(velocity);
                    }
            );
        }
    }

    /*
     * Berechnung des normalen Vanilla-Mace-Schadens.
     */
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
