# Run new-locale-batch-4

- Date: 2026-09-25
- Operation type (guide §A): **New locale** generation, then candidate
  promotion into `src/main/resources/lang/`, for locales chosen by the agent.
- Authorization: the repository user asked in chat to keep choosing more
  languages "in the same style" once the requested ones were done; this run
  continues batch 3 under that instruction. Registration in `locales.yml` is
  again left to the user.
- Selection (next Minecraft server communities not yet covered, in this
  order): es_MX, hr_HR, sl_SI, sr_RS, lt_LT, lv_LV, et_EE, ca_ES.
- Base commit for this run: `05dcb1d58a1801e38c6a6a768c7b8f10c40cb44e`
- English source: `src/main/resources/lang/en_US.yml`
  SHA-256 `3c95538cdff1143654851e228290f165f97826d53431986029ae3ba9b49e6670`
  (unchanged since batch 1; 1184 translatable entries)
- Process, provenance and the isolation limitation are identical to
  `new-locale-batch-1/manifest.md`; reviewers receive the same rubric as
  batch 3. Glossary columns go into a third continuation of the §H table.

## Per-locale record

### es_MX -- Español (México) (Mexican Spanish)

- Final catalog SHA-256: `13c4e3ee691f361b5264529b32c8e5b845044286e1857ecd1d93a35e083317f3` (identical in the run directory and
  `src/main/resources/lang/es_MX.yml`; NFC-normalized)
- Voice: Mexican Spanish, informal `tú`, decimal point; written from the
  English source rather than adapted from es_ES (Spain-form scan clean);
  gender-neutral toward the player; `Águila o sol`, `crupier`, Baccarat
  `Jugador` / `Banca`, cash-out `Cobrar`, pot `pozo`, Slots
  `Tragamonedas` with run `secuencia` vs streak `racha`. Recorded in a
  new third continuation of the §H table.
- Structural: helper strict check 0 errors (3 residue warnings are Spanish
  `a` and the loanword `chat`); `localizationCandidateCheck` CANDIDATE
  OK (1184); full `localizationCheck` with all twenty-four new locales
  registered: every one of the 30 locales OK (1184), no new warnings;
  `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 1 (`blackjack.resplit-offer` is a confirmation sent after the
  re-split, verified in `BlackjackInventory`; added to the guide's §C
  registry and the reviewer rubric), Tier 2 = 0, Tier 3 = 24, all applied
  ("Baccarat" name, queue "cola" vs row "fila", "Misma carta"). 26 keys
  patched, rechecked after. Findings: `reviews/es_MX-findings-*.md`.
- Not registered in `locales.yml`. Registry line:
  `es_MX: name: "Español (México)"`.
- Native-speaker review: not performed; recommended before release.

### hr_HR -- Hrvatski (Croatian)

- Final catalog SHA-256: `853dfa7965cf78f416cf47a989a853fc78a14c69c655d739b6d8453242c18df4` (identical in the run directory and
  `src/main/resources/lang/hr_HR.yml`; NFC-normalized)
- Voice: ijekavian Croatian, informal `ti`, Croatian UI vocabulary
  (Serbian-form scan clean); gender-neutral toward the player (no gendered
  second-person perfect); number agreement avoided; genitive fillers for
  `occupations.*` and the Dragon `{setting}` slot; `djelitelj`, Baccarat
  `Igrač` / `Bankar`, stake `ulog`, Slots `Slot automat` with run `niz`
  vs streak `serija`. Recorded in the guide.
- Structural: helper strict check 0 errors (2 residue warnings are
  Croatian `a`/`to`); `localizationCandidateCheck` CANDIDATE OK (1184);
  full `localizationCheck` with all twenty-five new locales registered:
  every one of the 31 locales OK (1184), no new warnings; `compileJava`
  succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0, Tier 2 = 1 (Slots stake term), Tier 3 = 34, all applied
  ("od strane igrača" calque, standard plural `asove`, left-game and
  left-chair confirmations, `Uporaba:`). 40 keys patched, rechecked after.
  Findings: `reviews/hr_HR-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `hr_HR: name: "Hrvatski"`.
- Native-speaker review: not performed; recommended before release.

### sl_SI -- Slovenščina (Slovenian)

- Final catalog SHA-256: `3f5a325f00745756c8b6021a1101745e69a3265ae4e5025f9a6651e5804a8e58` (identical in the run directory and
  `src/main/resources/lang/sl_SI.yml`; NFC-normalized)
- Voice: standard Slovenian, informal `ti`, `»«` quotes, Slovenian UI
  vocabulary (Croatian/Serbian-form scan clean); gender-neutral toward the
  player (no gendered second-person past); singular/dual/plural agreement
  avoided with labels; genitive fillers for `occupations.*` and the Dragon
  `{setting}` slot; `delivec`, Baccarat `Igralec` / `Bankir`, stake
  `vložek` vs placed bet `stava`, pot `sklad` (mob stack therefore
  `kup`), Slots `Igralni avtomat` with `koluti`. Recorded in the guide.
- Structural: helper strict check 0 errors (6 residue warnings are
  Slovenian `in`/`to`/`a`); `localizationCandidateCheck` CANDIDATE OK
  (1184); full `localizationCheck` with all twenty-six new locales
  registered: every one of the 32 locales OK (1184), no new warnings;
  `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 2 (the Coin Flip / RPS pot label left as English "Pot", which
  reads as Slovenian "path"; now `Sklad`), Tier 2 = 11 (Slots stake term
  `vložek`, All In label), Tier 3 = 32 (plugin literals given a classifier
  noun, `sestavljene različice`, "set to" phrasing), all applied; 11
  consistency follow-ups (remaining Slots stake prose, mob stack renamed
  `kup`). 56 keys patched including one self-review fix, rechecked after.
  Findings: `reviews/sl_SI-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `sl_SI: name: "Slovenščina"`.
- Native-speaker review: not performed; recommended before release.

### sr_RS -- Српски (Serbian, Cyrillic)

- Final catalog SHA-256: `0501949dc80a3d2d52d69381a84ee4dd1dd5143431c8de65eea60771337407dd` (identical in the run directory and
  `src/main/resources/lang/sr_RS.yml`; NFC-normalized)
- Voice: ekavian Serbian in Cyrillic, informal `ти`, `„“` quotes, Serbian UI
  vocabulary (Croatian/ijekavian scan and mixed-script scan clean; seconds
  as Cyrillic `с`); gender-neutral toward the player (no gendered perfect
  or `да би` participles); number agreement avoided with labels; genitive
  fillers for `occupations.*` and the Dragon `{setting}` slot; `делилац`,
  Baccarat `Играч` / `Банкар`, stake `улог` vs placed bet `опклада`,
  Slots `Слот машина` with `ролне`. Recorded in the guide.
- Structural: helper strict check 0 errors, 0 residue warnings;
  `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all twenty-seven new locales registered: every
  one of the 33 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0, Tier 2 = 0, Tier 3 = 11, all applied (profile-name length as
  a label because 24 takes the paucal `знака`, `Пробни мени`, double
  `за` in occupation fillers, "changed to" label form); 1 consistency
  follow-up. 14 keys patched including two self-review fixes (masculine
  `да би уложио` / `да би се кладио`), rechecked after. Findings:
  `reviews/sr_RS-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `sr_RS: name: "Српски"`.
- Native-speaker review: not performed; recommended before release.

### lt_LT -- Lietuvių (Lithuanian)

- Final catalog SHA-256: `84cd677c323b51e4fd1d6e5296e59dc9f1e6e2d17f32005d17dec611806c6417` (identical in the run directory and
  `src/main/resources/lang/lt_LT.yml`; NFC-normalized)
- Voice: standard Lithuanian, informal `tu`, `„“` quotes, decimal comma;
  gender-neutral toward the player (gerunds such as `keičiant` /
  `spustelint` instead of gendered half-participles); number agreement
  avoided with labels; accusative `occupations.*` and genitive-plural Dragon
  fillers; `dalytojas`, Baccarat `Žaidėjas` / `Bankininkas`, `statymas`,
  pot `Bankas`, Slots `Lošimo automatas` with `ritiniai` (not `būgnai`,
  the diamonds suit), run `seka` vs streak `serija`. Recorded in the guide.
- Structural: helper strict check 0 errors, 0 residue warnings (one
  token-order slip in `slots.demo-result-win` caught and fixed before
  review); `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all twenty-eight new locales registered: every
  one of the 34 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 3 (`seat-unavailable` rendered as "occupied" although the chair
  is empty and locked -- verified in `CoinFlipClient` /
  `RockPaperScissorsClient` and added to the guide's §C registry and the
  rubric; Slots settlement is delivery, not calculation), Tier 2 = 3
  (`atlikti statymą`), Tier 3 = 46 (`nebepriimami`, button-label
  restructuring, reflexive `savo`, "on split aces" calque), all applied;
  5 consistency follow-ups. 56 review keys plus 11 self-review fixes,
  rechecked after. Findings: `reviews/lt_LT-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `lt_LT: name: "Lietuvių"`.
- Native-speaker review: not performed; recommended before release.

### lv_LV -- Latviešu (Latvian)

- Final catalog SHA-256: `07a47a4c1fc85d636f922ccf94f40a0390c841730786605e5ba23557b5743a0f` (identical in the run directory and
  `src/main/resources/lang/lv_LV.yml`; NFC-normalized)
- Voice: standard Latvian, informal `tu`, `„“` quotes, decimal comma;
  gender-neutral toward the player (impersonal or finite forms instead of
  participles; `atteicies` replaced); number agreement avoided with labels;
  accusative `occupations.*` and genitive-plural Dragon fillers;
  `dīleris`, Baccarat `Spēlētājs` / `Baņķieris`, `likme`, pot `banka`,
  Slots `Spēļu automāts` with `ruļļi`, run `virkne` vs streak `sērija`.
  Lessons from the lt_LT review (settlement = delivery, reflexive `savus`,
  no colon after a preposition, `sērija` kept out of auto-spin) applied
  before review. Recorded in the guide.
- Structural: helper strict check 0 errors (1 residue warning is Latvian
  `to`); `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all twenty-nine new locales registered: every
  one of the 35 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0, Tier 2 = 2 (mob "adult" as the noun `Pieaugušais`), Tier 3 =
  36 ("nomainīts uz:" restructured, `izkrita` for roulette results,
  `galējais kreisais`, side labels), all applied. 38 review keys plus 16
  self-review fixes, rechecked after. Findings:
  `reviews/lv_LV-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `lv_LV: name: "Latviešu"`.
- Native-speaker review: not performed; recommended before release.

### et_EE -- Eesti (Estonian)

- Final catalog SHA-256: `9c814310f5554385f6b9b3e60a2c72cd0baa7d40f2d257e825b6d86d2a79feb3` (identical in the run directory and
  `src/main/resources/lang/et_EE.yml`; NFC-normalized)
- Voice: standard Estonian, informal `sina`, `„“` quotes, decimal comma;
  no grammatical gender, so the work is numerals and case endings (colon
  labels, quoted appositions such as `mängu „{game}“`); genitive
  `occupations.*` and genitive-plural Dragon fillers; `diiler`, Baccarat
  `Bakkara` with `Mängija` / `Pankur`, `panus`, pot `pott`, Slots
  `Mänguautomaat` with `rullid`, run `jada` vs streak `seeria`. Recorded in
  the guide.
- Structural: helper strict check 0 errors (one token-order slip in
  `slots.profile-overwrite-retry` caught and fixed before review; 4 residue
  warnings are Estonian `on`); `localizationCandidateCheck` CANDIDATE OK
  (1184); full `localizationCheck` with all thirty new locales registered:
  every one of the 36 locales OK (1184), no new warnings; `compileJava`
  succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0, Tier 2 = 0, Tier 3 = 32 (`Bakkara` spelling, count labels,
  "number of" in Dragon updates, insurance cost wording), all applied, plus
  the `Bakkara` follow-up in the settings keys. 32 review keys plus 9
  self-review fixes, rechecked after. Findings: `reviews/et_EE-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `et_EE: name: "Eesti"`.
- Native-speaker review: not performed; recommended before release.

### ca_ES -- Català (Catalan)

- Final catalog SHA-256: `0dc7019060ff7a2238e9f3708fd8bd5a944b24d9bab9f8b27f07fa68765631f3` (identical in the run directory and
  `src/main/resources/lang/ca_ES.yml`; NFC-normalized)
- Voice: standard Central Catalan (Softcatalà / TERMCAT conventions),
  informal `tu`, `«»` quotes, decimal comma; gender-neutral toward the
  player (no gendered adjectives; invariable `haver` participles);
  `occupations.*` carry their own article; `crupier`, Baccarat `Jugador` /
  `Banca`, `aposta`, cash-out `Cobra`, Slots `Escurabutxaques` with
  `rodets` and `tirada`, run `seqüència` vs streak `ratxa`. Written from the
  English source, not adapted from es_ES. Recorded in the guide.
- Structural: helper strict check 0 errors (one token-order slip in
  `dealer.game-set` caught and fixed before review; 21 residue warnings are
  Catalan `a` / `has`); `localizationCandidateCheck` CANDIDATE OK (1184);
  full `localizationCheck` with all thirty-one new locales registered: every
  one of the 37 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 1 (`blackjack.starts-in` "comença en" castellanisme; `{seconds}`
  verified as a raw integer), Tier 2 = 18 (capitalized game names in
  settings titles, `nombre` vs `número`, `Activat` / `Desactivat`), Tier 3 =
  17, all applied. 36 review keys plus 5 self-review fixes, rechecked after.
  Findings: `reviews/ca_ES-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `ca_ES: name: "Català"`.
- Native-speaker review: not performed; recommended before release.

## Batch summary

All eight batch-4 locales are complete. To expose them in the language menu,
append to `src/main/resources/lang/locales.yml` (after the batch-1, batch-2
and batch-3 lines):

```yaml
  es_MX:
    name: "Español (México)"
  hr_HR:
    name: "Hrvatski"
  sl_SI:
    name: "Slovenščina"
  sr_RS:
    name: "Српски"
  lt_LT:
    name: "Lietuvių"
  lv_LV:
    name: "Latviešu"
  et_EE:
    name: "Eesti"
  ca_ES:
    name: "Català"
```

With the batch-1 to batch-4 lines appended, `localizationCheck` reports all
37 locales OK at 1184 entries (verified in this run) and `compileJava`
succeeds. Remaining warnings are the seven pre-existing ones plus the
de_DE-precedent identity in nl_NL; batch 4 adds none. Two semantic facts
were verified in Java during this batch and added to the guide's §C
registry and the reviewer rubric: `blackjack.resplit-offer` confirms a
re-split that already happened, and `coin-flip` / `rock-paper-scissors`
`seat-unavailable` labels an empty, locked chair.

Out-of-scope observations (reported, not changed -- each would be a
targeted refinement needing its own run and approval):

- `blackjack.resplit-offer` is rendered as an instruction or offer ("Split
  again!") rather than a completed action in zh_CN, th_TH, ro_RO, zh_TW,
  nl_NL, tr_TR, vi_VN, ja_JP, sv_SE, ru_RU, fr_FR, uk_UA, pt_PT, pl_PL,
  el_GR, nb_NO, sk_SK, ko_KR, it_IT, bg_BG, pt_BR, hu_HU, fi_FI, cs_CZ,
  de_DE, da_DK, id_ID and es_ES.
- `seat-unavailable` reads as "not free / not vacant", which usually means
  occupied, in bg_BG ("не е свободно"), da_DK / nb_NO ("ikke ledig"), fi_FI
  ("ei ole vapaana"), hr_HR ("nije slobodno"), sk_SK ("nie je voľné"),
  sl_SI ("ni prosto") and sr_RS ("није слободно"). hr_HR, sl_SI and sr_RS
  were promoted in this batch before the fact was verified during the lt_LT
  review.
- hr_HR `slots.profile-name-empty` / `profile-name-too-long` use "{max}
  znakova"; with `SlotsProfileName.MAX_LENGTH` = 24 Croatian needs the
  paucal "znaka" (or a label form, as sr_RS now uses). The other Slavic and
  Baltic catalogs checked (bg, cs, pl, ru, sk, sl, uk, lt, lv) are correct
  for 24.
- es_ES: `chain-win` wording and the gendered "sentado" (noted in batch 4's
  es_MX work).

Still needed before release: an in-game check (Cyrillic and Baltic
diacritics in inventory titles, line wrapping of the longer Baltic and
Catalan strings) and native-speaker review.
