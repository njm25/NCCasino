package org.nc.nccasino;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;
import org.nc.nccasino.commands.CommandExecutor;

import java.util.ArrayList;
import java.util.List;

public final class Nccasino extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        // Register event listeners
        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();

        // Initialize the HelpCommand instance
        CommandExecutor commandExecutor = new CommandExecutor(this);

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

        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("Nccasino plugin enabled!");
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(Component.text("Hello, " + event.getPlayer().getName() + "!"));
    }
    public NamespacedKey getKey(String key) {
        return new NamespacedKey(this, key);
    }
}
