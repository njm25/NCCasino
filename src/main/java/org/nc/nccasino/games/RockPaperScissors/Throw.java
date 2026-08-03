package org.nc.nccasino.games.RockPaperScissors;

public enum Throw {
    ROCK,
    PAPER,
    SCISSORS;

    /** True if this throw beats the given opponent throw. */
    public boolean beats(Throw other) {
        return (this == ROCK && other == SCISSORS)
            || (this == PAPER && other == ROCK)
            || (this == SCISSORS && other == PAPER);
    }
}
