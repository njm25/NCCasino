package org.nc.nccasino;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.nc.nccasino.commands.CommandExecutor;
import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.listeners.DealerInteractListener;
import org.nc.nccasino.listeners.VillagerPositionLockListener;

import java.util.List;
import java.util.UUID;

public final class Nccasino extends JavaPlugin implements Listener {

    private Material currency; // Material used for betting currency
    private String currencyName; // Display name for the currency

    @Override
    public void onEnable() {
        // Save default config if not present
        saveDefaultConfig();

        // Load the currency from config
        loadCurrencyFromConfig();

        // Register event listeners
        getServer().getPluginManager().registerEvents(new VillagerPositionLockListener(), this);
        getServer().getPluginManager().registerEvents(new DealerInteractListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);

        // Initialize the CommandExecutor instance
        CommandExecutor commandExecutor = new CommandExecutor(this);

        // Register the "ncc" command using Paper's command system
        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(
                Commands.literal("ncc")
                    .then(Commands.argument("args", StringArgumentType.greedyString()) // Accept multiple arguments
                        .executes(ctx -> {
                            getLogger().info(ctx.getInput());
                            CommandSender sender = ctx.getSource().getSender();

                            // Retrieve the arguments
                            String input = StringArgumentType.getString(ctx, "args");
                            String[] args = input.split(" ");

                            commandExecutor.execute(sender, "ncc", args); // Pass arguments to executor
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .executes(ctx -> { // Handles the case when no arguments are provided
                        getLogger().info(ctx.getInput());
                        CommandSender sender = ctx.getSource().getSender();

                        // Pass empty arguments
                        commandExecutor.execute(sender, "ncc", new String[]{});
                        return Command.SINGLE_SUCCESS;
                    })
                    .build(),
                "Command execution",
                List.of("ncc")
            );
        });

        // Reinitialize dealer villagers on server start
        reinitializeDealerVillagers();

        getLogger().info("Nccasino plugin enabled!");
    }

    // Load the currency material and name from the config file
    public void loadCurrencyFromConfig() {
        String currencyMaterialName = getConfig().getString("currency.material", "EMERALD").toUpperCase();
        currency = Material.matchMaterial(currencyMaterialName);

        if (currency == null) {
            getLogger().warning("Invalid currency material specified in config. Defaulting to EMERALD.");
            currency = Material.EMERALD;
        }

        currencyName = getConfig().getString("currency.name", "Emerald");
    }

    public Material getCurrency() {
        return currency;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    // Reinitialize dealer villagers on server start
    private void reinitializeDealerVillagers() {
        Bukkit.getWorlds().forEach(world -> {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager villager) {
                    if (DealerVillager.isDealerVillager(villager)) {
                        // Reinitialize inventory based on stored game type
                        UUID dealerId = DealerVillager.getUniqueId(villager);
                        String name = DealerVillager.getName(villager);
                        DealerVillager.initializeInventory(villager, dealerId, name, this); // Pass the plugin instance
                    }
                }
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(Component.text("Hello, " + event.getPlayer().getName() + "!"));
    }

    // Utility method to create NamespacedKey instances
    public NamespacedKey getKey(String key) {
        return new NamespacedKey(this, key);
    }

    public void saveDefaultDealerConfig(String internalName) {
        String path = "dealers." + internalName;

        if (!getConfig().contains(path)) {
            getConfig().set(path + ".currency.material", "EMERALD");
            getConfig().set(path + ".currency.name", "Emerald");
            getConfig().set(path + ".chip-sizes.size1", 1);
            getConfig().set(path + ".chip-sizes.size2", 5);
            getConfig().set(path + ".chip-sizes.size3", 10);
            getConfig().set(path + ".chip-sizes.size4", 25);
            getConfig().set(path + ".chip-sizes.size5", 50);
            saveConfig();
        }
    }

    public Material getCurrency(String internalName) {
        String materialName = getConfig().getString("dealers." + internalName + ".currency.material", "EMERALD").toUpperCase();
        return Material.matchMaterial(materialName);
    }

    public String getCurrencyName(String internalName) {
        return getConfig().getString("dealers." + internalName + ".currency.name", "Emerald");
    }

    public double getChipValue(String internalName, int index) {
        return getConfig().getDouble("dealers." + internalName + ".chip-sizes.size" + index, 0);
    }

    public String getChipName(String internalName, int index) {
        double value = getChipValue(internalName, index);
        return (int) value + " " + getCurrencyName(internalName);
    }

    // Method to reinitialize dealer configurations
    public void reinitializeDealerConfigurations() {
        // Logic to reinitialize dealer configurations if needed
        // This can be customized as per the plugin's requirements
        getLogger().info("Reinitializing dealer configurations...");
        reinitializeDealerVillagers();
    }
}
