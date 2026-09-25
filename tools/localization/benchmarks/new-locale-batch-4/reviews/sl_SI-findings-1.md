# sl_SI review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Scripted structural comparison: every record
preserves placeholders, `&` codes and `\n` in English order; `/ncc claim`,
`-1` and `off` intact. No gendered second-person forms, no Croatian or
Serbian forms, number placeholders in label form. Verified in Java: the
house-edge parser accepts `2,5 %`; Dragon Descent rebet-reset clears the
wager.

KEY: coin-flip.pot
TIER: 1
ISSUE: "&oPot" is untranslated English and reads as Slovenian "pot" (path/way).
SUGGEST: "&oSklad"

KEY: rock-paper-scissors.pot
TIER: 1
ISSUE: Same untranslated "Pot".
SUGGEST: "&oSklad"

KEY: coin-flip.chain-win
TIER: 2
ISSUE: "Trenutni pot" uses English "pot" as a masculine loanword; align with the pot label.
SUGGEST: "&aZmaga! Niz: {streak}. Trenutni sklad: &e{amount}&a. Unovči ali znova vrzi kovanec."

KEY: rock-paper-scissors.chain-win
TIER: 2
ISSUE: Same loanword.
SUGGEST: "&aZmaga! Niz: {streak}. Trenutni sklad: &e{amount}&a. Unovči ali igraj znova."

KEY: dragon-descent.dealer-cannot-cover
TIER: 2
ISSUE: "tako velikega pota" is non-standard; other cannot-cover messages use "dobitka".
SUGGEST: "&cTa spust trenutno ne more kriti tako velikega dobitka. Poskusi pozneje ali zmanjšaj vložek."

KEY: slots.wager-control
TIER: 2
ISSUE: "Stava na linijo" uses "stava" for the stake amount; elsewhere the amount is "vložek".
SUGGEST: "&eVložek na linijo"

KEY: slots.rail-wager
TIER: 2
ISSUE: Same stake-amount drift.
SUGGEST: "&fVložek na linijo"

KEY: slots.guide-machine-wager
TIER: 2
ISSUE: Same stake-amount drift.
SUGGEST: "&7Vložek na linijo: &a{amount}"

KEY: slots.profile-entry-wager
TIER: 2
ISSUE: Same stake-amount drift.
SUGGEST: "&7Vložek na linijo: &a{amount}"

KEY: slots.rail-wager-current
TIER: 2
ISSUE: "Trenutna stava" for the current wager amount.
SUGGEST: "&7Trenutni vložek: &a{amount} na linijo"

KEY: slots.invalid-denomination
TIER: 2
ISSUE: "višina stave" vs "višina vložka" elsewhere.
SUGGEST: "&cTa višina vložka za tega delivca ni nastavljena."

KEY: slots.no-safe-denomination
TIER: 2
ISSUE: "nobena druga stava" may read as "no other bet type"; the message concerns the denomination.
SUGGEST: "&cPri tem delivcu nobena druga višina vložka ni varna za igro."

KEY: coin-flip.mode-switch
TIER: 3
ISSUE: "Zamenjaj način na:" calque of "switch mode to".
SUGGEST: "&oPreklopi na način {mode}"

KEY: rock-paper-scissors.mode-switch
TIER: 3
ISSUE: Same calque.
SUGGEST: "&oPreklopi na način {mode}"

KEY: coin-flip.max-chain-hit
TIER: 3
ISSUE: Relative clause reads as "the longest streak that was achieved" rather than "you reached the configured cap".
SUGGEST: "&6Dosežen je najdaljši niz zmag, ki ga dovoli ta delivec (število iger: {rounds}). Čestitke za največji dobitek!"

KEY: rock-paper-scissors.max-chain-hit
TIER: 3
ISSUE: Same.
SUGGEST: "&6Dosežen je najdaljši niz zmag, ki ga dovoli ta delivec (število iger: {rounds}). Čestitke za največji dobitek!"

KEY: payout.retry-one
TIER: 3
ISSUE: "Samodejno bo poskušeno znova." clumsy impersonal passive.
SUGGEST: "&6Imaš 1 čakajoče izplačilo, ki ga še ni bilo mogoče dostaviti. Dostava se bo samodejno ponovila."

KEY: payout.retry-many
TIER: 3
ISSUE: Same.
SUGGEST: "&6Čakajoča izplačila, ki jih še ni bilo mogoče dostaviti: {count}. Dostava se bo samodejno ponovila."

KEY: preferences.overflow.drop-explained
TIER: 3
ISSUE: "ostalo ostane" repetition.
SUGGEST: "&7Na tla: odvržejo se v bližini do omejitve strežnika, preostanek ostane varno shranjen."

KEY: rock-paper-scissors.tie
TIER: 3
ISSUE: "Izbira se znova ..." awkward reflexive passive.
SUGGEST: "&eNeodločeno! Ponovna izbira ..."

KEY: slots.loss
TIER: 3
ISSUE: "To vrtenje brez dobitka." verbless and stilted.
SUGGEST: "&c&lTokrat brez dobitka."

KEY: mines.instrument-set
TIER: 3
ISSUE: "spremenjeno v" calque; settings use "nastavljeno na".
SUGGEST: "&bGlasbilo nastavljeno na: {instrument}"

KEY: mines.mode-set
TIER: 3
ISSUE: Same calque.
SUGGEST: "&eNačin nastavljen na: {mode}"

KEY: slots.profile-saved
TIER: 3
ISSUE: Singular "Ta nastavitev" for a whole set of settings.
SUGGEST: "&aTe nastavitve so shranjene kot {name}."

KEY: slots.profiles-left-click-save
TIER: 3
ISSUE: Same singular.
SUGGEST: "&7Levi klik za shranjevanje teh nastavitev."

KEY: slots.rail-profiles-controls
TIER: 3
ISSUE: Same singular.
SUGGEST: "&7Levi klik na gumb spodaj za shranjevanje teh nastavitev, desni klik za odpiranje tvojih profilov."

## Terminology notes

- Pot: untranslated "Pot" label, "Trenutni pot" loanword, "pota" in the
  Dragon cannot-cover message; proposed "Sklad" for label and chain
  messages, "dobitek" in the cannot-cover sentence.
- Stake amount: "vložek" in betting / mines / chain keys, "stava" in the
  Slots per-line keys; if "vložek" is adopted, also align
  slots.profiles-saves-what, rail-profiles-stores, profile-adjusted,
  slots-settings.variance-max-exposure, paytable-legend-multiplier /
  rail-paytable-multiplier and paytable-legend-return /
  rail-paytable-return. "Skupna stava" for Total bet is fine.
- "Set to": "nastavljen(a) na" is the idiomatic form.
- "gumb" is standard Slovenian for a UI button (not flagged).

Counts: Tier 0: 0 · Tier 1: 2 · Tier 2: 10 · Tier 3: 14 · Total: 26

## Disposition (translator)

All 26 applied as suggested. Follow-ups for consistency: the eight Slots
prose keys listed in the terminology notes now use `vložek`; because `sklad`
now means the pot, the mob-editor stack (`mob-settings.add-vehicle-lore`,
`add-passenger-lore`, `jockey-not-found`) was renamed to `kup` so one word
does not carry two concepts.
