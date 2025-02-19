package org.nc.nccasino.games.TestGame;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;

public class TestClient extends Client {

    public TestClient(TestServer server, Player player, Nccasino plugin, String internalName) {
        super(server, player, 54, "Test Game", plugin, internalName);
    }

    @Override
    public void initializeUI() {
        // 1) Let the parent class set up the Mines-style betting row
        super.initializeUI();

        // 2) Then do any custom UI for TestClient (the "stateItem" in slot 13)
        updateUI(server.getServerState());
    }

    @Override
    public void onServerStateChange(Server.SessionState oldState, Server.SessionState newState) {
        updateUI(newState);
    }

    @Override
    public void onServerUpdate(String eventType, Object data) {
        super.onServerUpdate(eventType, data);
        // e.g. if the server says "UPDATE_UI", refresh
        if (eventType.equals("UPDATE_UI")) {
            updateUI((Server.SessionState) data);
        }
    }

    /**
     * Simple UI refresh that clears everything, re-sets the bet row, 
     * then places a status item at slot 13.
     */
    private void updateUI(Server.SessionState state) {
        inventory.clear();
        // Rebuild the betting row from the parent class
        // (Chip row in 47..51, rebet 43, etc.)
        super.initializeUI();

        // Now place the 'stateItem' in slot 13
        ItemStack stateItem;
        switch (state) {
            case LOBBY:
                stateItem = createCustomItem(Material.YELLOW_WOOL, "Waiting for game to start...", 1);
                break;
            case IN_PROGRESS:
                stateItem = createCustomItem(Material.GREEN_WOOL, "Game in progress!", 1);
                break;
            default:
                stateItem = createCustomItem(Material.RED_WOOL, "Game Over", 1);
                break;
        }
        inventory.setItem(13, stateItem);

        // Force update on player's side
        player.updateInventory();
    }


    /**
     * All non-bet-slot clicks come here.
     */
    @Override
    protected void handleClientSpecificClick(int slot, Player player, InventoryClickEvent event) {
        // Example custom: if they click slot 13, we do a server update
        if (slot == 13) {
            sendUpdateToServer("SLOT_CLICKED", slot);
        }
        // Otherwise do nothing special
    }

}
