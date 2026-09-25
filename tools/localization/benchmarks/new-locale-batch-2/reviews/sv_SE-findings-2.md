# sv_SE review -- packet-2 (592 records), isolated reviewer 2

Scripted token check: placeholders, & codes, \n, /ncc tokens, protected
literals in order and unchanged; no placeholder+letter joins beyond EN. Glossary
consistent; "du" throughout. Tier 0: none. Tier 1: none.

Tier 2:
- admin.slots-rtp-lore: "återbetalning" also means refund in this catalog;
  SUGGEST "återbetalningsprocent".
- blackjack.shoe-exhausted-refunded / start-transition-failed-refunded,
  blackjack-settings.edit-timer-desc-2: "runda" vs "omgång" in Blackjack;
  desc-2 "den" ambiguous.

Tier 3:
- blackjack.starts-in: "börjar om:" reads as "restarts"; SUGGEST "Tid till
  start: {seconds}".
- blackjack.wager-transaction-failed: "drogs" = drew a card; SUGGEST
  "du har inte debiterats".
- dragon-settings / coin-flip-settings / rock-paper-scissors-settings .title:
  double "för"; SUGGEST "Inställningar för X ({dealer})".
- complex-variant.cycle-* (7 keys): "Bläddra" is intransitive; SUGGEST "Byt".
- mob-selection.no-variants: "bläddra mellan".
- *-settings.prompt-max-chain: "max antalet" -> "maxantalet".
- blackjack-settings.max-hands-desc: "Flest händer" calque.
- blackjack-settings.toggle-split-matching / split-matching-updated: add
  "Matchningsregel".
- blackjack-settings.toggle-aces-hit: "Växla kort" = swap cards.
- blackjack-settings.toggle-aces-resplit / aces-resplit-updated: "omdelning"
  = re-deal; SUGGEST "Dela ess igen".

Terminology notes: omgång vs runda; RTP vs refund; game-name capitalization
in running text (minor); Draknedstigningen definite form (verify).

Counts: Tier 0 = 0, Tier 1 = 0, Tier 2 = 2 (4 keys), Tier 3 = 10 (22 keys).
