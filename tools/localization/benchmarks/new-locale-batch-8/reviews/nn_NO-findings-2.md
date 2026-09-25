# nn_NO review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean (token order, protected
literals; the "-1" hit is inside "Stand-on-17"). No Bokmål leakage;
participle agreement, template slots, `resplit-offer`,
`turn-timer-timeout-desc-2`, the two split rules and round-count max
chain all hold.

KEY: mob-selection.save-failed
TIER: 2
ISSUE: "dealardata" breaks the "dealer" spelling used everywhere else.
APPLIED: "&cKunne ikkje lagre dealerdata: {error}"

KEY: roulette.no-wager
TIER: 3
ISSUE: Masculine innsats takes "vald", not neuter "valt".
APPLIED: "&cIngen innsats er vald"

KEY: roulette.bet-placed
TIER: 3
ISSUE: Subjectless "Sette ..." reads as an infinitive or headline; this is a past-tense confirmation.
APPLIED: "&6Du sette {amount} på {bet}"

KEY: coin-flip-settings.mode-switching-updated
TIER: 3
ISSUE: "spelarbyte" normally means a player substitution; match the toggle label.
APPLIED: "&aInnstillinga for om spelarane kan byte spelmodus, er oppdatert."

KEY: rock-paper-scissors-settings.mode-switching-updated
TIER: 3
ISSUE: Same.
APPLIED: "&aInnstillinga for om spelarane kan byte spelmodus, er oppdatert."

KEY: mines.burning
TIER: 3
ISSUE: A bare "Brenn" is identical to the imperative ("Burn!"); the tile label is a state.
APPLIED: "Brennande"

KEY: admin.select-vanilla-currency
TIER: 3
ISSUE: "vanleg valuta" loses the Minecraft sense of "vanilla" shown next to the VANILLA mode name.
APPLIED: "Vel vanilla-valuta"

KEY: blackjack.round-summary-hand-blackjack
TIER: 3
ISSUE: Lower-case "blackjack!" while the sibling summary lines are capitalised.
DECLINED: the capitalised value is byte-identical to the English source and is flagged as possible residue; kept "&aHand {number}: blackjack! +{amount}".

Tier 0: 0 · Tier 1: 0 · Tier 2: 1 · Tier 3: 7

## Disposition (translator)

Seven applied, one declined: `blackjack.round-summary-hand-blackjack`
keeps the lower-case "blackjack!" because the capitalised form is
byte-identical to the English line, which the candidate check flags as
possible untranslated residue. Follow-up in self-review:
`admin.prompt-currency-item` also says "vanilla-gjenstand".
