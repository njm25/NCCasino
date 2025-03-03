package org.nc.nccasino.games.DragonDescent;

import java.util.UUID;

import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;
import org.bukkit.entity.Player;
import org.nc.nccasino.Nccasino;

public class DragonServer extends Server {

    public DragonServer(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, plugin, internalName);
    }

        @Override
    protected Client createClientForPlayer(Player player) {
        DragonClient client = new DragonClient(this, player, plugin, internalName);
        client.initializeUI(true, true,false);
        clients.put(player.getUniqueId(), client);
        return client;
    }
    
        @Override
    public void onClientUpdate(Client client, String eventType, Object data) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onClientUpdate'");
    }
}
