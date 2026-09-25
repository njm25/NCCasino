# uz_UZ review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean (script-checked token order;
no suffix on a placeholder; `ʻ` only after o/g, `ʼ` only in Nomaʼlum,
maʼmuriyatiga, qatʼi; protected literals intact). Context notes hold
(seat not yet available, current pot, future cash-out, round-count max
chain, vines, auto-spin batch never "seriya", "2,5%").

KEY: cards.suits.spades
TIER: 1
ISSUE: "qarga" is a misspelling of "qargʻa" (crow, the suit name); it shows in every spade card.
APPLIED: "qargʻa"

KEY: baccarat.bets-closed
TIER: 1
ISSUE: "Stavkalar qabul qilish yopildi" is ungrammatical (bare plural object of a verbal noun used as subject).
APPLIED: "&cStavkalar qabuli yopildi."

KEY: baccarat.bets-closed-place
TIER: 1
ISSUE: Same.
APPLIED: "&cStavkalar qabuli yopildi, stavka qoʻyib boʻlmaydi."

KEY: baccarat.bets-closed-undo
TIER: 1
ISSUE: Same.
APPLIED: "&cStavkalar qabuli yopildi, stavkani bekor qilib boʻlmaydi."

KEY: slots.payout-blocked-retry
TIER: 1
ISSUE: "urinmoq" is intransitive and cannot take the accusative "toʻlovni".
APPLIED: "&7Toʻlovni qayta amalga oshirish uchun bosing."

KEY: slots-settings.variance-tradeoff
TIER: 1
ISSUE: "kamroq toʻlov keltirishini" reads as "pay smaller amounts"; the mechanic is lower hit frequency.
APPLIED: "&7Yuqoriroq volatillik chiziqlar kamroq hollarda toʻlov keltirishini, lekin uzun ketma-ketliklardagi jekpotlar ancha katta boʻlishini bildiradi."

KEY: slots.rail-spin
TIER: 2
ISSUE: Rail heading "Aylanish" differs from its control label "Aylantirish".
APPLIED: "&fAylantirish"

KEY: betting.inventory-full
TIER: 3
ISSUE: "Joy yoʻq: {amount}" makes the amount look like the missing room; "{amount} uchun" keeps it bare.
APPLIED: "&cInventarda {amount} uchun joy yoʻq; ular yaqin atrofga tashlanadi."

KEY: payout.retry-one
TIER: 3
ISSUE: Awkward passive "yetkazib boʻlinmagan"; natural "yetkazilmagan".
APPLIED: "&6Sizda hali yetkazilmagan 1 ta kutilayotgan toʻlov bor. U avtomatik ravishda qayta yuboriladi."

KEY: payout.retry-many
TIER: 3
ISSUE: Same, and the "Sizda … bor" frame dropped.
APPLIED: "&6Sizda hali yetkazilmagan {count} ta kutilayotgan toʻlov bor. Ular avtomatik ravishda qayta yuboriladi."

KEY: slots-settings.variance-hit-rate
TIER: 3
ISSUE: Telegraphic; genitive "Chiziqning" missing.
APPLIED: "&7Chiziqning toʻlov keltirish ehtimoli: &e{chance}"

KEY: slots.rail-reels-tradeoff
TIER: 3
ISSUE: "kattaroq, lekin kamroq" can read as "bigger but smaller".
APPLIED: "&7Barabanlar kam boʻlsa, kichik yutuqlar tez-tez chiqadi; koʻp boʻlsa, yutuqlar kattaroq, lekin kamdan-kam chiqadi."

KEY: slots.profiles-full
TIER: 3
ISSUE: Clumsy; "{max} ta" is placeholder-safe.
APPLIED: "&cSizda allaqachon {max} ta saqlangan profil bor. Avval bittasini oʻchiring."

KEY: slots.paytable-card-no-runs
TIER: 3
ISSUE: Awkward parenthetical; "{columns} ta barabanga" is safe and natural.
APPLIED: "&8Bu belgining hech bir ketma-ketligi {columns} ta barabanga sigʻmaydi."

KEY: mines.no-currency
TIER: 3
ISSUE: Label form reads like a form field; a bare nominative before "yoʻq" is natural.
APPLIED: "&c{currency} yoʻq"

KEY: mines.insufficient-currency
TIER: 3
ISSUE: Same.
APPLIED: "&c{currency} yetarli emas."

KEY: slots.wager-control-hint
TIER: 3
ISSUE: "kattaroq fishka" can mean a physically bigger chip; the control changes value.
APPLIED: "&7Chap tugma — qiymati kattaroq fishka, oʻng tugma — qiymati kichikroq fishka."

KEY: slots.rail-wager-controls
TIER: 3
ISSUE: Same.
APPLIED: "&7Pastda chap tugma — qiymati kattaroq fishka, oʻng tugma — qiymati kichikroq fishka."

Tier 0: 0 · Tier 1: 6 · Tier 2: 1 · Tier 3: 11

## Disposition (translator)

All 18 applied. Follow-ups in self-review: the roulette / blackjack
`no-currency` and roulette `not-enough-currency` lines take the same bare
nominative form, and the mines / roulette / blackjack `inventory-full`
lines take "{amount} uchun".
