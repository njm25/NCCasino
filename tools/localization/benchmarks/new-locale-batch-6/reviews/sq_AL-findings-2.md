# sq_AL review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 checks clean. The reviewer checked
Java for `{mob}` (an English entity-type name), `blackjack.currently-selected`
and the `admin.select-*` items. Checked and correct: glossary terms,
`resplit-offer` "Dora u nda sërish!", the three distinct `closed-*` timers,
`occupations.*` in finish-editing, the dragon genitive plurals, both
`updated-detailed` templates, invariable "Asnjë" in the decor / collar
slots, no gendered text about the player.

KEY: roulette.bets-closed-final
TIER: 1
ISSUE: Gheg/colloquial "çka"; standard "ç'".
SUGGEST: "BASTET U MBYLLËN, Ç'U BË, U BË"

KEY: blackjack.title
TIER: 1
ISSUE: Noun after the linking article "e" is not genitive.
SUGGEST: "Tavolina e lojës Blackjack"

KEY: blackjack-settings.title
TIER: 1
ISSUE: Same missing genitive.
SUGGEST: "Cilësimet e lojës Blackjack ({dealer})"

KEY: blackjack-settings.invalid-settings-option
TIER: 1
ISSUE: Same.
SUGGEST: "&cU zgjodh një opsion i pavlefshëm në cilësimet e lojës Blackjack."

KEY: blackjack-settings.dealer-not-found
TIER: 1
ISSUE: Same after "të".
SUGGEST: "&cKrupieri i cilësimeve të lojës Blackjack nuk u gjet."

KEY: rock-paper-scissors-settings.title
TIER: 1
ISSUE: Bare nominative "Gur" after "e"; reads like a list.
SUGGEST: "Cilësimet e lojës Gur, letër, gërshërë ({dealer})"

KEY: rock-paper-scissors-settings.invalid-settings-option
TIER: 1
ISSUE: Same.
SUGGEST: "&cU zgjodh një opsion i pavlefshëm në cilësimet e lojës Gur, letër, gërshërë."

KEY: rock-paper-scissors-settings.dealer-not-found
TIER: 1
ISSUE: Same.
SUGGEST: "&cKrupieri i cilësimeve të lojës Gur, letër, gërshërë nuk u gjet."

KEY: jockey-options.age-set
TIER: 1
ISSUE: Genitive needs an inflected noun; {mob} is an uninflected entity name.
SUGGEST: "&aMosha e mob-it {mob}: &e{age}&a."

KEY: jockey-options.size-set
TIER: 1
ISSUE: Same.
SUGGEST: "&aMadhësia e mob-it {mob}: &e{size}&a."

KEY: jockey-options.variant-changed
TIER: 1
ISSUE: Same.
SUGGEST: "&aVarianti i mob-it {mob} u ndryshua në &e{variant}&a."

KEY: mob-selection.age-set
TIER: 1
ISSUE: Same.
SUGGEST: "&aMosha e mob-it {mob}: &e{age}&a."

KEY: admin.select-currency
TIER: 2
ISSUE: Plural "Zgjidhni" among singular admin items.
SUGGEST: "Zgjidh monedhën"

KEY: admin.select-currency-disabled
TIER: 2
ISSUE: Same.
SUGGEST: "Zgjidh monedhën [E çaktivizuar në mënyrën Vault]"

KEY: admin.select-vanilla-currency
TIER: 2
ISSUE: Same.
SUGGEST: "Zgjidh monedhë të zakonshme"

KEY: blackjack.already-seated
TIER: 3
ISSUE: Progressive "po" with resultative "jeni ulur"; playful tone lost.
SUGGEST: "&cHej, jeni ulur tashmë!"

KEY: blackjack.round-summary-hand-blackjack
TIER: 3
ISSUE: Lowercase "blackjack!" vs "Blackjack!" elsewhere.
SUGGEST: "&aDora {number}: Blackjack! +{amount}"

KEY: jockey-mob.select-vehicle-title
TIER: 3
ISSUE: Two bare nouns side by side.
SUGGEST: "Zgjidhni mob-in si mjet"

KEY: jockey-mob.select-jockey-title
TIER: 3
ISSUE: Same.
SUGGEST: "Zgjidhni mob-in si kalorës"

KEY: mob-selection.age-unsupported
TIER: 3
ISSUE: Accusative followed by nominative adjective phrases.
SUGGEST: "&cKy mob nuk e lejon ndryshimin e moshës (i vogël/i rritur)."

KEY: admin.standard-mode-fallback
TIER: 3
ISSUE: "Derisa" is one word.
SUGGEST: "&8Derisa të jetë i disponueshëm, përdoret mënyra standarde."

KEY: occupations.rps-max-chain
TIER: 3
ISSUE: Parses like a list inside finish-editing; add "loja".
SUGGEST: "raundet maksimale në seri te loja Gur, letër, gërshërë"

KEY: mines.dealer-cannot-cover
TIER: 3
ISSUE: Clitic doubling of an indefinite object; glossary "tërheqje fitimi".
SUGGEST: "&cKjo fushë nuk mund të mbulojë tani një tërheqje fitimi kaq të madhe. Provoni më vonë ose zvogëloni bastin."

KEY: roulette.dealer-cannot-cover
TIER: 3
ISSUE: Clitic doubling of an indefinite object.
SUGGEST: "&cTavolina nuk mund të mbulojë tani një fitim kaq të madh. Provoni më vonë ose zvogëloni bastet."

KEY: blackjack.dealer-cannot-cover
TIER: 3
ISSUE: Same.
SUGGEST: "&cKrupieri nuk mund të mbulojë tani një fitim kaq të madh. Provoni më vonë ose zvogëloni bastin."

## Terminology notes

Game names after "e / të" handled three ways (classifier noun, inflected,
bare -- the bare ones fixed above); admin-menu items in the singular
imperative except three plural "Zgjidhni" (fixed); `{mob}` in genitive
constructions (fixed); "Blackjack!" capitalization.

Counts: Tier 0: 0 | Tier 1: 12 | Tier 2: 3 | Tier 3: 10 | Total: 25

## Disposition (translator)

All 25 applied as suggested. Follow-up: `occupations.coin-flip-max-chain`
takes the same "te loja …" form as the RPS value.
