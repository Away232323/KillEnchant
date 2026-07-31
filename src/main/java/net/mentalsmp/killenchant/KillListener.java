package net.mentalsmp.killenchant;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class KillListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Der Spieler wurde nicht von einem anderen Spieler getötet.
        if (killer == null || killer.equals(victim)) {
            return;
        }

        ItemStack killItem = killer.getInventory().getItemInMainHand();

        // Mit einer leeren Hand kann kein Item verzaubert werden.
        if (killItem.getType() == Material.AIR) {
            killer.sendMessage(
                    Component.text(
                            "Du hast mit deiner Hand getötet – es wurde kein Item verzaubert.",
                            NamedTextColor.RED
                    )
            );
            return;
        }

        List<Enchantment> possibleEnchantments = new ArrayList<>();

        // Alle Vanilla-Enchantments durchsuchen.
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            NamespacedKey key = enchantment.getKey();

            // Nur echte Minecraft-Enchantments verwenden.
            if (!key.getNamespace().equals(NamespacedKey.MINECRAFT)) {
                continue;
            }

            int currentLevel = killItem.getEnchantmentLevel(enchantment);
            int maximumLevel = enchantment.getMaxLevel();

            // Bereits vollständig gelevelte Enchantments nicht auswählen.
            if (currentLevel < maximumLevel) {
                possibleEnchantments.add(enchantment);
            }
        }

        // Das Item besitzt bereits jedes Enchantment auf maximaler Stufe.
        if (possibleEnchantments.isEmpty()) {
            killer.sendMessage(
                    Component.text(
                            "Dieses Item besitzt bereits alle Enchantments auf maximaler Stufe!",
                            NamedTextColor.GOLD
                    )
            );
            return;
        }

        Enchantment selectedEnchantment = possibleEnchantments.get(
                ThreadLocalRandom.current().nextInt(possibleEnchantments.size())
        );

        int oldLevel = killItem.getEnchantmentLevel(selectedEnchantment);
        int newLevel = Math.min(
                oldLevel + 1,
                selectedEnchantment.getMaxLevel()
        );

        /*
         * addUnsafeEnchantment erlaubt:
         * - Enchantments auf jedem Item
         * - normalerweise inkompatible Enchantments zusammen
         */
        killItem.addUnsafeEnchantment(selectedEnchantment, newLevel);

        // Das veränderte Item wieder in die Haupthand setzen.
        killer.getInventory().setItemInMainHand(killItem);

        String enchantmentName = formatName(
                selectedEnchantment.getKey().getKey()
        );

        if (oldLevel == 0) {
            killer.sendMessage(
                    Component.text("Dein Kill-Item erhielt ", NamedTextColor.GREEN)
                            .append(Component.text(enchantmentName, NamedTextColor.AQUA))
                            .append(Component.text(
                                    " Stufe " + newLevel + "!",
                                    NamedTextColor.YELLOW
                            ))
            );
        } else {
            killer.sendMessage(
                    Component.text(enchantmentName, NamedTextColor.AQUA)
                            .append(Component.text(
                                    " wurde von Stufe " + oldLevel
                                            + " auf Stufe " + newLevel
                                            + " verbessert!",
                                    NamedTextColor.GREEN
                            ))
            );
        }
    }

    private String formatName(String name) {
        String[] words = name.split("_");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(" ");
            }

            result.append(
                    Character.toUpperCase(word.charAt(0))
            ).append(
                    word.substring(1).toLowerCase()
            );
        }

        return result.toString();
    }
}
