# it_IT review -- packet-2 (592 records), isolated reviewer 2

Mechanical check: token order, protected literals, /ncc tokens and
placeholder+letter joins all fine; no English residue (10 identical values
are glyph/placeholder-only or accepted loanwords Timer, Mob, No, Blackjack).
Tier 0: none. Tier 1: none.

Tier 2:
- roulette.all-in-ready: "Puntata totale" vs "Punta tutto" elsewhere.
  SUGGEST "&aPunti tutto: puntata di {amount} pronta da piazzare."

Tier 3:
- roulette.welcome / blackjack.welcome: "nel gioco X" stiff; SUGGEST
  "alla Roulette" / "al Blackjack".
- roulette.no-funds / blackjack.no-funds: "Nessun fondo" -> "Non hai fondi."
- roulette.bet-placed: "Puntati {amount}" cannot agree with {amount}.
  SUGGEST "&6Hai puntato {amount} su {bet}"
- blackjack.shoe-exhausted-refunded / start-transition-failed-refunded /
  table-reset-refunded: "rimborsati {amount}" agreement; SUGGEST
  "rimborso di {amount}".
- blackjack.stood: identical to the button; SUGGEST "&9Hai scelto di stare."
- blackjack.closed-during-turn / closed-before-turn: "la tua mano starà";
  SUGGEST "per la tua mano verrà scelto automaticamente Stai".
- blackjack-settings.prompt-max-hands / invalid-max-hands: "pari o superiore"
  readable as "even"; SUGGEST "un numero intero da 2 in su".
- blackjack-settings.enabled / disabled: masculine "Attivo" fills feminine
  labels (Assicurazione: {value}); SUGGEST "&aSì" / "&cNo".
- admin.slots-rtp-lore: "Ritorno al giocatore attuale" -> "the current
  player"; SUGGEST "&7RTP attuale: &a{rtp}".
- commands.help-hint / unknown: "per l'aiuto" -> "per ricevere aiuto".
- blackjack-settings.max-hands-desc: "tenere insieme" -> "avere
  contemporaneamente".
- coin-flip-settings / rock-paper-scissors-settings .max-chain-current:
  vincita/vittoria redundancy; SUGGEST "paga {multiplier}x in caso di vittoria".

Terminology notes: all-in (above); enabled/disabled uses three forms
(Attivo/Disattivo, ATTIVA/DISATTIVA -- readable as imperative on a toggle --,
Disattivato); rebet "Ripetizione disattivata" alone vague; game-name
capitalisation mixed but defensible.

Counts: Tier 0 = 0, Tier 1 = 0, Tier 2 = 1, Tier 3 = 21.
