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
