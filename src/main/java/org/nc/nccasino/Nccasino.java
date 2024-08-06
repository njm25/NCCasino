package org.nc.nccasino;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
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

public final class Nccasino extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        // Register event listeners
        getServer().getPluginManager().registerEvents(new VillagerPositionLockListener(), this);
        getServer().getPluginManager().registerEvents(new DealerInteractListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);

        // Initialize the HelpCommand instance
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

    // Reinitialize dealer villagers on server start
    private void reinitializeDealerVillagers() {
        Bukkit.getWorlds().forEach(world -> {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager villager) {
                    if (DealerVillager.isDealerVillager(villager)) {
                        // Reinitialize inventory based on stored game type
                        UUID dealerId = DealerVillager.getUniqueId(villager);
                        String name = DealerVillager.getName(villager);
                        DealerVillager.initializeInventory(villager, dealerId, name);
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
}
