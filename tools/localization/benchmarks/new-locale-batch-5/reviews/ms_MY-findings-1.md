# ms_MY review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Scripted token-order check clean; protected
literals and parser words intact; no Indonesian forms; third-person
`player-turn`; `seat-unavailable` "belum tersedia" (not occupied); payout,
overflow, max-chain, chain-win pot, house-edge example (2.5%) and
variance/RTP meanings correct.

KEY: dragon-descent.rebet-placed
TIER: 2
ISSUE: "Taruhan ulang" reverses the rebet term "Ulang Taruhan" used everywhere else.
SUGGEST: "&dUlang Taruhan sebanyak {amount} dibuat."

KEY: slots.win-line-lore
TIER: 2
ISSUE: Clipped "berturut" echoes the PvE streak term; the slots run is "Urutan".
SUGGEST: "&7Urutan {run}: &ax{multiplier}"

KEY: slots.paytable-card-row
TIER: 2
ISSUE: Same; mismatches the "Urutan" header above it.
SUGGEST: "&fUrutan {run} &8• &a{multiplier}× &8• &e{amount}"

KEY: common.return-to
TIER: 3
ISSUE: Colon directly after the preposition "ke".
SUGGEST: "Kembali ke {menu}"

KEY: player-menu.return-to
TIER: 3
ISSUE: Same.
SUGGEST: "&dKembali ke {menu}"

KEY: preferences.overflow.title
TIER: 3
ISSUE: DBP title case keeps "yang" lowercase.
SUGGEST: "&eKemenangan yang Tidak Muat"

KEY: game.welcome
TIER: 3
ISSUE: Stiff workaround; "Selamat datang ke {game}" works for any name and matches mines.welcome.
SUGGEST: "&aSelamat datang ke {game}"

KEY: coin-flip.title
TIER: 3
ISSUE: "Kepala atau Ekor" (heads or tails) does not match the mechanic: the picks are Kiri/Kanan (CoinFlipClient.renderPveSeats); "Lambung Syiling" is neutral and is also inserted into {game}.
SUGGEST: "Lambung Syiling"

KEY: game-options.coin-flip
TIER: 3
ISSUE: Same; keep both names identical.
SUGGEST: "Lambung Syiling"

KEY: coin-flip.leave
TIER: 3
ISSUE: Transitive "Tinggalkan" with no object on the door button.
SUGGEST: "&f&oKeluar"

KEY: rock-paper-scissors.leave
TIER: 3
ISSUE: Same.
SUGGEST: "&f&oKeluar"

KEY: dragon-descent.begin
TIER: 3
ISSUE: "Penurunan" usually means a decrease.
SUGGEST: "Mulakan Perjalanan Turun Anda?"

KEY: dragon-descent.dealer-cannot-cover
TIER: 3
ISSUE: "Penurunan ini" can read as "this decrease".
SUGGEST: "&cPermainan ini tidak dapat menampung pot sebesar itu sekarang. Cuba lagi nanti atau kurangkan taruhan anda."

KEY: slots.payout-blocked-retry
TIER: 3
ISSUE: "cuba bayaran" is ungrammatical.
SUGGEST: "&7Klik untuk mencuba semula pembayaran."

KEY: slots.bet-too-large
TIER: 3
ISSUE: Says the wager itself is spun.
SUGGEST: "&cTaruhan itu terlalu besar untuk digunakan dalam putaran."

KEY: slots.dealer-cannot-cover
TIER: 3
ISSUE: "kurang garisan" is colloquial; "kurangkan garisan".
SUGGEST: "&cMesin ini tidak dapat menampung kemenangan sebesar itu sekarang. Cuba lagi nanti atau kurangkan garisan."

KEY: slots.dealer-wager-too-large
TIER: 3
ISSUE: Same.
SUGGEST: "&cMesin ini tidak menerima taruhan sebesar itu. Cuba taruhan lebih kecil atau kurangkan garisan."

KEY: slots.guide-seeds-ends-run
TIER: 3
ISSUE: Relative clause missing its preposition.
SUGGEST: "&7Biji Benih serta-merta menamatkan mana-mana urutan yang mengandunginya."

KEY: slots.auto-rule-big-win
TIER: 3
ISSUE: Clause has no verb.
SUGGEST: "&7Berhenti apabila pulangan mencapai &f{multiplier}x &7jumlah taruhan."

KEY: slots.rail-reels-tradeoff
TIER: 3
ISSUE: Colloquial "Kurang gelendong"; unbalanced with the second half.
SUGGEST: "&7Lebih sedikit gelendong lebih kerap menang kecil; lebih banyak gelendong membayar lebih besar tetapi lebih jarang."

## Terminology notes

Rebet should be "Ulang Taruhan" everywhere; the slots run is "Urutan",
kept apart from the PvE streak "berturut-turut". Card names Sat / Jek /
Ratu / Raja and suits Lekuk / Wajik / Kelawar / Sped confirmed as valid
Malaysian usage ("Pekak" is the older name for Jack). Other glossary terms
consistent; the auto-spin batch is "sejak ia bermula", never a streak.

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 3 | Tier 3: 17 | Total: 20

## Disposition (translator)

All 20 applied. Follow-up: "Kepala atau Ekor" was also replaced by
"Lambung Syiling" in the Coin Flip settings keys and the occupation filler.
