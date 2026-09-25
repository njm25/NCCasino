# ms_MY review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Scripted checks clean (tokens, protected
literals, `/ncc`, `-1`); no Indonesian vocabulary (hits were inside ruang /
peluang / kehabisan). Semantic points handled: completed-action
`resplit-offer`, distinct `closed-*`, round-count max chain, chip size as
`nilai cip`, `player-only`, distinct Same Rank / Same Value, fitting
occupation and Dragon fillers.

KEY: mines.dealer-cannot-cover
TIER: 2
ISSUE: "tunai" (cash) drops the cash-out concept; use "penunaian".
SUGGEST: "&cPapan ini tidak dapat menampung penunaian sebesar itu sekarang. Cuba lagi nanti atau kurangkan taruhan anda."

KEY: mines.rebet-placed
TIER: 2
ISSUE: "Taruhan ulang" reverses "Ulang Taruhan".
SUGGEST: "&dUlang Taruhan sebanyak {amount} dibuat"

KEY: roulette.refund-exit
TIER: 3
ISSUE: "Pulangkan" has no object.
SUGGEST: "Pulangkan Taruhan dan/atau Keluar"

KEY: roulette.exit-refund
TIER: 3
ISSUE: Same.
SUGGEST: "KELUAR (Pulangkan Taruhan dan Keluar)"

KEY: roulette.bets-closed-final
TIER: 3
ISSUE: Literal calque; the idiom is "yang sudah itu sudahlah".
SUGGEST: "TARUHAN DITUTUP, YANG SUDAH ITU SUDAHLAH"

KEY: roulette.all-in-ready
TIER: 3
ISSUE: Button label stacked as a noun modifier.
SUGGEST: "&aSedia untuk mempertaruhkan semua sebanyak {amount}."

KEY: roulette.bet-placed
TIER: 3
ISSUE: "Letak …" reads as an order; the message confirms a placed bet.
SUGGEST: "&6{amount} diletakkan pada {bet}"

KEY: blackjack.must-sit-all-in
TIER: 3
ISSUE: Capitalized button label where a verb belongs.
SUGGEST: "&cAnda mesti duduk untuk mempertaruhkan semua."

KEY: blackjack.sat-down
TIER: 3
ISSUE: "sudah duduk" overlaps the already-seated error.
SUGGEST: "&aAnda telah duduk."

KEY: blackjack.insurance-lost
TIER: 3
ISSUE: "hilang" = went missing; a lost side bet is "hangus".
SUGGEST: "&cInsurans hangus."

KEY: blackjack.closed-during-insurance
TIER: 3
ISSUE: "ia akan menjadi Tidak" calques "resolve to No"; "ia" has no referent.
SUGGEST: "&cAnda mesti kembali dan membuat keputusan insurans dalam {seconds} s atau jawapan Tidak akan dipilih secara automatik."

KEY: blackjack-settings.insurance-timeout-desc
TIER: 3
ISSUE: Word-for-word "Saat yang pemain ada untuk…" calque.
SUGGEST: "&7Tempoh (saat) untuk pemain membuat keputusan sebelum jawapan Tidak dipilih secara automatik."

KEY: blackjack-settings.turn-timer-timeout-desc-1
TIER: 3
ISSUE: Same.
SUGGEST: "&7Tempoh (saat) untuk pemain bertindak sebelum Berhenti secara automatik."

KEY: roulette-settings.invalid-settings-option
TIER: 3
ISSUE: Lowercase game name vs siblings.
SUGGEST: "&cPilihan tetapan Rolet tidak sah."

KEY: roulette-settings.dealer-not-found
TIER: 3
ISSUE: Same.
SUGGEST: "&cTidak dapat menemui pengendali tetapan Rolet."

KEY: baccarat-settings.invalid-settings-option
TIER: 3
ISSUE: Same.
SUGGEST: "&cPilihan tetapan Bakarat tidak sah."

KEY: baccarat-settings.dealer-not-found
TIER: 3
ISSUE: Same.
SUGGEST: "&cTidak dapat menemui pengendali tetapan Bakarat."

KEY: blackjack-settings.invalid-settings-option
TIER: 3
ISSUE: Same.
SUGGEST: "&cPilihan tetapan Blackjack tidak sah."

KEY: blackjack-settings.dealer-not-found
TIER: 3
ISSUE: Same.
SUGGEST: "&cTidak dapat menemui pengendali tetapan Blackjack."

## Terminology notes

Rebet "Ulang Taruhan" vs "Taruhan ulang" and cash-out "Tunaikan" vs
"tunai" fixed; game names capitalized in all settings error keys.
Otherwise consistent (pengendali, taruhan, Pertaruhkan Semua, cip / nilai
cip, pemasa, sembang, insurans, Ambil / Berhenti / Gandakan / Pisah, kotak
kad, Sat, tumbuhan menjalar, penunggang, makhluk, tunggangan / penumpang,
timbunan, dek, tetapan, pentadbir). `*.updated-detailed` template defects,
the "{seconds} s" spacing and admin-facing "Chunk" not scored.

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 2 | Tier 3: 17

## Disposition (translator)

All 19 applied as suggested.
