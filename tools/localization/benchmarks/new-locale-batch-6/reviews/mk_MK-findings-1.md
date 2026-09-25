# mk_MK review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 checks clean (scripted token order;
`/ncc claim`, `off`, `-1` intact; no ћ/ђ/я/ю/щ/ъ/ь/й/ы/э, no Serbian or
Bulgarian vocabulary; „“ quotes, decimal comma). No gendered forms aimed at
the player. Context notes hold: `seat-unavailable` "not yet available",
chain-win `{amount}` as "Тековна банка", future cash-out notice,
third-person `player-turn` (call site checked), `waiting-bet` shown only to
player 2, max chain as a count, vines as "Лијани", variance tradeoff as hit
rate, overflow held / dropped, glossary terms consistent.

KEY: payout.paid-with-profit
TIER: 2
ISSUE: "добивка" (winnings everywhere else) used for net profit; the slots keys already say "профит".
SUGGEST: "&a&lИсплатено: {amount}
&r&a&o(профит: {profit})"

KEY: slots.wager-control-description
TIER: 3
ISSUE: "се влога" is not a standard Macedonian verb form (вложува / става).
SUGGEST: "&7Го одредува износот што се става како облог на секоја активна линија."

KEY: slots.rail-paylines-cost
TIER: 3
ISSUE: Same non-standard "влога".
SUGGEST: "&7На секоја активна линија се става посебен облог, па повеќе линии чинат повеќе."

KEY: coin-flip.cash-out-lore
TIER: 3
ISSUE: "Кликнете за да подигнете:" has no object and reads as cut off.
SUGGEST: "&oКликнете за да ја подигнете добивката: &o&a{amount}"

KEY: rock-paper-scissors.cash-out-lore
TIER: 3
ISSUE: Same.
SUGGEST: "&oКликнете за да ја подигнете добивката: &o&a{amount}"

KEY: coin-flip.already-seated
TIER: 3
ISSUE: "Веќе сте на место" reads as "already in position / all set"; "Веќе седите" is natural and neutral.
SUGGEST: "Веќе седите."

KEY: rock-paper-scissors.already-seated
TIER: 3
ISSUE: Same.
SUGGEST: "Веќе седите."

KEY: baccarat.already-seated
TIER: 3
ISSUE: Same.
SUGGEST: "&eВеќе седите."

KEY: game-options.reset-confirmation
TIER: 3
ISSUE: Elliptical calque "…на стандардната?".
SUGGEST: "Да се вратат стандардните вредности на конфигурацијата?"

KEY: slots.prompt-spin-limit
TIER: 3
ISSUE: "за без ограничување" is colloquial and awkward.
SUGGEST: "&eВнесете ограничување на вртења во чатот или &f-1&e ако не сакате ограничување."

KEY: slots.prompt-invalid-spin-limit
TIER: 3
ISSUE: Same "за без".
SUGGEST: "&cВнесете цел број вртења или {unlimited} ако не сакате ограничување."

KEY: slots.rail-paylines-feedback
TIER: 3
ISSUE: Elided verb in the second clause reads as run-on.
SUGGEST: "&7Зелен блесок означува додадена линија, а црн – отстранета линија."

KEY: mines.welcome
TIER: 3
ISSUE: "Добредојдовте во Мини" can read as "into mines"; game.welcome uses "во играта {game}".
SUGGEST: "&aДобредојдовте во играта Мини."

## Terminology notes

Profit "добивка" vs "профит" (fixed above). Not scored: "Пробајте" beside
"Обидете се" in a different context.

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 1 | Tier 3: 12 | Total: 13

## Disposition (translator)

All 13 applied as suggested. Follow-ups: `test-game.already-seat-one` /
`already-seat-two` now say "Веќе седите на место …" like the other seated
messages; `mob-selection.reset-confirmation` (packet 2) takes the same
wording as `game-options.reset-confirmation`.
