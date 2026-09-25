# fi_FI review findings -- packet 1 (592 records)

Counts: Tier 0 = 0, Tier 1 = 3, Tier 2 = 6, Tier 3 = 25.

Tier 1 (fixed):
- coin-flip.chain-win / rock-paper-scissors.chain-win -- "niin voitto on {amount}" states a certain win; next flip can lose -> "niin voit voittaa".
- slots.rail-profiles-controls-empty -- "Klikkaa alta vasemmalla" reads as a location, not left-click.

Tier 2 (fixed): cards.ranks five..ten colloquial (Vitonen, Kutonen, Seiska, Kasi, Ysi, Kymppi) mixed with standard forms -> all ranks use standard numerals.

Tier 3 (applied unless noted): preferences.messages.minimal; betting.inventory-full; payout.delivered (neutral "Hyvitetty"); payout.bank-still-blocked; "ennen kuin" commas (payout.wager-blocked, slots.auto-rule-loss, auto-loss-limit-description, auto-loss-limit-set); dragon-descent.sweep; test-game.round-finished, server-won, server-lost; game-options.created, created-at; slots.dealer-wager-too-large, auto-spin-shift-left-back, auto-big-win-description, rail-wager-total, rail-spin-controls; slots-settings.house-edge-timed-out, default-lines-hint, variance-top-line; mines.refund-exit.
Not applied: slots-settings.house-edge-prompt / house-edge-invalid "2,5 %" -> kept; SlotsHouseEdgeInput.parse strips a trailing % and trims, so "2,5 %" is verified parseable and the space is Finnish standard.
Noted, not changed: "klikkaa" (vs standard "napsauta") is used consistently catalog-wide and is the norm in Finnish gaming UIs.
