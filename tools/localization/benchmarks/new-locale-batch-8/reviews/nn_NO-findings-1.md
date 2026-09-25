# nn_NO review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean (script-checked token order,
`/ncc claim`, `-1`, `off`; "2,5 %" confirmed parseable in
`SlotsHouseEdgeInput.parse`). No Bokmål leakage found by word search;
pronoun gender, strong participles and « » typography correct; the
chair-2 label says "not open yet".

KEY: dragon-descent.vines-per-floor
TIER: 3
ISSUE: "Klatreplanter" is the Bokmål plural; Nynorsk plante is masculine ("plantar").
APPLIED: "Klatreplantar (trygge felt per etasje)"

KEY: slots.wager-control-description
TIER: 3
ISSUE: "blir sett på" reads as the passive of "sjå på" (looked at); sentence-initial "Set" reads as an imperative.
APPLIED: "&7Bestemmer beløpet som blir satsa på kvar aktiv linje."

KEY: slots.auto-reset-already-default
TIER: 3
ISSUE: "Alt står alt" puts "everything" next to "already".
APPLIED: "&8Alt står allereie på standard."

KEY: slots.rail-exit-session
TIER: 3
ISSUE: Same clash in "alt du alt har vunne".
APPLIED: "&7Det avsluttar økta di her; alt du allereie har vunne, blir framleis utbetalt."

KEY: slots.auto-spin-title-active
TIER: 3
ISSUE: "autospinn" is neuter, so the predicative is "aktivt".
APPLIED: "&a&lAutospinn -- aktivt"

KEY: slots.rail-clock-auto-active
TIER: 3
ISSUE: Same neuter agreement.
APPLIED: "&7Autospinn: &aaktivt &8(grense {spins})"

KEY: test-game.server-lost
TIER: 3
ISSUE: "vart teke" needs plural agreement for a plural amount; reworded to avoid agreement.
APPLIED: "Du tapte! Du miste {amount} {currency}."

KEY: coin-flip.click-cancel-bet
TIER: 3
ISSUE: "avbryte innsatsen" means aborting a process; withdrawing a bet is "trekkje tilbake".
APPLIED: "&oKlikk for å trekkje tilbake innsatsen"

KEY: rock-paper-scissors.click-cancel-bet
TIER: 3
ISSUE: Same.
APPLIED: "&oKlikk for å trekkje tilbake innsatsen"

KEY: slots.prompt-invalid-spin-limit
TIER: 3
ISSUE: "eit heilt tal spinn" is ungrammatical.
APPLIED: "&cSkriv talet på spinn som eit heiltal, eller {unlimited} for inga grense."

Tier 0: 0 · Tier 1: 0 · Tier 2: 0 · Tier 3: 10

## Disposition (translator)

All 10 applied. Follow-up in self-review: every other "klatreplanter"
(settings label, prompt value, lore, error, update message) now uses
"klatreplantar" / "klatreplantane".
