# af_ZA review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 checks clean (tokens, protected
words, `/ncc claim`, `-1` / `off`, no placeholder joined to a letter). No
Dutch or untranslated English; every negation closes with "nie"; nothing
assumes a gender. Context notes hold: `seat-unavailable` is an empty seat
not yet available, third-person `player-turn`, future cash-out notice,
chain-win as current pot, max chain as a round count, overflow held /
dropped nearby, the auto-spin batch never uses "reeks". The reviewer
checked the RPS tie ("Kies weer": fresh choice buttons) and the loss limit
("stops before going past", so "oorskry") in Java.

KEY: cards.suits.diamonds
TIER: 2
ISSUE: "diamante" is a calque; the Afrikaans suit is "ruitens", matching harte / klawers / skoppens.
SUGGEST: "ruitens"

KEY: slots.payout-pending
TIER: 2
ISSUE: "toustaanry" is non-standard and differs from "ry" in payout-blocked; use "wagtou" in both.
SUGGEST: "&eJou winste kon nie dadelik afgelewer word nie en is in die wagtou vir aflewering geplaas."

KEY: slots.payout-blocked
TIER: 2
ISSUE: Queue term does not match payout-pending.
SUGGEST: "&cJou winste kon nie afgelewer of in die wagtou geplaas word nie. Klik “Draai” om weer te probeer; kontak die personeel as die probleem voortduur."

KEY: slots.auto-spin-limit-set
TIER: 2
ISSUE: "Outodraai-limiet" drifts from "Draailimiet" and drops "at most"; "keer" reads correctly for any number.
SUGGEST: "&aOutodraai sal hoogstens {spins} keer draai."

KEY: payout.bank-reminder
TIER: 2
ISSUE: "om dit te kry" vs "afgehaal" in bank-claimed / bank-cleared.
SUGGEST: "&6Winste wat nog op plek in jou inventaris wag: {amount}. Maak plek en gebruik dan &e/ncc claim&6 om dit af te haal."

KEY: payout.bank-still-blocked
TIER: 2
ISSUE: Same "kry" vs "afhaal" drift.
SUGGEST: "&6Winste wat nog op plek in jou inventaris wag: {amount}. Maak plek en gebruik dan &e/ncc claim&6 om dit af te haal."

KEY: betting.inventory-full
TIER: 3
ISSUE: Second clause has no subject.
SUGGEST: "&cGeen plek vir {amount} nie; dit word naby op die grond gelos."

KEY: coin-flip.round-started
TIER: 3
ISSUE: "Rondte het begin" is telegraphic without the article.
SUGGEST: "&cDie rondte het begin, sterkte!"

KEY: rock-paper-scissors.round-started
TIER: 3
ISSUE: Same.
SUGGEST: "&cDie rondte het begin, sterkte!"

KEY: baccarat.player-pair-odds
TIER: 3
ISSUE: Unneeded hyphen in a short compound (AWS).
SUGGEST: "Spelerpaar - 11:1"

KEY: baccarat.banker-pair-odds
TIER: 3
ISSUE: Same.
SUGGEST: "Bankierpaar - 11:1"

KEY: dragon-descent.title
TIER: 3
ISSUE: Title case; the other game names use sentence case.
SUGGEST: "Draak se afdaling"

KEY: game-options.dragon-descent
TIER: 3
ISSUE: Same.
SUGGEST: "Draak se afdaling"

KEY: slots.partial-return
TIER: 3
ISSUE: English word order after the participle.
SUGGEST: "&eJy het {amount} van 'n inset van {bet} teruggekry."

KEY: slots.invalid-denomination
TIER: 3
ISSUE: Drops "denomination".
SUGGEST: "&cDaardie insetwaarde is nie vir hierdie kroepier opgestel nie."

KEY: slots.spin-lore-breakdown
TIER: 3
ISSUE: "1 lyne" for a single payline.
SUGGEST: "&7{wager} per lyn &7× &a{lines} &7lyn(e)"

KEY: slots.rail-wager-total
TIER: 3
ISSUE: Same number agreement.
SUGGEST: "&7Totale inset: &a{amount} &7oor &a{lines} &7lyn(e)"

KEY: slots-settings.variance-max-exposure
TIER: 3
ISSUE: Number agreement and an unneeded hyphen.
SUGGEST: "&7Maksimum uitbetaling met inset 1 oor die versteklyne ({lines}): &e{amount}"

KEY: slots.prompt-retry
TIER: 3
ISSUE: "1 sekondes" at the end of the countdown.
SUGGEST: "&7Probeer weer in die klets -- oorblywende sekondes: {seconds}."

KEY: slots.prompt-invalid-spin-limit
TIER: 3
ISSUE: "'n heelgetal draaie" is awkward.
SUGGEST: "&cTik die aantal draaie as 'n heelgetal, of {unlimited} vir geen limiet nie."

KEY: slots.auto-big-win-off-set
TIER: 3
ISSUE: Unquoted phrase as subject; siblings name the setting.
SUGGEST: "&aDie grootwen-vermenigvuldiger is nou af."

KEY: slots.auto-stop-settlement-failed
TIER: 3
ISSUE: "afgelewer" narrows "settled" to delivery.
SUGGEST: "&cOutodraai gestop: 'n uitbetaling kon nie afgehandel word nie."

KEY: slots.rail-reels-tradeoff
TIER: 3
ISSUE: Calque that makes the reels the winners.
SUGGEST: "&7Met minder rolle wen jy meer gereeld klein bedrae; meer rolle betaal groter, maar minder gereeld."

KEY: slots.profile-name-illegal-characters
TIER: 3
ISSUE: "onderstrepe" means underlinings, not underscore characters.
SUGGEST: "&c'n Profielnaam mag net letters, syfers, spasies, koppeltekens en onderstreeptekens bevat."

KEY: slots-settings.variance-tradeoff
TIER: 3
ISSUE: Main-clause order after "beteken"; use a verb-final "dat" clause (hit-rate meaning is correct).
SUGGEST: "&7Hoër wisselvalligheid beteken dat lyne minder gereeld uitbetaal, maar dat boerpotte uit lang opeenvolgings baie groter is."

KEY: slots-settings.default-columns
TIER: 3
ISSUE: "Verstek-rolle" hyphenated while "Verstekhoogte" is solid.
SUGGEST: "&bVerstekrolle"

KEY: slots-settings.default-columns-updated
TIER: 3
ISSUE: Same.
SUGGEST: "&aVerstekrolle op {columns} gestel."

## Terminology notes

Payout queue "toustaanry" vs "ry"; spin limit "Outodraai-limiet" vs
"Draailimiet"; collecting held winnings "kry" vs "afhaal"; "Verstek"
compounds hyphenated vs solid. Consistent elsewhere: kroepier, Bankier,
weddenskap / inset, Herhaal weddenskap, Alles in, Betaal uit, pot, skyfie,
klets, kieslys, instellings / voorkeure, bediener, knoppie, rolle,
betaallyne, draai / rondte, wisselvalligheid, huisvoordeel, terugbetaling
aan speler, opeenvolging (slots run) vs reeks (PvE streak), Outodraai.

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 6 | Tier 3: 21 | Total: 27

## Disposition (translator)

26 of 27 applied as suggested. Not applied:
`slots.auto-stop-settlement-failed` keeps "afgelewer" -- guide §C records
that Slots settlement is delivery of the payout, so "delivered" is the
verified meaning, not a narrowing. Follow-ups in the same families:
`dragon-settings.*` now use sentence-case "Draak se afdaling" (5 keys) and
`slots.prompt-deadline` uses "{seconds} s" to avoid "1 sekondes".
