# gl_ES review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 checks clean (tokens, protected
words, command tokens, `-1` / `off`, no placeholder joined to a letter).
Context notes hold: `seat-unavailable` "Asento non dispoñible" (locked, not
occupied), future cash-out notice, third-person `player-turn`, chain-win as
current pot, game-count max chain, auto-spin batch never uses "racha",
player-neutral forms ("Xa tes asento", "Dámosche a benvida"), well-formed
enclitics ("Gárdamas", "Déixaas", "téntao").

KEY: dragon-descent.yay
TIER: 1
ISSUE: "Bieeen!" is the Spanish "¡Bien!" stretched out (Galician "ben"); Spanish interference.
SUGGEST: "&aIupiii!"

KEY: slots.auto-spin-limit-unlimited
TIER: 2
ISSUE: Shows the unlimited spin limit as "ningún", while `auto-summary-unlimited` fills the same "Límite de tiradas:" line with "Ilimitado".
SUGGEST: "&7Límite de tiradas: &aIlimitado"

KEY: preferences.overflow.drop
TIER: 3
ISSUE: "no chan preto" can read as "on the black ground" (preto = black / near).
SUGGEST: "&eDéixaas no chan, preto de min"

KEY: betting.inventory-full
TIER: 3
ISSUE: Same "chan preto" ambiguity.
SUGGEST: "&cNon hai sitio para {amount}; déixase no chan, preto de ti."

KEY: slots.guide-volatility-tradeoff
TIER: 3
ISSUE: English loanword "jackpots".
SUGGEST: "&7Con máis rolos, os premios pequenos e frecuentes de secuencias curtas dan paso a grandes premios, máis raros, de secuencias longas."

KEY: slots-settings.variance-tradeoff
TIER: 3
ISSUE: English loanword "jackpots" (the "lines pay less often" meaning is correct).
SUGGEST: "&7Máis volatilidade significa que as liñas pagan con menos frecuencia, pero os premios de secuencias longas son moito maiores."

KEY: slots.profile-adjusted
TIER: 3
ISSUE: After "así que" the pronoun goes before the verb, as elsewhere in the catalog.
SUGGEST: "&eEsta máquina non admite todos os valores gardados, así que o perfil se axustou: filas {rows}, rolos {columns}, liñas {lines}, aposta {amount}."

KEY: slots.rail-exit-session
TIER: 3
ISSUE: A "todo" subject triggers proclisis ("todo … se paga").
SUGGEST: "&7Así remata aquí a túa sesión; todo o xa gañado se paga igualmente."

KEY: slots.auto-big-win-current
TIER: 3
ISSUE: Colon directly after the preposition "de".
SUGGEST: "&7Detense cun pagamento de &a{multiplier}× a aposta total"

KEY: slots.auto-profit-target-current
TIER: 3
ISSUE: Same colon after "de".
SUGGEST: "&7Detense cun beneficio de &a{amount}"

KEY: slots.auto-loss-limit-current
TIER: 3
ISSUE: Same colon after "de".
SUGGEST: "&7Detense cunha perda de &c{amount}"

KEY: test-game.server-lost
TIER: 3
ISSUE: Plural "Retiráronse" breaks when the amount is 1; use a label form.
SUGGEST: "Perdiches! Cantidade retirada: {amount} {currency}."

KEY: slots.wager-control-hint
TIER: 3
ISSUE: "unha ficha maior / menor" reads as a physically bigger chip; the control changes the chip value.
SUGGEST: "&7Clic esquerdo para unha ficha de máis valor, clic dereito para unha de menos valor."

KEY: slots.rail-wager-controls
TIER: 3
ISSUE: Same size-versus-value ambiguity.
SUGGEST: "&7Botón de abaixo: clic esquerdo para unha ficha de máis valor, clic dereito para unha de menos valor."

## Terminology notes

Unlimited spin limit was "ningún" in one key and "Ilimitado" in the value
that fills the same line. Everything else consistent: aposta / Repetir
aposta / Apostalo todo / Cobrar; bote (Coin Flip / RPS pot); crupier vs
Banca; rolos, liñas de premio, tirada (spin) vs rolda (round); secuencia
(slots run) vs racha (PvE streak only); volatilidade, vantaxe da casa,
retorno ao xogador; configuración vs preferencias; «» quotes, decimal comma.

Counts: Tier 0: 0 | Tier 1: 1 | Tier 2: 1 | Tier 3: 12 | Total: 14

## Disposition (translator)

All 14 applied as suggested. Self-review follow-up in the same family:
`preferences.overflow.drop-explained` now also says "preto de ti".
