# az_AZ review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 checks clean (token order, no
letter after a placeholder, protected literals, command tokens); no
Turkish forms or needless Russian loans. Context notes hold:
`resplit-offer` is a completed re-split; the three `closed-*` timers are
distinct; the dragon fillers fit "yeni standart {setting} sayını"
("sarmaşıq" in the climbing-plant sense); `occupations.*` fit
`finish-editing`; both `updated-detailed` templates work.

KEY: roulette.category-straight-up
TIER: 1
ISSUE: "Tək rəqəm" reads as "odd digit"; "Tək" is the Odd bet in the same table.
SUGGEST: "Bir nömrə"

KEY: mines.rebet-off-reset
TIER: 2
ISSUE: "bağlıdır" (state) vs "söndürüldü" in the other rebet-off messages.
SUGGEST: "&c“Mərci təkrarla” söndürüldü, mərc 0-a sıfırlandı."

KEY: blackjack.already-seated
TIER: 3
ISSUE: The playful English line became flat.
SUGGEST: "&cDostum, siz onsuz da oturmusunuz"

KEY: blackjack.stood
TIER: 3
ISSUE: Quoted definite object without the accusative sounds unnatural.
SUGGEST: "&9Dayandınız."

KEY: blackjack.round-summary-hand-lost
TIER: 3
ISSUE: "uduzma" is awkward; roulette.paid-loss uses "itki".
SUGGEST: "&cƏl {number}: itki -{amount}"

KEY: blackjack.wager-not-insurance-compatible
TIER: 3
ISSUE: "yoxlayın" means "check", not "try".
SUGGEST: "&cBu mərci sığorta üçün bərabər yarıya bölmək olmur -- cüt məbləğ seçin."

KEY: blackjack.turn-started-while-away
TIER: 3
ISSUE: "{seconds} san.!" is an awkward fragment.
SUGGEST: "&aBlekcekdə növbəniz başladı! {seconds} saniyəniz var!"

KEY: blackjack.leave-exit
TIER: 3
ISSUE: Mixes an imperative and a noun on one button.
SUGGEST: "Stuldan qalx/Çıx"

KEY: blackjack-settings.insurance-desc-2
TIER: 3
ISSUE: Does not say clearly that insurance costs half the wager.
SUGGEST: "&7Qiyməti mərcinizin yarısıdır. Dilerin gizli kartı 10 dəyərində olarsa (blekcek), 2:1 ödəyir."

KEY: admin.chip-size-updated-detailed
TIER: 3
ISSUE: Drops "updated"; siblings use "yeni".
SUGGEST: "&a№{index} fişkanın yeni dəyəri: &e{size}&a."

KEY: admin.slots-rtp-lore
TIER: 3
ISSUE: "Cari oyunçuya qayıdış" can read as return to the current player.
SUGGEST: "&7Oyunçuya qayıdış (cari): &a{rtp}"

KEY: mob-selection.location-not-found
TIER: 3
ISSUE: "yerini almaq" collides with the idiom "to replace someone".
SUGGEST: "&cDilerin yerini müəyyən etmək mümkün olmadı."

KEY: mob-selection.illusioner
TIER: 3
ISSUE: "İllüziyaçı" is a coinage; the standard word is "illüzionist".
SUGGEST: "İllüzionist"

KEY: jockey-options.edit-collar-color
TIER: 3
ISSUE: "Boyunluq" is a generic neck item; an animal collar is "xalta".
SUGGEST: "Xaltanın rəngini dəyiş: {mob}"

KEY: dealer.game-set
TIER: 3
ISSUE: "oyunu indi belədir" is an awkward calque.
SUGGEST: "&a“&e{dealer}&a” dilerinə oyun təyin edildi: &e{game}&a."

KEY: commands.help-hint
TIER: 3
ISSUE: "istifadə etmək" needs an ablative complement; add "əmrindən".
SUGGEST: "&dKömək üçün &b/ncc help&d əmrindən istifadə edin."

KEY: commands.unknown
TIER: 3
ISSUE: Same.
SUGGEST: "&cNaməlum əmr. Kömək üçün &b/ncc help&c əmrindən istifadə edin."

KEY: admin.positive-number
TIER: 3
ISSUE: "müsbət rəqəm" (positive digit); the standard term is "müsbət ədəd".
SUGGEST: "Zəhmət olmasa, müsbət ədəd daxil edin."

KEY: blackjack-settings.number-range
TIER: 3
ISSUE: "rəqəm" for a multi-digit value; literary usage is "ədəd".
SUGGEST: "Zəhmət olmasa, {min} ilə {max} arasında ədəd daxil edin."

## Terminology notes

"Disabled" as "söndürülüb" vs toggle "Açıq/Bağlı" (acceptable, except the
rebet-off message flagged above); "rəqəm" (digit) vs "ədəd" (number) --
"ədəd" recommended throughout; loss "uduzma" vs "itki"; "Dəstə" for a deck
consistent (optional "kart dəstəsi").

Counts: Tier 0: 0 | Tier 1: 1 | Tier 2: 1 | Tier 3: 17 | Total: 19

## Disposition (translator)

All 19 applied as suggested. Follow-up from the terminology notes: the
five `*-settings.prompt-number` keys and `blackjack-settings.invalid-number-format`
now use "ədəd" too.
