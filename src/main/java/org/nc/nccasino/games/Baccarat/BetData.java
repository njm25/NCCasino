package org.nc.nccasino.games.Baccarat;

import java.io.Serializable;

public class BetData implements Serializable {
    public final BaccaratClient.BetOption betType;
    public final double amount;

    public BetData(BaccaratClient.BetOption betType, double amount) {
        this.betType = betType;
        this.amount = amount;
    }

        @Override
    public String toString() {
        return "BetData{" +
               "betType=" + betType +
               ", amount=" + amount +
               '}';
    }
    
}
