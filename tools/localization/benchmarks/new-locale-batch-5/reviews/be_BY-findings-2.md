# be_BY review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 checks clean (token order, protected
literals, `-1`, `/ncc` commands; no и/щ/ъ; ў rule correct). Every past form
addressed to the player is plural; crupier / mob wording uses ordinary
masculine agreement. `occupations.*` fit `finish-editing`; the dragon
genitive plurals fit "новую колькасць {setting} па змаўчанні"; both
`updated-detailed` templates work with a full "…абноўлены." sentence;
"Няма" fits the llama-decor and wolf-collar slots. `resplit-offer` is a
completed action; the three `closed-*` timers are distinct; Same Rank /
Same Value are distinct (К-К / К-Д); `{mob}` / `{variant}` receive English
entity names (`formatEntityName`).

KEY: coin-flip-settings.max-chain-current
TIER: 1
ISSUE: Conditional "пры перамозе" is a Russian calque ("при победе"); standard Belarusian is "у выпадку перамогі".
SUGGEST: "&7Цяпер: {rounds}, выплата {multiplier}x у выпадку перамогі"

KEY: rock-paper-scissors-settings.max-chain-current
TIER: 1
ISSUE: Same calque.
SUGGEST: "&7Цяпер: {rounds}, выплата {multiplier}x у выпадку перамогі"

KEY: blackjack-settings.turn-timer-timeout-desc-2
TIER: 1
ISSUE: "пры закрытым меню або страце злучэння" is a Russian-pattern calque; "Столькі ж чакаецца завяршэнне" is clumsy and "на гэтым месцы" has no antecedent.
SUGGEST: "&7Гэты ж час даецца, калі меню закрыта або злучэнне страчана: потым ход завяршаецца аўтаматычна, а стаўка на месцы гульца застаецца ў гульні да выніку."

KEY: mines.tile-revealed
TIER: 3
ISSUE: "Гэта клетка" should be the feminine "Гэтая" and can misread as "This is a tile".
SUGGEST: "&dГэтая клетка ўжо адкрыта."

KEY: interaction.no-game-permission
TIER: 3
ISSUE: "гуляць у {game}" needs an accusative the fixed nominative name cannot supply; use apposition after "гульню".
SUGGEST: "&cУ вас няма дазволу гуляць у гульню {game}."

KEY: commands.help-create
TIER: 3
ISSUE: "на вашым месцы" idiomatically means "in your place / if I were you".
SUGGEST: "&b/ncc create &e<імя> &b- Стварае крупье там, дзе вы стаіце"

KEY: admin.chip-size-updated-detailed
TIER: 3
ISSUE: Bare index can read as the value; related keys write "№{index}".
SUGGEST: "&aНамінал фішкі №{index}: &e{size}&a."

## Terminology notes

No Tier 2 inconsistencies (крупье, стаўка, Ва-банк, Паўтор стаўкі,
намінал фішкі, шуз, Яшчэ карту / Хопіць / Падвоіць / Падзяліць, рука,
страхоўка, моб, вершнік, транспарт / пасажыр, раўнды серыі, тузін
consistent). Uncounted note: five Blackjack keys keep the English ASCII
"--" while a few others use "—".

Counts: Tier 0: 0 | Tier 1: 3 | Tier 2: 0 | Tier 3: 4 | Total: 7

## Disposition (translator)

All 7 applied as suggested. The "--" separators stay as in the English
source (optional typography, not changed).
