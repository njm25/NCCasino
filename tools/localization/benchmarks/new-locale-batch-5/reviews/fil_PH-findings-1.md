# fil_PH review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 checks clean (tokens, protected
words, command tokens, `-1` / `off`). Context notes hold: third-person
`player-turn`, `seat-unavailable` "Hindi pa bukas ang upuan" (locked, not
occupied), future cash-out notice, chain-win as current pot, game-count max
chain, overflow held / dropped nearby, lines pay less often, decimal point
in the house-edge example.

KEY: coin-flip.click-leave-chair
TIER: 1
ISSUE: "tumayo sa upuan" reads as "stand ON the chair"; the action is leaving the seat.
SUGGEST: "&7&oI-click para umalis sa upuan"

KEY: rock-paper-scissors.click-leave-chair
TIER: 1
ISSUE: Same.
SUGGEST: "&7&oI-click para umalis sa upuan"

KEY: baccarat.click-leave-chair
TIER: 1
ISSUE: Same.
SUGGEST: "&7&oI-click para umalis sa upuan"

KEY: slots.auto-settings-reset
TIER: 2
ISSUE: "Mga Setting ng Auto" is clipped English; every other key says "Awtomatikong Ikot".
SUGGEST: "&cI-reset ang Mga Setting ng Awtomatikong Ikot"

KEY: common.return-to
TIER: 3
ISSUE: Unneeded colon after "sa".
SUGGEST: "Bumalik sa {menu}"

KEY: player-menu.return-to
TIER: 3
ISSUE: Same.
SUGGEST: "&dBumalik sa {menu}"

KEY: game.welcome
TIER: 3
ISSUE: Clunky; "Maligayang pagdating sa {game}" works for any name.
SUGGEST: "&aMaligayang pagdating sa {game}"

KEY: coin-flip.round-started
TIER: 3
ISSUE: "good luck!" left in English.
SUGGEST: "&cNagsimula na ang round, suwertehin ka!"

KEY: rock-paper-scissors.round-started
TIER: 3
ISSUE: Same.
SUGGEST: "&cNagsimula na ang round, suwertehin ka!"

KEY: coin-flip.max-pot-hit
TIER: 3
ISSUE: "nang isang beses" reads as "one time only"; "nang minsanan".
SUGGEST: "&6Naabot ng panalo mo ang pinakamalaking halagang kayang ibayad ng larong ito nang minsanan. Binabati kita sa pinakamalaking panalo!"

KEY: rock-paper-scissors.max-pot-hit
TIER: 3
ISSUE: Same.
SUGGEST: "&6Naabot ng panalo mo ang pinakamalaking halagang kayang ibayad ng larong ito nang minsanan. Binabati kita sa pinakamalaking panalo!"

KEY: coin-flip.mode-switch
TIER: 3
ISSUE: Colon directly after the linker "na".
SUGGEST: "&oPalitan ang mode sa {mode}"

KEY: rock-paper-scissors.mode-switch
TIER: 3
ISSUE: Same.
SUGGEST: "&oPalitan ang mode sa {mode}"

KEY: baccarat.must-be-seated
TIER: 3
ISSUE: "Kailangan mong" + stative "nakaupo" is nonstandard.
SUGGEST: "&cKailangang nakaupo ka para makataya."

KEY: dragon-descent.dragon-name
TIER: 3
ISSUE: "Si … na Dragon" does not work as a nameplate; "X ang Y".
SUGGEST: "Drungus ang Dragon"

KEY: cards.ranks.four
TIER: 3
ISSUE: Spelling "Kwatro" vs the full spellings of the other ranks.
SUGGEST: "Kuwatro"

KEY: slots.bet-too-large
TIER: 3
ISSUE: "para paikutin" makes the wager the thing spun.
SUGGEST: "&cMasyadong malaki ang tayang iyan para makapag-ikot."

KEY: slots.payout-blocked
TIER: 3
ISSUE: "kontakin" is Taglish; "makipag-ugnayan".
SUGGEST: "&cHindi naipadala o naipila ang panalo mo. I-click ang Paikutin para subukan ulit; makipag-ugnayan sa staff kung magpatuloy ang problema."

KEY: slots.demo-result-loss
TIER: 3
ISSUE: Future instead of counterfactual (the win key uses "sana").
SUGGEST: "&ePansubok: wala sanang napanalunan ang ikot na ito sa tayang {bet}. Walang perang ginamit."

KEY: slots.auto-rule-profit
TIER: 3
ISSUE: "lamang na nang" is clumsy.
SUGGEST: "&7Hihinto kapag umabot na sa &a{amount}&7 ang tubo."

KEY: slots.auto-profit-target-current
TIER: 3
ISSUE: Colon after "nang".
SUGGEST: "&7Hihinto kapag ang tubo ay: &a{amount}"

KEY: slots.auto-loss-limit-current
TIER: 3
ISSUE: "talong" (talo + -ng) reads as "eggplant".
SUGGEST: "&7Hihinto kapag ang talo ay: &c{amount}"

KEY: slots.auto-big-win-description
TIER: 3
ISSUE: "multiple" left in English; "kahit" is vague for "at least".
SUGGEST: "&7Hinihinto ang Awtomatikong Ikot kapag ang isang ikot ay nagbayad nang hindi bababa sa ganitong multiplier ng kabuuang taya nito."

KEY: slots.auto-big-win-set
TIER: 3
ISSUE: Align "at least" with the description.
SUGGEST: "&aHihinto ang Awtomatikong Ikot kapag ang bayad ay hindi bababa sa {multiplier}× ng kabuuang taya."

KEY: slots.paytable-legend-run
TIER: 3
ISSUE: Noun "bilang" where the verb "counted" is needed.
SUGGEST: "&7Sunuran: ilang magkakaparehong simbolo ang magkakasunod, binibilang mula sa kaliwang reel."

KEY: slots.guide-volatility-normalized
TIER: 3
ISSUE: "Alinman dito" is stiff; lost the calibration idea.
SUGGEST: "&7Sa alinmang paraan, inaayos ang talaan ng bayad para tumapat ang balik sa &a{rtp}&7."

KEY: slots.variance-balanced
TIER: 3
ISSUE: "Balanse" collides with the wallet label "Balanse:".
SUGGEST: "Katamtaman"

KEY: slots.profile-adjusted
TIER: 3
ISSUE: "value" left in English.
SUGGEST: "&eHindi sinusuportahan ng makinang ito ang lahat ng naka-save na setting, kaya inangkop ito sa {rows} hilera, {columns} reel, {lines} linya at tayang {amount}."

KEY: slots.profile-duplicate
TIER: 3
ISSUE: Does not say "named".
SUGGEST: "&eMay profile ka nang pinangalanang {name}. I-type ang {overwrite} sa chat para palitan ito, o {cancel} para huminto."

KEY: slots.prompt-unavailable
TIER: 3
ISSUE: Mostly English.
SUGGEST: "&cHindi magagamit ang pag-type sa chat ngayon."

KEY: slots-settings.house-edge-timed-out
TIER: 3
ISSUE: "paglagay" should be "paglalagay".
SUGGEST: "&cNaubos ang oras sa paglalagay ng kalamangan ng casino; isinara ang menu ng mga setting."

## Terminology notes

"Awtomatikong Ikot" everywhere after the reset-button fix; the three
`click-leave-chair` keys fixed together; "Balanse" no longer doubles as a
variance preset. Otherwise consistent (Ulitin ang Taya, NAKA-ON / NAKA-OFF,
Itaya Lahat, Kunin ang Panalo, Pot, Bangkero vs Dealer, ikot vs round,
sunuran vs sunod-sunod, Mga Linya ng Panalo, Kalamangan ng Casino, Balik
sa Manlalaro, card names).

Counts: Tier 0: 0 | Tier 1: 3 | Tier 2: 1 | Tier 3: 27 | Total: 31

## Disposition (translator)

All 31 applied as suggested.
