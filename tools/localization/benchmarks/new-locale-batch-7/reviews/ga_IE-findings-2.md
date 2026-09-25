# ga_IE review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean (the `-1` hit in
`edit-stand-17` is the English "Stand-on-17", not a parser token). Template
slots hold (dragon settings, occupations, complex-variant changes, "Dada"),
`resplit-offer` is a completed action, no mutating word before a
placeholder, "in chat" / "at your location" kept.

KEY: mines.safe-label
TIER: 3
ISSUE: "Slán" reads first as "Goodbye"; the other Mines strings use "sábháilte".
APPLIED: "Sábháilte"

KEY: roulette.paid-even
TIER: 3
ISSUE: No lenition in the paired "gan X ná Y" pattern, as `admin.no-passengers-vehicles` already does.
APPLIED: "&6&lÍoctha: {amount}
 &r&6&o (gan brabús ná caillteanas)"

KEY: blackjack.shoe-exhausted-refunded
TIER: 3
ISSUE: "Rith ... amach" calques "ran out" (reads as "ran outside").
APPLIED: "&eBhí an bosca cártaí folamh i lár an bhabhta -- aisíocadh {amount}."

KEY: blackjack-settings.max-hands-desc
TIER: 3
ISSUE: "de bharr" takes the genitive ("scoilte"), and "a bheith aige" was clumsy.
APPLIED: "&7An líon is mó lámh is féidir a bheith ag imreoir amháin ag an am céanna de bharr scoilte."

KEY: blackjack-settings.split-matching-desc-1
TIER: 2
ISSUE: "(K-K)" leaves English rank initials; Irish ranks are "Rí" / "Banríon".
APPLIED: "&7Céim chéanna: ní scoiltear ach cártaí den chéim chéanna (Rí-Rí)."

KEY: blackjack-settings.split-matching-desc-2
TIER: 2
ISSUE: Same with "(K-Q)".
APPLIED: "&7Luach céanna: scoiltear aon dá chárta ar luach 10 freisin (Rí-Banríon)."

KEY: admin.stand-17-lore
TIER: 3
ISSUE: "Seans seasamh ar 17" juxtaposes a bare verbal noun; the sibling keys use "an seans go seasfaidh an déileálaí ar 17".
APPLIED: "&7Seans go seasfaidh an déileálaí ar 17: &a{value}%"

KEY: occupations.stand-on-17
TIER: 3
ISSUE: Same.
APPLIED: "an seans go seasfaidh an déileálaí ar 17"

Tier 0: 0 · Tier 1: 0 · Tier 2: 2 · Tier 3: 6

## Disposition (translator)

All 8 applied. Terminology notes taken as follow-ups:
`dragon-descent.rebet-placed` now matches `mines.rebet-placed` ("Cuireadh
an geall arís"), and round / hand / spin counts use "uaslíon" (maximum
number) instead of "uasmhéid", which stays for the money ceiling.
