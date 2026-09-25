# cy_GB review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean (script-checked token order;
protected literals, `/ncc`, `-1`, `off` intact). No mutating word precedes
a non-numeric placeholder; no gendered `ei` / `hi` / `ef` refers to a
player. Context notes hold (seat locked, chain-win current pot, future
cash-out notice, RTP "dychweliad", "rhediad" vs "cyfres", "2,5%").
Not scored: "atal" / "stopio" and "Cyfredol" / "presennol" both occur;
"gosodiad" serves both setting and setup.

KEY: coin-flip.dealer-cannot-cover
TIER: 1
ISSUE: Missing soft mutation after "neu": "neu rhowch" -> "neu rowch".
APPLIED: "&cNi all y deliwr hwn dalu enillion cyfres mor fawr ar hyn o bryd. Casglwch eich enillion neu rowch gynnig arall arni yn nes ymlaen."

KEY: rock-paper-scissors.dealer-cannot-cover
TIER: 1
ISSUE: Same: "neu rhowch".
APPLIED: "&cNi all y deliwr hwn dalu enillion cyfres mor fawr ar hyn o bryd. Casglwch eich enillion neu rowch gynnig arall arni yn nes ymlaen."

KEY: coin-flip.chain-win
TIER: 1
ISSUE: Missing soft mutation: "neu taflwch" -> "neu daflwch" (the current-pot reading is correct).
APPLIED: "&aRydych chi wedi ennill! Cyfres: {streak}. Pot presennol: &e{amount}&a. Casglwch eich enillion neu daflwch eto."

KEY: baccarat.dealer-cannot-cover
TIER: 1
ISSUE: Missing soft mutation: "neu lleihewch" -> "neu leihewch".
APPLIED: "&cNi all y bwrdd dalu ennill mor fawr ar hyn o bryd. Rhowch gynnig arall arni yn nes ymlaen, neu leihewch eich betiau."

KEY: dragon-descent.dealer-cannot-cover
TIER: 1
ISSUE: Same: "neu lleihewch".
APPLIED: "&cNi all y disgyniad hwn dalu pot mor fawr ar hyn o bryd. Rhowch gynnig arall arni yn nes ymlaen, neu leihewch eich bet."

KEY: slots.prompt-spin-limit
TIER: 1
ISSUE: Missing soft mutation: "neu teipiwch" -> "neu deipiwch" (literal -1 kept).
APPLIED: "&eTeipiwch derfyn troelli yn y sgwrs, neu deipiwch &f-1&e am ddim terfyn."

KEY: slots.prompt-big-win-multiplier
TIER: 1
ISSUE: Same: "neu teipiwch" (literal off kept).
APPLIED: "&eTeipiwch luosydd ennill mawr yn y sgwrs, neu deipiwch &foff&e i'w ddiffodd."

KEY: slots.prompt-profit-target
TIER: 1
ISSUE: Same: "neu teipiwch".
APPLIED: "&eTeipiwch darged elw yn y sgwrs, neu deipiwch &foff&e i'w ddiffodd."

KEY: slots.prompt-loss-limit
TIER: 1
ISSUE: Same: "neu teipiwch".
APPLIED: "&eTeipiwch derfyn colled yn y sgwrs, neu deipiwch &foff&e i'w ddiffodd."

KEY: slots.rail-height-effect
TIER: 1
ISSUE: "siapiau llinell talu" lacks the mutation after feminine "llinell"; the plural matches the payline term.
APPLIED: "&7Mae'r uchder yn gosod y ffenestr weladwy a pha siapiau llinellau talu sy'n bodoli."

KEY: slots-settings.variance-tradeoff
TIER: 1
ISSUE: "yn y tymor hir" means "in the long term", not jackpots from long runs; "llai o linellau sy'n talu" can read as fewer paylines.
APPLIED: "&7Mae amrywiant uwch yn golygu bod llinellau'n talu'n llai aml, ond bod jacpotiau rhediadau hir yn llawer mwy."

KEY: slots.guide-seeds-never-pays
TIER: 3
ISSUE: "beth bynnag hyd y rhediad" lacks a verb: "beth bynnag yw hyd".
APPLIED: "&7Nid yw Hadau byth yn talu, beth bynnag yw hyd y rhediad."

KEY: slots-settings.variance-top-line
TIER: 3
ISSUE: "lled llawn" normally means "fairly full"; the label is the top full-line multiplier.
APPLIED: "&7Lluosydd mwyaf llinell lawn: &e{multiplier}x"

KEY: slots.auto-settings-reset
TIER: 3
ISSUE: "Gosodiadau Awtomatig" reads as "automatic settings"; the source means the Auto Spin settings.
APPLIED: "&cAilosod Gosodiadau Troelli Awtomatig"

KEY: slots.auto-big-win-set
TIER: 3
ISSUE: "pan fydd y taliad o leiaf" lacks the "yn" complement; match the description key.
APPLIED: "&aBydd Troelli Awtomatig yn stopio pan fydd troelliad yn talu o leiaf {multiplier}× cyfanswm y bet."

KEY: slots.auto-stop-big-win
TIER: 3
ISSUE: "roedd hwnnw'n ennill mawr" can read as the progressive "was winning big".
APPLIED: "&eTroelli Awtomatig wedi stopio: cafwyd ennill mawr."

KEY: slots.profile-adjusted
TIER: 3
ISSUE: "cafodd ei addasu" takes "peiriant" as antecedent; the profile was fitted.
APPLIED: "&eNid yw'r peiriant hwn yn cefnogi pob gwerth a gadwyd, felly addaswyd y proffil: rhesi {rows}, riliau {columns}, llinellau {lines}, bet {amount}."

KEY: test-game.round-finished
TIER: 3
ISSUE: Clipped "Rownd wedi gorffen" and an unquoted, lowercase button name.
APPLIED: "Mae'r rownd wedi gorffen. Cliciwch “Ailosod y Gêm” i ddechrau eto."

KEY: slots.payout-blocked
TIER: 3
ISSUE: The button name "Troelli" should be quoted so it is not read as a verb.
APPLIED: "&cNid oedd modd dosbarthu eich enillion na'u rhoi mewn ciw. Cliciwch “Troelli” i ailgeisio; cysylltwch â'r staff os yw'r broblem yn parhau."

KEY: preferences.language.explicit
TIER: 3
ISSUE: Fills "Modd: {mode}"; the past-tense "Dewiswyd" reads "Mode: Was selected".
APPLIED: "Eich dewis chi"

Tier 0: 0 · Tier 1: 11 · Tier 2: 0 · Tier 3: 9

## Disposition (translator)

All 20 applied; the `dealer-cannot-cover` keys also take "yn nes
ymlaen" from the packet-2 note so the catalog says "later" one way.
