# sq_AL review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 checks clean. The reviewer checked
`DragonClient.java` for the safe / unsafe labels. Context notes hold:
`seat-unavailable` "nuk është ende i disponueshëm", chain-win `{amount}` as
"Poti aktual", vines "Liana", max chain as a round count, future cash-out
notice, `-1` / `off` / `/ncc claim` verbatim, house-edge example "2,5%"
with a trailing sign, overflow held / dropped; "seri" only for the PvE
streak (the auto-spin batch "që nga nisja e tij").

KEY: slots.prompt-invalid-amount
TIER: 1
ISSUE: "shumë e vlefshme" reads as "very valid" (adverb), not "a valid amount".
SUGGEST: "&cKjo nuk është një shumë e vlefshme."

KEY: slots.rail-profiles-global
TIER: 1
ISSUE: Predicate possessive needs its article: "të tuajat".
SUGGEST: "&7Profilet tuaja janë vetëm të tuajat dhe funksionojnë në çdo makinë slot."

KEY: coin-flip.player-turn
TIER: 3
ISSUE: "lojtarit" assumes the named player is male ("lojtares").
SUGGEST: "&oE ka radhën {player}&o"

KEY: rock-paper-scissors.player-turn
TIER: 3
ISSUE: Same.
SUGGEST: "&oE ka radhën {player}&o"

KEY: coin-flip.leave
TIER: 3
ISSUE: Plural imperative among singular-imperative buttons.
SUGGEST: "&f&oNgrihu"

KEY: rock-paper-scissors.leave
TIER: 3
ISSUE: Same.
SUGGEST: "&f&oNgrihu"

KEY: dragon-descent.safe
TIER: 3
ISSUE: Bare feminine adjective can read as "(you are) safe" to a female player (DragonClient.java:346/348/399/644).
SUGGEST: "&aZonë e sigurt!"

KEY: dragon-descent.unsafe
TIER: 3
ISSUE: Same.
SUGGEST: "&cZonë e rrezikshme!"

KEY: dragon-descent.sweep
TIER: 3
ISSUE: "përfshin" usually means "includes".
SUGGEST: "&cDragoi fshin gjithçka!"

KEY: coin-flip.dealer-cannot-cover
TIER: 3
ISSUE: Clitic "ta" doubles a non-specific indefinite object.
SUGGEST: "&cKy krupier nuk mund të mbulojë tani një fitim kaq të madh nga seria. Tërhiqni fitimin ose provoni më vonë."

KEY: rock-paper-scissors.dealer-cannot-cover
TIER: 3
ISSUE: Same.
SUGGEST: "&cKy krupier nuk mund të mbulojë tani një fitim kaq të madh nga seria. Tërhiqni fitimin ose provoni më vonë."

KEY: baccarat.dealer-cannot-cover
TIER: 3
ISSUE: Same.
SUGGEST: "&cTavolina nuk mund të mbulojë tani një fitim kaq të madh. Provoni më vonë ose zvogëloni bastet."

KEY: dragon-descent.dealer-cannot-cover
TIER: 3
ISSUE: Same.
SUGGEST: "&cKjo zbritje nuk mund të mbulojë tani një pot kaq të madh. Provoni më vonë ose zvogëloni bastin."

KEY: slots.dealer-cannot-cover
TIER: 3
ISSUE: Same.
SUGGEST: "&cKjo makinë nuk mund të mbulojë tani një fitim kaq të madh. Provoni më vonë ose luani me më pak linja."

KEY: slots.partial-return
TIER: 3
ISSUE: Plural verb must agree with an uninflectable amount; drops "you".
SUGGEST: "&eMorët mbrapsht {amount} nga një bast prej {bet}."

KEY: slots.prompt-spin-limit
TIER: 3
ISSUE: Awkward "për pa kufi".
SUGGEST: "&eShkruani në chat një kufi rrotullimesh, ose &f-1&e që të mos ketë kufi."

KEY: slots.prompt-invalid-spin-limit
TIER: 3
ISSUE: Same.
SUGGEST: "&cShkruani një numër të plotë rrotullimesh, ose {unlimited} që të mos ketë kufi."

KEY: errors.currency-unavailable
TIER: 3
ISSUE: "nuk u kaluan dot" is vague for "credited".
SUGGEST: "&cMonedha nuk është konfiguruar, ndaj fitimet nuk u kredituan dot."

## Terminology notes

No Tier 2 inconsistencies; Leave buttons in the plural imperative (fixed
above).

Counts: Tier 0: 0 | Tier 1: 2 | Tier 2: 0 | Tier 3: 16 | Total: 18

## Disposition (translator)

All 18 applied as suggested. Follow-ups: `blackjack.current-player-turn`
now reads "E ka radhën {player}" and `blackjack.other-betting-circle`
uses a label form, so no string names another player with a gendered
noun.
