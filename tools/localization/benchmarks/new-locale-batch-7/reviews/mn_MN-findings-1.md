# mn_MN review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean (script-checked token order,
`/ncc claim`, `-1`, `off`; no case suffix on any placeholder, only digit
markers such as "1-ээс"). No Tier 1: цуврал / дараалал / багц,
суудал / сандал, Банкир / Дилер, "0,95:1" and "2,5%" all hold.

KEY: slots-settings.house-edge-current
TIER: 2
ISSUE: RTP shortened to "тоглогчид олгох хувь"; the glossary form keeps "төлбөрийн".
APPLIED: "&7Одоогийн утга: &e{edge} &7(тоглогчид олгох төлбөрийн хувь: &a{rtp}&7)"

KEY: slots-settings.variance-same-rtp
TIER: 2
ISSUE: RTP rendered a third way ("тохируулсан төлбөрийн хувь"), in the very line that says variance never changes it.
APPLIED: "&7Хэлбэлзлийн бүх түвшинд тоглогчид олгох төлбөрийн хувь тохируулсан хэмжээндээ хэвээр байна: &a{rtp}"

KEY: slots-settings.house-edge-updated
TIER: 3
ISSUE: No predicate ("the machine now percentage given to players"), and the short RTP form.
APPLIED: "&aКазиногийн давуу талыг тохирууллаа: {edge}; машины тоглогчид олгох төлбөрийн хувь одоо {rtp} боллоо."

KEY: mines.all-in
TIER: 3
ISSUE: ь-stem past tense is "тавилаа", not "тавьлаа" (the catalog already has "тавилаа").
APPLIED: "&cБүгдийг тавилаа."

KEY: mines.all-in-with
TIER: 3
ISSUE: Same.
APPLIED: "&aБүгдийг тавилаа: {amount}"

KEY: dragon-descent.got-you
TIER: 3
ISSUE: Same with "барилаа".
APPLIED: "&4Луу таныг барилаа!"

KEY: preferences.overflow.bank
TIER: 3
ISSUE: "Надад хадгалах" can read as "keep with me" (the full inventory); "хадгалж өгөх" is "keep it for me".
APPLIED: "&aНадад хадгалж өгөх"

KEY: slots.paylines-description
TIER: 3
ISSUE: "хээ" is a decorative ornament; a payline is a pattern of positions.
APPLIED: "&7Идэвхтэй шугам бүр нь хожил авчирч болох тэмдэгтийн байрлалын загвар юм."

KEY: slots.payout-blocked-retry
TIER: 3
ISSUE: "Төлбөрийг дахин оролдох" calques "retry the payout"; the retry is of the delivery.
APPLIED: "&7Төлбөрийг хүргэхийг дахин оролдохын тулд дарна уу."

KEY: slots.demo-result-win
TIER: 3
ISSUE: "буцаах" (give back) sits near the refund wording and clashes with the paytable's "Төлбөр".
APPLIED: "&eТуршилт: энэ эргэлт {amount} төлбөр олгох байсан (бооцоо: {bet}). Валют зарцуулаагүй."

KEY: slots.guide-seeds-never-pays
TIER: 3
ISSUE: "Үр дараалал" first parses as "the Seeds run"; lead with the concession.
APPLIED: "&7Дараалал хэдий урт байсан ч Үр хэзээ ч төлбөр авчирдаггүй."

KEY: slots.rail-clock-controls
TIER: 3
ISSUE: An infinitive coordinated with finite verbs.
APPLIED: "&7Доор зүүн товч дарж эхлүүлнэ эсвэл зогсооно, баруун товч дарж хурдыг өөрчилнө, Shift + зүүн товч дарж тохиргоог нээнэ."

KEY: slots-settings.variance-top-line
TIER: 3
ISSUE: "Бүтэн өргөний" calques "full-width"; the value is the multiplier of a line spanning every reel.
APPLIED: "&7Бүх барабаныг хамарсан шугамын хамгийн том үржүүлэгч: &e{multiplier}x"

Tier 0: 0 · Tier 1: 0 · Tier 2: 2 · Tier 3: 11

## Disposition (translator)

All 13 applied. Follow-up in self-review: `blackjack.all-in-with` had
the same "тавьлаа" spelling.
