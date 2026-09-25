# lv_LV review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Scripted checks: tokens in English order, no
placeholder followed by a letter, `/ncc claim`, `-1`, `&foff` intact. No
gendered participles toward the player (imperatives only); `savs`/`tavs`
correct; number placeholders in label form. Verified in Java: `waiting-bet`
is shown to chair 2 while chair 1 has not bet (CoinFlipClient), so
"pretinieka" is right.

KEY: preferences.overflow.bank
TIER: 3
ISSUE: "Glabāt tos manā vietā" ("in my place / instead of me") is an awkward calque of "Hold For Me"; "tos" is clumsy in a button.
SUGGEST: "&aGlabāt drošībā"

KEY: coin-flip.pick-left
TIER: 3
ISSUE: "Pa kreisi" is a direction; the item names the side being picked.
SUGGEST: "&oKreisā puse"

KEY: coin-flip.pick-right
TIER: 3
ISSUE: "Pa labi" is a direction; the item names the side being picked.
SUGGEST: "&oLabā puse"

KEY: rock-paper-scissors.tie
TIER: 3
ISSUE: "Izvēle notiek vēlreiz..." is stiff and loses "Throwing again...".
SUGGEST: "&eNeizšķirts! Spēlējam vēlreiz..."

KEY: dragon-descent.vines-per-floor
TIER: 3
ISSUE: "drošās vietas stāvā" drops "per floor".
SUGGEST: "Vīteņaugi (drošās vietas katrā stāvā)"

KEY: slots.payout-blocked
TIER: 3
ISSUE: "servera komandu" is ambiguous ("komanda" also means a command, e.g. /ncc claim).
SUGGEST: "&cTavus laimestus nevarēja ne piegādāt, ne ievietot rindā. Noklikšķini „Griezt“, lai mēģinātu vēlreiz; ja problēma atkārtojas, sazinies ar servera administrāciju."

KEY: slots.paytable-leftmost-rule
TIER: 3
ISSUE: "kreisākais" is colloquial; standard is "galējais kreisais".
SUGGEST: "&8Jāsākas uz galējā kreisā ruļļa."

KEY: slots.rail-paytable-run
TIER: 3
ISSUE: Same colloquial "kreisākais".
SUGGEST: "&7Virkne: vienādi simboli pēc kārtas, sākot no galējā kreisā ruļļa."

KEY: slots.guide-seeds-title
TIER: 3
ISSUE: Common noun capitalized inside a heading.
SUGGEST: "&bPar sēklām"

KEY: slots.auto-big-win-current
TIER: 3
ISSUE: Colon directly after the preposition "pie"; siblings use "pie peļņas:" / "pie zaudējuma:".
SUGGEST: "&7Apstājas pie izmaksas: &a{multiplier}× kopējās likmes"

KEY: slots.auto-spin-limit-unlimited
TIER: 3
ISSUE: "ierobežojums: bez ierobežojuma" is tautological.
SUGGEST: "&7Griezienu ierobežojums: &anav"

KEY: slots.guide-volatility-tradeoff
TIER: 3
ISSUE: Grammatical but clumsy; the trade-off idea is lost.
SUGGEST: "&7Vairāk ruļļu nozīmē, ka biežos mazos laimestus no īsām virknēm aizstāj retāki, bet lielāki džekpoti no garām virknēm."

KEY: slots.guide-volatility-normalized
TIER: 3
ISSUE: "līdzsvarota ap … atdevi" is an unnatural calque of "normalized around".
SUGGEST: "&7Jebkurā gadījumā izmaksu tabula ir normalizēta tā, lai atdeve būtu &a{rtp}&7."

KEY: slots.profile-name-illegal-characters
TIER: 3
ISSUE: "pasvītras" is not the usual IT term; "apakšsvītras".
SUGGEST: "&cProfila nosaukumā drīkst būt tikai burti, cipari, atstarpes, defises un apakšsvītras."

KEY: slots-settings.house-edge-updated
TIER: 3
ISSUE: Verb "atdod" where the catalog uses the noun "atdeve" for RTP.
SUGGEST: "&aKazino priekšrocība iestatīta: {edge}; automāta atdeve tagad ir {rtp}."

## Terminology notes

No Tier 2 splits: dīleris, likme, Atkārtot likmi, Likt visu, Paņemt
laimestu, banka, žetons, tērzēšana, izvēlne, iestatījumi vs personīgie
iestatījumi, ruļļi, laimestu līnijas, grieziens, volatilitāte, kazino
priekšrocība, atdeve spēlētājam; slots run `virkne`, PvE streak `sērija`,
and the auto-spin batch never uses `sērija`.

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 0 | Tier 3: 15

## Disposition (translator)

All 15 applied as suggested.
