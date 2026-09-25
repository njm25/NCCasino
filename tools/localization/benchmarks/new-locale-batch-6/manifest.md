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
