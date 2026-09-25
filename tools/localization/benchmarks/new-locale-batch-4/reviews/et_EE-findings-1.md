# et_EE review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Scripted token-order check clean; `/ncc
claim`, `-1`, `off`, `{overwrite}`, `{cancel}`, `{unlimited}` intact; „“
quotes, decimal comma, no English residue. Semantic notes hold
(`seat-unavailable` neutral "Koht pole saadaval", third-person
`player-turn`, conditional cash-out notice, chain-win as current pot, max
chain as a game count, `jada` vs `seeria`, auto-spin never `seeria`).
Verified in Java: `baccarat.current-total` is the hand point total
(BaccaratClient); `mines.all-in` follows going all-in (MinesTable).

KEY: baccarat.title
TIER: 3
ISSUE: "Bakaraa" is not standard; the dictionary spelling is "bakkara". The name is also inserted into {game}.
SUGGEST: "Bakkara"

KEY: game-options.baccarat
TIER: 3
ISSUE: Same spelling; keep identical to baccarat.title.
SUGGEST: "Bakkara"

KEY: game.welcome
TIER: 3
ISSUE: Correct but stiff and unlike mines.welcome; a quoted apposition after "mängu" works for any {game}.
SUGGEST: "&aTere tulemast mängu „{game}“!"

KEY: errors.currency-unavailable
TIER: 3
ISSUE: "ei saanud võite kanda" lacks a destination; "credited" means paid out.
SUGGEST: "&cValuuta pole seadistatud, seega ei saanud võite välja maksta."

KEY: preferences.overflow.drop-explained
TIER: 3
ISSUE: Label "Maha:" is an adverb while bank-explained uses the noun "Hoidmine:".
SUGGEST: "&7Mahapanek: võidud pannakse lähedale maha kuni serveri piirini, ülejäänu hoitakse turvaliselt."

KEY: baccarat.current-total
TIER: 3
ISSUE: Per BaccaratClient this is the hand point total; "summa" can read as money.
SUGGEST: "Praegune punktisumma: {total}"

KEY: slots.paytable-card-no-runs
TIER: 3
ISSUE: Roundabout; the allative "{columns} rullile" works for every number.
SUGGEST: "&8Ükski selle sümboli jada ei mahu {columns} rullile."

KEY: slots.guide-volatility-normalized
TIER: 3
ISSUE: "tasakaalustatud … ümber" is a calque of "normalized around".
SUGGEST: "&7Mõlemal juhul on väljamaksetabel seadistatud nii, et tagastus on &a{rtp}&7."

KEY: slots.auto-rule-profit
TIER: 3
ISSUE: Reads as exactly {amount}; the rule is "at least".
SUGGEST: "&7Peatub, kui kasum on vähemalt &a{amount}&7."

KEY: game-options.select-title
TIER: 3
ISSUE: Split "Mängu tüübi" vs the compound "mängutüüp" elsewhere.
SUGGEST: "Mängutüübi valik"

KEY: game-options.edit-title
TIER: 3
ISSUE: Same split spelling.
SUGGEST: "Mängutüübi muutmine"

## Terminology notes

No Tier 2 inconsistencies (diiler / pankur, panus / kogupanus, Korda
panust, Pane kõik, Võta võit välja, pott, žetoon, vestlus, isiklikud
seaded, rullid, võiduliinid, keerutus, volatiilsus, kasiino eelis,
tagastus mängijale, jada vs seeria, seljakott, hoiustatud võidud).

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 0 | Tier 3: 11

## Disposition (translator)

All 11 applied. Follow-up: the baccarat settings keys now also use
"Bakkara" / "bakkara".
