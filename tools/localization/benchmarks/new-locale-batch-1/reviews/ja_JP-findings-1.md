# ja_JP review findings -- packet 1 (592 records)

Counts: Tier 0 = 0, Tier 1 = 1, Tier 2 = 2, Tier 3 = 29.

Tier 1 (confirmed in Java, fixed): slots.profile-name-illegal-characters said 英数字 (Latin letters + digits only), but SlotsProfileName accepts any Character.isLetter code point after NFC, so kana/kanji names are valid -> generic 文字（かな・漢字・英字など）. Registry entry added to the guide.
Tier 2 (fixed): common.return-to / player-menu.return-to "戻る：{menu}" -> "{menu} に戻る" pattern.
Tier 3 (all applied): payout.paid / paid-with-profit; context-disconnected; overflow.server-controlled-notice; language.explicit; coin-flip.awaiting-wager-lore; max-chain-hit / max-pot-hit congratulations (x4); chip "高額/低額" (x2); payout-pending / payout-blocked queue wording; bet-too-large; demo-result-loss hypothetical; line-flash-added/removed; auto-reset-already-default; auto-loss-limit-description; rail-exit; rail-exit-what ドア; rail-paylines-cost; mines.hidden / instrument-set / mode-set; test-game.round-finished / server-lost; dragon-descent.dealer-cannot-cover.
Coordinator follow-through: removed ASCII " -- " from the remaining Slots keys (spin-active, auto-spin-title-active, demo-cell-note, prompt-retry) to match the packet-2 finding.
Out-of-scope observation (not changed): zh_CN slots.profile-name-illegal-characters uses 字母, which Chinese readers often take as Latin letters only.
