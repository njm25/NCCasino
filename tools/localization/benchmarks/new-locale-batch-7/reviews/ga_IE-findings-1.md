# ga_IE review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean (script-checked token order,
`/ncc claim`, `-1`, `off`, no placeholder joined to a letter). Context
notes hold (third-person turn, future cash-out, seat not yet available,
current pot, round-count max chain, three payout contexts, overflow held
not lost, RTP not "aisíoc", vines, "2.5%"). Mutations, genitives and
adjective agreement otherwise correct.

KEY: preferences.overflow.title
TIER: 3
ISSUE: "nach n-oireann" uses "oir" (suit, as clothes fit); room in the inventory is "spás" / "slí".
APPLIED: "&eAirgead buaite nach bhfuil spás dó"

KEY: slots.payout-banked
TIER: 3
ISSUE: Same "oir" calque ("did not suit").
APPLIED: "&eNí raibh slí do chuid den airgead buaite, agus tá an méid sin ag fanacht ar spás san fhardal: {amount}."

KEY: slots.paytable-card-no-runs
TIER: 3
ISSUE: Missing t-prefix ("den tsiombail", siombail is feminine) and the same "oir" calque.
APPLIED: "&8Níl slí d'aon seicheamh den tsiombail seo ar {columns} ríl."

KEY: dragon-descent.safe
TIER: 3
ISSUE: A bare "Slán!" reads first as "Goodbye!" next to "Contúirteach!".
APPLIED: "&aSábháilte!"

KEY: baccarat.rebet-disabled
TIER: 3
ISSUE: "Tá geall arís as." can read as "a bet is off again"; mark the toggle name.
APPLIED: "&cTá “Geall arís” as."

KEY: baccarat.rebet-insufficient
TIER: 3
ISSUE: Same ambiguity, and "Níl go leor ann" did not say who lacks the money.
APPLIED: "&cNíl go leor agat chun an geall a athdhéanamh. Tá “Geall arís” as."

KEY: slots.rail-wager-total
TIER: 3
ISSUE: "thar {lines} líne" reads as "more than N lines"; the catalog elsewhere uses "ar".
APPLIED: "&7Geall iomlán: &a{amount} &7ar &a{lines} &7líne"

KEY: slots.prompt-spin-limit
TIER: 3
ISSUE: "le haghaidh gan teorainn" calques "for no limit"; "le haghaidh" wants a noun.
APPLIED: "&eClóscríobh teorainn castaí sa chomhrá, nó clóscríobh &f-1&e chun gan aon teorainn a bheith ann."

KEY: slots.prompt-invalid-spin-limit
TIER: 3
ISSUE: Same calque.
APPLIED: "&cClóscríobh slánuimhir castaí, nó {unlimited} chun gan aon teorainn a bheith ann."

KEY: coin-flip.max-pot-hit
TIER: 3
ISSUE: "ag an am céanna" means simultaneously; "at once" here means in one payout ("d'aon iarraidh").
APPLIED: "&6Shroich an t-airgead a bhuaigh tú an t-uasmhéid is féidir leis an gcluiche seo a íoc d'aon iarraidh. Comhghairdeas leis an mbua is mó!"

KEY: rock-paper-scissors.max-pot-hit
TIER: 3
ISSUE: Same.
APPLIED: "&6Shroich an t-airgead a bhuaigh tú an t-uasmhéid is féidir leis an gcluiche seo a íoc d'aon iarraidh. Comhghairdeas leis an mbua is mó!"

KEY: slots.paytable-legend-run
TIER: 3
ISSUE: "comhoiriúnacha" means compatible; a run needs identical symbols ("comhionanna").
APPLIED: "&7Seicheamh: líon na siombailí comhionanna as a chéile, á gcomhaireamh ón ríl ar chlé."

KEY: slots.rail-paytable-run
TIER: 3
ISSUE: Same.
APPLIED: "&7Seicheamh: siombailí comhionanna as a chéile ón ríl is faide ar chlé."

Tier 0: 0 · Tier 1: 0 · Tier 2: 0 · Tier 3: 13

## Disposition (translator)

All 13 applied. Follow-ups in self-review: `mines.rebet-off-reset` marks
the toggle name the same way, and every other "-1 le haghaidh gan
teorainn" (max-chain and max-hands prompts) became "-1 chun gan aon
teorainn a bheith ann".
