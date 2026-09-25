# lt_LT review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Scripted checks: tokens in English order,
protected literals and parser words (`/ncc claim`, `-1`, `off`) intact, no
placeholder followed by a letter, „“ quotes, decimal comma. Verified in Java:
the house-edge parser strips a trailing `%` and trims spaces, so "2,5 %" is
valid. No text assumes the player's gender (the only -ęs participles,
"išmokėjęs" / "laimėjęs", describe the spin).

KEY: coin-flip.seat-unavailable
TIER: 1
ISSUE: "Vieta užimta" means "occupied", but `CoinFlipClient` shows this on chair 2 while it is empty and locked (chair 1 empty or PvP reset); the paired lore "Sėsk ant kitos kėdės" relies on that.
SUGGEST: "&7&oVieta neprieinama"

KEY: rock-paper-scissors.seat-unavailable
TIER: 1
ISSUE: Same, `RockPaperScissorsClient` chair 2.
SUGGEST: "&7&oVieta neprieinama"

KEY: slots.auto-stop-settlement-failed
TIER: 1
ISSUE: "išmokos nepavyko apskaičiuoti" = could not be calculated; in `SlotsSpinController` settlement is delivery (neither delivered live nor queued).
SUGGEST: "&cAutomatinis sukimas sustabdytas: išmokos nepavyko pristatyti."

KEY: coin-flip.no-bet
TIER: 2
ISSUE: "nepadarytas" vs "atlikti statymą" elsewhere.
SUGGEST: "&cStatymas neatliktas."

KEY: rock-paper-scissors.no-bet
TIER: 2
ISSUE: Same.
SUGGEST: "&cStatymas neatliktas."

KEY: dragon-descent.no-bet
TIER: 2
ISSUE: Same.
SUGGEST: "&cStatymas neatliktas."

KEY: payout.bank-claimed
TIER: 3
ISSUE: Leading "Atsiimti" reads as the Cash Out infinitive / a command.
SUGGEST: "&aAtsiėmei išsaugotus laimėjimus: {amount}."

KEY: coin-flip.player-two-seat
TIER: 3
ISSUE: "2 žaidėjo vieta" reads as a quantity, not an ordinal.
SUGGEST: "&f&oAntrojo žaidėjo vieta"

KEY: coin-flip.player-two-turn
TIER: 3
ISSUE: Same; also mismatched with the seat label.
SUGGEST: "&oAntrojo žaidėjo ėjimas"

KEY: rock-paper-scissors.player-two-seat
TIER: 3
ISSUE: Same.
SUGGEST: "&f&oAntrojo žaidėjo vieta"

KEY: rock-paper-scissors.player-two-turn
TIER: 3
ISSUE: Same.
SUGGEST: "&oAntrojo žaidėjo ėjimas"

KEY: baccarat.bets-closed
TIER: 3
ISSUE: "Statymai uždaryti" calque; casino UIs say "nebepriimami".
SUGGEST: "&cStatymai nebepriimami."

KEY: baccarat.bets-closed-place
TIER: 3
ISSUE: Same.
SUGGEST: "&cStatymai nebepriimami, statyti negalima."

KEY: baccarat.bets-closed-undo
TIER: 3
ISSUE: Same.
SUGGEST: "&cStatymai nebepriimami, atšaukti statymo negalima."

KEY: slots.no-safe-denomination
TIER: 3
ISSUE: Awkward "suma nėra saugi žaisti".
SUGGEST: "&cPrie šio dalytojo nėra jokios kitos statymo sumos, kuria būtų saugu žaisti."

KEY: slots.paylines-description
TIER: 3
ISSUE: "raštas" = ornament; a payline is an arrangement.
SUGGEST: "&7Kiekviena aktyvi linija – tai simbolių išdėstymas, kuris gali laimėti."

KEY: slots.paytable-leftmost-rule
TIER: 3
ISSUE: Subjectless "Turi" reads as "You must"; "ant" should be "nuo".
SUGGEST: "&8Seka turi prasidėti nuo kairiausio ritinio."

KEY: slots.guide-seeds-title
TIER: 3
ISSUE: Capitalized common noun mid-title.
SUGGEST: "&bApie sėklas"

KEY: slots.auto-settings-reset-done
TIER: 3
ISSUE: "atkurti į numatytuosius" calque.
SUGGEST: "&aAtkurti numatytieji automatinio sukimo nustatymai."

KEY: slots.auto-profit-target-set
TIER: 3
ISSUE: Bare "šios serijos" is vague and reuses the PvE streak term.
SUGGEST: "&aAutomatinis sukimas sustos, kai pelnas nuo jo pradžios pasieks {amount}."

KEY: slots.auto-loss-limit-set
TIER: 3
ISSUE: Same.
SUGGEST: "&aAutomatinis sukimas sustos, kad nuostolis nuo jo pradžios neviršytų {amount}."

KEY: slots.rail-height-effect
TIER: 3
ISSUE: Wordy; "egzistuoja" calque.
SUGGEST: "&7Aukštis nustato matomą langą ir galimas laimėjimo linijų formas."

KEY: slots.profiles-right-click-open
TIER: 3
ISSUE: Reflexive possessive "savo" required.
SUGGEST: "&7Dešinysis spustelėjimas – atverti savo profilius."

KEY: slots.rail-wager-controls
TIER: 3
ISSUE: "spustelėjimas mygtuku apačioje" (instrumental) reads as clicking with the button (same slip in all 8 rail-*-controls keys).
SUGGEST: "&7Mygtukas apačioje: kairysis spustelėjimas – didesnės vertės žetonas, dešinysis – mažesnės."

KEY: slots.rail-reels-controls
TIER: 3
ISSUE: Same.
SUGGEST: "&7Mygtukas apačioje: kairysis spustelėjimas – pridėti ritinį, dešinysis – pašalinti vieną."

KEY: slots.rail-spin-controls
TIER: 3
ISSUE: Same.
SUGGEST: "&7Mygtukas apačioje: kairysis spustelėjimas – sukti, dešinysis – bandomasis sukimas, dar vienas kairysis – užbaigti sukimą anksčiau."

KEY: slots.rail-clock-controls
TIER: 3
ISSUE: Same; "jo nustatymai" would refer to the button after restructuring.
SUGGEST: "&7Mygtukas apačioje: kairysis spustelėjimas – paleisti arba sustabdyti, dešinysis – keisti greitį, Shift + kairysis – automatinio sukimo nustatymai."

KEY: slots.rail-paylines-controls
TIER: 3
ISSUE: Same.
SUGGEST: "&7Mygtukas apačioje: kairysis spustelėjimas – pridėti liniją, dešinysis – pašalinti vieną."

KEY: slots.rail-height-controls
TIER: 3
ISSUE: Same.
SUGGEST: "&7Mygtukas apačioje: kairysis spustelėjimas – daugiau eilučių, dešinysis – mažiau."

KEY: slots.rail-profiles-controls
TIER: 3
ISSUE: Same, plus reflexive "savo".
SUGGEST: "&7Mygtukas apačioje: kairysis spustelėjimas – išsaugoti šiuos nustatymus, dešinysis – atverti savo profilius."

KEY: slots.rail-profiles-controls-empty
TIER: 3
ISSUE: Same.
SUGGEST: "&7Mygtukas apačioje: kairysis spustelėjimas – išsaugoti pirmąjį profilį."

## Terminology notes

- Placing a bet: standardize on "atlikti statymą".
- "Serija" is reserved for the PvE win streak; the auto-spin batch should
  not reuse it (the two description keys should follow the -set keys).
- "Užimta" is for occupied seats only (test-game); the locked chair is not
  occupied.
- Otherwise consistent: dalytojas, Bankininkas, statymas, Kartoti statymą,
  Statyti viską, Atsiimti, Bankas, žetonas, pokalbis, meniu, nustatymai vs
  asmeniniai nustatymai, serveris, ritiniai, laimėjimo linijos, sukimas,
  volatilumas, kazino pranašumas, grąža žaidėjui, seka vs serija, vijokliai.

Counts: Tier 0: 0 · Tier 1: 3 · Tier 2: 3 · Tier 3: 25 · Total: 31

## Disposition (translator)

All 31 applied. The seat-unavailable finding was re-verified in
`CoinFlipClient` (chair 2 rendered with `seat-unavailable` when both chairs
are empty or on reset to PvP) and added to the guide's §C registry and the
reviewer rubric. Follow-ups: the two auto-spin description keys now say
"nuo jo pradžios" instead of "šios sukimų serijos"; the roulette
bets-closed / bets-close-in lines follow "nebepriimami" (see packet 2).
