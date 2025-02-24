package org.nc.nccasino.games.CoinFlip;

import java.io.Serializable;
import org.bukkit.entity.Player;

public class PlayerData implements Serializable {
    private final Player player;

    public PlayerData(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

}
