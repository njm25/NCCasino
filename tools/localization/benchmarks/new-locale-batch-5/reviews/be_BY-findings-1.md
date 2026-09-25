# be_BY review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 checks clean (token order in all 592
records; no Russian-only letters; ў/у rule correct; `off`, `-1`,
`/ncc claim`, `{overwrite}`, `{cancel}`, `{unlimited}` intact). No gendered
past forms addressed to the player (plural `вы` throughout). Context notes
hold: `seat-unavailable` "Месца пакуль недаступнае" (empty, locked),
chain-win `{amount}` as "Бягучы банк", future cash-out notice, third-person
`player-turn`, max chain as a count, overflow held / dropped, variance
tradeoff as hit rate.

KEY: common.current
TIER: 3
ISSUE: "Цяпер:" (the adverb "now") as a value label; other keys use "Бягучы/Бягучая …".
SUGGEST: "&7Бягучае значэнне: {value}"

KEY: coin-flip.pick-left
TIER: 3
ISSUE: "Злева" describes a location, not a choice; the lore asks to pick a side.
SUGGEST: "&oЛевы бок"

KEY: coin-flip.pick-right
TIER: 3
ISSUE: Same for "Справа".
SUGGEST: "&oПравы бок"

KEY: coin-flip.max-chain-hit
TIER: 3
ISSUE: "гульняў" can read as separate sessions; the count is consecutive rounds (раўнд elsewhere).
SUGGEST: "&6Для гэтага крупье ўстаноўлена максімальная серыя перамог (раўндаў: {rounds}), і вы яе дасягнулі. Віншуем з максімальным выйгрышам!"

KEY: rock-paper-scissors.max-chain-hit
TIER: 3
ISSUE: Same.
SUGGEST: "&6Для гэтага крупье ўстаноўлена максімальная серыя перамог (раўндаў: {rounds}), і вы яе дасягнулі. Віншуем з максімальным выйгрышам!"

KEY: baccarat.player-wins
TIER: 3
ISSUE: Lowercase "гулец" can read as the human player; mark the betting side like the "Гулец" label.
SUGGEST: "Перамагае Гулец!"

KEY: baccarat.banker-wins
TIER: 3
ISSUE: Match the capitalized side name "Банкір".
SUGGEST: "Перамагае Банкір!"

KEY: baccarat.current-total
TIER: 3
ISSUE: "сума" alone reads as money; this is the hand's point total.
SUGGEST: "Бягучая сума ачкоў: {total}"

KEY: dragon-descent.click-move
TIER: 3
ISSUE: "каб рухацца" is vague for stepping onto a tile.
SUGGEST: "Націсніце тут, каб зрабіць ход"

KEY: slots.paylines-description
TIER: 3
ISSUE: A pattern pays out; it does not "win".
SUGGEST: "&7Кожная актыўная лінія — гэта ўзор сімвалаў, які можа прынесці выйгрыш."

KEY: slots.spin-lore-breakdown
TIER: 3
ISSUE: Genitive plural "(ліній)" is wrong for 1 and 2-4; use a wording without agreement.
SUGGEST: "&7{wager} за лінію &7× &a{lines} &7(колькасць ліній)"

KEY: slots.paytable-leftmost-rule
TIER: 3
ISSUE: No subject; neuter "павінна" does not agree with the implied "камбінацыя"; use "пачынацца з".
SUGGEST: "&8Камбінацыя павінна пачынацца з крайняга левага барабана."

KEY: slots.no-safe-denomination
TIER: 3
ISSUE: "гуляць стаўку" is not idiomatic.
SUGGEST: "&cУ гэтага крупье няма іншай стаўкі, якую можна бяспечна зрабіць."

KEY: slots.auto-rule-big-win
TIER: 3
ISSUE: Clumsy "пры выплаце ў …" construction.
SUGGEST: "&7Спыняецца, калі выплата дасягае &f{multiplier}x &7ад агульнай стаўкі."

KEY: slots.auto-big-win-description
TIER: 3
ISSUE: "кратнасць" is maths jargon.
SUGGEST: "&7Спыняе аўтакручэнне, калі кручэнне прыносіць не менш за яго агульную стаўку, памножаную на гэты множнік."

KEY: slots.auto-profit-target
TIER: 3
ISSUE: "Мэта па прыбытку" copies Russian "цель по прибыли".
SUGGEST: "&eМэтавы прыбытак"

KEY: slots.auto-profit-target-off-set
TIER: 3
ISSUE: Same calque.
SUGGEST: "&aМэтавы прыбытак выключаны."

KEY: slots.auto-stop-profit-target-reached
TIER: 3
ISSUE: Same calque.
SUGGEST: "&eАўтакручэнне спынена: мэтавы прыбытак дасягнуты."

KEY: slots.prompt-profit-target
TIER: 3
ISSUE: Same calque.
SUGGEST: "&eУвядзіце ў чат мэтавы прыбытак або &foff&e, каб выключыць."

KEY: slots.paytable-legend
TIER: 3
ISSUE: "Легенда" mainly means a myth or story; the header introduces explanations.
SUGGEST: "&bТлумачэнні"

KEY: slots-settings.variance-top-line
TIER: 3
ISSUE: Reads as a physically largest line; the value is its top multiplier.
SUGGEST: "&7Найбольшы множнік лініі на ўсю шырыню: &e{multiplier}x"

KEY: mines.mine-count-error
TIER: 3
ISSUE: "Памылка пры апрацоўцы" copies Russian "ошибка при обработке".
SUGGEST: "&cНе ўдалося распазнаць колькасць мін."

## Terminology notes

"Current:" as "Цяпер:" vs "Бягучы/Бягучая …"; Baccarat sides capitalized
only in the standalone labels; consecutive PvE rounds as "гульняў" vs
"раўнд"; "мэта па прыбытку" in four keys that must change together.

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 0 | Tier 3: 22 | Total: 22

## Disposition (translator)

All 22 applied as suggested. Follow-ups from the terminology notes: the
four Baccarat odds labels now capitalize the sides too ("Пара Гульца",
"Перамога Банкіра"), and the remaining conditional "пры …" phrasings in the
auto-spin keys (`auto-rule-any-win`, `auto-any-win`, `auto-big-win-current`,
`auto-big-win-set`, `auto-big-win-off-set`, `auto-profit-target-current`,
`auto-loss-limit-current`) were rewritten in line with packet 2's Tier 1
finding.
