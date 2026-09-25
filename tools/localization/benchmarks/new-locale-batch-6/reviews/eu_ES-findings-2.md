# eu_ES review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 checks clean (token order, no letter
after a placeholder, protected literals, `/ncc` tokens, `-1`). Checked and
correct: `occupations.*` in finish-editing (except the two max-chain
values), dragon fillers in "{setting} kopuru lehenetsi berria" ("liana" as
climbing plant), both `updated-detailed` templates, `resplit-offer` as a
completed action, the three distinct `closed-*` timers, `player-only`, the
`{game}` constructions, "Bat ere ez" in the decor / collar slots, Same Rank
vs Same Value.

KEY: blackjack-settings.max-hands-desc
TIER: 1
ISSUE: "esku gehienak" means the majority of the hands, not the maximum.
SUGGEST: "&7Banatuz, jokalari batek aldi berean izan dezakeen gehieneko esku kopurua."

KEY: blackjack-settings.edit-max-hands
TIER: 2
ISSUE: "maximoa" vs "gehieneko" everywhere else.
SUGGEST: "Editatu gehieneko esku kopurua"

KEY: blackjack-settings.max-hands-updated
TIER: 2
ISSUE: Same drift; drops "set to".
SUGGEST: "&aGehieneko esku kopurua ezarri da: &e{value}&a."

KEY: mines.inventory-full
TIER: 3
ISSUE: Stilted "honentzat: {amount}".
SUGGEST: "&cEz dago lekurik {amount} gordetzeko; ondoan botatzen da..."

KEY: roulette.inventory-full
TIER: 3
ISSUE: Same.
SUGGEST: "&cEz dago lekurik {amount} gordetzeko; ondoan botatzen da..."

KEY: blackjack.inventory-full
TIER: 3
ISSUE: Same.
SUGGEST: "&cEz dago lekurik {amount} gordetzeko; ondoan botatzen da..."

KEY: blackjack.click-to-add-wager
TIER: 3
ISSUE: Needless label form.
SUGGEST: "&aEgin klik {amount} gehitzeko"

KEY: blackjack.insurance-insufficient-funds
TIER: 3
ISSUE: Benefactive -rentzat on an inanimate purpose; use -rako.
SUGGEST: "&cEz dago nahikoa funts aseguruarako."

KEY: blackjack.wager-not-insurance-compatible
TIER: 3
ISSUE: Same.
SUGGEST: "&cApustu hori ezin da erdibitu aseguruarako -- saiatu zenbateko bikoiti batekin."

KEY: blackjack.closed-during-turn
TIER: 3
ISSUE: -rentzat on "hand" is awkward (meaning correct and distinct).
SUGGEST: "&c{seconds} s barru itzuli eta ekintza bat aukeratu behar duzu; bestela, zure eskuan «Plantatu» aukeratuko da automatikoki."

KEY: blackjack.closed-before-turn
TIER: 3
ISSUE: Same.
SUGGEST: "&eZure txandaren tenporizadorea amaitu aurretik itzuli behar duzu; bestela, zure eskuan «Plantatu» aukeratuko da automatikoki."

KEY: blackjack-settings.edit-timer-desc-2
TIER: 3
ISSUE: Unstated subject reads as "the round starts when the round reaches 0".
SUGGEST: "&7Tenporizadorea 0ra iristean hasten da txanda."

KEY: blackjack-settings.valid-positive-integer
TIER: 3
ISSUE: -ko adjective after the noun.
SUGGEST: "Idatzi baliozko zenbaki oso positibo bat."

KEY: mob-selection.reset-confirmation
TIER: 3
ISSUE: Participle turned into a noun ("to the defaulted").
SUGGEST: "Berrezarri konfigurazio lehenetsia?"

KEY: coin-flip-settings.prompt-max-chain
TIER: 3
ISSUE: "(-1 mugarik gabe)" vs the clearer "-1 mugarik ez jartzeko".
SUGGEST: "&aIdatzi txatean boladako gehieneko txanda kopuru berria (-1 mugarik ez jartzeko)."

KEY: rock-paper-scissors-settings.prompt-max-chain
TIER: 3
ISSUE: Same.
SUGGEST: "&aIdatzi txatean boladako gehieneko txanda kopuru berria (-1 mugarik ez jartzeko)."

KEY: occupations.rps-max-chain
TIER: 3
ISSUE: Two parentheses in a row inside finish-editing; a genitive phrase reads naturally.
SUGGEST: "Harri, orri, artazi jokoaren boladako gehieneko txandak"

KEY: occupations.coin-flip-max-chain
TIER: 3
ISSUE: Same.
SUGGEST: "Txanpona airera jokoaren boladako gehieneko txandak"

KEY: commands.list-header
TIER: 3
ISSUE: The ordinal dot lands on the total ("2/5. orria").
SUGGEST: "&d&lKrupierrak (orria: {page}/{pages}):"

KEY: admin.no-passengers-vehicles
TIER: 3
ISSUE: Leaves out the first "ez" of "ez … ez".
SUGGEST: "Ez dago ez bidaiaririk ez ibilgailurik"

## Terminology notes

Max "maximoa" vs "gehieneko" (also in packet 1's max-chain-hit); "-1 for
unlimited" phrasing; select "Aukeratu" vs "hautatu" and "dibisa" (not
counted).

Counts: Tier 0: 0 | Tier 1: 1 | Tier 2: 2 | Tier 3: 17 | Total: 20

## Disposition (translator)

All 20 applied as suggested. Follow-ups: `coin-flip` / `rock-paper-scissors`
`max-chain-hit` and `max-pot-hit` now say "gehieneko" too, and
`game-options.reset-confirmation` takes the same wording as
`mob-selection.reset-confirmation`.
