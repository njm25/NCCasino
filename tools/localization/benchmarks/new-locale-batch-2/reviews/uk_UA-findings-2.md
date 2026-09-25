# uk_UA review -- packet-2 (592 records), isolated reviewer 2

Script compared placeholders, & codes, \n, /ncc tokens, protected literals and
-1 in order: no Tier 0. Only English left is `Shift` and command tokens. No
Russian-only letters; "ви" with plural past tenses throughout. Glossary
consistent. updated-detailed keys not scored (known source defect).

Tier 0: none. Tier 1: none. Tier 2: none.

Tier 3:
- interaction.no-game-permission: "грати в {game}" needs an accusative the
  placeholder cannot take ("грати в Рулетка"). SUGGEST "&cУ вас немає дозволу
  грати в цю гру: {game}."
- commands.help-create: "на вашому місці" = "in your place/if I were you".
  SUGGEST "там, де ви стоїте".
- blackjack.insurance-declined: SUGGEST "&7Ви відмовилися від страховки."
- blackjack-settings.valid-positive-integer: "дійсне число" = real number;
  SUGGEST "коректне".
- admin.currency-selection-disabled-future: SUGGEST "Заплановано на майбутнє".
- mines.rebet-broke: "Гаманець порожній" overstates; SUGGEST "&cНа мілині —
  на повтор ставки не вистачає."
- blackjack.wager-per-hand-title: "(рук)" wrong for 2-4; SUGGEST
  "Ставка: {amount} на руку, рук: {count}".

Terminology notes: ймовірність vs імовірність mixed; game-name styling mixed
(optional); "Баккара" vs "бакара" spelling (uncertain, not scored); chip
number "№" missing in chip-size-updated-detailed (trivial).

Counts: Tier 0 = 0, Tier 1 = 0, Tier 2 = 0, Tier 3 = 7.
