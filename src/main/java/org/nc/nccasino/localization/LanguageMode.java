package org.nc.nccasino.localization;

/**
 * How a player's casino language is chosen.
 */
public enum LanguageMode {
    /** Follow the Minecraft client's language when it maps to a catalog, else the server default. */
    CLIENT,
    /** Always the server default, chosen deliberately in the language menu. */
    SERVER_DEFAULT,
    /** A specific language picked in the language menu. */
    EXPLICIT
}
