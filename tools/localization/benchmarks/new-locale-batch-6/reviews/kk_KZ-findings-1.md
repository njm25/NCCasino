# kk_KZ review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean (script-checked order of
placeholders, `&` codes and `\n`; `/ncc claim`, `-1`, `off` intact; no
suffix on any placeholder). Context notes hold: `seat-unavailable` as an
empty locked chair, chain-win `{amount}` as the current pot ("Ағымдағы
банк"), vines "шырмауық", "серия" only for the PvE streak (the auto-spin
batch is "басталғаннан бергі", a slots run "тізбек"). Style note, not
scored: the verbless "… үшін сол жақ / оң жақ батырма" mouse hints.

KEY: coin-flip.leave
TIER: 1
ISSUE: "Тұру" means "stand up" or "stay"; the door closes the game (closeInventory) and collides with the chair action "Орындықтан тұру". Other exits use "Шығу".
SUGGEST: "&f&oШығу"

KEY: rock-paper-scissors.leave
TIER: 1
ISSUE: Same as coin-flip.leave.
SUGGEST: "&f&oШығу"

KEY: slots.rail-exit-session
TIER: 1
ISSUE: "бұрын ұтылған барлық сома" uses "ұтылу" (to lose, as in payout.lost), so the line says previously LOST sums are paid.
SUGGEST: "&7Осымен мұндағы сеансыңыз аяқталады; бұған дейін ұтып алған барлық сомаңыз бәрібір төленеді."

KEY: slots-settings.house-edge-hint
TIER: 1
ISSUE: The мен/бен/пен allomorph depends on the sound before it, so it cannot follow a placeholder; after "…%" ("пайыз") "мен" is wrong for every value.
SUGGEST: "&7Басыңыз да, чатқа {min}–{max} аралығындағы кез келген пайызды жазыңыз."

KEY: slots-settings.house-edge-prompt
TIER: 1
ISSUE: Same harmony problem; the "2,5%" example with a trailing sign is correct.
SUGGEST: "&eЧатқа {min}–{max} аралығындағы казино артықшылығын жазыңыз (мысалы, 2,5%)."

KEY: slots-settings.house-edge-invalid
TIER: 1
ISSUE: Same harmony problem; also a missing comma after "мысалы".
SUGGEST: "&c{min}–{max} аралығындағы пайызды енгізіңіз, мысалы, 2,5%."

KEY: cards.ranks.jack
TIER: 3
ISSUE: "Валет" is the Russian term while ace and suits use traditional Kazakh terms; traditional jack is "балта".
SUGGEST: "Балта"
DISPOSITION: declined (see below)

KEY: cards.ranks.queen
TIER: 3
ISSUE: "Дама" is the Russian term; traditional queen is "мәтке".
SUGGEST: "Мәтке"
DISPOSITION: declined (see below)

KEY: coin-flip.dealer-cannot-cover
TIER: 3
ISSUE: "сериядан түскен ... ұтысты" is an awkward calque for "chain win".
SUGGEST: "&cБұл дилер қазір серия бойынша жиналған мұндай үлкен ұтысты төлей алмайды. Ұтысты алыңыз немесе кейінірек қайталаңыз."

KEY: rock-paper-scissors.dealer-cannot-cover
TIER: 3
ISSUE: Same as coin-flip.dealer-cannot-cover.
SUGGEST: "&cБұл дилер қазір серия бойынша жиналған мұндай үлкен ұтысты төлей алмайды. Ұтысты алыңыз немесе кейінірек қайталаңыз."

KEY: coin-flip.round-started
TIER: 3
ISSUE: Bare "сәттілік!" is a colloquial calque of "Удачи!".
SUGGEST: "&cРаунд басталды, сәттілік тілейміз!"

KEY: rock-paper-scissors.round-started
TIER: 3
ISSUE: Same as coin-flip.round-started.
SUGGEST: "&cРаунд басталды, сәттілік тілейміз!"

KEY: dragon-descent.dealer-cannot-cover
TIER: 3
ISSUE: "Бұл түсу ... төлей алмайды" ("this descending cannot pay") reads oddly; the subject should be the game.
SUGGEST: "&cБұл ойын қазір мұндай үлкен банкті төлей алмайды. Кейінірек қайталаңыз немесе бәсіңізді азайтыңыз."

KEY: slots.rail-paylines-cost
TIER: 3
ISSUE: "шығын" means loss in the Auto Spin strings, so "more lines, more шығын" reads as "more loss".
SUGGEST: "&7Әр белсенді сызыққа бөлек бәс тігіледі, сондықтан сызық көп болса, жалпы бәс те көп болады."

KEY: slots.guide-volatility-tradeoff
TIER: 3
ISSUE: Two nested "бірақ" and a heavy genitive chain make it hard to parse.
SUGGEST: "&7Барабандар көбейген сайын қысқа тізбектердің жиі әрі шағын ұтыстарының орнын ұзын тізбектердің сирек әрі ірі джекпоттары басады."

KEY: game-options.reset-confirmation
TIER: 3
ISSUE: "…қайтару керек пе?" is a calque; a confirmation addresses the user.
SUGGEST: "Конфигурацияны әдепкі күйге қайтарасыз ба?"

KEY: slots.modal-view-locked
TIER: 3
ISSUE: "басқару" alone means management; the UI term is "басқару элементі".
SUGGEST: "&8Бұл басқару элементін пайдалану үшін барабандарға оралыңыз."

KEY: slots.paylines-description
TIER: 3
ISSUE: A noun subject with a noun predicate takes a dash in Kazakh punctuation.
SUGGEST: "&7Әр белсенді сызық — ұтыс әкеле алатын таңбалар өрнегі."

KEY: slots.profile-name-illegal-characters
TIER: 3
ISSUE: "астын сызу белгілері" means underlining marks; `_` is "астыңғы сызық".
SUGGEST: "&cПрофиль атауында тек әріптер, цифрлар, бос орындар, дефистер және астыңғы сызықтар болуы мүмкін."

KEY: betting.inventory-full
TIER: 3
ISSUE: "Орын жоқ: {amount}" drops "for" and the inventory ("қоржын", used by every other overflow string).
SUGGEST: "&cҚоржында {amount} үшін орын жоқ; жаныңызға тасталады."

KEY: language-menu.selected
TIER: 3
ISSUE: "Таңдалды" is an event; the state label needs the participle.
SUGGEST: "&aТаңдалған"

Tier 0: 0 · Tier 1: 6 · Tier 2: 0 · Tier 3: 15

## Disposition (translator)

19 applied. Declined: `cards.ranks.jack` / `cards.ranks.queen`. The
catalog keeps "Король", and the split-matching labels read "К-Д", so
changing only jack and queen would make a new mix. The Russian-origin
court-card names are also in wide everyday Kazakh use. Left for
native-speaker review. Follow-ups in self-review: `mob-selection.reset-confirmation`
takes the same "қайтарасыз ба?" form, and the mines / roulette /
blackjack `inventory-full` lines take the "Қоржында {amount} үшін орын
жоқ" form.
