# ro_RO review -- packet-1 (592 records), isolated reviewer 1

Script token check: placeholders, & codes, \n in EN order in all 592; no
placeholder+letter joins; no cedilla ş/ţ; /ncc claim, -1, off kept; tu
address and gender-neutral phrasing throughout; player-turn third person,
cash-out notice future, chain-win "Potul actual", max chain a round count,
Crupier vs Bancher, Liane, variance-tradeoff "liniile plătesc mai rar",
overflow strings safe-keeping. Tier 0: none. Tier 1: none.

Tier 2 (applied):
- slots.auto-settings-reset: "setările automate" -> "setările rotirii automate"
  (every other key uses "rotire automată").

Tier 3 (23, applied; inventory-full wording extended to the three sibling
keys for consistency):
- coin-flip / rock-paper-scissors .max-chain-hit: "{rounds} runde" needs "de"
  from 20 up -> "(runde: {rounds})".
- test-game.server-won / server-lost: "{amount} {currency}" numeral agreement
  -> label form.
- slots.profile-name-empty / profile-name-too-long: {max} is 24 -> "de caractere".
- slots.prompt-spin-limit / prompt-invalid-spin-limit: calque "pentru nicio
  limită" -> "pentru rotiri nelimitate".
- slots.auto-stop-spin-rejected: a spin is started, not placed -> "pornită".
- slots.bet-too-large: awkward "pentru a roti cu ea".
- betting.inventory-full: reflexive "se lasă pe jos" -> explicit items left
  on the ground.
- slots-settings.variance-top-line: "cea mai mare linie" -> the top
  multiplier for a full-width run.
- slots.variance-steady: "Constantă" reads as "volatility unchanged" ->
  "Foarte scăzută".
- coin-flip.pot / rock-paper-scissors.pot: bare "Pot" also reads as the verb
  -> "Potul".
- slots.rail-*-controls (8 keys): bare "Clic stânga dedesubt" -> "pe butonul
  de mai jos".

Tier counts: 0 / 0 / 1 / 23.
