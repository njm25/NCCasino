# sw_KE review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean (mechanical token-order
check of all 592 records). Template slots and semantic points hold
(`updated-detailed` with a full sentence in `{setting}`, `resplit-offer`
as a completed action, `none` in both slots, chip value, animate
agreement for jockeys / riders, `finish-editing` with every occupation).

KEY: admin.prompt-destination
TIER: 1
ISSUE: "Bofya unakoenda" ("click where you are going") names the admin; the destination is the dealer's.
APPLIED: "&aBofya mahali lengwa."

KEY: admin.prompt-chip-index
TIER: 2
ISSUE: "nambari" here vs "namba" everywhere else.
APPLIED: "&aAndika thamani mpya ya chipu namba {index} kwenye gumzo."

KEY: admin.chip-size-updated-detailed
TIER: 2
ISSUE: Same.
APPLIED: "&aThamani mpya ya chipu namba {index}: &e{size}&a."

KEY: admin.edit-chip-size
TIER: 2
ISSUE: Same.
APPLIED: "Hariri thamani ya chipu namba {index}"

KEY: admin.decks-lore
TIER: 2
ISSUE: "Idadi ya pakiti" dropped "za karata" used by every other deck key.
APPLIED: "&7Idadi ya pakiti za karata: &a{value}"

KEY: roulette.not-enough-currency
TIER: 3
ISSUE: "Hazitoshi: {currency}" needs agreement with the placeholder; the governing noun "kiasi" removes it.
APPLIED: "&cKiasi cha {currency} hakitoshi"

KEY: blackjack.current-player-turn
TIER: 3
ISSUE: Colon after the connective "ya".
APPLIED: "Zamu ya {player}"

KEY: blackjack.insurance-paid
TIER: 3
ISSUE: "Bima imelipwa" reads as "the insurance premium has been paid"; this is the payout to the player.
APPLIED: "&aUmelipwa bima: {amount}!"

KEY: coin-flip-settings.prompt-max-chain
TIER: 3
ISSUE: "kwa bila kikomo" calques "for unlimited".
APPLIED: "&aAndika kwenye gumzo idadi mpya ya juu ya raundi za mfululizo (-1 kwa idadi isiyo na kikomo)."

KEY: rock-paper-scissors-settings.prompt-max-chain
TIER: 3
ISSUE: Same.
APPLIED: "&aAndika kwenye gumzo idadi mpya ya juu ya raundi za mfululizo (-1 kwa idadi isiyo na kikomo)."

KEY: blackjack-settings.prompt-max-hands
TIER: 3
ISSUE: Same.
APPLIED: "&aAndika -1 kwa idadi isiyo na kikomo, au namba kamili ya 2 au zaidi."

KEY: blackjack-settings.invalid-max-hands
TIER: 3
ISSUE: Same.
APPLIED: "Tafadhali ingiza -1 kwa idadi isiyo na kikomo, au namba kamili ya 2 au zaidi."

KEY: coin-flip-settings.toggle-mode-switching
TIER: 3
ISSUE: "Wachezaji kubadilisha hali" reads like a headline, not a setting name.
APPLIED: "Ruhusa ya wachezaji kubadilisha hali"

KEY: rock-paper-scissors-settings.toggle-mode-switching
TIER: 3
ISSUE: Same.
APPLIED: "Ruhusa ya wachezaji kubadilisha hali"

KEY: admin.coin-flip-mode-switching-lore
TIER: 3
ISSUE: Same.
APPLIED: "&7Ruhusa ya wachezaji kubadilisha hali: &a{value}"

KEY: admin.rps-mode-switching-lore
TIER: 3
ISSUE: Same.
APPLIED: "&7Ruhusa ya wachezaji kubadilisha hali: &a{value}"

KEY: admin.standard-mode-fallback
TIER: 3
ISSUE: The implied subject of "ipatikane" continued "hali ya kawaida"; the thing that becomes available is the Vault mode named in the title above.
APPLIED: "&8Hali ya kawaida inatumika hadi hali hii ipatikane."

KEY: blackjack-settings.turn-timer-timeout-desc-1
TIER: 3
ISSUE: "kabla hajasimamishwa" stands the player as a person ("suspended"); the hand stands.
APPLIED: "&7Sekunde alizonazo mchezaji kutenda kabla mkono wake haujasimama kiotomatiki."

KEY: blackjack-settings.turn-timer-timeout-desc-2
TIER: 3
ISSUE: "kabla ... kuisha" is loose, "kiti hicho" has no antecedent, and "resolves" became "ends".
APPLIED: "&7Pia ndio muda kabla zamu ya mchezaji aliyefunga menyu au aliyekatika muunganisho haijaamuliwa, na dau la kiti chake hubaki mchezoni hadi matokeo."

KEY: mob-selection.reset-confirmation
TIER: 3
ISSUE: Subjunctive "Urejeshe ...?" is unnatural for a UI confirmation.
APPLIED: "Ungependa kurejesha usanidi kwenye chaguo-msingi?"

Tier 0: 0 · Tier 1: 1 · Tier 2: 4 · Tier 3: 15

## Disposition (translator)

All 20 applied. `admin.standard-mode-fallback` deviates from the
suggested "hadi Vault iweze kutumika": the English line has no "Vault",
and adding one changes the protected-literal count, so it reads "hadi
hali hii ipatikane" (until this mode is available). Follow-ups: the two
`mode-switching-updated` lines follow the new "Ruhusa ya wachezaji
kubadilisha hali" label, and `game-options.reset-confirmation` matches
`mob-selection.reset-confirmation`.
