# sr_RS review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Scripted checks: placeholder / `&` / `\n`
order identical to English in every record; Latin letters only in protected
or expected tokens (`/ncc claim`, `x`/`y`/`z`, multiplier `x`, `V`, `Shift`,
`off`, `-1`); end punctuation matches. Standard ekavian throughout, no
Croatian/ijekavian forms, no gendered forms toward the player; `„“` quotes,
decimal comma, Cyrillic `с`. Context notes verified (player-turn is a
third-person announcement, cash-out notice is future tense, chain-win
`{amount}` is the current pot, max chain is a round count, overflow drop
means on the ground, variance lines pay less often, house edge typed in
chat without a leading `%`, rebet wording consistent, Banker distinct from
Dealer).

KEY: slots.profile-name-empty
TIER: 3
ISSUE: Numeral agreement: `SlotsProfileName.MAX_LENGTH` is 24, giving "од 1 до 24 знакова"; the noun agrees with the last number ("24 знака"). A label form is robust to limit changes.
SUGGEST: "&cДужина имена профила (број знакова) мора да буде од {min} до {max}."

KEY: slots.profile-name-too-long
TIER: 3
ISSUE: Same: "највише 24 знакова" should be "24 знака".
SUGGEST: "&cНајвећа дозвољена дужина имена профила (број знакова): {max}."

KEY: preferences.overflow.title
TIER: 3
ISSUE: "Добици који не стају" is ambiguous ("стати" = fit / stop).
SUGGEST: "&eДобици који не стају у инвентар"

## Terminology notes

No Tier 2 inconsistencies: делилац, опклада/улог, Понови улог, Уложи све,
Подигни добитак, пот, жетон, чет, мени, подешавања vs лична подешавања,
ролне, добитне линије, окретање, волатилност, предност куће, повраћај играчу.
Informational: Slots "run" and the PvE "streak" (`низ победа`) are both
`низ` but never share a game; Slots stake messages use `улог` per the
stake-amount rule.

Counts: Tier 0: 0, Tier 1: 0, Tier 2: 0, Tier 3: 3

## Disposition (translator)

All 3 applied as suggested. The profile-name agreement point also affects
the already-promoted hr_HR catalog ("{max} znakova" with max 24); recorded
as an out-of-scope observation in the batch summary, not fixed here.
