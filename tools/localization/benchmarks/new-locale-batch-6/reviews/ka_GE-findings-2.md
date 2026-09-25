# ka_GE review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`; Java call sites read for the
`updated-detailed` templates. Tier 0 clean. Placeholder checks pass for
`prompt-setting*`, `finish-editing`, `game-settings`,
`no-game-permission`; `updated-detailed` receives a full sentence and
stays grammatical. Glossary consistent; `none` fits both slots; vines
"ლიანა". Note, not scored: "ვარიანტი" serves both option and variant on
separate screens.

KEY: blackjack.sat-down
TIER: 1
ISSUE: 2pl aorist "დაჯექით" is spelled like the imperative "sit down!", which the catalog also uses as an order.
APPLIED: "&aახლა სკამზე ზიხართ."

KEY: blackjack.result-won
TIER: 1
ISSUE: "თქვენ მოიგეთ!" on a result banner can read as the encouragement "Win!".
APPLIED: "&a&lგამარჯვება!"

KEY: blackjack.left-chair
TIER: 1
ISSUE: "თქვენ დატოვეთ სკამი." can read as "leave the chair!".
APPLIED: "&aსკამი დატოვებულია."

KEY: blackjack.left-game
TIER: 1
ISSUE: "თქვენ დატოვეთ თამაში." can read as "leave the game!".
APPLIED: "&aთამაში დატოვებულია."

KEY: roulette.left-game
TIER: 1
ISSUE: Same; in red the command reading is likelier.
APPLIED: "&cთამაში დატოვებულია."

KEY: test-menu.clicked-one
TIER: 1
ISSUE: "დააწკაპუნეთ" is the catalog imperative "click!", so the line reads "Click Option 1!".
APPLIED: "თქვენი არჩევანი: „ვარიანტი 1“!"

KEY: test-menu.clicked-two
TIER: 1
ISSUE: Same as clicked-one.
APPLIED: "თქვენი არჩევანი: „ვარიანტი 2“!"

KEY: blackjack.insurance-paid
TIER: 1
ISSUE: "დაზღვევის გადახდა" reads as the player paying the premium; the source is the insurance payout ("ანაზღაურება").
APPLIED: "&aდაზღვევის ანაზღაურება: {amount}!"

KEY: roulette.dealer-cannot-cover
TIER: 2
ISSUE: "მოგების დაფარვა" (debt-covering calque) vs "მოგების გადახდა" for identical English in blackjack.
APPLIED: "&cმაგიდას ახლა ასეთი დიდი მოგების გადახდა არ შეუძლია. სცადეთ მოგვიანებით ან შეამცირეთ ფსონები."

KEY: mines.inventory-full
TIER: 3
ISSUE: "ნივთები იყრება..." without a place can read as discarding; items drop on the ground.
APPLIED: "&cინვენტარში ადგილი არ არის: {amount}; ნივთები მიწაზე იყრება..."

KEY: roulette.inventory-full
TIER: 3
ISSUE: Same.
APPLIED: "&cინვენტარში ადგილი არ არის: {amount}; ნივთები მიწაზე იყრება..."

KEY: blackjack.inventory-full
TIER: 3
ISSUE: Same.
APPLIED: "&cინვენტარში ადგილი არ არის: {amount}; ნივთები მიწაზე იყრება..."

KEY: roulette.all-in-ready
TIER: 3
ISSUE: Tautological "the bet of betting everything" and drops "to place".
APPLIED: "&aფსონი „ყველაფრის დადება“ მზადაა დასადებად: {amount}."

KEY: roulette.item-payout-too-large
TIER: 3
ISSUE: Word-for-word "ზედმეტად დიდია ნივთის სახით ვალუტისთვის".
APPLIED: "&cამ ფსონის შესაძლო მოგება ზედმეტად დიდია ნივთებით გასაცემად. შეამცირეთ ფსონი ან გადაანაწილეთ ის ნაკლებ ფსონზე."

KEY: blackjack.game-info
TIER: 3
ISSUE: "ინფორმაცია თამაშზე" is a Russian calque; literary "…შესახებ".
APPLIED: "ინფორმაცია თამაშის შესახებ"

KEY: blackjack.insurance-prompt
TIER: 3
ISSUE: "დილერს ტუზი უჩანს" can read as "the dealer can see an ace".
APPLIED: "&eდილერის ღია კარტი ტუზია. გსურთ დაზღვევა?"

KEY: blackjack-settings.insurance-desc-1
TIER: 3
ISSUE: Same ambiguity.
APPLIED: "&7შემოთავაზებულია, როცა დილერის ღია კარტი ტუზია."

KEY: blackjack-settings.insurance-desc-2
TIER: 3
ISSUE: A numeral + -იანი compound takes a hyphen: "10-ქულიანი".
APPLIED: "&7ღირს თქვენი ფსონის ნახევარი. თუ დილერის დამალული კარტი 10-ქულიანია (ბლექჯეკი), იხდის 2:1 კოეფიციენტით."

KEY: blackjack-settings.split-matching-desc-2
TIER: 3
ISSUE: Same hyphenation.
APPLIED: "&7ერთნაირი ღირებულება: იყოფა ნებისმიერი ორი 10-ქულიანი კარტიც (მეფე-ქალი)."

KEY: blackjack-settings.turn-timer-timeout-desc-2
TIER: 3
ISSUE: One subject for two verbs needing different cases (ergative for დახურა, dative for გაუწყდა); the turn seemed to end at once.
APPLIED: "&7ამდენივე დრო ეძლევა მოთამაშეს, რომელმაც მენიუ დახურა ან რომელსაც კავშირი გაუწყდა: შემდეგ მისი რიგი სრულდება, ხოლო მისი ადგილის ფსონი შედეგამდე თამაშში რჩება."

KEY: mob-settings.add-vehicle-lore
TIER: 3
ISSUE: "ქვედა ნაწილში" means "in the lower part"; the mob goes to the very bottom.
APPLIED: "ამატებს ახალ მობს სტეკის ყველაზე ქვემოთ"

KEY: mob-settings.add-passenger-lore
TIER: 3
ISSUE: Same for the very top.
APPLIED: "ამატებს ახალ მობს სტეკის ყველაზე ზემოთ"

KEY: admin.already-moving
TIER: 3
ISSUE: "დილერი უკვე გადაადგილდება" says the dealer moves by itself; an admin move is in progress.
APPLIED: "&cდილერის გადატანა უკვე მიმდინარეობს. გთხოვთ, მოიცადოთ."

KEY: admin.no-settings
TIER: 3
ISSUE: "მიუწვდომელია" means inaccessible; the source means there are none.
APPLIED: "&7ხელმისაწვდომი პარამეტრები არ არის."

KEY: commands.dealer-exists
TIER: 3
ISSUE: "სახელით" is a calque of Russian "с именем"; literary "სახელად".
APPLIED: "&cდილერი სახელად „&e{name}&c“ უკვე არსებობს."

Tier 0: 0 · Tier 1: 8 · Tier 2: 1 · Tier 3: 16

## Disposition (translator)

All 25 applied. Follow-ups in self-review: the remaining
result lines move to label forms (`coin-flip` / `rock-paper-scissors`
`chain-win` "გამარჯვება!", `slots.win` and `test-game.server-won`
"მოგება: {amount}"), and every other "cover" line takes "გადახდა"
(coin-flip, rock-paper-scissors, baccarat, dragon-descent, slots).
