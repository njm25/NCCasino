# hy_AM review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`; BaccaratClient read for `{total}`. Tier 0
clean (script-checked token order; no placeholder joined to a letter).
Every placeholder stands after "՝", in parentheses, or before a noun that
takes the ending. Context notes hold (seat not yet available, third-person
turn, future cash-out, current pot, round-count max chain, vines, RTP not
a refund, "սերիա" only for the streak, variance meaning, "2,5%").

KEY: slots.demo-result-win
TIER: 2
ISSUE: "կվերադարձներ" renders the slot return as "give back", leaning toward refund; the paytable uses "Վճարում".
APPLIED: "&eԴեմո՝ այս պտույտը կվճարեր {amount} (խաղադրույք՝ {bet})։ Արժույթ չի ծախսվել։"

KEY: preferences.language.server-default
TIER: 3
ISSUE: "Սերվերի լռելյայն" has no head noun inside "Ռեժիմ՝ {mode}".
APPLIED: "Սերվերի լռելյայն լեզու"

KEY: payout.context-committed-result
TIER: 3
ISSUE: The -ն article on "արդյունքն" was chosen for the vowel after a parenthetical placeholder; use -ը.
APPLIED: "Սերվերը վերագործարկվեց այն բանից հետո, երբ ձեր արդյունքը ({game}) արդեն որոշված էր։ Արդյունքը պահպանվեց։"

KEY: cards.ranks.jack
TIER: 3
ISSUE: "Վալետ" is a Russian loan next to native "Թագավոր".
APPLIED: "Զինվոր"

KEY: cards.ranks.queen
TIER: 3
ISSUE: "Դամա" is a Russian loan next to native "Թագավոր".
APPLIED: "Թագուհի"

KEY: coin-flip.mode-switch-cashout-notice
TIER: 3
ISSUE: Article before a vowel-initial word must be -ն: "շահումն ավտոմատ".
APPLIED: "&eՓոխելիս շահումն ավտոմատ կերպով կվճարվի ձեզ՝ &a{amount}"

KEY: rock-paper-scissors.mode-switch-cashout-notice
TIER: 3
ISSUE: Same article error.
APPLIED: "&eՓոխելիս շահումն ավտոմատ կերպով կվճարվի ձեզ՝ &a{amount}"

KEY: coin-flip.max-pot-hit
TIER: 3
ISSUE: "որը այս" should be "որն այս".
APPLIED: "&6Ձեր շահումները հասել են առավելագույն գումարին, որն այս խաղը կարող է միանգամից վճարել։ Շնորհավորում ենք առավելագույն շահման կապակցությամբ։"

KEY: rock-paper-scissors.max-pot-hit
TIER: 3
ISSUE: Same.
APPLIED: "&6Ձեր շահումները հասել են առավելագույն գումարին, որն այս խաղը կարող է միանգամից վճարել։ Շնորհավորում ենք առավելագույն շահման կապակցությամբ։"

KEY: coin-flip.awaiting-wager-lore
TIER: 3
ISSUE: Adjective used as an adverb ("իմաստուն"), and "your" dropped.
APPLIED: "&7&oԱյնուհետև խելամտորեն ընտրեք ձեր կողմը"

KEY: baccarat.title
TIER: 3
ISSUE: "Բակկարա" copies the Russian double к.
APPLIED: "Բակարա"

KEY: game-options.baccarat
TIER: 3
ISSUE: Same spelling.
APPLIED: "Բակարա"

KEY: baccarat.current-total
TIER: 3
ISSUE: {total} is the hand point total (BaccaratClient); "գումար" reads as money.
APPLIED: "Ընթացիկ միավորներ՝ {total}"

KEY: dragon-descent.columns
TIER: 3
ISSUE: "Սյուներ" means physical pillars; the value is the grid-width count.
APPLIED: "Սյունակներ"

KEY: test-game.waiting-accept
TIER: 3
ISSUE: "2-ը ընդունի": -ն before a vowel.
APPLIED: "Սպասում ենք, որ տեղ 2-ն ընդունի խաղադրույքը..."

KEY: slots.spin-locked
TIER: 3
ISSUE: "ընթացքի մեջ" calques Russian "в процессе".
APPLIED: "&cՊտույտն ընթացքում է..."

KEY: slots.spin-active
TIER: 3
ISSUE: Same calque.
APPLIED: "&e&lՊտույտ -- ընթացքում"

KEY: slots.paylines-inert
TIER: 3
ISSUE: "աջակցում է ... գիծ" calques "supports" with a wrong object case.
APPLIED: "&8Այս բարձրության դեպքում հնարավոր է միայն մեկ գիծ։"

KEY: slots-settings.default-lines-inert-hint
TIER: 3
ISSUE: Same calque.
APPLIED: "&8Լռելյայն 1 բարձրության դեպքում հնարավոր է միայն մեկ շահող գիծ։"

KEY: slots.profile-adjusted
TIER: 3
ISSUE: Same calque with an accusative object.
APPLIED: "&eՊահված բոլոր արժեքները չեն համապատասխանում այս ավտոմատին, ուստի պրոֆիլը հարմարեցվեց՝ տողեր {rows}, թմբուկներ {columns}, գծեր {lines}, խաղադրույք {amount}։"

KEY: slots.auto-rule-any-win
TIER: 3
ISSUE: Colloquial genitive "շահումի"; standard "շահման".
APPLIED: "&7Կանգ է առնում ցանկացած շահման դեպքում։"

KEY: slots.auto-any-win
TIER: 3
ISSUE: Same.
APPLIED: "&eԿանգ ցանկացած շահման դեպքում"

KEY: slots.auto-big-win
TIER: 3
ISSUE: Same.
APPLIED: "&eՄեծ շահման բազմապատկիչ"

KEY: slots.auto-big-win-off-set
TIER: 3
ISSUE: Same.
APPLIED: "&aՄեծ շահման դեպքում կանգառն այժմ անջատված է։"

KEY: slots.prompt-big-win-multiplier
TIER: 3
ISSUE: Same.
APPLIED: "&eԶրուցարանում գրեք մեծ շահման բազմապատկիչը կամ գրեք &foff&e՝ անջատելու համար։"

KEY: slots.auto-rule-loss
TIER: 3
ISSUE: Future form after "նախքան"; the norm is the subjunctive.
APPLIED: "&7Կանգ է առնում, նախքան կորուստը գերազանցի սահմանը՝ &c{amount}&7։"

KEY: slots.auto-loss-limit-description
TIER: 3
ISSUE: Same mood error.
APPLIED: "&7Կանգնեցնում է ավտոպտույտը, նախքան մեկնարկից ի վեր կորուստը գերազանցի այս գումարը։"

KEY: slots.auto-loss-limit-set
TIER: 3
ISSUE: Same mood error.
APPLIED: "&aԱվտոպտույտը կկանգնի, նախքան մեկնարկից ի վեր կորուստը գերազանցի սահմանը՝ {amount}։"

KEY: slots.auto-reset-change
TIER: 3
ISSUE: ASCII colon copied from English; Armenian labels use "՝".
APPLIED: "&7{setting}՝ &f{from} &8-> &a{to}"

KEY: slots.auto-stop-profit-target-reached
TIER: 3
ISSUE: "թիրախը ձեռք բերվեց" ("the target was acquired") calques "reached".
APPLIED: "&eԱվտոպտույտը կանգնեց՝ շահույթը հասավ թիրախին։"

KEY: slots.paytable-legend
TIER: 3
ISSUE: "Լեգենդ" is a calque; the Armenian term is "Պայմանական նշաններ".
APPLIED: "&bՊայմանական նշաններ"

KEY: slots.profile-saved
TIER: 3
ISSUE: "՝" cannot follow "որպես"; the name reads better quoted.
APPLIED: "&aԱյս կարգավորումը պահվեց «{name}» անունով։"

KEY: mines.mine-count-error
TIER: 3
ISSUE: Parses as "the number of wrong mines" and has no main verb.
APPLIED: "&cԱկանների քանակը կարդալիս սխալ առաջացավ։"

Tier 0: 0 · Tier 1: 0 · Tier 2: 1 · Tier 3: 32

## Disposition (translator)

All 33 applied. Follow-ups in self-review: the remaining questions end
with "։" (`dragon-descent.begin`, both `reset-confirmation` keys,
`admin.delete-confirmation`, `blackjack.already-seated`); the column term
"սյունակ" and the spelling "Բակարա" carry through the settings keys; the
split rule reads "Թագավոր-Թագուհի"; `mob-selection.age-unsupported` loses
the same "աջակցում" calque.
