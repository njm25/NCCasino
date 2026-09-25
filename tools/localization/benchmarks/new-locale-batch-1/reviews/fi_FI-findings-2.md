# fi_FI review findings -- packet 2 (592 records)

Counts: Tier 0 = 0, Tier 1 = 3, Tier 2 = 6, Tier 3 = 27.

Tier 1 (fixed):
- blackjack.insurance-paid -- "Vakuutus maksettu" reads as the premium being charged -> "Vakuutuksesta voittoa".
- admin.standard-mode-fallback -- "se" referred back to standard mode (circular). Reviewer suggested naming Vault, but adding the protected literal "Vault" where English has none would break the Class B literal count; reworded without it ("kunnes valittu tila on käytettävissä").
- blackjack-settings.split-matching-desc-1 -- "Identtinen kortti ... täsmälleen samat" implies same suit too -> "Vain parit ... maasta riippumatta (K-K)".

Tier 2 (fixed): hidden-card vs piilokortti; panospaikka vs panosalue; mode-switching lore name; "uudelleenjako" for resplit (x2).

Tier 3 (all applied): mines.burning; roulette.invalid-wager-selected; blackjack must-sit-all-in, stood, turn-timer-expired, starts-in, round-summary-hand-busted, insurance-timer-lore, insurance-declined, insurance-lost, start-transition-failed-refunded; complex-variant title/none/unavailable; *-settings.mode-updated (x2); blackjack-settings insurance-desc-2, insurance-timeout-desc, turn-timer-timeout-desc-1, match-same-rank, en-dash ranges (x2); admin economy plugin term (x3), currency-selection-disabled-future, cant-do-that.
Info only: game-name capitalization in settings titles; "ratsastaja" used for any stacked mob (mirrors English "jockey").
