# ca_ES review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Token order, protected literals, gender
neutrality toward the player, `occupations.*` article handling, lowercase
plural Dragon fillers, distinct Same Rank / Same Value and distinct
`closed-*` messages all verified.

KEY: blackjack.starts-in
TIER: 1
ISSUE: "comença en" for a future point is a castellanisme; Catalan uses "d'aquí a" (as roulette.bets-close-in already does). {seconds} is a raw integer (BlackjackInventory), so the unit is added.
SUGGEST: "La partida comença d'aquí a {seconds} s"

KEY: blackjack.title
TIER: 3
ISSUE: Lowercase game name vs "al Blackjack" and the glossary.
SUGGEST: "Taula de Blackjack"

KEY: roulette-settings.title
TIER: 2
ISSUE: Lowercase game name while parallel settings titles capitalize theirs.
SUGGEST: "Configuració de la Ruleta ({dealer})"

KEY: roulette-settings.invalid-settings-option
TIER: 2
ISSUE: Same.
SUGGEST: "&cOpció no vàlida a la configuració de la Ruleta."

KEY: roulette-settings.dealer-not-found
TIER: 2
ISSUE: Same.
SUGGEST: "&cNo s'ha trobat el crupier de la configuració de la Ruleta."

KEY: roulette-settings.prompt-number
TIER: 2
ISSUE: "número" (identifier) for a quantity; siblings use "nombre".
SUGGEST: "&aEscriu el nombre nou al xat."

KEY: baccarat-settings.title
TIER: 2
ISSUE: Lowercase game name.
SUGGEST: "Configuració del Baccarat ({dealer})"

KEY: baccarat-settings.invalid-settings-option
TIER: 2
ISSUE: Same.
SUGGEST: "&cOpció no vàlida a la configuració del Baccarat."

KEY: baccarat-settings.dealer-not-found
TIER: 2
ISSUE: Same.
SUGGEST: "&cNo s'ha trobat el crupier de la configuració del Baccarat."

KEY: baccarat-settings.prompt-number
TIER: 2
ISSUE: "número" vs "nombre" for a quantity.
SUGGEST: "&aEscriu el nombre nou al xat."

KEY: blackjack-settings.title
TIER: 2
ISSUE: Lowercase game name.
SUGGEST: "Configuració del Blackjack ({dealer})"

KEY: blackjack-settings.invalid-settings-option
TIER: 2
ISSUE: Same.
SUGGEST: "&cOpció no vàlida a la configuració del Blackjack."

KEY: blackjack-settings.dealer-not-found
TIER: 2
ISSUE: Same.
SUGGEST: "&cNo s'ha trobat el crupier de la configuració del Blackjack."

KEY: blackjack-settings.enabled
TIER: 2
ISSUE: Feminine "Activada" vs "Activat" elsewhere and the masculine infinitive labels it fills.
SUGGEST: "&aActivat"

KEY: blackjack-settings.disabled
TIER: 2
ISSUE: Same.
SUGGEST: "&cDesactivat"

KEY: blackjack-settings.number-range
TIER: 2
ISSUE: "número" vs "nombre" for a quantity.
SUGGEST: "Introdueix un nombre entre {min} i {max}."

KEY: blackjack-settings.prompt-number
TIER: 2
ISSUE: Same.
SUGGEST: "&aEscriu el nombre nou al xat."

KEY: admin.positive-number
TIER: 2
ISSUE: Same.
SUGGEST: "Introdueix un nombre positiu."

KEY: coin-flip-settings.prompt-number
TIER: 2
ISSUE: Same.
SUGGEST: "&aEscriu el nombre nou al xat."

KEY: rock-paper-scissors-settings.prompt-number
TIER: 2
ISSUE: Same.
SUGGEST: "&aEscriu el nombre nou al xat."

KEY: mines.rebet-off-reset
TIER: 3
ISSUE: Bare button label in running text; quote it.
SUGGEST: "&c«Repetir aposta» desactivat; l'aposta torna a 0."

KEY: roulette.wrong-game
TIER: 3
ISSUE: "no porta la ruleta" is colloquial / can read as "doesn't carry".
SUGGEST: "Error: aquest crupier no té assignada la Ruleta."

KEY: interaction.no-game-permission
TIER: 3
ISSUE: Colon directly after the preposition "a"; quote the arbitrary game name.
SUGGEST: "&cNo tens permís per jugar a «{game}»."

## Terminology notes

Game-name capitalization was inconsistent between welcomes / Mines / Coin
Flip / RPS / Dragon settings (capitalized) and the Roulette / Baccarat /
Blackjack settings keys (lowercase); titles now capitalize. "nombre" is
the quantity term; "número" stays for identifiers ("Número de pàgina",
"Format de número"). Toggle states are "Activat" / "Desactivat".

Counts: Tier 0: 0 | Tier 1: 1 | Tier 2: 18 | Tier 3: 4

## Disposition (translator)

All 23 applied as suggested (the blackjack.title capitalization is
included with the Tier 3 items).
