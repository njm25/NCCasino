# vi_VN review findings -- packet 1 (592 records)

Counts: Tier 0 = 0, Tier 1 = 2 (not reproduced), Tier 2 = 5, Tier 3 = 25.

Tier 1 (checked against Java, does not reproduce, no change): coin-flip.waiting-bet / rock-paper-scissors.waiting-bet -- reviewer worried "đối phương" would be wrong for the player whose bet is pending. CoinFlipClient.handlePlayerTwoSit / resetPlayerTwoUI show this lore only on the chair-two player's own client while chair one has not bet, so "their bet" = the opponent's bet; "đối phương" is correct. Registry entry added to the guide.
Tier 2 (applied): "quay lại" (go back) vs Spin "Quay" in Slots -> "trở lại màn hình cuộn" (x4); slots.rail-exit-session "lượt chơi" -> "phiên chơi".
Tier 3 (all applied): overflow.drop; language-menu.selected; payout.paid / paid-with-profit (recipient explicit); retry-one/-many; player-turn word order (x2); RPS click-choose, tie, chain-win; dragon-descent click-move, oof, rebet-reset (verified: bet stack is cleared); slots spin-active, demo results (x2), auto-any-win, auto-big-win-off-set, auto profit/loss description/set (x4), rail-paylines-cost, rail-paylines-feedback, prompt-invalid-spin-limit.
