# cy_GB review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`; Java call sites read for the
`updated-detailed` templates. Tier 0-2 clean. Placeholder slots use
colons or parentheses (`prompt-setting*`, `finish-editing`,
`updated-detailed`); `resplit-offer` completed; `none` fits both slots;
vines "planhigion dringo". Not scored: "I ffwrdd" (toggle state) vs
"wedi'i analluogi" (server restriction).

KEY: mines.dealer-cannot-cover
TIER: 3
ISSUE: "neu lleihewch" lacks the soft mutation; "wedyn" reads "afterwards" where software says "yn nes ymlaen".
APPLIED: "&cNi all y bwrdd hwn dalu enillion mor fawr ar hyn o bryd. Rhowch gynnig arall arni yn nes ymlaen, neu leihewch eich bet."

KEY: roulette.dealer-cannot-cover
TIER: 3
ISSUE: Same two points.
APPLIED: "&cNi all y bwrdd dalu ennill mor fawr ar hyn o bryd. Rhowch gynnig arall arni yn nes ymlaen, neu leihewch eich betiau."

KEY: blackjack.dealer-cannot-cover
TIER: 3
ISSUE: Same two points.
APPLIED: "&cNi all y deliwr dalu ennill mor fawr ar hyn o bryd. Rhowch gynnig arall arni yn nes ymlaen, neu leihewch eich bet."

KEY: roulette.item-payout-too-large
TIER: 3
ISSUE: "neu rhannwch" lacks the soft mutation.
APPLIED: "&cMae taliad posibl y bet hwnnw'n rhy fawr ar gyfer arian cyfred ar ffurf eitemau. Lleihewch eich bet neu rannwch ef ar draws llai o fetiau."

KEY: blackjack.already-seated
TIER: 3
ISSUE: The playful source ("buster") came out flat; any vocative must stay gender-neutral.
APPLIED: "&cGan bwyll! Rydych chi'n eistedd yn barod!"

KEY: blackjack.shoe-exhausted-refunded
TIER: 3
ISSUE: "Rhedodd … allan ganol rownd" calques "ran out"; "yng nghanol y rownd" is standard.
APPLIED: "&eDaeth y cardiau yn y blwch i ben yng nghanol y rownd -- ad-dalwyd {amount}."

KEY: commands.player-only
TIER: 3
ISSUE: "Dim ond chwaraewyr all" drops the relative "a" (spoken register).
APPLIED: "&cDim ond chwaraewyr sy'n gallu defnyddio'r gorchymyn hwn."

Tier 0: 0 · Tier 1: 0 · Tier 2: 0 · Tier 3: 7

## Disposition (translator)

All 7 applied. Follow-ups in self-review: `slots.dealer-cannot-cover`
takes "yn nes ymlaen", and the three `closed-*` blackjack lines take "neu
fe fydd" so every "neu" is followed by a mutated or particle form.
