# ca_ES review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Scripted token-order check clean (only the
× sign after placeholders, as in English); NCCasino, `/ncc claim`, `-1`,
`off` intact. Semantic notes hold (neutral `seat-unavailable`, third-person
`player-turn`, future cash-out notice, chain-win as current pot, round-count
max chain, overflow held vs dropped nearby, lines pay less often, "Lianes",
no `ratxa` for auto-spin). No gendered address, no Spanish interference.

KEY: common.click-skip
TIER: 3
ISSUE: "saltar" without a pronoun reads as "jump"; the Catalan UI term is "ometre".
SUGGEST: "FES CLIC PER OMETRE"

KEY: coin-flip.mode-switch-cashout-notice
TIER: 3
ISSUE: Impersonal "es cobrarà" hides who is paid and can read as a charge.
SUGGEST: "&eEn canviar, cobraràs automàticament &a{amount}"

KEY: rock-paper-scissors.mode-switch-cashout-notice
TIER: 3
ISSUE: Same.
SUGGEST: "&eEn canviar, cobraràs automàticament &a{amount}"

KEY: baccarat.rebet-disabled
TIER: 3
ISSUE: Infinitive label + participle ("Repetir aposta desactivat") is ungrammatical; quote the label.
SUGGEST: "&cS'ha desactivat «Repetir aposta»."

KEY: baccarat.rebet-insufficient
TIER: 3
ISSUE: Same in the second sentence.
SUGGEST: "&cNo tens prou diners per repetir l'aposta. S'ha desactivat «Repetir aposta»."

KEY: slots.bet-too-large
TIER: 3
ISSUE: "massa gran per girar" says the bet would spin; use "tirada".
SUGGEST: "&cAquesta aposta és massa gran per fer una tirada."

KEY: slots.auto-any-win
TIER: 3
ISSUE: Reflexive imperative "Atura't" tells the player to stop; siblings are labels.
SUGGEST: "&eAturar amb qualsevol premi"

KEY: slots.auto-settings-reset
TIER: 3
ISSUE: Reads as resetting the auto-spin itself, not its settings.
SUGGEST: "&cRestableix la configuració de la tirada automàtica"

KEY: slots.paytable-card-no-runs
TIER: 3
ISSUE: "Cap … no hi cap" juxtaposes two homographs.
SUGGEST: "&8Amb aquest nombre de rodets ({columns}) no hi ha lloc per a cap seqüència d'aquest símbol."

KEY: slots.profile-duplicate
TIER: 3
ISSUE: "per aturar-te" means "to stop yourself".
SUGGEST: "&eJa tens un perfil anomenat {name}. Escriu {overwrite} al xat per substituir-lo o {cancel} per deixar-ho estar."

KEY: slots.profile-overwrite-retry
TIER: 3
ISSUE: Same.
SUGGEST: "&cEscriu {overwrite} al xat per substituir {name} o {cancel} per deixar-ho estar."

KEY: slots-settings.house-edge-updated
TIER: 3
ISSUE: "definit a" copies "set to"; Catalan uses "establir en".
SUGGEST: "&aS'ha establert l'avantatge de la casa en {edge}; ara la màquina retorna {rtp}."

KEY: slots-settings.default-lines-hint
TIER: 3
ISSUE: "alternar" implies two states; "d'1 fins a" is awkward for a range.
SUGGEST: "&7Fes clic per recórrer els valors d'1 a {max}."

## Terminology notes

All glossary terms consistent (crupier, Banca / Jugador, aposta, Repetir
aposta, Apostar-ho tot, Cobra, Pot, ratxa for the PvE streak only, rodets,
línies de premi, seqüència, tirada, volatilitat, avantatge de la casa,
retorn al jugador, configuració vs preferències, xat, fitxa, botó).
"jackpot" kept as an accepted loanword (not scored).

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 0 | Tier 3: 13

## Disposition (translator)

All 13 applied as suggested.
