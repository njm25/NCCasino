package org.nc.nccasino;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.nc.nccasino.commands.HelpCommand;

public final class Nccasino extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        // Register event listeners
        Bukkit.getPluginManager().registerEvents(this, this);

        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(
                    String.valueOf(Commands.literal("new-command")
                            .executes(ctx -> {
                                ctx.getSource().getSender().sendPlainMessage("some message");
                                return Command.SINGLE_SUCCESS;
                            })
                            .build()),
                    "some bukkit help description string",
                    List.of("an-alias")
            );
        });

        getLogger().info("Nccasino plugin enabled!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(Component.text("Hello, " + event.getPlayer().getName() + "!"));
    }
}
