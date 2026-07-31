package net.mentalsmp.killenchant;

import org.bukkit.plugin.java.JavaPlugin;

public final class KillEnchant extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(
                new KillListener(),
                this
        );

        getServer().getPluginManager().registerEvents(
                new UniversalEnchantListener(this),
                this
        );

        getServer().getPluginManager().registerEvents(
                new UniversalArmorListener(this),
                this
        );

        getServer().getPluginManager().registerEvents(
                new UniversalProjectileListener(),
                this
        );

        getServer().getPluginManager().registerEvents(
                new UniversalUtilityListener(this),
                this
        );

        getLogger().info(
                "KillEnchant wurde erfolgreich aktiviert!"
        );
    }

    @Override
    public void onDisable() {
        getLogger().info(
                "KillEnchant wurde deaktiviert."
        );
    }
}
