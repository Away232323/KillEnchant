package net.mentalsmp.killenchant;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UniversalUtilityListener implements Listener {

    private static final long LOYALTY_COOLDOWN_MILLIS = 1200L;

    private final KillEnchant plugin;
    private final NamespacedKey efficiencyKey;
    private final NamespacedKey loyaltyProjectileKey;

    private final Map<UUID, FishHook> activeHooks =
            new HashMap<>();

    private final Map<UUID, Long> lastLoyaltyUse =
            new HashMap<>();

    public UniversalUtilityListener(KillEnchant plugin) {
        this.plugin = plugin;

        this.efficiencyKey = new NamespacedKey(
                plugin,
                "universal_efficiency"
        );

        this.loyaltyProjectileKey = new NamespacedKey(
                plugin,
                "universal_loyalty_projectile"
        );

        plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::updateEfficiency,
                1L,
                5L
        );
    }

    /*
     * LURE, LUCK OF THE SEA UND LOYALTY
     */
    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = true
    )
    public void onUniversalRightClick(
            PlayerInteractEvent event
    ) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction()
                != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();

        ItemStack item =
                player.getInventory().getItemInMainHand();

        if (item.getType() == Material.AIR) {
            return;
        }

        int lureLevel =
                item.getEnchantmentLevel(
                        Enchantment.LURE
                );

        int luckLevel =
                item.getEnchantmentLevel(
                        Enchantment.LUCK_OF_THE_SEA
                );

        /*
         * Schleichen + Rechtsklick aktiviert
         * die Angel-Funktion.
         */
        if (player.isSneaking()
                && item.getType() != Material.FISHING_ROD
                && (lureLevel > 0 || luckLevel > 0)) {

            event.setCancelled(true);

            useFishingAbility(
                    player,
                    lureLevel
            );

            return;
        }

        int loyaltyLevel =
                item.getEnchantmentLevel(
                        Enchantment.LOYALTY
                );

        if (loyaltyLevel <= 0
                || item.getType() == Material.TRIDENT) {
            return;
        }

        // Im Wasser erhält Riptide Vorrang.
        if (item.getEnchantmentLevel(
                Enchantment.RIPTIDE
        ) > 0 && (player.isInWater()
                || player.isInRain())) {
            return;
        }

        // Bogen-Enchantments erhalten Vorrang.
        if (hasProjectileEnchantment(item)) {
            return;
        }

        long currentTime =
                System.currentTimeMillis();

        long previousUse =
                lastLoyaltyUse.getOrDefault(
                        player.getUniqueId(),
                        0L
                );

        if (currentTime - previousUse
                < LOYALTY_COOLDOWN_MILLIS) {
            return;
        }

        lastLoyaltyUse.put(
                player.getUniqueId(),
                currentTime
        );

        event.setCancelled(true);

        throwLoyaltyProjectile(
                player,
                item,
                loyaltyLevel
        );
    }

    /*
     * FLUCH DER BINDUNG
     */
    @EventHandler(ignoreCancelled = true)
    public void onBoundItemSlotChange(
            PlayerItemHeldEvent event
    ) {
        ItemStack currentItem = event.getPlayer()
                .getInventory()
                .getItem(event.getPreviousSlot());

        if (isBound(currentItem)) {
            event.setCancelled(true);

            event.getPlayer().sendActionBar(
                    Component.text(
                            "Der Fluch der Bindung hält das Item fest!",
                            NamedTextColor.RED
                    )
            );
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBoundItemDrop(
            PlayerDropItemEvent event
    ) {
        if (isBound(
                event.getItemDrop().getItemStack()
        )) {
            event.setCancelled(true);

            event.getPlayer().sendActionBar(
                    Component.text(
                            "Dieses Item ist durch den Fluch gebunden!",
                            NamedTextColor.RED
                    )
            );
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBoundItemSwap(
            PlayerSwapHandItemsEvent event
    ) {
        if (isBound(event.getMainHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBoundInventoryClick(
            InventoryClickEvent event
    ) {
        if (!(event.getWhoClicked()
                instanceof Player player)) {
            return;
        }

        int heldSlot =
                player.getInventory().getHeldItemSlot();

        ItemStack heldItem =
                player.getInventory().getItem(heldSlot);

        if (!isBound(heldItem)) {
            return;
        }

        boolean clickedHeldSlot =
                event.getClickedInventory()
                        == player.getInventory()
                        && event.getSlot() == heldSlot;

        boolean numberKeyUsesHeldSlot =
                event.getHotbarButton() == heldSlot;

        if (clickedHeldSlot
                || numberKeyUsesHeldSlot) {
            event.setCancelled(true);
        }
    }

    /*
     * FLUCH DES VERSCHWINDENS
     */
    @EventHandler
    public void onVanishingDeath(
            PlayerDeathEvent event
    ) {
        event.getDrops().removeIf(
                item -> item.getEnchantmentLevel(
                        Enchantment.VANISHING_CURSE
                ) > 0
        );
    }

    /*
     * LOYALTY-PROJEKTIL ENTFERNEN,
     * DAMIT KEIN ITEM DUPLIZIERT WIRD.
     */
    @EventHandler(ignoreCancelled = true)
    public void onLoyaltyHit(
            ProjectileHitEvent event
    ) {
        if (!(event.getEntity()
                instanceof Trident trident)) {
            return;
        }

        if (!trident.getPersistentDataContainer().has(
                loyaltyProjectileKey,
                PersistentDataType.BYTE
        )) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (trident.isValid()) {
                        trident.remove();
                    }
                },
                20L
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onLoyaltyPickup(
            PlayerPickupArrowEvent event
    ) {
        AbstractArrow arrow = event.getArrow();

        if (arrow.getPersistentDataContainer().has(
                loyaltyProjectileKey,
                PersistentDataType.BYTE
        )) {
            event.setCancelled(true);
            arrow.remove();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        FishHook hook = activeHooks.remove(
                event.getPlayer().getUniqueId()
        );

        if (hook != null && hook.isValid()) {
            hook.remove();
        }

        lastLoyaltyUse.remove(
                event.getPlayer().getUniqueId()
        );
    }

    /*
     * ANGELFUNKTION AUF JEDEM ITEM
     */
    private void useFishingAbility(
            Player player,
            int lureLevel
    ) {
        FishHook oldHook =
                activeHooks.remove(
                        player.getUniqueId()
                );

        if (oldHook != null && oldHook.isValid()) {
            oldHook.retrieve(EquipmentSlot.HAND);

            if (oldHook.isValid()) {
                oldHook.remove();
            }

            return;
        }

        Vector velocity = player.getEyeLocation()
                .getDirection()
                .normalize()
                .multiply(1.5D);

        FishHook hook = player.getWorld().spawn(
                player.getEyeLocation(),
                FishHook.class,
                spawnedHook -> {
                    spawnedHook.setShooter(player);
                    spawnedHook.setVelocity(velocity);

                    int minimumWait = Math.max(
                            20,
                            100 - (lureLevel * 20)
                    );

                    int maximumWait = Math.max(
                            minimumWait + 20,
                            600 - (lureLevel * 100)
                    );

                    spawnedHook.setWaitTime(
                            minimumWait,
                            maximumWait
                    );
                }
        );

        activeHooks.put(
                player.getUniqueId(),
                hook
        );

        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_FISHING_BOBBER_THROW,
                1.0F,
                1.0F
        );
    }

    /*
     * LOYALTY AUF JEDEM ITEM
     */
    private void throwLoyaltyProjectile(
            Player player,
            ItemStack weapon,
            int loyaltyLevel
    ) {
        Vector velocity = player.getEyeLocation()
                .getDirection()
                .normalize()
                .multiply(2.5D);

        Trident trident = player.launchProjectile(
                Trident.class,
                velocity
        );

        trident.setShooter(player);
        trident.setWeapon(weapon.clone());

        trident.setItemStack(
                new ItemStack(Material.TRIDENT)
        );

        trident.setLoyaltyLevel(loyaltyLevel);
        trident.setGlint(true);

        trident.setPickupStatus(
                AbstractArrow.PickupStatus.DISALLOWED
        );

        trident.getPersistentDataContainer().set(
                loyaltyProjectileKey,
                PersistentDataType.BYTE,
                (byte) 1
        );

        player.getWorld().playSound(
                player.getLocation(),
                Sound.ITEM_TRIDENT_THROW,
                1.0F,
                1.0F
        );
    }

    /*
     * EFFICIENCY AUF JEDEM ITEM
     */
    private void updateEfficiency() {
        for (Player player :
                plugin.getServer().getOnlinePlayers()) {

            ItemStack item =
                    player.getInventory()
                            .getItemInMainHand();

            int efficiencyLevel =
                    item.getEnchantmentLevel(
                            Enchantment.EFFICIENCY
                    );

            AttributeInstance breakSpeed =
                    player.getAttribute(
                            Attribute.BLOCK_BREAK_SPEED
                    );

            if (breakSpeed == null) {
                continue;
            }

            breakSpeed.removeModifier(
                    efficiencyKey
            );

            if (efficiencyLevel > 0) {
                breakSpeed.addTransientModifier(
                        new AttributeModifier(
                                efficiencyKey,
                                0.20D * efficiencyLevel,
                                AttributeModifier.Operation.ADD_SCALAR
                        )
                );
            }
        }
    }

    private boolean isBound(ItemStack item) {
        return item != null
                && item.getType() != Material.AIR
                && item.getEnchantmentLevel(
                        Enchantment.BINDING_CURSE
                ) > 0;
    }

    private boolean hasProjectileEnchantment(
            ItemStack item
    ) {
        return item.getEnchantmentLevel(
                Enchantment.POWER
        ) > 0
                || item.getEnchantmentLevel(
                        Enchantment.PUNCH
                ) > 0
                || item.getEnchantmentLevel(
                        Enchantment.FLAME
                ) > 0
                || item.getEnchantmentLevel(
                        Enchantment.INFINITY
                ) > 0
                || item.getEnchantmentLevel(
                        Enchantment.MULTISHOT
                ) > 0
                || item.getEnchantmentLevel(
                        Enchantment.QUICK_CHARGE
                ) > 0
                || item.getEnchantmentLevel(
                        Enchantment.PIERCING
                ) > 0;
    }
}
