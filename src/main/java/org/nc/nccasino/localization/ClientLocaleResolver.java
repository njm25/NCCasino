package org.nc.nccasino.localization;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Maps the language a Minecraft client reports ({@code Player#getLocale()},
 * e.g. {@code "pt_br"}, {@code "no_no"}, {@code "sah_sah"}) to a registered
 * NCCasino catalog, or {@code null} when the client gives no usable signal.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@code en_us} is Minecraft's untouched default, so it says nothing
 *       about what the player reads and resolves to {@code null} (the caller
 *       then uses the server default).</li>
 *   <li>An exact alias for client codes that differ from our ids or that
 *       should fall back to a related catalog. Aliases are looked up on the
 *       raw code because several real Minecraft codes ({@code be_latn},
 *       {@code enws}, {@code esan}, {@code sah_sah}, {@code zlm_arab}) do not
 *       fit {@link LocaleIds}.</li>
 *   <li>The normalized code itself, e.g. {@code eo_uy} to {@code eo_UY}.</li>
 *   <li>The first registered catalog of the same language, which keeps a
 *       future regional client code (say {@code fr_be}) out of the server
 *       default.</li>
 * </ol>
 *
 * <p>Right-to-left languages and constructed languages have no entry on
 * purpose: they resolve to {@code null} until a catalog for them is
 * registered, at which point step 3 picks them up with no code change.
 */
public final class ClientLocaleResolver {
    /** Minecraft's default client language; carries no signal on its own. */
    static final String MINECRAFT_DEFAULT = "en_us";

    static final Map<String, String> ALIASES = Map.ofEntries(
        // Our ids differ from Minecraft's spelling for the same language.
        Map.entry("no_no", "nb_NO"),
        Map.entry("sr_sp", "sr_RS"),
        // Latin-script Serbian; our catalog is Cyrillic but readable to the same players.
        Map.entry("sr_cs", "sr_RS"),

        // English regional variants and English-based novelty languages.
        Map.entry("en_au", "en_US"),
        Map.entry("en_ca", "en_US"),
        Map.entry("en_gb", "en_US"),
        Map.entry("en_nz", "en_US"),
        Map.entry("en_pt", "en_US"),
        Map.entry("en_ud", "en_US"),
        Map.entry("enp", "en_US"),
        Map.entry("enws", "en_US"),
        Map.entry("lol_us", "en_US"),

        // Latin American Spanish reads closer to the Mexican catalog.
        Map.entry("es_ar", "es_MX"),
        Map.entry("es_cl", "es_MX"),
        Map.entry("es_ec", "es_MX"),
        Map.entry("es_uy", "es_MX"),
        Map.entry("es_ve", "es_MX"),
        // Indigenous languages of Mexico; their speakers read Mexican Spanish.
        Map.entry("nah", "es_MX"),
        Map.entry("pls", "es_MX"),
        Map.entry("tzo_mx", "es_MX"),
        // Varieties of Spain.
        Map.entry("esan", "es_ES"),
        Map.entry("qcb_es", "es_ES"),
        Map.entry("val_es", "ca_ES"),

        // Dialects whose written standard is a catalog we have.
        Map.entry("de_at", "de_DE"),
        Map.entry("de_ch", "de_DE"),
        Map.entry("bar", "de_DE"),
        Map.entry("sxu", "de_DE"),
        Map.entry("ksh", "de_DE"),
        Map.entry("fra_de", "de_DE"),
        Map.entry("nl_be", "nl_NL"),
        Map.entry("brb", "nl_NL"),
        Map.entry("fr_ca", "fr_FR"),
        Map.entry("fr_ch", "fr_FR"),
        Map.entry("go_fr", "fr_FR"),
        Map.entry("fur_it", "it_IT"),
        Map.entry("lmo", "it_IT"),
        Map.entry("vro", "et_EE"),
        Map.entry("ovd", "sv_SE"),
        Map.entry("hn_no", "nn_NO"),
        Map.entry("hal_ua", "uk_UA"),
        Map.entry("ry_ua", "uk_UA"),
        Map.entry("rpr", "ru_RU"),
        Map.entry("sah_sah", "ru_RU"),
        Map.entry("qid", "id_ID"),
        Map.entry("tl_ph", "fil_PH"),

        // Same language, other script.
        Map.entry("be_latn", "be_BY"),
        Map.entry("zlm_arab", "ms_MY"),
        Map.entry("zh_hk", "zh_TW"),
        Map.entry("lzh", "zh_TW")
    );

    private ClientLocaleResolver() {
    }

    /**
     * @param rawClientLocale what {@code Player#getLocale()} returned
     * @param supported registered locale ids, in registry order
     * @return a registered locale id, or {@code null} for "no usable signal"
     */
    public static String resolve(String rawClientLocale, Collection<String> supported) {
        if (rawClientLocale == null || rawClientLocale.isBlank()) {
            return null;
        }
        String code = rawClientLocale.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (code.equals(MINECRAFT_DEFAULT)) {
            return null;
        }

        String alias = ALIASES.get(code);
        if (alias != null && supported.contains(alias)) {
            return alias;
        }

        String normalized = LocaleIds.normalize(code);
        if (normalized == null) {
            return null;
        }
        if (supported.contains(normalized)) {
            return normalized;
        }

        String language = normalized.split("_", 2)[0];
        for (String locale : supported) {
            if (locale.equals(language) || locale.startsWith(language + "_")) {
                return locale;
            }
        }
        return null;
    }
}
