# bn_BD review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean (token order, protected
literals, danda, no Bengali digits, no ASCII quotes). Template slots,
`resplit-offer`, max chain as a round count, chip value, shoe vs deck and
the two split rules all hold.

KEY: mines.inventory-full
TIER: 1
ISSUE: A bare "ফেলে দেওয়া হচ্ছে" reads as discarding, suggesting the money is lost.
APPLIED: "&cইনভেন্টরিতে জায়গা নেই: {amount}; কাছে মাটিতে ফেলা হচ্ছে..."

KEY: roulette.inventory-full
TIER: 1
ISSUE: Same.
APPLIED: "&cইনভেন্টরিতে জায়গা নেই: {amount}; কাছে মাটিতে ফেলা হচ্ছে..."

KEY: blackjack.inventory-full
TIER: 1
ISSUE: Same.
APPLIED: "&cইনভেন্টরিতে জায়গা নেই: {amount}; কাছে মাটিতে ফেলা হচ্ছে..."

KEY: blackjack-settings.turn-timer-timeout-desc-2
TIER: 1
ISSUE: "ততক্ষণ ... পর্যন্ত" put the seat riding to the result inside the window; it follows the resolution. Non-honorific "তার" for a player.
APPLIED: "&7মেনু বন্ধ করা বা সংযোগ বিচ্ছিন্ন খেলোয়াড়ের পালা নিষ্পত্তি হওয়ার আগের সময়ও এটিই; এরপর তাঁর আসনের বাজি ফলাফল পর্যন্ত খেলায় থেকে যায়।"

KEY: mines.dealer-cannot-cover
TIER: 2
ISSUE: "অঙ্ক তুলতে" drifts from the cash-out term "জেতা অর্থ তোলা".
APPLIED: "&cএই বোর্ড এখন এত বড় অঙ্কের জেতা অর্থ তুলতে দিতে পারবে না। পরে আবার চেষ্টা করুন বা আপনার বাজি কমান।"

KEY: blackjack-settings.insurance-timeout-desc
TIER: 3
ISSUE: "সিদ্ধান্ত নেওয়ার সেকেন্ড" is unidiomatic, and the auto-"No" wording differed from `closed-during-insurance`.
APPLIED: "&7স্বয়ংক্রিয়ভাবে “না” ধরে নেওয়ার আগে সিদ্ধান্ত নিতে খেলোয়াড়েরা যত সেকেন্ড সময় পান।"

KEY: blackjack-settings.turn-timer-timeout-desc-1
TIER: 3
ISSUE: Same construction.
APPLIED: "&7হাত স্বয়ংক্রিয়ভাবে থেমে যাওয়ার আগে পদক্ষেপ নিতে একজন খেলোয়াড় যত সেকেন্ড সময় পান।"

KEY: blackjack-settings.split-matching-desc-1
TIER: 3
ISSUE: "হুবহু একই কার্ড" suggests the suit must match too; the rule is rank only.
APPLIED: "&7একই কার্ড: শুধু একই নামের কার্ড ভাগ করা যায় (সাহেব-সাহেব)।"

KEY: dealer.game-set
TIER: 3
ISSUE: Verb before the value with no colon.
APPLIED: "&aডিলার “&e{dealer}&a”-এর খেলা নির্ধারণ করা হয়েছে: &e{game}&a।"

KEY: complex-variant.title
TIER: 3
ISSUE: "জটিল ধরনের মেনু" means "a complicated kind of menu".
APPLIED: "জটিল ধরনগুলোর মেনু"

KEY: jockey-options.edit-collar-color
TIER: 3
ISSUE: "গলাবন্ধ" is a scarf; a dog / wolf collar is "বকলস".
APPLIED: "বকলসের রং সম্পাদনা করুন: {mob}"

KEY: roulette.refund-exit
TIER: 3
ISSUE: A bare noun paired with an imperative.
APPLIED: "ফেরত নিন এবং/অথবা বের হোন"

KEY: mines.rebet-broke
TIER: 3
ISSUE: The slangy source became a neutral line nearly identical to `blackjack.insufficient-bet`.
APPLIED: "&cপকেট ফাঁকা, আবার বাজি ধরা যাবে না।"

Tier 0: 0 · Tier 1: 4 · Tier 2: 1 · Tier 3: 8

## Disposition (translator)

All 13 applied; the three `inventory-full` lines use the packet-1 wording
"কাছে মাটিতে ফেলা হচ্ছে..." rather than the suggested "কাছেই ফেলে দেওয়া
হচ্ছে...", so every drop line avoids the discard sense. Follow-up in
self-review: the four other Rock Paper Scissors mentions (settings title,
two errors, occupation) use the hyphenated name.
