# af_ZA review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 checks clean (scripted token order,
protected literals, `/ncc` commands, `-1`). Every negation closes with
"nie"; only the allowed "chunk" / "ticks" remain in English; nothing
assumes a gender. Context notes hold: `resplit-offer` "Hand weer verdeel!"
is a completed action; the `closed-*` timers stay distinct; max chain is
"reeksrondes" (a round count); "Dieselfde rang (H-H)" vs "Dieselfde waarde
(H-V)"; every `occupations.*` value fits `finish-editing`;
`dragon-settings.columns|vines|floors` fit after "verstekaantal"; both
`updated-detailed` templates read "…bygewerk. Nuwe waarde: X.".

KEY: blackjack.seat-taken-elsewhere
TIER: 3
ISSUE: Stacked particles "af op" read clumsily.
SUGGEST: "As jy hierdie sitplek wil hê, moet jy eers van jou eie stoel opstaan"

KEY: blackjack.must-sit-all-in
TIER: 3
ISSUE: "sit om alles in te sit" is an unintended pun and does not point to the button.
SUGGEST: "&cJy moet sit om “Alles in” te kies."

KEY: blackjack.insurance-declined
TIER: 3
ISSUE: The idiom "van die hand gewys" clashes with the Blackjack "hand".
SUGGEST: "&7Versekering afgewys."

KEY: blackjack.wager-not-insurance-compatible
TIER: 3
ISSUE: "verdeel" is the Split term; here the wager is halved.
SUGGEST: "&cDaardie inset kan nie vir versekering gehalveer word nie -- probeer 'n ewe bedrag."

KEY: blackjack-settings.prompt-stand-17
TIER: 3
ISSUE: Bare verb phrase used as a noun.
SUGGEST: "&aTik die nuwe persentasie vir “staan op 17” in die klets."

KEY: blackjack-settings.prompt-stand-17-detailed
TIER: 3
ISSUE: Same.
SUGGEST: "&aTik 'n nuwe persentasie vir “staan op 17” tussen 0 en 100 in die klets."

KEY: blackjack-settings.turn-timer-timeout-desc-2
TIER: 3
ISSUE: Possessive relative needs "wie se"; "of se" is ungrammatical.
SUGGEST: "&7Ook die tyd voordat die beurt van 'n speler wat die kieslys toegemaak het of wie se verbinding verbreek is, afgehandel word; die inset op daardie sitplek bly in die spel tot by die uitslag."

KEY: blackjack-settings.max-hands-desc
TIER: 3
ISSUE: "Die meeste hande" reads as "most of the hands", not a maximum.
SUGGEST: "&7Die maksimum aantal hande wat een speler op een slag deur verdeling kan hê."

KEY: blackjack-settings.toggle-aces-resplit
TIER: 2
ISSUE: "herverdeling" normally means redistribution and differs from "weer verdeel" in resplit-offer.
SUGGEST: "Wissel herhaalde verdeling van Asse"

KEY: blackjack-settings.aces-resplit-updated
TIER: 2
ISSUE: Same drift and ambiguity.
SUGGEST: "&eHerhaalde verdeling van Asse: {value}"

KEY: mob-settings.delete-mode-stays-lore
TIER: 3
ISSUE: Separable verb is written solid at the end of a subordinate clause.
SUGGEST: "wat aanbly totdat jy weer klik"

KEY: mob-selection.data-file-not-found
TIER: 3
ISSUE: Unneeded hyphen; differs from "kroepierdata".
SUGGEST: "&cDie kroepiers se datalêer is nie gevind nie!"

KEY: roulette.wrong-game
TIER: 3
ISSUE: A dealer runs the game rather than playing it.
SUGGEST: "Fout: hierdie kroepier bied nie Roulette aan nie."

KEY: occupations.rps-max-chain
TIER: 3
ISSUE: Double "vir" inside finish-editing, plus the commas in the game name.
SUGGEST: "die maksimum reeksrondes in Klip, papier, skêr"

KEY: occupations.coin-flip-max-chain
TIER: 3
ISSUE: Same double "vir".
SUGGEST: "die maksimum reeksrondes in Kop of stert"

KEY: coin-flip-settings.toggle-mode-switching
TIER: 3
ISSUE: A full clause as a label reads as contradictory above "Huidig: Afgeskakel".
SUGGEST: "Modusverandering deur spelers"

KEY: rock-paper-scissors-settings.toggle-mode-switching
TIER: 3
ISSUE: Same.
SUGGEST: "Modusverandering deur spelers"

KEY: admin.rps-mode-switching-lore
TIER: 3
ISSUE: "Spelers kan van modus verander: Afgeskakel" contradicts itself.
SUGGEST: "&7Modusverandering deur spelers: &a{value}"

KEY: admin.coin-flip-mode-switching-lore
TIER: 3
ISSUE: Same.
SUGGEST: "&7Modusverandering deur spelers: &a{value}"

## Terminology notes

Re-split "weer verdeel" vs "herverdeling"; player mode switching clause vs
"spelers se modusverandering"; three phrasings of the stand-on-17 chance
(minor). Consistent elsewhere: kroepier, weddenskap / inset, Herhaal
weddenskap, Alles in, skyfie(waarde), tydhouer / tydlimiet, klets, kieslys,
instellings, kaartskoen, kaartpakke, Trek / Staan / Verdubbel / Verdeel,
versekering, skepsel, jokkie, voertuig / passasier, reeksrondes,
rankplante, Aangeskakel / Afgeskakel, skrap, bygewerk.

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 2 | Tier 3: 17 | Total: 19

## Disposition (translator)

All 19 applied as suggested. Follow-ups from the terminology notes:
`coin-flip-settings.mode-switching-updated` /
`rock-paper-scissors-settings.mode-switching-updated` now say
"modusverandering deur spelers", and `admin.stand-17-lore` /
`occupations.stand-on-17` use "die kans dat die kroepier op 17 staan" like
`edit-stand-17` and `stand-17-updated`.
