package org.nc.nccasino.games.Baccarat;

import java.io.Serializable;

public class BetDisplayData implements Serializable {
    public final BaccaratClient.BetOption betType;
    public final double playerTotal;
    public final double totalBets;

    public BetDisplayData(BaccaratClient.BetOption betType, double playerTotal, double totalBets) {
        this.betType = betType;
        this.playerTotal = playerTotal;
        this.totalBets = totalBets;
    }
}
