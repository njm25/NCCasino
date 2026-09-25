# ru_RU review findings -- packet 1 (592 records)

Counts: Tier 0 = 0, Tier 1 = 1, Tier 2 = 7, Tier 3 = 32.

Tier 1 (confirmed in Java, fixed): betting.inventory-full called the dropped amount "выигрыш", but Client/Server.creditPlayer also returns stakes (Baccarat refunds, undo, cancelled offers) -> neutral "предметы". Registry entry added to the guide.
Tier 2 (all applied): Auto Spin "batch" rendered "серия автовращений" (collides with streak term, mixes автовращение/автоспин) x4 -> "с момента запуска"; spin "return" as "возврат" (collides with refund) x3 -> "выплата".
Tier 3 (all applied): messages.minimal; mode-switch-cashout-notice agreement (x2); awaiting-wager-lore; dragon-descent.dealer-cannot-cover, rebet-placed; test-game.waiting-accept; reset-confirmation; slots partial-return, no-safe-denomination, paylines-wrap-notice, spin speeds as feminine adjectives (x3), auto-rule-loss comma, reset wording (x3), auto-spin-limit-set, "Порог остановки" (x3), guide-volatility-normalized, rail-height-current, "конфигурация" for setup (x3), prompt-another-game-cancelled; slots-settings hints (x2), variance-tradeoff; mines.instrument-set.
Coordinator follow-through: "--" -> "—" in the four Slots keys the packet-2 finding did not cover; mines.rebet-placed aligned to the label form; auto-spin-limit-current aligned.
