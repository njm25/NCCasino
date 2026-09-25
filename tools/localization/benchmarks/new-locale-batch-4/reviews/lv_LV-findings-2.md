# lv_LV review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Scripted checks clean (tokens in order,
protected literals). No gendered text toward the player, no formal "Jūs",
„“ quotes; accusative `occupations.*` and lowercase genitive-plural Dragon
fillers correct; distinct `closed-*`, completed-action `resplit-offer`,
distinct Same Rank / Same Value, round-count max chain, neutral
`inventory-full`.

KEY: mines.rebet-broke
TIER: 3
ISSUE: Playful "2 broke 4 rebet" became a flat neutral error.
SUGGEST: "&cMaks par plānu, lai atkārtotu likmi."

KEY: roulette.hit-red
TIER: 3
ISSUE: "uzkrist" means to fall onto; results use "izkrist".
SUGGEST: "&cIzkrita sarkanais {number}!"

KEY: roulette.hit-black
TIER: 3
ISSUE: Same "uzkrita" → "izkrita".
SUGGEST: "&fIzkrita melnais {number}!"

KEY: roulette.hit-green
TIER: 3
ISSUE: Same "uzkrita" → "izkrita".
SUGGEST: "&aIzkrita zaļais {number}, OHO!"

KEY: roulette.bet-placed
TIER: 3
ISSUE: Masculine singular "Likts" agrees with the amount and clashes with plural item amounts.
SUGGEST: "&6Likme {amount} likta uz laukuma „{bet}“"

KEY: roulette.item-payout-too-large
TIER: 3
ISSUE: "sadali to uz mazāk likmēm" is ungrammatical.
SUGGEST: "&cŠīs likmes iespējamā izmaksa ir pārāk liela priekšmetu valūtai. Samazini likmi vai sadali to starp mazāku skaitu likmju."

KEY: mob-settings.adult
TIER: 2
ISSUE: Participle "Pieaudzis" vs noun "pieaugušais" in mob-selection.age-unsupported; pair with "Mazulis".
SUGGEST: "Pieaugušais"

KEY: admin.adult
TIER: 2
ISSUE: Same as mob-settings.adult.
SUGGEST: "Pieaugušais"

KEY: jockey-mob.jockey-changed
TIER: 3
ISSUE: Colon directly after the preposition "uz".
SUGGEST: "&aJātnieks nomainīts. Jaunais jātnieks: &e{mob}&a."

KEY: jockey-options.variant-changed
TIER: 3
ISSUE: Colon directly after "uz".
SUGGEST: "&aVariants ({mob}) nomainīts. Jaunais variants: &e{variant}&a."

KEY: mob-selection.dealer-changed-detailed
TIER: 3
ISSUE: Colon directly after "uz".
SUGGEST: "&aDīleris nomainīts. Jaunais radījums: &e{mob}&a."

KEY: complex-variant.changed-detailed
TIER: 3
ISSUE: Colon directly after "uz"; {change} is a full phrase.
SUGGEST: "&a{change}. Jaunā vērtība: &e{value}&a."

KEY: admin.currency-mode-switched
TIER: 3
ISSUE: Colon directly after "uz".
SUGGEST: "&eValūtas režīms nomainīts. Jaunais režīms: &a{mode}&e."

KEY: admin.currency-updated-detailed
TIER: 3
ISSUE: Colon after "uz"; "atjaunināta uz" calque.
SUGGEST: "&aValūta atjaunināta. Jaunā valūta: &e{name}&a (&e{material}&a)."

KEY: admin.dealer-name-updated-detailed
TIER: 3
ISSUE: Colon after "uz"; calque.
SUGGEST: "&aDīlera vārds atjaunināts. Jaunais vārds: „&e{name}&a“."

KEY: admin.timer-updated-detailed
TIER: 3
ISSUE: Colon after "uz"; calque.
SUGGEST: "&aDīlera taimeris atjaunināts. Jaunā vērtība: &e{timer}&a."

KEY: admin.animation-updated-detailed
TIER: 3
ISSUE: Colon after "uz"; calque.
SUGGEST: "&aDīlera animācijas ziņojums atjaunināts. Jaunais ziņojums: „&e{message}&a“."

KEY: admin.chip-size-updated-detailed
TIER: 3
ISSUE: Colon after "uz"; calque (chip value meaning correct).
SUGGEST: "&aŽetona Nr. {index} vērtība atjaunināta. Jaunā vērtība: &e{size}&a."

KEY: blackjack-settings.prompt-max-hands
TIER: 3
ISSUE: "no 2" reads as "from 2 (to …)"; the English means "2 or more".
SUGGEST: "&aIeraksti -1, lai nebūtu ierobežojuma, vai veselu skaitli, kas ir vismaz 2."

KEY: blackjack-settings.invalid-max-hands
TIER: 3
ISSUE: Same.
SUGGEST: "Ievadi -1, lai nebūtu ierobežojuma, vai veselu skaitli, kas ir vismaz 2."

KEY: occupations.rps-max-chain
TIER: 3
ISSUE: In the finish-editing template this yields two brackets in a row.
SUGGEST: "spēles „Akmens, šķēres, papīrs“ maksimālo raundu skaitu sērijā"

KEY: occupations.coin-flip-max-chain
TIER: 3
ISSUE: Same.
SUGGEST: "spēles „Ģerbonis vai cipars“ maksimālo raundu skaitu sērijā"

KEY: admin.currency-selection-disabled-future
TIER: 3
ISSUE: "Plānota nākotnē" is a literal calque.
SUGGEST: "&cValūtas izvēle VAULT režīmā ir izslēgta. To plānots ieviest nākotnē"

## Terminology notes

Adult (mob age) should be the noun "Pieaugušais" everywhere, paired with
"Mazulis". Everything else consistent (dīleris, likme, žetons as value,
taimeris, tērzēšana, apdrošināšana, Ņemt / Pietiek / Dubultot / Sadalīt,
kāršu kaste, kāršu kava, dūzis, vīteņaugi, jātnieks, radījums, nesējs /
pasažieris, kaudze, iestatījumi, izvēlne, game names). The "nomainīts uz:"
pattern is restructured wherever it occurs.

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 2 | Tier 3: 21 | Total: 23

## Disposition (translator)

All 23 applied as suggested; a grep confirmed no remaining " uz: " pattern.
