# eo_UY review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean (token order, protected
literals; the "-1" hit is inside "Stand-on-17"). No placeholder needs an
accusative or plural ending; `resplit-offer`, `turn-timer-timeout-desc-2`,
the `{setting}` / `{occupation}` / `{change}` template slots, the two split
rules and "kartujo" vs "kartaro" all hold.

KEY: admin.rps-max-chain-lore
TIER: 2
ISSUE: Drifts from the glossary term "maksimuma nombro de seriaj raŭndoj" used by the settings buttons and prompts; the max chain is a round count.
APPLIED: "&7Maksimuma nombro de seriaj raŭndoj: &a{value} &7(PvE)"

KEY: admin.rps-max-chain-lore-unbounded
TIER: 2
ISSUE: Drifts from the glossary term "maksimuma nombro de seriaj raŭndoj" used by the settings buttons and prompts; the max chain is a round count. Also "raŭndoj: senlima" number clash.
APPLIED: "&7Maksimuma nombro de seriaj raŭndoj: &asenlima &7(PvE)"

KEY: admin.coin-flip-max-chain-lore
TIER: 2
ISSUE: Drifts from the glossary term "maksimuma nombro de seriaj raŭndoj" used by the settings buttons and prompts; the max chain is a round count.
APPLIED: "&7Maksimuma nombro de seriaj raŭndoj: &a{value} &7(PvE)"

KEY: admin.coin-flip-max-chain-lore-unbounded
TIER: 2
ISSUE: Drifts from the glossary term "maksimuma nombro de seriaj raŭndoj" used by the settings buttons and prompts; the max chain is a round count. Also "raŭndoj: senlima" number clash.
APPLIED: "&7Maksimuma nombro de seriaj raŭndoj: &asenlima &7(PvE)"

KEY: occupations.rps-max-chain
TIER: 2
ISSUE: Drifts from the glossary term "maksimuma nombro de seriaj raŭndoj" used by the settings buttons and prompts; the max chain is a round count. Fills {occupation} in commands.finish-editing.
APPLIED: "la maksimuma nombro de seriaj raŭndoj en Ŝtono, papero, tondilo"

KEY: occupations.coin-flip-max-chain
TIER: 2
ISSUE: Drifts from the glossary term "maksimuma nombro de seriaj raŭndoj" used by the settings buttons and prompts; the max chain is a round count.
APPLIED: "la maksimuma nombro de seriaj raŭndoj en Monerĵeto"

KEY: mines.rebet-off-reset
TIER: 3
ISSUE: "nuligita al 0" is redundant; "reset to 0" is "restarigita al 0"; comma splice.
APPLIED: "&c“Reveti” estas malŝaltita; la veto estis restarigita al 0."

KEY: blackjack.starts-in
TIER: 3
ISSUE: A colon directly after the preposition "post" is ungrammatical.
APPLIED: "Ĝis la komenco de la ludo: {seconds}"

KEY: mob-selection.age-unsupported
TIER: 3
ISSUE: "ido kaj plenkreska" joins a noun and an adjective.
APPLIED: "&cĈi tiu estaĵo ne subtenas alternadon inter ido kaj plenkreskulo."

KEY: coin-flip-settings.toggle-mode-switching
TIER: 3
ISSUE: A declarative sentence ("Players may switch the mode") next to the state reads as a contradiction when the value is "Malŝaltita".
APPLIED: "Reĝimŝanĝo fare de ludantoj"

KEY: rock-paper-scissors-settings.toggle-mode-switching
TIER: 3
ISSUE: A declarative sentence ("Players may switch the mode") next to the state reads as a contradiction when the value is "Malŝaltita".
APPLIED: "Reĝimŝanĝo fare de ludantoj"

KEY: admin.rps-mode-switching-lore
TIER: 3
ISSUE: A declarative sentence ("Players may switch the mode") next to the state reads as a contradiction when the value is "Malŝaltita".
APPLIED: "&7Reĝimŝanĝo fare de ludantoj: &a{value}"

KEY: admin.coin-flip-mode-switching-lore
TIER: 3
ISSUE: A declarative sentence ("Players may switch the mode") next to the state reads as a contradiction when the value is "Malŝaltita".
APPLIED: "&7Reĝimŝanĝo fare de ludantoj: &a{value}"

KEY: blackjack-settings.split-matching-desc-1
TIER: 3
ISSUE: Says the ranks are split; it is the cards of the same rank.
APPLIED: "&7Sama rango: nur kartoj de identa rango estas divideblaj (Reĝo-Reĝo)."

KEY: blackjack-settings.split-matching-desc-2
TIER: 3
ISSUE: "valoraj 10" (valuable) cannot take a measure complement; "kun valoro 10".
APPLIED: "&7Sama valoro: ankaŭ iuj ajn du kartoj kun valoro 10 estas divideblaj (Reĝo-Damo)."

KEY: blackjack-settings.toggle-aces-hit
TIER: 3
ISSUE: "Ŝalti/malŝalti" took a bare infinitive object; siblings use nouns.
APPLIED: "Ŝalti/malŝalti prenon de karto sur dividitaj asoj"

KEY: blackjack-settings.aces-hit-updated
TIER: 3
ISSUE: Infinitive status label; siblings use nouns ("Duobligo...", "Redivido...").
APPLIED: "&ePreno de karto sur dividitaj asoj: {value}"

KEY: admin.player-menu
TIER: 3
ISSUE: "ludanta menuo" is the active participle of ludi ("a playing menu"), not "player menu".
APPLIED: "Ludantmenuo"

KEY: admin.player-menu-no-permission
TIER: 3
ISSUE: "ludanta menuo" is the active participle of ludi ("a playing menu"), not "player menu".
APPLIED: "&cVi ne rajtas uzi la ludantmenuon."

KEY: commands.invalid-page
TIER: 3
ISSUE: "Montrante paĝon 1." is a subjectless -ing fragment.
APPLIED: "&cNevalida paĝonumero. Montriĝas paĝo 1."

KEY: mob-settings.click-customize-lore
TIER: 3
ISSUE: "personecigi" read as "give a personality to"; suggested "Klaku por adapti".
DECLINED: "personecigi" is the established software term for "customize"; kept "Klaku por personecigi".

Tier 0: 0 · Tier 1: 0 · Tier 2: 6 · Tier 3: 15

## Disposition (translator)

20 applied, one declined: `mob-settings.click-customize-lore` keeps
"Klaku por personecigi", because "personecigi" is the established
Esperanto software term for "customize" (e.g. Firefox "Personecigi
ilaron").
