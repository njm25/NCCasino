# sl_SI review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Scripted structural comparison: placeholders,
`&` codes, `\n`, protected literals and command tokens all preserved in
English order (one false-positive `-1` hit inside "Stand-on-17"). No
Croatian/Serbian forms, no gendered second-person l-participles, number
agreement correct. Context notes verified: the three `closed-*` messages
stay distinct, `resplit-offer` is a completed action, max chain is a round
count, chip size is a value, `commands.player-only` is correct, Same Rank /
Same Value distinct, `occupations.*` genitive and Dragon `{setting}` fillers
lowercase genitive plural. Known `*.updated-detailed` template defects not
scored.

KEY: blackjack.must-sit-all-in
TIER: 2
ISSUE: "stavo vsega" paraphrases All In; every other key uses the label "Stavi vse".
SUGGEST: "&cZa »Stavi vse« moraš sedeti za mizo."

KEY: blackjack.shoe-exhausted-refunded
TIER: 3
ISSUE: "Delilniku kart je ... zmanjkalo kart" repeats "kart".
SUGGEST: "&eDelilnik kart se je sredi kroga izpraznil -- vrnjeno: {amount}."

KEY: complex-variant.title
TIER: 3
ISSUE: "zapletenih" = complicated; the sense is multi-attribute, "sestavljenih".
SUGGEST: "Meni sestavljenih različic"

KEY: complex-variant.none
TIER: 3
ISSUE: Same.
SUGGEST: "Ni sestavljenih različic"

KEY: complex-variant.unavailable
TIER: 3
ISSUE: Same.
SUGGEST: "&cTo bitje nima sestavljenih različic."

KEY: blackjack-settings.turn-timer-timeout-desc-2
TIER: 3
ISSUE: Masculine l-participles and possessive for a generic player; easy to neutralize.
SUGGEST: "&7To je tudi čas čakanja, preden se po zaprtju menija ali prekinitvi povezave poteza igralca razreši; mesto nato nespremenjeno nadaljuje do izida."

KEY: admin.vault-missing
TIER: 3
ISSUE: "Vault ni najden" calque; "z NCCasino" lacks a case; add a classifier noun.
SUGGEST: "&cVtičnika Vault ni bilo mogoče najti. Namesti Vault za uporabo z vtičnikom NCCasino."

KEY: admin.economy-missing
TIER: 3
ISSUE: "za uporabo Vault" needs genitive.
SUGGEST: "&cNi vtičnika za ekonomijo. Za uporabo vtičnika Vault namesti vtičnik za ekonomijo."

KEY: admin.install-economy
TIER: 3
ISSUE: Same.
SUGGEST: "&7Za uporabo vtičnika Vault namesti vtičnik za ekonomijo"

KEY: admin.install-vault
TIER: 3
ISSUE: "z NCCasino" lacks the instrumental construction.
SUGGEST: "&7Namesti Vault za uporabo z vtičnikom NCCasino"

KEY: admin.prompt-currency-item
TIER: 3
ISSUE: English "vanilla" used adjectivally.
SUGGEST: "&ePovleci sem predmet iz osnovne igre (vanilla), da ga nastaviš kot valuto."

KEY: admin.select-vanilla-currency
TIER: 3
ISSUE: Same.
SUGGEST: "Izbira valute iz osnovne igre"

KEY: admin.already-moving
TIER: 3
ISSUE: "Delivec se že premika" reads as the mob walking; the English means a move operation is in progress.
SUGGEST: "&cPremikanje delivca že poteka. Počakaj."

KEY: admin.move-failed
TIER: 3
ISSUE: "Chunk" left in English.
SUGGEST: "&cDelivca ni bilo mogoče premakniti. Kos sveta (chunk) se ni naložil."

KEY: admin.move-failed-detailed
TIER: 3
ISSUE: Same.
SUGGEST: "&cDelivca ni bilo mogoče premakniti. Kos sveta (chunk) se ni naložil niti po 30 tikih."

KEY: admin.stand-17-lore
TIER: 3
ISSUE: Subject missing; blackjack-settings keys say "da delivec obstane na 17".
SUGGEST: "&7Verjetnost, da delivec obstane na 17: &a{value}%"

KEY: occupations.stand-on-17
TIER: 3
ISSUE: Subjectless clause; in the finish-editing template "za »…«" attaches to it. Nominal genitive fits better.
SUGGEST: "verjetnosti obstanka delivca na 17"

KEY: occupations.rps-max-chain
TIER: 3
ISSUE: "za Kamen, škarje, papir" plus the template's "za »{name}«" stacks two "za" phrases.
SUGGEST: "največjega števila krogov v nizu v igri Kamen, škarje, papir"

KEY: occupations.coin-flip-max-chain
TIER: 3
ISSUE: Same.
SUGGEST: "največjega števila krogov v nizu v igri Cifra ali mož"

## Terminology notes

- All In: "Stavi vse" everywhere except blackjack.must-sit-all-in.
- Dealer stand on 17: subject dropped in admin.stand-17-lore and
  occupations.stand-on-17.
- Plugin literals in running text: standardize on "vtičnik Vault" /
  "vtičnik NCCasino" with the literal kept verbatim.

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 1 | Tier 3: 18 | Total: 19

## Disposition (translator)

18 applied as suggested. `blackjack-settings.turn-timer-timeout-desc-2` had
already been neutralized during self-review (the packet was cut from the
pre-patch build) as "... preden se razreši poteza, ko igralec zapre meni ali
prekine povezavo; to mesto nato nespremenjeno nadaljuje do izida." and was
kept in that form.
