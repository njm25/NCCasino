# bs_BA review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`; `SlotsProfileName.MAX_LENGTH` read in Java.
Tier 0 clean (script-checked token order; `/ncc claim`, `-1`, `off`
intact). No Croatian-only or ekavian forms; informal `ti` throughout; no
gendered form about the player. Context notes hold (seat not yet
available, current pot, vines "puzavice", "serija" only for the streak,
round-count max chain, future cash-out notice, "2,5%"). Not scored:
"Protivnik je potvrdio" agrees with the generic noun, no named player.

KEY: slots.profile-name-empty
TIER: 1
ISSUE: "od {min} do {max} znakova" puts the noun in agreement with the number; SlotsProfileName.MAX_LENGTH = 24 gives "24 znakova" (should be "24 znaka").
APPLIED: "&cBroj znakova u imenu profila mora biti od {min} do {max}."

KEY: slots.profile-name-too-long
TIER: 1
ISSUE: Same agreement problem: "najviše 24 znakova".
APPLIED: "&cNajveći dozvoljeni broj znakova u imenu profila: {max}."

KEY: slots.guide-volatility-tradeoff
TIER: 3
ISSUE: "jackpotove" keeps the English spelling; Bosnian adapts it as "džekpot".
APPLIED: "&7Više valjaka znači manje čestih malih dobitaka iz kratkih nizova, ali veće i rjeđe džekpotove iz dugih nizova."

KEY: slots-settings.variance-tradeoff
TIER: 3
ISSUE: Same: "jackpotovi".
APPLIED: "&7Veća volatilnost znači da linije rjeđe donose dobitak, ali su džekpotovi iz dugih nizova mnogo veći."

KEY: slots.auto-big-win-current
TIER: 3
ISSUE: A colon straight after the preposition "pri" is awkward; match auto-rule-big-win.
APPLIED: "&7Zaustavlja se pri isplati od &a{multiplier}× ukupnog uloga"

KEY: slots.auto-stop-settlement-failed
TIER: 3
ISSUE: "dostavljena" (delivered) blurs this with the pending-payout delivery strings.
APPLIED: "&cAutomatsko okretanje zaustavljeno: isplata nije mogla biti izvršena."

KEY: payout.context-disconnected
TIER: 3
ISSUE: "tokom" repeated in consecutive sentences.
APPLIED: "Veza ti je prekinuta tokom aktivne igre ({game}). Igra je završena u tvom odsustvu."

KEY: rock-paper-scissors.forfeit-no-choice
TIER: 3
ISSUE: Present "gubiš" for a forfeit that already happened; a neutral passive keeps it completed.
APPLIED: "&cVrijeme za izbor je isteklo, pa je runda izgubljena."

Tier 0: 0 · Tier 1: 2 · Tier 2: 0 · Tier 3: 6

## Disposition (translator)

All 8 applied.
