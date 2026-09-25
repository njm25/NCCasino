# it_IT review -- packet-1 (592 records), isolated reviewer 1

Script over the packet (placeholders, & codes, \n, /ncc tokens, protected
literals, -1/off): only false positives (English "off" in prose; `}×` joins
also in EN). Informal tu throughout; no gendered player forms.
Tier 0: none. Tier 1: none (player-turn, cashout notice tense, chain-win as
current pot, max chain vs max pot, payout contexts, overflow drop, override,
Banco vs Croupier, vines, variance vs RTP, run vs streak, demo wording all
verified).

Tier 2:
- slots.auto-settings-reset: "impostazioni automatiche" vs "giri automatici".
  SUGGEST "&cRipristina le impostazioni dei giri automatici"

Tier 3:
- betting/baccarat/mines .rebet-off: "DISATTIVA" reads as the Deactivate
  action; SUGGEST "DISATTIVATA".
- coin-flip / rock-paper-scissors / baccarat .already-seated: "Hai già un
  posto" unidiomatic; SUGGEST "Hai già preso posto."
- baccarat.must-be-seated: SUGGEST "&cDevi sederti per puntare."
- payout.context-committed-result (and context-server-restart): "a {game}"
  -> "nel gioco {game}".
- payout.wager-blocked: {amount} attaches to "giocare di nuovo"; reorder.
- slots.payout-banked: "Parte della vincita" may be the whole win.
- slots.last-result-not-yet-spun: "ancora nessun giro".
- slots.last-result-won: "vinti {amount}" agreement; "vincita di {amount}".
- slots.auto-rule-profit / auto-rule-loss: awkward; SUGGEST "quando sei in
  attivo di" / "prima di perdere più di".
- slots.auto-big-win: missing "di".
- slots.auto-profit-target-description / -set: "quando, da quando" clash.
- slots.rail-spin-controls: "concludere prima" ambiguous -> "subito".
- slots-settings.house-edge-timed-out: "per inserire il vantaggio".
- game-options.created-at: "alle coordinate x: ...".
- mines.no-funds: "Non hai fondi."
- mines.refund-exit: "Rimborso/Uscita".

Terminology notes: Auto Spin (above); big-win multiplier preposition;
"illimitato" vs "nessuno" (minor); on/off state words mixed; wording around
{game} mixed. Everything else consistent.

Counts: Tier 0 = 0, Tier 1 = 0, Tier 2 = 1, Tier 3 = 22.
