# es_MX review -- packet-2 (592 records), isolated reviewer 2

Script token check: placeholders, & codes, \n in EN order; no
placeholder+letter joins; protected literals, /ncc commands and -1 intact.
No Spain-only forms; no player-gendered text; {game} without article;
occupations fit "termina de editar {occupation}"; glossary consistent;
closed-* distinct; max chain a round count; player-only. Tier 0/2: none.

Tier 1 (applied; verified in code and added to the guide's §C registry):
- blackjack.resplit-offer: "¡Divide otra vez!" is an instruction, but
  BlackjackInventory sends the key after a re-split has been performed
  (`wasResplit ? "blackjack.resplit-offer" : "blackjack.split-success"`) ->
  "¡Mano dividida otra vez!".

Tier 3 (11, applied):
- roulette.hit-green "WOW" -> "ÓRALE"; all-in-ready; no-wager (the amount,
  not the bet, is missing); wrong-game "no maneja".
- blackjack.currently-selected "Selección actual"; insurance-paid colon;
  wager-not-insurance-compatible ("partir exactamente", not the Split verb);
  dealer-turn-capitalized.
- blackjack-settings.match-same-rank / split-matching-desc-1: "rango" is
  not the card-rank word -> "Misma carta".
- dragon-settings.vines-less-columns: compare numbers, not vines.

Tier counts: 0 / 1 / 0 / 11.
