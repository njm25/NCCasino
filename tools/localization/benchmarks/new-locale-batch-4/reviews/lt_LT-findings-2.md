# lt_LT review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Mechanical checks clean (token order,
protected literals, command tokens, `-1`, balanced „“). No gendered text
toward the player ("Suaugęs" describes a mob). Numeral agreement safe
(labels, fixed "30 tikų"). Context points verified: accusative
`occupations.*`, lowercase genitive-plural Dragon fillers, chip size as
value, max chain as round count, distinct `closed-*` messages,
completed-action `resplit-offer`, distinct Same Rank / Same Value,
`player-only` excludes the console.

KEY: roulette.bet-placed
TIER: 3
ISSUE: Colon straight after the preposition "ant"; {bet} is a label, so frame it.
SUGGEST: "&6Pastatyta {amount} ant laukelio „{bet}“"

KEY: blackjack.other-betting-circle
TIER: 3
ISSUE: "Statymų ratas" suggests a wheel (collides with "Ruletės ratas"); the slot is "statymo vieta" elsewhere.
SUGGEST: "Statymo vieta: {name}"

KEY: blackjack.cannot-bet-other
TIER: 3
ISSUE: "statyti už … vietą" means betting on the spot as an outcome.
SUGGEST: "&cNegali statyti kito žaidėjo vietoje."

KEY: blackjack.starts-in
TIER: 3
ISSUE: Colon after the preposition "po".
SUGGEST: "Iki žaidimo pradžios: {seconds}"

KEY: blackjack.insufficient-double-down
TIER: 3
ISSUE: Missing dative object of purpose.
SUGGEST: "&cNepakanka pinigų statymui dvigubinti."

KEY: blackjack.wager-per-hand-title
TIER: 3
ISSUE: Count label should be genitive plural; "už ranką" clearer.
SUGGEST: "Statymas: {amount} už ranką, rankų: {count}"

KEY: blackjack.insurance-prompt
TIER: 3
ISSUE: Bare "Drausti?" also means "to forbid".
SUGGEST: "&eDalytojas rodo tūzą. Imti draudimą?"

KEY: blackjack.closed-before-turn
TIER: 3
ISSUE: Past "nesibaigė" should be future.
SUGGEST: "&eTuri grįžti, kol nesibaigs tavo ėjimo laikas, kitaip tavo rankai bus automatiškai pasirinkta „Sustoti“."

KEY: mines.rebet-broke
TIER: 3
ISSUE: Playful English tone lost.
SUGGEST: "&cKišenė per plona statymui pakartoti."

KEY: mob-selection.no-variants
TIER: 3
ISSUE: Hanging infinitive of purpose.
SUGGEST: "&cPadaras &e{mob}&c neturi variantų, kuriuos būtų galima perjungti."

KEY: blackjack-settings.insurance-timeout-desc
TIER: 3
ISSUE: "kol + present" reads as "while", not "before".
SUGGEST: "&7Sekundės, per kurias žaidėjai turi apsispręsti, kol nebus automatiškai pasirinkta „Ne“."

KEY: blackjack-settings.turn-timer-timeout-desc-1
TIER: 3
ISSUE: Same.
SUGGEST: "&7Sekundės, per kurias žaidėjas turi atlikti veiksmą, kol nebus automatiškai pasirinkta „Sustoti“."

KEY: blackjack-settings.toggle-aces-hit
TIER: 3
ISSUE: "ant suskaidytų tūzų" calque of "on split aces".
SUGGEST: "Įjungti / išjungti „Imti“ suskaidžius tūzus"

KEY: blackjack-settings.toggle-aces-double
TIER: 3
ISSUE: Same.
SUGGEST: "Įjungti / išjungti dvigubinimą suskaidžius tūzus"

KEY: blackjack-settings.aces-hit-updated
TIER: 3
ISSUE: Same; keep aligned with its toggle.
SUGGEST: "&e„Imti“ suskaidžius tūzus: {value}"

KEY: blackjack-settings.aces-double-updated
TIER: 3
ISSUE: Same.
SUGGEST: "&eDvigubinimas suskaidžius tūzus: {value}"

KEY: admin.dealer-not-found-chat
TIER: 3
ISSUE: Hard-to-parse genitive chain.
SUGGEST: "&cNepavyko rasti dalytojo, kuriam skirtas administratoriaus meniu atsakymas pokalbyje."

KEY: admin.move-failed
TIER: 3
ISSUE: "įsikelti" = to move in; "did not load" is "nebuvo įkelta".
SUGGEST: "&cDalytojo perkelti nepavyko. Pasaulio dalis (chunk) nebuvo įkelta."

KEY: admin.move-failed-detailed
TIER: 3
ISSUE: Same.
SUGGEST: "&cDalytojo perkelti nepavyko. Pasaulio dalis (chunk) nebuvo įkelta net po 30 tikų."

KEY: commands.help-hint
TIER: 3
ISSUE: "Pagalbos rasi su" is a "with" calque.
SUGGEST: "&dNaudok &b/ncc help&d, jei reikia pagalbos."

KEY: commands.unknown
TIER: 3
ISSUE: Same.
SUGGEST: "&cNežinoma komanda. Naudok &b/ncc help&c, jei reikia pagalbos."

## Terminology notes

No Tier 2 inconsistencies. Game names appear both as quoted titles in
welcomes and as common nouns in settings (both valid). "Max chain rounds"
is "didžiausias raundų skaičius serijoje" in settings and "Daugiausia
raundų serijoje" in lore (both round counts). The "atnaujinta į:" colon
pattern is used consistently and not flagged.

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 0 | Tier 3: 21 (total 21)

## Disposition (translator)

All 21 applied. Consistency follow-ups carrying packet 1's "nebepriimami":
`roulette.bets-closed` "STATYMAI NEBEPRIIMAMI", `roulette.bets-closed-final`
"STATYMAI NEBEPRIIMAMI, KAS PADARYTA, TAS PADARYTA" and
`roulette.bets-close-in` "STATYMAI PRIIMAMI DAR {seconds} S!".
EOF
