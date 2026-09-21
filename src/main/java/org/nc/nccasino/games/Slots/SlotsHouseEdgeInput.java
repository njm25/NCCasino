package org.nc.nccasino.games.Slots;

import java.util.OptionalDouble;

/** Parses the two natural ways administrators write a Slots house edge. */
public final class SlotsHouseEdgeInput {

    private SlotsHouseEdgeInput() {
    }

    /**
     * Accepts a displayed percentage ({@code 2.5} or {@code 2.5%}) or the
     * configuration-file fraction ({@code 0.025}). Decimal commas are accepted
     * when no decimal point is present. Values outside the supported 1%-6%
     * range are rejected rather than silently clamped.
     */
    public static OptionalDouble parse(String raw) {
        if (raw == null) {
            return OptionalDouble.empty();
        }
        String input = raw.trim();
        boolean explicitPercent = input.endsWith("%");
        if (explicitPercent) {
            input = input.substring(0, input.length() - 1).trim();
        }
        if (input.indexOf('.') < 0 && input.indexOf(',') >= 0) {
            input = input.replace(',', '.');
        }

        final double entered;
        try {
            entered = Double.parseDouble(input);
        } catch (NumberFormatException ignored) {
            return OptionalDouble.empty();
        }
        if (!Double.isFinite(entered)) {
            return OptionalDouble.empty();
        }

        double fraction = explicitPercent || entered >= 1.0 ? entered / 100.0 : entered;
        if (fraction < SlotsPaytable.MIN_HOUSE_EDGE || fraction > SlotsPaytable.MAX_HOUSE_EDGE) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(fraction);
    }
}
