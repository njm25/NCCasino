package org.nc.nccasino.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Vault integration hook for economy detection.
 * To test: install Vault + an economy plugin (e.g., EssentialsX Economy) and confirm the log line shows a provider.
 */
public class VaultHook {
    private final JavaPlugin plugin;
    private Economy economy;
    /** True if the Vault plugin is loaded (even when no economy provider is registered). */
    private boolean vaultPresent;

    public VaultHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempts to hook into Vault and detect an Economy provider.
     * Logs the status of Vault installation and Economy provider availability.
     */
    public void hookAndLog() {
        // Check if Vault plugin is installed
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found; skipping Vault hook.");
            return;
        }
        vaultPresent = true;

        // Attempt to get Economy service provider
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        
        if (rsp == null || rsp.getProvider() == null) {
            plugin.getLogger().warning("Vault found, but no Economy provider is registered (install an economy plugin).");
            return;
        }

        // Store the economy provider
        economy = rsp.getProvider();
        String providerName = rsp.getProvider().getName();
        plugin.getLogger().info("Vault economy detected: " + providerName);
    }

    /**
     * Checks if the Vault plugin is present (may still have no economy provider).
     */
    public boolean isVaultPresent() {
        return vaultPresent;
    }

    /**
     * Checks if an Economy provider is available.
     * @return true if Vault and an Economy provider are available, false otherwise
     */
    public boolean isEconomyAvailable() {
        return economy != null;
    }

    /**
     * Gets the Economy provider instance.
     * @return the Economy instance, or null if not available
     */
    public Economy getEconomy() {
        return economy;
    }
}
