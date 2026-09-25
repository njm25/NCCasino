# mn_MN review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean (token order, protected
literals, no English residue, "{index}-р" only). Template slots hold
(`{setting}` with багана / ороонго / давхар, "{setting} Шинэ утга:",
`{occupation}`, `{change}`, "Байхгүй"), `resplit-offer` is a completed
action, loanword genitives (минагийн, Ламагийн, Баккарагийн) consistent.

KEY: blackjack.table-reset-refunded
TIER: 2
ISSUE: "шинэчилсэн" is the catalog's word for "updated"; reset is "анхны төлөвт буцаах".
APPLIED: "&eАдмин ширээг анхны төлөвт нь буцаасан -- буцаан олгосон: {amount}."

KEY: mines.inventory-full
TIER: 3
ISSUE: A bare "хаяж байна" can read as discarding; "ойролцоо" gives the item-drop sense, as in `betting.inventory-full`.
APPLIED: "&cИнвентарт зай алга: {amount}; ойролцоо хаяж байна..."

KEY: roulette.inventory-full
TIER: 3
ISSUE: Same.
APPLIED: "&cИнвентарт зай алга: {amount}; ойролцоо хаяж байна..."

KEY: blackjack.inventory-full
TIER: 3
ISSUE: Same.
APPLIED: "&cИнвентарт зай алга: {amount}; ойролцоо хаяж байна..."

KEY: mines.rebet-broke
TIER: 3
ISSUE: The source is deliberately slangy; the flat system wording lost the tone.
APPLIED: "&cХалаас хоосон, бооцоо давтах мөнгө алга."

KEY: roulette.bets-closed-final
TIER: 3
ISSUE: "ХИЙСЭН НЬ ХИЙСЭН" copies "what's done is done"; the idiom is "болсон юм болсон".
APPLIED: "БООЦОО АВАХАА ЗОГСООЛОО, БОЛСОН ЮМ БОЛСОН"

KEY: blackjack.starts-in
TIER: 3
ISSUE: "эхлэхэд" reads "when the game starts", not a countdown.
APPLIED: "Тоглоом эхлэх хүртэл: {seconds}"

KEY: blackjack.dealer-turn-capitalized
TIER: 3
ISSUE: English title case copied; Mongolian does not capitalise the second word.
APPLIED: "Дилерийн ээлж"

KEY: blackjack.closed-before-turn
TIER: 3
ISSUE: The particle "тань" split the genitive from its head noun.
APPLIED: "&eТаны ээлжийн таймер дуусахаас өмнө та буцаж ирэх ёстой, эс бөгөөс таны гар автоматаар зогсоно."

KEY: blackjack-settings.turn-timer-timeout-desc-2
TIER: 3
ISSUE: "тэр хооронд" put the seat riding to the result inside the timeout window; it follows the resolution.
APPLIED: "&7Мөн цэсээ хаасан эсвэл холболт нь тасарсан тоглогчийн ээлж шийдэгдэхээс өмнөх хугацаа; дараа нь түүний суудал үр дүн гартал тоглоомд үлдэнэ."

KEY: mob-selection.return-to-selection
TIER: 3
ISSUE: "сонгох руу" attaches a directional ending to an infinitive; it needs the menu noun.
APPLIED: "Амьтан сонгох цэс рүү буцах"

KEY: coin-flip-settings.mode-pvp
TIER: 3
ISSUE: "эсрэг" takes the genitive.
APPLIED: "Тоглогч тоглогчийн эсрэг"

KEY: coin-flip-settings.mode-pvd
TIER: 3
ISSUE: Same.
APPLIED: "Тоглогч дилерийн эсрэг"

KEY: rock-paper-scissors-settings.mode-pvp
TIER: 3
ISSUE: Same.
APPLIED: "Тоглогч тоглогчийн эсрэг"

KEY: rock-paper-scissors-settings.mode-pvd
TIER: 3
ISSUE: Same.
APPLIED: "Тоглогч дилерийн эсрэг"

Tier 0: 0 · Tier 1: 0 · Tier 2: 1 · Tier 3: 14

## Disposition (translator)

All 15 applied. Terminology notes taken as follow-ups: `test-game.reset`
and its mention in `test-game.round-finished` no longer use
"шинэчлэх", and the four split-rule toggles now say "асаах/унтраах" like
the insurance and splitting toggles.
