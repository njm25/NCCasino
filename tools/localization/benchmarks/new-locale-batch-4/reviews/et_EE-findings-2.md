# et_EE review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Scripted checks clean (tokens, protected
literals; the only "-1" mismatch is inside "Stand-on-17"). Read-through:
distinct `closed-*`, completed-action `resplit-offer`, distinct Same Rank /
Same Value, `player-only` correct, genitive `occupations.*`, lowercase
genitive-plural Dragon fillers, round-count max chain, chip value,
`kaardikast`, label-form counts, „“ quotes. `*.updated-detailed` template
defects not scored.

KEY: blackjack.must-sit-all-in
TIER: 3
ISSUE: A quoted imperative cannot take "jaoks" directly; opening with a bare quote is awkward.
SUGGEST: "&cValiku „Pane kõik“ kasutamiseks pead laua taga istuma."

KEY: blackjack.insufficient-bet
TIER: 3
ISSUE: Slangy "Too broke" became neutral; tone lost.
SUGGEST: "&cRahakott on panuse tegemiseks liiga kõhn."

KEY: blackjack.wager-per-hand-title
TIER: 3
ISSUE: "käsi: {count}" reads as singular "hand: 3", not a count label.
SUGGEST: "Panus: {amount} käe kohta, käte arv: {count}"

KEY: dragon-settings.columns-updated
TIER: 3
ISSUE: Reads as the columns being refreshed; the setting is their number.
SUGGEST: "&aMängu „Draakoni laskumine“ veergude arv on uuendatud."

KEY: dragon-settings.vines-updated
TIER: 3
ISSUE: Same.
SUGGEST: "&aMängu „Draakoni laskumine“ väätide arv on uuendatud."

KEY: dragon-settings.floors-updated
TIER: 3
ISSUE: Same.
SUGGEST: "&aMängu „Draakoni laskumine“ korruste arv on uuendatud."

KEY: blackjack-settings.prompt-stand-17
TIER: 3
ISSUE: "uus protsent tõenäosusele" is an awkward calque.
SUGGEST: "&aKirjuta vestlusesse diileri 17 juures jäämise uus tõenäosus protsentides."

KEY: blackjack-settings.prompt-stand-17-detailed
TIER: 3
ISSUE: Same calque; the range dangles.
SUGGEST: "&aKirjuta vestlusesse diileri 17 juures jäämise uus tõenäosus protsentides vahemikus 0 kuni 100."

KEY: blackjack-settings.insurance-desc-2
TIER: 3
ISSUE: "Maksab poole" reads as "pays half"; with the next "Maksab" it can be misread as a payout.
SUGGEST: "&7Hind on pool sinu panusest. Maksab välja 2:1, kui diileri peidetud kaardi väärtus on 10 (blackjack)."

KEY: blackjack-settings.insurance-timeout-desc
TIER: 3
ISSUE: "Sekundid, mis mängijatel on" is a clunky calque.
SUGGEST: "&7Mitu sekundit on mängijatel aega otsustada, enne kui automaatselt valitakse „Ei“."

KEY: blackjack-settings.turn-timer-timeout-desc-1
TIER: 3
ISSUE: Same calque.
SUGGEST: "&7Mitu sekundit on mängijal aega tegutseda, enne kui automaatselt valitakse „Jää“."

KEY: blackjack-settings.turn-timer-timeout-desc-2
TIER: 3
ISSUE: Meaning correct but two "kui" clauses and a semicolon are hard to parse.
SUGGEST: "&7See on ka ooteaeg, mille järel menüü sulgenud või ühenduse kaotanud mängija käik lahendatakse ja tema koha panus jääb tulemuseni mängu."

KEY: admin.currency-selection-disabled-future
TIER: 3
ISSUE: "Plaanis tulevikus" is verbless and unidiomatic.
SUGGEST: "&cValuuta valik on VAULT-režiimis keelatud. Tulevikus on see plaanis"

KEY: admin.rps-max-chain-lore
TIER: 3
ISSUE: English-style "max" + nominative plural; use a count label.
SUGGEST: "&7Seeria maks. voorude arv: &a{value} &7(PvE)"

KEY: admin.rps-max-chain-lore-unbounded
TIER: 3
ISSUE: Same.
SUGGEST: "&7Seeria maks. voorude arv: &apiiramatu &7(PvE)"

KEY: admin.coin-flip-max-chain-lore
TIER: 3
ISSUE: Same.
SUGGEST: "&7Seeria maks. voorude arv: &a{value} &7(PvE)"

KEY: admin.coin-flip-max-chain-lore-unbounded
TIER: 3
ISSUE: Same.
SUGGEST: "&7Seeria maks. voorude arv: &apiiramatu &7(PvE)"

KEY: admin.vault-no-economy
TIER: 3
ISSUE: "majanduseta" is vague; the state is "no economy plugin".
SUGGEST: "&cVault (majanduspluginat pole)"

KEY: test-menu.clicked-one
TIER: 3
ISSUE: Impersonal "Klõpsati" drops the direct "You clicked".
SUGGEST: "Klõpsasid valikul „Esimene valik“!"

KEY: test-menu.clicked-two
TIER: 3
ISSUE: Same.
SUGGEST: "Klõpsasid valikul „Teine valik“!"

KEY: commands.invalid-page
TIER: 3
ISSUE: "lehekülje number" should be one compound, and "leht" is used elsewhere.
SUGGEST: "&cVigane lehenumber. Kuvatakse leht 1."

## Terminology notes

No Tier 2 inconsistencies. Max chain was "seeria maksimaalne voorude arv"
in settings but "Seeria max voorud" in admin lore (fixed above). Settings
titles use the genitive for single nouns and "Mängu „X“ seaded" for
quoted titles (acceptable). "Vault'i" vs strict "Vaulti" noted, not
scored.

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 0 | Tier 3: 21

## Disposition (translator)

All 21 applied as suggested.
