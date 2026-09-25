# az_AZ review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 checks clean (scripted token order;
no placeholder followed by a letter except `{multiplier}x` as in English;
`-1`, `off`, `/ncc claim`, `PvP`, `PvE` intact). No Turkish forms or
k-for-q spellings. Context notes hold: `seat-unavailable` (empty, not
open yet), chain-win `{amount}` as "Cari bank", future cash-out notice,
max chain vs max pot, the three payout contexts distinct, overflow held,
third-person `player-turn`, vines as "Sarmaşıqlar", variance keys correct,
the auto-spin batch "Başladılandan bəri" (never "seriya").

KEY: preferences.overflow.drop
TIER: 3
ISSUE: "Yaxına at" is colloquial and terse for dropping items on the ground nearby.
SUGGEST: "&eYaxınlıqda yerə at"

KEY: preferences.overflow.drop-explained
TIER: 3
ISSUE: Should match the corrected Drop label.
SUGGEST: "&7At: serverin limitinə qədər yaxınlıqda yerə atılır, qalanı təhlükəsiz saxlanılır."

KEY: betting.inventory-full
TIER: 3
ISSUE: "Yer yoxdur: {amount}" is ambiguous and "yaxına" colloquial; "üçün" after the placeholder is a separate word.
SUGGEST: "&c{amount} üçün yer yoxdur; yaxınlıqda yerə atılır."

KEY: payout.context-disconnected
TIER: 3
ISSUE: Clumsy "({game})" parenthetical; use the governing noun "{game} oyunu" as game.welcome does.
SUGGEST: "Aktiv {game} oyunu zamanı bağlantınız kəsildi. Siz oflayn olarkən oyun başa çatdı."

KEY: payout.context-server-restart
TIER: 3
ISSUE: Same parenthetical.
SUGGEST: "{game} oyunundakı mərciniz hələ nəticə gözləyərkən server yenidən başladı, ona görə mərciniz geri qaytarıldı."

KEY: payout.context-committed-result
TIER: 3
ISSUE: Parenthetical, repeated "Nəticəniz", and "olunduqdan sonra" is more standard.
SUGGEST: "{game} oyunundakı nəticəniz artıq müəyyən olunduqdan sonra server yenidən başladı. Bu nəticə qorunub saxlanıldı."

KEY: payout.retry-many
TIER: 3
ISSUE: Drops "You have" and does not match retry-one; "{count} ödənişiniz" works.
SUGGEST: "&6Hələ çatdırıla bilməyən {count} ödənişiniz var. Avtomatik yenidən cəhd ediləcək."

KEY: payout.bank-reminder
TIER: 3
ISSUE: "/ncc claim istifadə edin" lacks the ablative; add the governing noun "əmrindən".
SUGGEST: "&6Uduşunuzun bu hissəsi hələ də inventarda yer gözləyir: {amount}. Yer açın, sonra onu götürmək üçün &e/ncc claim&6 əmrindən istifadə edin."

KEY: payout.bank-still-blocked
TIER: 3
ISSUE: Same missing ablative.
SUGGEST: "&6Uduşlar hələ də inventarda yer gözləyir: {amount}. Yer açın, sonra onları götürmək üçün &e/ncc claim&6 əmrindən istifadə edin."

KEY: coin-flip.max-chain-hit
TIER: 3
ISSUE: Clumsy "(raundlar: {rounds})"; "{rounds} raund" reads naturally.
SUGGEST: "&6Bu diler üçün maksimum qələbə seriyası {rounds} raund təyin olunub və siz bu həddə çatdınız. Maksimum uduş münasibətilə təbriklər!"

KEY: rock-paper-scissors.max-chain-hit
TIER: 3
ISSUE: Same.
SUGGEST: "&6Bu diler üçün maksimum qələbə seriyası {rounds} raund təyin olunub və siz bu həddə çatdınız. Maksimum uduş münasibətilə təbriklər!"

KEY: baccarat.player-win-odds
TIER: 3
ISSUE: "Oyunçu qalib" is telegraphic for a bet label.
SUGGEST: "Oyunçunun qələbəsi - 1:1"

KEY: baccarat.banker-win-odds
TIER: 3
ISSUE: Same.
SUGGEST: "Bankirin qələbəsi - 0,95:1"

KEY: baccarat.player-wins
TIER: 3
ISSUE: Present/habitual tense for a finished round's result.
SUGGEST: "Oyunçu qalib gəldi!"

KEY: baccarat.banker-wins
TIER: 3
ISSUE: Same.
SUGGEST: "Bankir qalib gəldi!"

KEY: slots.payout-blocked-retry
TIER: 3
ISSUE: "təkrar yoxlamaq" reads as re-check; clicking retries the delivery.
SUGGEST: "&7Ödənişi yenidən göndərməyə cəhd etmək üçün klikləyin."

KEY: slots.dealer-wager-too-large
TIER: 3
ISSUE: Colloquial "yoxlayın" for "try".
SUGGEST: "&cBu maşın bu qədər böyük mərc qəbul etmir. Daha kiçik mərclə və ya daha az xətlə oynayın."

KEY: slots.auto-spin-limit-unlimited-set
TIER: 3
ISSUE: Repeats "fırlatma".
SUGGEST: "&aAvtomatik fırlatma limitsiz işləyəcək."

KEY: slots.paytable-card-no-runs
TIER: 3
ISSUE: Parenthetical; "{columns} barabana" is valid with the suffix on the noun.
SUGGEST: "&8Bu simvolun heç bir ardıcıllığı {columns} barabana sığmır."

KEY: slots.guide-volatility-normalized
TIER: 3
ISSUE: "Hər halda" means "anyway", not "either way".
SUGGEST: "&7Hər iki halda ödəniş cədvəli &a{rtp}&7 oyunçuya qayıdışa görə tənzimlənib."

KEY: slots.guide-volatility-tradeoff
TIER: 3
ISSUE: Adverb "tez-tez" used as an adjective.
SUGGEST: "&7Daha çox baraban qısa ardıcıllıqlardan gələn kiçik, amma tez-tez düşən uduşları uzun ardıcıllıqlardan gələn daha böyük, lakin nadir cekpotlarla əvəz edir."

KEY: slots.profile-saved
TIER: 3
ISSUE: "belə saxlanıldı" means "saved like this", not under this name.
SUGGEST: "&aBu quruluş bu adla saxlanıldı: {name}."

KEY: slots.prompt-spin-limit
TIER: 3
ISSUE: Elliptical "limitsiz üçün".
SUGGEST: "&eÇata fırlatma limiti yazın və ya limitsiz fırlatma üçün &f-1&e yazın."

KEY: slots.prompt-invalid-spin-limit
TIER: 3
ISSUE: "tam sayını" can mean the complete count; elliptical "limitsiz üçün".
SUGGEST: "&cFırlatma sayını tam ədədlə yazın və ya limitsiz fırlatma üçün {unlimited} yazın."

KEY: mines.instrument-set
TIER: 3
ISSUE: "Alət" alone reads as "tool"; this is the musical instrument.
SUGGEST: "&bMusiqi aləti təyin edildi: {instrument}"

## Terminology notes

`{game}` parentheticals vs the governing-noun form; colloquial "yoxlamaq"
for "try"; on/off wording split between toggle states and actions
(acceptable). Glossary terms otherwise consistent.

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 0 | Tier 3: 25 | Total: 25

## Disposition (translator)

All 25 applied as suggested. Follow-up: the three game-specific
`inventory-full` keys now use the same "{amount} üçün yer yoxdur,
yaxınlıqda yerə atılır..." wording as `betting.inventory-full`.
