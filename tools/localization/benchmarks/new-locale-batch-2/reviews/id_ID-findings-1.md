# id_ID review -- packet-1 (592 records), isolated reviewer 1

Scripted token-order check (placeholders, & codes, \n, /ncc tokens): no
mismatch. -1, &foff&e, {overwrite}, {cancel}, {unlimited} kept; no
placeholder+letter joins. Glossary followed (bandar vs Pemain/Bankir,
keunggulan kasino, keping, pengali, deret vs beruntun, Taruhan Ulang, kamu).
All context notes respected (player-turn, cashout notice future, chain-win as
current pot, max chain as round count, overflow bank/drop, server override,
vines, 2,5%).

Tier 0: none. Tier 1: none. Tier 2: none.

Tier 3:
- preferences.overflow.bank: "Simpan Untukku" -> "Simpan untukku".
- payout.bank-claimed: "Mengambil ..." reads as in progress; SUGGEST
  "&aBerhasil mengambil {amount} kemenangan yang disimpan."
- coin-flip.leave / rock-paper-scissors.leave: "Pergi" odd as a button;
  SUGGEST "&f&oTinggalkan Kursi".
- coin-flip.chain-win / rock-paper-scissors.chain-win: bare "Beruntun:" label;
  SUGGEST "Kemenangan beruntun: {streak}".
- dragon-descent.begin: "Penurunanmu" = decline; SUGGEST "Mulai Turun?"
- dragon-descent.dealer-cannot-cover: "Penurunan ini" -> "Permainan ini".
- dragon-descent.title / game-options.dragon-descent (optional): "Penurunan
  Naga" reads as "Dragon Decline"; SUGGEST keep "Dragon Descent" or
  "Turun ke Sarang Naga".
- slots.auto-settings-reset: SUGGEST "&cAtur Ulang Pengaturan Putar Otomatis".
- mines.all-in (low confidence): imperative; SUGGEST "&cSemua dipertaruhkan."

Terminology notes: bare "Beruntun" vs "kemenangan beruntun"; "Pengaturan
Otomatis" vs "Pengaturan Putar Otomatis".

Counts: Tier 0 = 0, Tier 1 = 0, Tier 2 = 0, Tier 3 = 12.
