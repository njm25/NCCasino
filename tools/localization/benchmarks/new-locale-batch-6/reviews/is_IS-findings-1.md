# is_IS review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean (script-checked order of
placeholders, `&` codes and `\n`; protected literals, `/ncc claim`, `-1`,
`off` intact). No gendered predicate about the player or a named player;
"welcome" as "Góða skemmtun í …". Terminology notes (not scored): "Staða"
is both balance and status on different items; the mode-switch cash-out
notice says "verður þér sjálfkrafa greitt", which is correct.

KEY: coin-flip.seat-unavailable
TIER: 1
ISSUE: "Sætið er ekki laust enn" reads as "not vacant yet", suggesting someone is sitting there; the chair is empty and locked until chair 1 is taken.
SUGGEST: "&7&oSætið er enn læst"

KEY: rock-paper-scissors.seat-unavailable
TIER: 1
ISSUE: Same as coin-flip.seat-unavailable.
SUGGEST: "&7&oSætið er enn læst"

KEY: errors.no-return-target
TIER: 1
ISSUE: "-staður" is masculine (and "skilgreindur" agrees), so the determiner must be "Enginn", not "Engin".
SUGGEST: "&cEnginn endurkomustaður var skilgreindur."

KEY: slots.variance-steady
TIER: 1
ISSUE: Filled into "Sveiflur: {variance}", "Stöðugar" gives the set phrase "stöðugar sveiflur" (constant fluctuations), close to the opposite of the lowest-risk preset.
SUGGEST: "Mjög litlar"

KEY: coin-flip.player-turn
TIER: 3
ISSUE: Clipped "Röðin að {player}" puts the name in a dative slot that a placeholder cannot show.
SUGGEST: "&o{player}&o á leik"

KEY: rock-paper-scissors.player-turn
TIER: 3
ISSUE: Same as coin-flip.player-turn.
SUGGEST: "&o{player}&o á leik"

KEY: preferences.overflow.bank-explained
TIER: 3
ISSUE: "þar til pláss losnar" drops the player as the one who acts ("until you make room").
SUGGEST: "&7Geyma: geymt örugglega þar til þú losar pláss."

KEY: slots.auto-settings-reset
TIER: 3
ISSUE: "Endurstilla sjálfvirka snúninga" can read as resetting a running batch; the button restores the Auto Spin settings.
SUGGEST: "&cEndurstilla stillingar sjálfvirkra snúninga"

KEY: slots.demo-result-win
TIER: 3
ISSUE: "skilað {amount} af veðmálinu {bet}" reads as the amount being part of the stake.
SUGGEST: "&ePrufa: þessi snúningur hefði skilað {amount} miðað við veðmálið {bet}. Enginn gjaldmiðill var notaður."

KEY: slots.demo-result-loss
TIER: 3
ISSUE: "hefði ekki unnið neitt af veðmálinu" is unidiomatic.
SUGGEST: "&ePrufa: þessi snúningur hefði ekki skilað neinum vinningi miðað við veðmálið {bet}. Enginn gjaldmiðill var notaður."

KEY: slots.profile-loaded
TIER: 3
ISSUE: Accusative "Sniðið … var hlaðið" reads like loading a gun; profile-entry-load uses the dative.
SUGGEST: "&aSniðinu {name} var hlaðið."

KEY: baccarat.bets-closed
TIER: 3
ISSUE: "Veðmálum er lokað" can read as the bets being settled; the idiom is "lokað fyrir veðmál".
SUGGEST: "&cLokað hefur verið fyrir veðmál."

KEY: baccarat.bets-closed-place
TIER: 3
ISSUE: Align with baccarat.bets-closed.
SUGGEST: "&cLokað hefur verið fyrir veðmál; ekki er hægt að veðja."

KEY: baccarat.bets-closed-undo
TIER: 3
ISSUE: Align with baccarat.bets-closed.
SUGGEST: "&cLokað hefur verið fyrir veðmál; ekki er hægt að afturkalla veðmál."

Tier 0: 0 · Tier 1: 4 · Tier 2: 0 · Tier 3: 10

## Disposition (translator)

All 14 applied. `seat-unavailable` shortened to "&7&oSætið er læst"
(the lock is the state; "enn" adds nothing on a label). Follow-up in
self-review: `blackjack.current-player-turn` takes the same "{player} á
leik" form.
