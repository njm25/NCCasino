package org.nc.nccasino.games.CoinFlip;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;

public class CoinFlipClient extends Client {

    public CoinFlipClient(Server server, Player player, Nccasino plugin, String internalName) {
        super(server, player, "Coin Flip", plugin, internalName);
    }

    @Override
    protected void handleClientSpecificClick(int slot, Player player, InventoryClickEvent event) {
        throw new UnsupportedOperationException("Unimplemented method 'handleClientSpecificClick'");
    }

    
}
