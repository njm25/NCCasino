# mk_MK review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 checks clean (scripted token order,
protected literals, `/ncc` tokens, `-1`; no Serbian, Bulgarian or Russian
letters). Context notes hold: the three `closed-*` timers are distinct;
`resplit-offer` "Раката е повторно поделена!" is a completed action;
`occupations.*` fit `finish-editing` (except the two max-chain values
below); the dragon fillers fit "нов стандарден број {setting}"; "Нема"
works in the decor and collar-colour slots; `player-only` means players,
not the console; Same Rank / Same Value distinct ((П-П) / (П-Д)); nothing
assumes the player's gender.

KEY: mines.inventory-full
TIER: 3
ISSUE: "се фрла..." alone can read as discarded; betting.inventory-full says "во близина".
SUGGEST: "&cНема место за {amount}, се фрла во близина..."

KEY: roulette.inventory-full
TIER: 3
ISSUE: Same.
SUGGEST: "&cНема место за {amount}, се фрла во близина..."

KEY: blackjack.inventory-full
TIER: 3
ISSUE: Same.
SUGGEST: "&cНема место за {amount}, се фрла во близина..."

KEY: blackjack.betting-closed
TIER: 3
ISSUE: "да се влога" is not standard and drifts from "облог".
SUGGEST: "&cНе може да се ставаат облози за време на игра."

KEY: blackjack.cannot-bet-other
TIER: 3
ISSUE: Same non-standard verb.
SUGGEST: "&cНе можете да ставите облог на туѓо место."

KEY: blackjack.insurance-taken
TIER: 3
ISSUE: "земено за {amount}" is a calque of "taken for".
SUGGEST: "&aЗемавте осигурување во износ од {amount}."

KEY: roulette.returning
TIER: 3
ISSUE: "Враќање на рулетот" can read as returning the wheel.
SUGGEST: "&aСе враќате на рулетот..."

KEY: dragon-settings.vines-less-columns
TIER: 3
ISSUE: Compares the objects with an adverb; the rule is about their counts.
SUGGEST: "Бројот на лијани мора да биде помал од бројот на колони ({columns})."

KEY: mob-settings.add-vehicle-lore
TIER: 3
ISSUE: Informal 2nd-singular imperative among "Кликнете" lore lines.
SUGGEST: "Додава нов моб на дното на купот"

KEY: mob-settings.add-passenger-lore
TIER: 3
ISSUE: Same.
SUGGEST: "Додава нов моб на врвот на купот"

KEY: mob-selection.no-variants
TIER: 3
ISSUE: Doubled "за" is awkward.
SUGGEST: "&cНема варијанти за менување кај &e{mob}&c."

KEY: mob-selection.reset-confirmation
TIER: 3
ISSUE: Unfinished calque "…на стандардната?".
SUGGEST: "Да се вратат стандардните вредности на конфигурацијата?"

KEY: coin-flip-settings.max-chain-updated
TIER: 3
ISSUE: Starts with the abbreviation "Макс.", hiding the needed definite article.
SUGGEST: "&aМаксималниот број рунди во низа на дилерот е ажуриран."

KEY: rock-paper-scissors-settings.max-chain-updated
TIER: 3
ISSUE: Same.
SUGGEST: "&aМаксималниот број рунди во низа на дилерот е ажуриран."

KEY: occupations.rps-max-chain
TIER: 3
ISSUE: Must be a definite noun phrase inside finish-editing; indefinite, abbreviated, "во … во".
SUGGEST: "максималниот број рунди во низа во играта Камен, ножици, хартија"

KEY: occupations.coin-flip-max-chain
TIER: 3
ISSUE: Same.
SUGGEST: "максималниот број рунди во низа во играта Фрлање паричка"

KEY: blackjack-settings.toggle-split-matching
TIER: 3
ISSUE: Drops "matching"; reads as a generic splitting rule.
SUGGEST: "Правило за совпаѓање при делење"

KEY: blackjack-settings.split-matching-updated
TIER: 3
ISSUE: Same.
SUGGEST: "&eПравило за совпаѓање при делење: &e{value}"

KEY: admin.drag-change
TIER: 3
ISSUE: Imperative verb and bare noun phrase joined by "или" do not parse cleanly.
SUGGEST: "Довлечете предмет тука или направете Shift + клик на него за промена"

KEY: admin.slots-rtp-lore
TIER: 3
ISSUE: "враќање на играчот" first reads as the player coming back.
SUGGEST: "&7Тековно враќање кон играчите: &a{rtp}"

## Terminology notes

Betting verb "влога" (two blackjack keys here, two slots keys in packet 1);
drop wording in the three `*.inventory-full` keys; game-name styling
(quoted "Спуштање кај змејот", "на рулет / блекџек" vs "во играта …")
minor. No Tier 2 drift.

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 0 | Tier 3: 20 | Total: 20

## Disposition (translator)

All 20 applied; `mob-selection.reset-confirmation` uses the packet-1
wording "Да се вратат стандардните вредности на конфигурацијата?" so both
reset prompts match. Follow-ups: `roulette.welcome` / `blackjack.welcome`
now use "Добредојдовте во играта …", and `slots.guide-machine-rtp` uses the
same "враќање кон играчите" as `admin.slots-rtp-lore`.
