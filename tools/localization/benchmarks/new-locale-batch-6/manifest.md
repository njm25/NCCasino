# Run new-locale-batch-6

- Date: 2026-09-25
- Operation type (guide §A): **New locale** generation, then candidate
  promotion into `src/main/resources/lang/`, for locales chosen by the agent.
- Authorization: the repository user asked in chat to keep choosing more
  languages "in the same style" once the requested ones were done; this run
  continues batch 5 under that instruction. Registration in `locales.yml` is
  again left to the user.
- Selection (Minecraft server communities not yet covered, left-to-right
  scripts first; right-to-left languages remain deferred until chat and
  inventory-title bidi rendering has been checked in game): mk_MK, az_AZ,
  eu_ES, sq_AL, is_IS, kk_KZ, ka_GE, cy_GB.
- Base commit for this run: `76b24696c17da6cb4510fa9f9b80c64f6651c36d`
- English source: `src/main/resources/lang/en_US.yml`
  SHA-256 `3c95538cdff1143654851e228290f165f97826d53431986029ae3ba9b49e6670`
  (unchanged since batch 1; 1184 translatable entries)
- Process, provenance and the isolation limitation are identical to
  `new-locale-batch-1/manifest.md`; reviewers receive the batch-4 rubric,
  which now also carries the verified `mob-settings.none` / `admin.none`
  note. Glossary columns go into a fifth continuation of the §H table.

## Per-locale record

### mk_MK -- Македонски (Macedonian)

- Final catalog SHA-256: `c926c21725b4b02509e462baab2da9375dc14e475c3fc124755eb1a9e217c538` (identical in the run directory and
  `src/main/resources/lang/mk_MK.yml`; NFC-normalized)
- Voice: standard literary Macedonian, polite `Вие` (aorist 2nd plural keeps
  the player gender-neutral), `„“` quotes, decimal comma; written from the
  English source, not adapted from bg_BG / sr_RS (script scan clean);
  definite articles, clitic doubling, definite `occupations.*`; `дилер`,
  Baccarat `Играч` / `Банкар`, `облог` with `става`, `Подигни добивка`,
  slots run `комбинација` vs streak `низа`, `Автоматско вртење`. Recorded
  in a new fifth continuation of the §H table.
- Structural: helper strict check 0 errors, 0 residue warnings;
  `localizationCandidateCheck` CANDIDATE OK (1184); full `localizationCheck`
  with all thirty-eight new locales registered: every one of the 44 locales
  OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0, Tier 2 = 1 (profit vs winnings), Tier 3 = 32 (non-standard
  `влога`, object-less `подигнете`, "Веќе седите", definite
  `occupations.*`, drop wording), all applied. 33 review keys plus 6
  self-review fixes, rechecked after. Findings: `reviews/mk_MK-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `mk_MK: name: "Македонски"`.
- Native-speaker review: not performed; recommended before release.

### az_AZ -- Azərbaycanca (Azerbaijani)

- Final catalog SHA-256: `55170e3e0faa0d2d36bd31dec239b7bd970d2a513056727e65b1ba7adda3189f` (identical in the run directory and
  `src/main/resources/lang/az_AZ.yml`; NFC-normalized)
- Voice: standard literary North Azerbaijani (Latin), polite `siz`, `“”`
  quotes, decimal comma; written from the English source, not adapted from
  tr_TR (Turkish-form scan clean); no suffix after a placeholder (case
  endings on governing nouns), numeral + singular noun; `ədəd` vs `rəqəm`;
  `diler`, Baccarat `Oyunçu` / `Bankir`, `Uduşu götür`, slots run
  `ardıcıllıq` vs streak `seriya`, `Avtomatik fırlatma`. Recorded in the
  fifth continuation of the §H table.
- Structural: helper strict check 0 errors, 0 residue warnings (one
  token-order slip in `dragon-settings.prompt-setting-detailed` caught and
  fixed before review); `localizationCandidateCheck` CANDIDATE OK (1184);
  full `localizationCheck` with all thirty-nine new locales registered:
  every one of the 45 locales OK (1184), no new warnings; `compileJava`
  succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 1 (straight-up "Tək rəqəm" colliding with the Odd bet),
  Tier 2 = 1 (rebet-off wording), Tier 3 = 42 (`{game}` parentheticals,
  missing ablative with `istifadə etmək`, `rəqəm` / `ədəd`, tense of
  Baccarat results), all applied. 44 review keys plus 10 self-review fixes,
  rechecked after. Findings: `reviews/az_AZ-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `az_AZ: name: "Azərbaycanca"`.
- Native-speaker review: not performed; recommended before release.

### eu_ES -- Euskara (Basque)

- Final catalog SHA-256: `f75eb4fec9dedbea4fc9f0885f0d2805123e638f592d40437353d87b7fd6d7ba` (identical in the run directory and
  `src/main/resources/lang/eu_ES.yml`; NFC-normalized)
- Voice: standard Basque (euskara batua), `zu` address, `«»` quotes, decimal
  comma; case endings on governing nouns or verbs, never on placeholders;
  `-ko` adjectives before the noun, `-rako` for inanimate purpose;
  `gehieneko` for max; `krupierra`, Baccarat `Jokalaria` / `Bankaria`,
  `Kobratu`, `sari nagusi`, RPS `Harri, orri, artazi`; slots run `segida` vs
  streak `bolada`; `Bira automatikoa`. Recorded in the fifth continuation of
  the §H table.
- Structural: helper strict check 0 errors, 0 residue warnings;
  `localizationCandidateCheck` CANDIDATE OK (1184); full `localizationCheck`
  with all forty new locales registered: every one of the 46 locales OK
  (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 7 (dragon sweep stated as a loss, verified in `DragonClient`;
  unparseable `% 2,5` house-edge example, verified in
  `SlotsHouseEdgeInput.parse` and added to guide §C; ungrammatical
  `-koa bat`, `zenbat` order, "esku gehienak"), Tier 2 = 4 (English
  "jackpot", `maximo` drift), Tier 3 = 33, all applied. 44 review keys plus
  11 self-review fixes (RPS name carried through, `gehieneko`), rechecked
  after. Findings: `reviews/eu_ES-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `eu_ES: name: "Euskara"`.
- Native-speaker review: not performed; recommended before release.

### sq_AL -- Shqip (Albanian)

- Final catalog SHA-256: `805e5f3b7ba79f315ca78051a096bd12b5fe1cddc2d852c811edcdfbcd8a3fef` (identical in the run directory and
  `src/main/resources/lang/sq_AL.yml`; NFC-normalized)
- Voice: standard literary Albanian, polite plural `ju` for instructions and
  the singular imperative for buttons, `„”` quotes, decimal comma; no
  adjective predicates about a player; classifier nouns carry the genitive
  for placeholders and game names (`e lojës Blackjack`, `e mob-it {mob}`);
  `krupieri`, Baccarat `Lojtari` / `Bankieri`, `Tërhiq fitimin`, slots run
  `varg` vs streak `seri`. Recorded in the fifth continuation of the §H
  table.
- Structural: helper strict check 0 errors (one residue warning, the
  `chat-it` loanword); `localizationCandidateCheck` CANDIDATE OK (1184);
  full `localizationCheck` with all forty-one new locales registered: every
  one of the 47 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 14 (uninflected game names and `{mob}` after a linking article,
  "shumë" read as "very", predicate possessive, Gheg `çka`), Tier 2 = 3
  (admin imperative number), Tier 3 = 26 (gendered `lojtarit {player}`,
  bare feminine safe / unsafe labels, clitic doubling of indefinite
  objects), all applied. 43 review keys plus 3 self-review fixes, rechecked
  after. Findings: `reviews/sq_AL-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `sq_AL: name: "Shqip"`.
- Native-speaker review: not performed; recommended before release.

### is_IS -- Íslenska (Icelandic)

- Final catalog SHA-256: `db820a5ab48d5deada8b6f0e8c74e976ae3f2fbdc6c431a31d1bb565de884ab5` (identical in the run directory and
  `src/main/resources/lang/is_IS.yml`; NFC-normalized)
- Voice: standard Icelandic, informal singular `þú` for instructions as
  Icelandic software does and the infinitive for buttons, `„“` quotes,
  decimal comma; no gendered predicate about a player (finite verbs,
  `{player} á leik`, "welcome" as `Góða skemmtun í …`); placeholders only in
  nominative slots; the comma-list RPS name quoted in running text;
  `gjafari`, Baccarat `Leikmaður` / `Banki`, `Innleysa`, RTP
  `útborgunarhlutfall` (not the refund word `endurgreiðsla`), turn `röð` vs
  round `umferð`. Recorded in the fifth continuation of the §H table.
- Structural: helper strict check 0 errors, 0 residue warnings (stray soft
  hyphens found in drafting were removed and the helper now rejects
  invisible characters); `localizationCandidateCheck` CANDIDATE OK (1184);
  full `localizationCheck` with all forty-two new locales registered: every
  one of the 48 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 5 (seat-unavailable read as "not vacant", `Engin` / `Enginn`
  agreement, "stöðugar sveiflur" inverting the low-risk preset, Same Rank
  labelled with the value word), Tier 2 = 1 (turn vs round), Tier 3 = 21
  (dative placeholder slots, "lokað fyrir veðmál", RTP read as a refund,
  bare comma-list game name), all applied. 27 review keys plus 7
  self-review fixes (the remaining RTP lines, `current-player-turn`,
  `partial-return`), rechecked after. Findings: `reviews/is_IS-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `is_IS: name: "Íslenska"`.
- Native-speaker review: not performed; recommended before release.

### kk_KZ -- Қазақша (Kazakh)

- Final catalog SHA-256: `a53dff5300b4692523e2d4bb53e44d913e4b3e39715b8794bb12fe527216b928` (identical in the run directory and
  `src/main/resources/lang/kk_KZ.yml`; NFC-normalized)
- Voice: standard literary Kazakh in Cyrillic, polite `Сіз` for
  instructions and verbal nouns for buttons, `«»` quotes, decimal comma;
  no grammatical gender. Nothing attaches to a placeholder: governing nouns
  carry the case (`{game} ойынына`), ranges use an en dash
  (`{min}–{max} аралығында`) because `мен/бен/пен` must agree with the
  preceding sound, names sit after a colon or in a parenthesis; `ұтылу`
  (lose) is kept apart from `ұтып алу` (win), `шығын` only for loss;
  `дилер`, Baccarat `Ойыншы` / `Банкир`, slots run `тізбек` vs streak
  `серия`. Recorded in the fifth continuation of the §H table.
- Structural: helper strict check 0 errors, 0 residue warnings; a
  catalog-wide scan found no suffix, hyphenated suffix or harmony-dependent
  particle after any placeholder once the fixes landed;
  `localizationCandidateCheck` CANDIDATE OK (1184); full `localizationCheck`
  with all forty-three new locales registered: every one of the 49 locales
  OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 8 (`{min} мен {max}` harmony in five ranges, the Leave door as
  "Тұру" (stand / stay), "ұтылған" turning "already won" into "already
  lost"), Tier 2 = 1 (Russian "Шарик"), Tier 3 = 32 (calques such as
  "күйіп кетті", "шығын" read as loss in the cost line, double
  locatives), 39 applied and 2 declined with reasons (court-card names
  jack / queen, left for native review). 39 review keys plus 4
  self-review fixes (the matching reset question and inventory-full
  lines), rechecked after. Findings: `reviews/kk_KZ-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `kk_KZ: name: "Қазақша"`.
- Native-speaker review: not performed; recommended before release
  (including the court-card register).

### ka_GE -- ქართული (Georgian)

- Final catalog SHA-256: `5bd7305723a5adf2698d7de4b6e99037421358a82542f2474dc8ff57a0aacef4` (identical in the run directory and
  `src/main/resources/lang/ka_GE.yml`; NFC-normalized)
- Voice: standard literary Georgian in Mkhedruli without Mtavruli capitals
  (all-caps English lines stay in ordinary Mkhedruli), polite plural
  `თქვენ` forms for instructions and verbal nouns for buttons, `„“`
  quotes, decimal comma; no grammatical gender. The 2pl aorist equals the
  polite imperative in spelling, so reported player actions use labels,
  passives or state forms; placeholders stand only in nominative slots
  (a real noun carries the dative / genitive and the value follows a
  colon), ranges use an en dash; `დილერი`, Baccarat `მოთამაშე` /
  `ბანკირი`, slots run `მიმდევრობა` vs streak `სერია`, vines `ლიანა`.
  Recorded in the fifth continuation of the §H table.
- Structural: helper strict check 0 errors, 0 residue warnings; scans
  found no Mtavruli, no mixed Latin/Georgian words and no suffix (plain or
  hyphenated) after a placeholder; `localizationCandidateCheck` CANDIDATE
  OK (1184); full `localizationCheck` with all forty-four new locales
  registered: every one of the 50 locales OK (1184), no new warnings;
  `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 9 (seven aorist lines readable as commands, the insurance
  payout read as paying the premium, "less profitable line" in the
  variance trade-off), Tier 2 = 2 (click verb, "cover" rendered two ways),
  Tier 3 = 31 (calques such as "ინფორმაცია თამაშზე" / "სახელით", "-ისას"
  on a noun, hyphenated "10-ქულიანი", stack top / bottom), all applied.
  42 review keys plus 10 self-review fixes (remaining result lines to
  label forms, every "cover" line to "გადახდა"), rechecked after. The
  draft had already replaced 20 aorist lines before review. Findings:
  `reviews/ka_GE-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `ka_GE: name: "ქართული"`.
- Native-speaker review: not performed; recommended before release, with
  an in-game check that the Minecraft font renders Mkhedruli.
