# gl_ES review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 checks clean (the only script hit,
"Stand-on-17" matching `-1`, is a false positive). RAG-normative, no Spanish
interference; pronoun placement correct ("xa se", "non se", "Erguícheste",
"asignóuselle", "Confírmalo?"); player-neutral wording; count labels
("Crupiers eliminados: {count}."); `occupations.*` fit "Remata primeiro de
editar {occupation} de «{name}»."; `dragon-settings.columns|vines|floors`
fit "o novo número predeterminado de {setting}"; re-split as a completed
action; distinct `closed-*` timer messages; "Mesmo rango" vs "Mesmo valor".
The reviewer checked Java for the two "None" keys and the refund `{amount}`.

KEY: roulette.bets-close-in
TIER: 3
ISSUE: Seconds symbol written as capital "S"; every other timer uses "{seconds} s".
SUGGEST: "AS APOSTAS PÉCHANSE DENTRO DE {seconds} s!"

KEY: roulette.returning
TIER: 3
ISSUE: Game name lowercased; other running text writes "Ruleta".
SUGGEST: "&aVolvendo á Ruleta..."

KEY: blackjack.other-betting-circle
TIER: 3
ISSUE: "Sitio de aposta: {name}" reads as if the spot were named {name}.
SUGGEST: "Sitio de aposta de {name}"

KEY: blackjack.current-player-turn
TIER: 3
ISSUE: Colon after the preposition; {player} is a name, no agreement to avoid.
SUGGEST: "Quenda de {player}"

KEY: blackjack.wager-per-hand-title
TIER: 3
ISSUE: Awkward comma splice.
SUGGEST: "Aposta: {amount} por man (mans: {count})"

KEY: blackjack.shoe-exhausted-refunded
TIER: 3
ISSUE: "a metade da rolda" can read as "half of the round"; plural "devolvéronse" breaks for "1 emerald" (formatWagerDisplay).
SUGGEST: "&eA zapata quedou sen cartas en plena rolda -- devolvéronche {amount}."

KEY: blackjack.start-transition-failed-refunded
TIER: 3
ISSUE: Same singular-amount agreement problem.
SUGGEST: "&eNon se puido comezar a rolda -- devolvéronche {amount}."

KEY: blackjack.table-reset-refunded
TIER: 3
ISSUE: Same singular-amount agreement problem.
SUGGEST: "&eUnha persoa administradora reiniciou a mesa -- devolvéronche {amount}."

KEY: admin.animation-updated-detailed
TIER: 3
ISSUE: "nova" attaches to "animación" instead of "mensaxe"; siblings use "Nome novo do crupier".
SUGGEST: "&aMensaxe nova da animación do crupier: «&e{message}&a»."

KEY: mob-settings.none
TIER: 3
ISSUE: Masculine "Ningún", but every call site fills a feminine slot (llama decoración, wolf collar cor); checked in ComplexVariantMenu, MobSettingsMenu, MobSelectionMenu, JockeyOptionsMenu.
SUGGEST: "Ningunha"

KEY: admin.none
TIER: 3
ISSUE: Only used as the llama decor value ("Decoración actual: …"), feminine.
SUGGEST: "Ningunha"

## Terminology notes

"Ruleta" capitalization, the seconds symbol and "None" agreement as above.
Out of scope for the reviewer, no fix proposed: `blackjack.cannot-bet-other`
"doutro xogador" is generic masculine where the catalog otherwise prefers
inclusive wording.

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 0 | Tier 3: 11 | Total: 11

## Disposition (translator)

All 11 applied as suggested; the "None" call sites were re-verified
(`getLlamaCarpetName`, `jockey-options.current`) and recorded in guide §C.
The optional note was taken too: `blackjack.cannot-bet-other` now says
"doutra persoa". Self-review follow-ups: `coin-flip.player-turn` /
`rock-paper-scissors.player-turn` drop the colon after "de" to match
`current-player-turn`, and `blackjack.stand` becomes the infinitive
"Plantarse" (with `stood` / `turn-timer-expired` quoting it) so the four
action buttons are parallel (`Pedir`, `Plantarse`, `Dobrar`, `Dividir`).
