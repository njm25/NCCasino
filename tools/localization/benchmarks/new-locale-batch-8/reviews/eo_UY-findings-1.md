# eo_UY review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. The run was cut off once by an API rate
limit and resumed with its context intact; on resumption it was told that
the packet-2 reviewer had flagged "ludanta menuo" so the three packet-1
siblings could be judged too. Tier 0 clean (script-checked token order,
`/ncc claim`, `-1`, `off`; "0,95:1" and "2,5%" confirmed). No placeholder
followed by a letter; the context-note traps (seat lock, chain-win pot,
overflow hold/drop, variance hit rate, RTP vs refund) all hold.

KEY: common.return-player-menu
TIER: 3
ISSUE: "ludanta menuo" is the active participle of ludi ("a playing menu"), not "player menu".
APPLIED: "&dReen al la ludantmenuo"

KEY: player-menu.title
TIER: 3
ISSUE: "ludanta menuo" is the active participle of ludi ("a playing menu"), not "player menu".
APPLIED: "Ludantmenuo"

KEY: errors.invalid-player-menu-option
TIER: 3
ISSUE: "ludanta menuo" is the active participle of ludi ("a playing menu"), not "player menu".
APPLIED: "&cNevalida elekto en la ludantmenuo."

KEY: errors.currency-unavailable
TIER: 3
ISSUE: The ongoing -ata participle is used for a completed action; the catalog uses -ita in the same pattern.
APPLIED: "&cLa valuto ne estas agordita, do la gajnoj ne povis esti kredititaj."

KEY: betting.inventory-full
TIER: 3
ISSUE: "Mankas loko ...: {amount}" can read as the amount of missing space, and "ĝi" then points back to "loko".
APPLIED: "&cMankas loko en la inventaro por {amount}; tio estas demetata proksime."

KEY: payout.wager-blocked
TIER: 3
ISSUE: {amount} was split off to the end and read as attached to "playing again" rather than to the banked items.
APPLIED: "&cFaru lokon por la konservitaj objektoj ({amount}) antaŭ ol denove ludi."

KEY: coin-flip.max-pot-hit
TIER: 3
ISSUE: "pagi samtempe" (pay simultaneously) calques "at once"; the meaning is the most a single payout can be.
APPLIED: "&6Viaj gajnoj atingis la maksimuman sumon, kiun ĉi tiu ludo povas pagi per unu elpago. Gratulon pro la maksimuma gajno!"

KEY: rock-paper-scissors.max-pot-hit
TIER: 3
ISSUE: Same calque.
APPLIED: "&6Viaj gajnoj atingis la maksimuman sumon, kiun ĉi tiu ludo povas pagi per unu elpago. Gratulon pro la maksimuma gajno!"

KEY: rock-paper-scissors.tie
TIER: 2
ISSUE: "Denove ĵetante..." calques "throw" (the catalog uses "gesto") and is a subjectless participle.
APPLIED: "&eEgaleco! Ankoraŭ unufoje..."

KEY: baccarat.rebet-insufficient
TIER: 2
ISSUE: Rebet was "ripeti la veton" here but "reveti" everywhere else; "Ne sufiĉas" had no subject.
APPLIED: "&cNe sufiĉas valuto por reveti. “Reveti” estas malŝaltita."

KEY: baccarat.player-wins
TIER: 3
ISSUE: Lower-case "la ludanto" reads as the human player; capitalise the betting side as in baccarat.player.
APPLIED: "La Ludanto venkas!"

KEY: baccarat.banker-wins
TIER: 3
ISSUE: Match the capitalised side name "Bankisto".
APPLIED: "La Bankisto venkas!"

KEY: baccarat.player-pair-odds
TIER: 3
ISSUE: Same side-name capitalisation.
APPLIED: "Paro de la Ludanto - 11:1"

KEY: baccarat.player-win-odds
TIER: 3
ISSUE: Same side-name capitalisation.
APPLIED: "Venko de la Ludanto - 1:1"

KEY: baccarat.banker-win-odds
TIER: 3
ISSUE: Same side-name capitalisation.
APPLIED: "Venko de la Bankisto - 0,95:1"

KEY: baccarat.banker-pair-odds
TIER: 3
ISSUE: Same side-name capitalisation.
APPLIED: "Paro de la Bankisto - 11:1"

KEY: slots.demo-result-win
TIER: 3
ISSUE: {amount} was a bare direct object that would need the accusative -n.
APPLIED: "&eDemonstro: ĉi tiu turno estus paginta sumon de {amount} (veto: {bet}). Neniu valuto estis interŝanĝita."

KEY: slots.auto-rule-profit
TIER: 3
ISSUE: {amount} was a bare direct object of "atingas".
APPLIED: "&7Haltas ĉe profito de &a{amount}&7."

KEY: slots.auto-rule-loss
TIER: 3
ISSUE: {amount} was a bare direct object of "superus".
APPLIED: "&7Haltas antaŭ perdo de pli ol &c{amount}&7."

KEY: slots.auto-profit-target-current
TIER: 3
ISSUE: "{amount} da profito" breaks when {amount} already renders with its own noun ("5 emeralds").
APPLIED: "&7Haltas ĉe profito de &a{amount}"

KEY: slots.auto-loss-limit-current
TIER: 3
ISSUE: Same "{amount} da perdo" construction.
APPLIED: "&7Haltas ĉe perdo de &c{amount}"

KEY: slots.auto-reset-already-default
TIER: 3
ISSUE: "estas je la defaŭltoj" misuses "je".
APPLIED: "&8Ĉio jam havas la defaŭltajn valorojn."

KEY: slots.auto-settings-reset
TIER: 2
ISSUE: "aŭtomatajn agordojn" means settings that are automatic; five other keys say "agordoj de aŭtomata turnado".
APPLIED: "&cRestarigi la agordojn de aŭtomata turnado"

KEY: slots.auto-big-win-off-set
TIER: 3
ISSUE: An infinitive subject ("Halti") with an adjective predicate is ungrammatical.
APPLIED: "&aLa halto ĉe granda gajno nun estas malŝaltita."

KEY: slots.paytable-legend-run
TIER: 3
ISSUE: "kalkulante" implies the symbols do the counting; passive "kalkulate" plus "ekde".
APPLIED: "&7Sinsekvo: kiom da samaj simboloj staras unu post alia, kalkulate ekde la maldekstra rulo."

KEY: slots.paytable-legend-multiplier
TIER: 3
ISSUE: "per kiu" after the colon has no antecedent.
APPLIED: "&7Multiplikanto: la nombro, per kiu la veto de unu linio estas multiplikata."

KEY: slots.rail-paytable-multiplier
TIER: 3
ISSUE: Same fragment.
APPLIED: "&7Multiplikanto: la nombro, per kiu la veto de unu linio estas multiplikata."

KEY: mines.no-funds
TIER: 3
ISSUE: "Neniu mono" puts neniu on a mass noun.
APPLIED: "&cVi ne havas monon."

Tier 0: 0 · Tier 1: 0 · Tier 2: 3 · Tier 3: 25

## Disposition (translator)

All 28 applied, 27 as suggested. `rock-paper-scissors.tie` became
"Egaleco! Ankoraŭ unufoje..." instead of the suggested "Ni ludas
denove...", to avoid a first-person "we" in a system message (the line
fires after a tied reveal, before the replay). Follow-ups in self-review:
`roulette.no-funds` and `blackjack.no-funds` now also say "Vi ne havas
monon."; `slots.auto-profit-target-set` / `auto-loss-limit-set` replace
the "verb: {amount}" colon workaround with "la sumon de {amount}".
