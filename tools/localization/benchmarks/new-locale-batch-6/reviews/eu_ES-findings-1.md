# eu_ES review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 checks clean (scripted token order;
`/ncc claim`, `-1`, `off` intact; `{multiplier}x` as in English). The
reviewer checked Java for three findings: `dragon-descent.sweep`
(DragonClient.java:449), the house-edge parser (SlotsHouseEdgeInput.parse),
and `coin-flip.waiting-bet` (shown to the chair-2 player, so "Aurkariaren"
is right). Context notes hold: `seat-unavailable` "ez dago erabilgarri
oraindik", chain-win `{amount}` as "Uneko potea", max chain as rounds,
future cash-out notice, third-person `player-turn`, vines as "Lianak",
overflow held.

KEY: dragon-descent.sweep
TIER: 1
ISSUE: "dena eraman du" states a completed loss; the label sits on every unsafe tile the dragon sweeps, even when the player survives (DragonClient.java:449).
SUGGEST: "&cHerensugea igarotzen ari da!"

KEY: slots.wager-control-hint
TIER: 1
ISSUE: "balio txikiagokoa baterako" is ungrammatical (article before "bat").
SUGGEST: "&7Ezkerreko klik balio handiagoko fitxa baterako, eskuineko klik balio txikiagoko baterako."

KEY: slots.rail-wager-controls
TIER: 1
ISSUE: Same.
SUGGEST: "&7Beheko botoia: ezkerreko klik balio handiagoko fitxa baterako, eskuineko klik balio txikiagoko baterako."

KEY: slots.height-description
TIER: 1
ISSUE: "zenbat" must precede the noun.
SUGGEST: "&7Zenbat ikur-errenkada erakusten diren aldatzen du."

KEY: slots-settings.house-edge-prompt
TIER: 1
ISSUE: The typed example "% 2,5" is rejected by SlotsHouseEdgeInput.parse (leading % unsupported).
SUGGEST: "&eIdatzi txatean etxearen abantaila, {min} eta {max} artean (adibidez, 2,5)."

KEY: slots-settings.house-edge-invalid
TIER: 1
ISSUE: Same unparseable example.
SUGGEST: "&cIdatzi {min} eta {max} arteko ehuneko bat, adibidez 2,5."

KEY: slots.guide-volatility-tradeoff
TIER: 2
ISSUE: English "jackpot" left in; "-engatik aldatzen dira" is a calque of "trade X for Y".
SUGGEST: "&7Arrabola gehiagorekin, segida laburren sari txiki eta ohikoen truke, segida luzeen sari nagusi handiago eta arraroagoak lortzen dira."

KEY: slots-settings.variance-tradeoff
TIER: 2
ISSUE: English "jackpotak"; heavy calque of "means".
SUGGEST: "&7Hegazkortasun handiagoarekin, lerroek sari gutxiagotan ematen dute, baina segida luzeen sari nagusiak askoz handiagoak dira."

KEY: betting.inventory-full
TIER: 3
ISSUE: Stiff "honentzat: {amount}" label; "{amount} gordetzeko" is natural.
SUGGEST: "&cEz dago lekurik {amount} gordetzeko; ondoan botatzen da."

KEY: payout.retry-many
TIER: 3
ISSUE: Label form drops "You have … pending".
SUGGEST: "&6Oraindik entregatu ezin izan diren {count} ordainketa dituzu zain. Automatikoki saiatuko dira berriro."

KEY: payout.bank-empty
TIER: 3
ISSUE: Awkward and ambiguous "gorderik".
SUGGEST: "&7Ez duzu gordetako irabazirik zain."

KEY: slots.no-safe-denomination
TIER: 3
ISSUE: Inanimate locative on a person noun.
SUGGEST: "&cKrupier honekin ez dago seguru jokatzeko beste apusturik."

KEY: slots.payout-blocked-retry
TIER: 3
ISSUE: "saiatu" does not take the payment as its object.
SUGGEST: "&7Egin klik ordainketa berriro egiten saiatzeko."

KEY: slots.guide-seeds-never-pays
TIER: 3
ISSUE: "zenbat luzea" is non-standard; "zein luzea".
SUGGEST: "&7Haziek ez dute inoiz saririk ematen, segida zein luzea izanda ere."

KEY: slots.line-flash-number
TIER: 3
ISSUE: English word order; a colon label is more natural.
SUGGEST: "&7Lerroa: &f{line}"

KEY: slots.paytable-card-no-runs
TIER: 3
ISSUE: "{columns} arrabolatan" is grammatical and natural.
SUGGEST: "&8Ikur honen segidarik ez da sartzen {columns} arrabolatan."

KEY: slots.auto-spin-limit-set
TIER: 3
ISSUE: Loses "will run at most … spins".
SUGGEST: "&aBira automatikoak gehienez {spins} bira egingo ditu."

KEY: slots.auto-big-win-set
TIER: 3
ISSUE: Awkward position of "gutxienez".
SUGGEST: "&aBira automatikoa geldituko da ordainketa gutxienez apustu osoaren {multiplier}× denean."

KEY: slots.auto-profit-target-set
TIER: 3
ISSUE: Reads as "when it reaches: X".
SUGGEST: "&aBira automatikoa geldituko da hasi zenetik lortutako etekina {amount} izatera iristen denean."

KEY: slots.profile-name-too-long
TIER: 3
ISSUE: Stiff label; the rule itself is lost.
SUGGEST: "&cProfilaren izenak gehienez {max} karaktere izan ditzake."

KEY: slots.prompt-invalid-amount
TIER: 3
ISSUE: -ko adjective after the noun.
SUGGEST: "&cHori ez da baliozko zenbatekoa."

KEY: slots.prompt-invalid-multiplier
TIER: 3
ISSUE: Same.
SUGGEST: "&cHori ez da baliozko biderkatzailea."

KEY: test-game.waiting-accept
TIER: 3
ISSUE: Ergative agent with "-tzeko zain" can read as "waiting in order to accept".
SUGGEST: "2. eserlekuak apustua onar dezan zain..."

KEY: rock-paper-scissors.title
TIER: 3
ISSUE: Literal; the established Basque name is "Harri, orri, artazi" (optional).
SUGGEST: "Harri, orri, artazi"

## Terminology notes

English "jackpot" (suggested "sari nagusi"); select "aukeratu" vs
"hautatu" (low priority); "dibisa" vs "moneta" for currency (consistent,
glossary decision, not changed).

Counts: Tier 0: 0 | Tier 1: 6 | Tier 2: 2 | Tier 3: 16 | Total: 24

## Disposition (translator)

All 24 applied as suggested, including the optional RPS name; the rename
was carried into `game-options.rock-paper-scissors`, `choose-paper` /
`choose-scissors` ("Orria" / "Artaziak") and the three RPS settings keys.
The house-edge parser fact was added to guide §C and the reviewer rubric.
