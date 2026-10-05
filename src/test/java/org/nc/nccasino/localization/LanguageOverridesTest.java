package org.nc.nccasino.localization;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageOverridesTest {
    private static final Map<String, String> ENGLISH = Map.of(
        "betting.wager", "Wager: {amount}",
        "payout.delivered", "&e{context}\n&aPayout: {amount}",
        "common.exit", "&cExit"
    );

    @Test
    void validValuesAreAcceptedAndMetaIsIgnored() {
        LanguageOverrides.Result result = validate(Map.of(
            "_meta.name", "Deutsch",
            "betting.wager", "Einsatz: {amount}",
            "common.exit", "&cRaus"
        ));
        assertEquals(Map.of("betting.wager", "Einsatz: {amount}", "common.exit", "&cRaus"), result.accepted());
        assertTrue(result.problems().isEmpty());
    }

    @Test
    void unknownBlankNonTextReorderedAndBidiValuesAreRejected() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("betting.removed-key", "x");
        values.put("common.exit", "   ");
        values.put("betting.wager", List.of("not", "text"));
        values.put("payout.delivered", "&aPayout: {amount}\n&e{context}");
        LanguageOverrides.Result result = validate(values);
        assertTrue(result.accepted().isEmpty());
        assertEquals(4, result.problems().size());

        LanguageOverrides.Result bidi = validate(Map.of("common.exit", "‏خروج"));
        assertTrue(bidi.accepted().isEmpty());
        assertEquals(1, bidi.problems().size());
    }

    private static LanguageOverrides.Result validate(Map<String, Object> values) {
        return LanguageOverrides.validate("de_DE", values, ENGLISH::get);
    }
}
