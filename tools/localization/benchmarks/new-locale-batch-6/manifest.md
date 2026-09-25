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
