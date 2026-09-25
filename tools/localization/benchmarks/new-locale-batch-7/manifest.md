# Run new-locale-batch-7

- Date: 2026-09-25
- Operation type (guide §A): **New locale** generation, then candidate
  promotion into `src/main/resources/lang/`, for locales chosen by the agent.
- Authorization: the repository user asked in chat to keep choosing more
  languages "in the same style" once the requested ones were done; this run
  continues batch 6 under that instruction. Registration in `locales.yml` is
  again left to the user.
- Selection (Minecraft server communities not yet covered, left-to-right
  scripts only; right-to-left languages remain deferred until chat and
  inventory-title bidi rendering has been checked in game): bs_BA, hy_AM,
  uz_UZ, sw_KE, ga_IE, mn_MN, bn_BD, ta_IN.
- Base commit for this run: `c6988502cd762ffab984f1d0bf181ee717e0de07`
- English source: `src/main/resources/lang/en_US.yml`
  SHA-256 `3c95538cdff1143654851e228290f165f97826d53431986029ae3ba9b49e6670`
  (unchanged since batch 1; 1184 translatable entries)
- Process, provenance and the isolation limitation are identical to
  `new-locale-batch-1/manifest.md`; reviewers receive the batch-4 rubric
  with the `mob-settings.none` / `admin.none` and house-edge parser notes.
  Glossary columns go into a sixth continuation of the §H table.

## Per-locale record

### bs_BA -- Bosanski (Bosnian)

- Final catalog SHA-256: `2f7127aa34ae979d463eecc4fda404d2b6ef0b6a3d82378fa77d0a4acd6844fe` (identical in the run directory and
  `src/main/resources/lang/bs_BA.yml`; NFC-normalized)
- Voice: standard ijekavian Bosnian in Latin script, informal `ti` like
  the hr_HR / sr_RS siblings, `„“` quotes, decimal comma; Bosnian UI
  vocabulary (`meni`, `dugme`, `sačuvati`, `sto`, `sprat`, `kolona`,
  `makaze`, `procenat`, `vjerovatnoća`, `komanda`), never Croatian-only or
  ekavian forms; gender-neutral outcomes (nouns, present, passive);
  number placeholders kept out of 1 / 2-4 / 5+ agreement; genitive
  template slots; `diler`, Baccarat `Igrač` / `Bankar`, `ulog` vs
  `opklada`, `niz` vs `serija`. Recorded in the sixth continuation of the
  §H table.
- Structural: helper strict check 0 errors, 0 residue warnings (with a
  documented allow-list for Bosnian `a` and the in-game loanword `chat`);
  scans for gendered 2sg perfect forms and Croatian-only vocabulary came
  back clean; `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all forty-six new locales registered: every one
  of the 52 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 8 (profile-name length in 1 / 2-4 / 5+ agreement with
  `MAX_LENGTH` 24, non-standard ace plural "aseve"), Tier 2 = 2 (ON/OFF
  form), Tier 3 = 19 ("jackpot" spelling, "od strane igrača" calque,
  "uginuo" for a humanoid mob, dealing vs splitting), all applied.
  29 review keys plus 1 self-review fix (a gendered "nisi bio" caught in
  drafting), rechecked after. Findings: `reviews/bs_BA-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `bs_BA: name: "Bosanski"`.
- Native-speaker review: not performed; recommended before release.

### hy_AM -- Հայերեն (Armenian)

- Final catalog SHA-256: `beb078a9d5987d4b15d065adfa82b71eda0bc1dd43820625e8a73dd7a3d42b65` (identical in the run directory and
  `src/main/resources/lang/hy_AM.yml`; NFC-normalized)
- Voice: standard Eastern Armenian (reformed orthography), polite plural
  `Դուք`, `«»` quotes, decimal comma, Armenian punctuation (`։` ends every
  sentence including questions, `՞` on the questioned word, `՝` before a
  label value); no grammatical gender; placeholders never take a case
  ending or article (after `՝`, in parentheses, or before the noun that
  carries the ending); `դիլեր`, Baccarat `Խաղացող` / `Բանկիր`, slot return
  `վճարում`, RTP as a percentage; native court cards. Recorded in the sixth
  continuation of the §H table.
- Structural: helper strict check 0 errors, 0 residue warnings; a scan of
  Armenian letters (not the Armenian punctuation block) found no suffix
  after a placeholder and no mixed-script word; `localizationCandidateCheck`
  CANDIDATE OK (1184); full `localizationCheck` with all forty-seven new
  locales registered: every one of the 53 locales OK (1184), no new
  warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0, Tier 2 = 2 (slot return read as "give back", two wordings
  for busted), Tier 3 = 43 (article -ը / -ն before vowels, standard
  genitive "շահման", subjunctive after "նախքան", "աջակցում" and "ընթացքի
  մեջ" calques, Russian-loan court cards and "Բակկարա"), all applied.
  45 review keys plus 16 self-review fixes (questions ending in "։", the
  column and Baccarat terms through the settings keys, the split-rule
  court card, one more "աջակցում"), rechecked after. Findings:
  `reviews/hy_AM-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `hy_AM: name: "Հայերեն"`.
- Native-speaker review: not performed; recommended before release, with
  an in-game check of Armenian letters and `։` / `՞` / `՝` in the font.
