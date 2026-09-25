# nb_NO review -- packet-2 (592 records), isolated reviewer 2

Script token check: placeholders, & codes, \n in EN order in all 592;
protected literals and /ncc commands intact; no placeholder+letter joins
(edit-stand-17 "-1" count is the false positive inside "Stand-on-17"). No
Danish/Swedish forms ("for at" in "sjansen for at" is the Bokmål
conjunction). Glossary consistent; split rules distinct; max chain a round
count; closed-* distinct. Tier 0: none. Tier 2: none.

Tier 1 (applied, and extended to every turn announcement in packet 1):
- blackjack.current-player-turn: "{player} har tur" -- in Bokmål "ha tur"
  means "to be lucky" -> "Turen til {player}". Same construction replaced in
  coin-flip / rock-paper-scissors .player-turn and .player-two-turn.

Tier 3 (6, applied):
- roulette.wrong-game: software sense "kjører" -> "tilbyr".
- blackjack.leave-exit: "Forlat stol" -> "Forlat stolen".
- mob-settings.enter-delete-mode-lore: "gå i" -> "gå inn i".
- blackjack-settings.toggle-aces-hit: mark the action «Trekk kort».
- admin.prompt-destination: "Klikk på målet" -> the place to move to.
- occupations.currency-item: stacked "av ... av" -> "valutagjenstanden".

Tier counts: 0 / 1 / 0 / 6.
