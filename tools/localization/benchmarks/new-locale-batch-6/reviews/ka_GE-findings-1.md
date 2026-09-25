# ka_GE review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`; Java call sites read for
`coin-flip.waiting-bet`, `dragon-descent.sweep`, `betting.inventory-full`.
Tier 0 clean (order of placeholders, `&` codes and `\n`; `/ncc claim`,
`-1`, `off`, `RTP`, `Shift` intact; no Mtavruli, no Cyrillic, only „“).
No placeholder stands in a dative / genitive / ergative slot; no report of
a player action reads as a command. Context notes hold (seat not yet
available, chain-win current pot, future cash-out notice, vines "ლიანები",
no "სერია" for the batch, "2,5%").

KEY: slots-settings.variance-tradeoff
TIER: 1
ISSUE: "ნიშნავს ნაკლებ მომგებიან ხაზს" reads as "a less profitable line", contradicting the larger jackpots; the "fewer winning lines" reading suggests fewer paylines.
APPLIED: "&7უფრო მაღალი ვოლატილობა ნიშნავს, რომ ხაზები უფრო იშვიათად იძლევა მოგებას, მაგრამ გრძელ მიმდევრობებზე ჯეკპოტები გაცილებით დიდია."

KEY: test-game.round-finished
TIER: 2
ISSUE: "დააჭირეთ" differs from the catalog click verb "დააწკაპუნეთ", and the reset button is not named.
APPLIED: "რაუნდი დასრულდა. თავიდან დასაწყებად დააწკაპუნეთ ღილაკზე „თამაშის გადატვირთვა“."

KEY: coin-flip.sit-other-chair
TIER: 3
ISSUE: An imperative after "გთხოვთ"; literary Georgian uses the optative (as in "გთხოვთ, სცადოთ").
APPLIED: "&oგთხოვთ, დაჯდეთ მეორე სკამზე"

KEY: rock-paper-scissors.sit-other-chair
TIER: 3
ISSUE: Same as coin-flip.sit-other-chair.
APPLIED: "&oგთხოვთ, დაჯდეთ მეორე სკამზე"

KEY: dragon-descent.sweep
TIER: 3
ISSUE: Future "ჩამოიქროლებს" ("will swoop"); the item names the sweep as it happens (DragonClient).
APPLIED: "&cდრაკონი მოქრის!"

KEY: dragon-descent.click-move
TIER: 3
ISSUE: "სვლისთვის" ("for a move") is awkward for a button that moves the player.
APPLIED: "დააწკაპუნეთ აქ გადასაადგილებლად"

KEY: slots.payout-blocked
TIER: 3
ISSUE: "Click Spin" became "click on spinning"; name the button.
APPLIED: "&cთქვენი მოგების მიწოდება ან რიგში ჩაყენება ვერ მოხერხდა. ხელახლა საცდელად დააწკაპუნეთ ღილაკზე „დატრიალება“; თუ პრობლემა გაგრძელდა, მიმართეთ ადმინისტრაციას."

KEY: slots.guide-seeds-never-pays
TIER: 3
ISSUE: "არასდროს იხდის" is a calque and colloquial; literary "არასოდეს".
APPLIED: "&7თესლი არასოდეს იძლევა მოგებას, მიმდევრობის სიგრძის მიუხედავად."

KEY: slots.auto-any-win-description
TIER: 3
ISSUE: "ყოველი" (every) where the stop condition means "any".
APPLIED: "&7აჩერებს ავტომატურ დატრიალებას ნებისმიერი დატრიალების შემდეგ, რომელიც მოგებას იხდის."

KEY: slots.auto-profit-target-current
TIER: 3
ISSUE: "ჩერდება მოგებისას" can read as stopping on a single win of the amount; the rule is the batch profit.
APPLIED: "&7ჩერდება, როცა მოგება მიაღწევს მიზანს: &a{amount}"

KEY: slots.auto-loss-limit-current
TIER: 3
ISSUE: "ჩერდება წაგებისას" is vague; match auto-rule-loss.
APPLIED: "&7ჩერდება, სანამ წაგება გადააჭარბებს ზღვარს: &c{amount}"

KEY: slots.auto-stop-spin-rejected
TIER: 3
ISSUE: "დატრიალების განთავსება" is a calque of "placed".
APPLIED: "&cავტომატური დატრიალება შეჩერდა: შემდეგი დატრიალების დაწყება ვერ მოხერხდა."

KEY: slots.paytable-legend-return
TIER: 3
ISSUE: The temporal suffix -ისას on a non-verbal noun ("ფსონისას").
APPLIED: "&7გადახდა: სრული თანხა, რომელსაც ეს ხაზი იხდის თქვენი მიმდინარე ფსონის შემთხვევაში."

KEY: slots.rail-paytable-return
TIER: 3
ISSUE: Same "ფსონისას" problem.
APPLIED: "&7გადახდა: სრული თანხა, რომელსაც ეს ხაზი იხდის თქვენი ფსონის შემთხვევაში."

KEY: slots.guide-volatility-tradeoff
TIER: 3
ISSUE: "…მოგებებს … ცვლის … ჯეკპოტებში" is clumsy.
APPLIED: "&7მეტი ბარაბნის შემთხვევაში მოკლე მიმდევრობების ხშირ მცირე მოგებებს ცვლის გრძელი მიმდევრობების იშვიათი, მაგრამ დიდი ჯეკპოტები."

KEY: slots-settings.variance-hit-rate
TIER: 3
ISSUE: "მომგებიანი" (profitable) implies net profit and sits close to the payline term.
APPLIED: "&7ხაზზე გადახდის ალბათობა: &e{chance}"

KEY: slots-settings.variance-top-line
TIER: 3
ISSUE: "ყველაზე დიდი სრული სიგანის ხაზი" does not say the value is a payout multiplier.
APPLIED: "&7ყველაზე დიდი გადახდა სრულ ხაზზე: &e{multiplier}x"

Tier 0: 0 · Tier 1: 1 · Tier 2: 1 · Tier 3: 15

## Disposition (translator)

All 17 applied. `slots.auto-profit-target-current` uses "მიზანს"
(target) rather than the suggested "ზღვარს" to match `auto-rule-profit`.
Follow-up in self-review: `guide-volatility-height` takes "არასოდეს".
