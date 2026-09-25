# bn_BD review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean (script-checked token order,
`/ncc claim`, `-1`, `off`; no placeholder joined to a letter except the
English `{multiplier}x`). Call sites checked for `waiting-bet`,
`mines.all-in`, the Leave button and `{game}`. Terms consistent: আবার
বাজি, টানা জয় vs ক্রম, দফা, লতা, RTP never "ফেরত", seat not yet open.

KEY: coin-flip.mode-switch-cashout-notice
TIER: 2
ISSUE: Timing is right, but "পরিশোধ করা হবে" (generic payment) replaces the cash-out term "জেতা অর্থ তোলা".
APPLIED: "&eপরিবর্তন করলে আপনার জেতা অর্থ স্বয়ংক্রিয়ভাবে তুলে দেওয়া হবে: &a{amount}"

KEY: rock-paper-scissors.mode-switch-cashout-notice
TIER: 2
ISSUE: Same.
APPLIED: "&eপরিবর্তন করলে আপনার জেতা অর্থ স্বয়ংক্রিয়ভাবে তুলে দেওয়া হবে: &a{amount}"

KEY: mines.potential-winnings
TIER: 2
ISSUE: "সম্ভাব্য জয়" where the catalog says "জেতা অর্থ" for winnings.
APPLIED: "সম্ভাব্য জেতা অর্থ: {amount}"

KEY: coin-flip.leave
TIER: 3
ISSUE: A standalone "চলে যান" reads as "Go away"; other exit buttons are "বের হোন".
APPLIED: "&f&oবের হোন"

KEY: rock-paper-scissors.leave
TIER: 3
ISSUE: Same.
APPLIED: "&f&oবের হোন"

KEY: mines.refund-exit
TIER: 3
ISSUE: A bare noun paired with an imperative.
APPLIED: "ফেরত নিন/বের হোন"

KEY: mines.exiting
TIER: 3
ISSUE: Impersonal passive of an intransitive verb.
APPLIED: "&cখেলা থেকে বের হচ্ছেন..."

KEY: dragon-descent.invalid-action-capitalized
TIER: 3
ISSUE: The English ends with a period; Bengali has no case, so match the plain variant.
APPLIED: "&cঅবৈধ পদক্ষেপ।"

KEY: coin-flip.max-chain-hit
TIER: 3
ISSUE: "(রাউন্ড: {rounds})" mid-sentence is stilted; "{rounds} রাউন্ড" is valid and natural.
APPLIED: "&6এই ডিলারের টানা জয়ের সর্বোচ্চ সীমা {rounds} রাউন্ড নির্ধারণ করা আছে, এবং আপনি সেই সীমায় পৌঁছেছেন। সর্বোচ্চ জয়ের জন্য অভিনন্দন!"

KEY: rock-paper-scissors.max-chain-hit
TIER: 3
ISSUE: Same.
APPLIED: "&6এই ডিলারের টানা জয়ের সর্বোচ্চ সীমা {rounds} রাউন্ড নির্ধারণ করা আছে, এবং আপনি সেই সীমায় পৌঁছেছেন। সর্বোচ্চ জয়ের জন্য অভিনন্দন!"

KEY: game-options.rock-paper-scissors
TIER: 3
ISSUE: Inserted into `{game}`; with commas it reads as a list. The usual form is hyphenated.
APPLIED: "পাথর-কাগজ-কাঁচি"

KEY: rock-paper-scissors.title
TIER: 3
ISSUE: Same, kept consistent with the game name.
APPLIED: "পাথর-কাগজ-কাঁচি"

KEY: payout.retry-one
TIER: 3
ISSUE: "আবার চেষ্টা করা হবে" is translationese; what is retried is the delivery.
APPLIED: "&6আপনার 1টি অপেক্ষমাণ পরিশোধ এখনো পৌঁছে দেওয়া যায়নি। স্বয়ংক্রিয়ভাবে এটি আবার পৌঁছে দেওয়ার চেষ্টা করা হবে।"

KEY: payout.retry-many
TIER: 3
ISSUE: Same, and "You have" was lost.
APPLIED: "&6আপনার কিছু অপেক্ষমাণ পরিশোধ এখনো পৌঁছে দেওয়া যায়নি (সংখ্যা: {count})। স্বয়ংক্রিয়ভাবে এগুলো আবার পৌঁছে দেওয়ার চেষ্টা করা হবে।"

KEY: slots.payout-blocked-retry
TIER: 3
ISSUE: "পরিশোধ আবার চেষ্টা করতে" is awkward.
APPLIED: "&7আবার পরিশোধের চেষ্টা করতে ক্লিক করুন।"

KEY: slots.auto-any-win
TIER: 3
ISSUE: A toggle title phrased as an order to stop playing; the sibling uses the verbal noun.
APPLIED: "&eযেকোনো জয়ে থামা"

KEY: slots.auto-settings-reset
TIER: 3
ISSUE: "স্বয়ংক্রিয় সেটিংস" reads as "automatic settings".
APPLIED: "&cস্বয়ংক্রিয় স্পিনের সেটিংস রিসেট করুন"

KEY: slots.rail-wager-total
TIER: 3
ISSUE: "মোট" repeated.
APPLIED: "&7মোট বাজি: &a{amount} &7(&a{lines} &7লাইন জুড়ে)"

KEY: slots-settings.variance-top-line
TIER: 3
ISSUE: "সবচেয়ে বড় লাইন" can read as the physically largest line; the value is a multiplier.
APPLIED: "&7পূর্ণ প্রস্থের লাইনে সর্বোচ্চ গুণক: &e{multiplier}x"

KEY: slots.auto-loss-limit-current
TIER: 3
ISSUE: "{amount} ক্ষতির আগে" implies a limit below the amount.
APPLIED: "&7থামবে: &c{amount} ক্ষতি হলে"

KEY: preferences.overflow.drop
TIER: 3
ISSUE: "ফেলে দিন" commonly means discard; "মাটিতে ফেলুন" is unambiguous.
APPLIED: "&eকাছে মাটিতে ফেলুন"

KEY: preferences.overflow.drop-explained
TIER: 3
ISSUE: Same.
APPLIED: "&7মাটিতে ফেলা: সার্ভারের সীমা পর্যন্ত কাছে মাটিতে ফেলা হয়, বাকিটা নিরাপদে রাখা হয়।"

KEY: betting.inventory-full
TIER: 3
ISSUE: Same.
APPLIED: "&cইনভেন্টরিতে জায়গা নেই: {amount}; কাছে মাটিতে ফেলা হচ্ছে।"

Tier 0: 0 · Tier 1: 0 · Tier 2: 3 · Tier 3: 20

## Disposition (translator)

All 23 applied. The drop wording ("কাছে মাটিতে ফেলা") was carried to
the three game `inventory-full` lines raised in packet 2.
