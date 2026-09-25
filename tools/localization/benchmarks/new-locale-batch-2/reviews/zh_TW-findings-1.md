# zh_TW review -- packet-1 (592 records), isolated reviewer 1

Mechanical pre-check: token order identical in every record; /ncc claim, -1,
off, {overwrite}, {cancel}, {unlimited} preserved; only placeholder+letter
join is `{multiplier}x` (same in EN). No Simplified characters or
Mainland-only vocabulary. Glossary consistent (荷官, 閒家/莊家, 賭場優勢, 派彩,
兌現, 連線 vs 連勝, 重複下注, 獎池); context keys correct.

Tier 0: none. Tier 1: none.

Tier 2:
- coin-flip / rock-paper-scissors .mode-switch-denied: 局 vs 回合 elsewhere.
  SUGGEST "&c請先完成這一回合再切換模式。"

Tier 3:
- betting / slots .insufficient-funds: "不足以下這筆注" misparses as 以下;
  SUGGEST "&c你的貨幣不足，無法下這筆注。"
- payout.context-server-restart: run-on; SUGGEST "伺服器重新啟動時，你在{game}
  的下注仍在等待結果，因此該筆下注已退還。"
- slots.demo-result-loss: counterfactual lost; SUGGEST "&e試玩：以 {bet}
  的下注，這次旋轉本來不會贏得任何獎金。沒有任何貨幣進出。"
- slots.rail-wager-total: space before full-width comma.
- slots.auto-big-win-off-set: "大獎停止" -> "大獎停止條件".
- slots.payout-blocked: quote the button 「旋轉」.
- slots.paytable-card-no-runs: "放進" literal.
- coin-flip / rock-paper-scissors / baccarat .click-leave-chair: 離座.
- slots.auto-rule-any-win: "只要中獎就停止。"
- slots.auto-big-win-description: convoluted.
- slots.prompt-invalid-spin-limit: "請以整數輸入旋轉次數".
- betting.inventory-full: space before {amount} for consistency.
- dragon-descent.vines-per-floor (optional): "每層安全位置數".

Terminology notes: 回合 vs 局; 離座 vs 離開椅子; 退款 vs 退還 (low);
house-edge-current "返還" vs 返還率 (optional).

Counts: Tier 0 = 0, Tier 1 = 0, Tier 2 = 2 (keys), Tier 3 = 16 (keys).
