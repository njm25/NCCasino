# Run new-locale-batch-2

- Date: 2026-09-25
- Operation type (guide §A): **New locale** generation, then candidate
  promotion into `src/main/resources/lang/`, for locales chosen by the agent.
- Authorization: after the seven locales of run `new-locale-batch-1` were
  finished, the repository user explicitly asked in chat to "choose more
  languages and keep going in the same style". The expansion was flagged in
  chat before starting; each locale here is otherwise handled exactly like
  batch 1. Registration in `locales.yml` is again left to the user.
- Selection (no bStats data available for these; judged by Minecraft
  community size and server-scene demand, in this order): ko_KR, pl_PL, it_IT,
  id_ID, zh_TW, uk_UA, cs_CZ, sv_SE.
- Base commit for this run: `b2e251b6f63a3ffcc2c070c4cb64ddfef6dd9b18`
- English source: `src/main/resources/lang/en_US.yml`
  SHA-256 `3c95538cdff1143654851e228290f165f97826d53431986029ae3ba9b49e6670`
  (unchanged since batch 1; 1184 translatable entries)
- Process, provenance and the isolation limitation are identical to
  `new-locale-batch-1/manifest.md`.

## Per-locale record

### ko_KR -- 한국어 (Korean)

- Final catalog SHA-256: `e56f4f05eab98a536a7a7d5a355eb42a937c5272d1886933f4728b9ff2c5cc8f` (identical in the run directory and
  `src/main/resources/lang/ko_KR.yml`; NFC-normalized)
- Voice: polite 합니다/하세요 for messages, noun-style labels; no 당신.
  Placeholders never take an attached or spaced particle (colon labels,
  following nouns, spaced counters instead) -- recorded in the guide.
  Dealer `딜러`, Baccarat Banker side `뱅커`.
- Structural: helper strict check 0 errors / 0 residue warnings (plus a
  dedicated scan for particles spaced after placeholders or colour codes);
  `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all eight new locales registered: every one of
  the 14 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0 claimed; Tier 2 = 1, Tier 3 = 19, all applied. One reviewer
  flagged `coin-flip.chain-win` / `rock-paper-scissors.chain-win` for a
  code check: `{amount}` is the current compounded pot (what cashing out
  pays now), not the next win's prize, so the draft "if you win once more:
  {amount}" was a real Tier 1 and was rewritten as "current pot"; a
  registry entry was added to the guide. The three blackjack bet-spot
  strings were also moved from `자리` (the pinned seat term) to
  `베팅 서클`. 25 keys patched, rechecked after. Findings:
  `reviews/ko_KR-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `ko_KR: name: "한국어"`.
- Native-speaker review: not performed; recommended before release.
- Out-of-scope observation (reported, not changed): with the chain-win fact
  above, several already-promoted catalogs render `{amount}` as the prize
  of the next win rather than the current pot -- fi_FI ("niin voit voittaa"),
  ja_JP ("を狙いましょう"), ru_RU ("побороться за"), th_TH ("ลุ้น"),
  vi_VN ("cơ hội nhận") and the pre-existing zh_CN ("争取"). The English
  "for {amount}" is itself ambiguous; a targeted refinement of those two
  keys is suggested if the owner wants the stricter reading.

### pl_PL -- Polski (Polish)

- Final catalog SHA-256: `fdaaeb3a4bf2009a382f5d6da079c41867b05508b14b38ddfc395620c1a92266` (identical in the run directory and
  `src/main/resources/lang/pl_PL.yml`; NFC-normalized)
- Voice: informal `ty`; no gendered second-person past tense or
  adjectives; placeholders behind colon labels or governing nouns; dealer
  `krupier`, Baccarat Banker side `Bankier` (recorded in the guide).
- Structural: helper strict check 0 errors (4 residue warnings, all false
  positives: Polish `to`/`a` and the shared `/ncc create|delete`
  usage text); `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all nine new locales registered: every one of
  the 15 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 3 (demo strings used `wymiana waluty`, i.e. currency
  conversion; fixed), Tier 2 = 11, Tier 3 = 24. All applied, with one
  deviation: the suggested `seria obrotów` for the Auto Spin "batch" would
  have collided with the pinned win-streak term `seria`, so
  `od ich uruchomienia` was used instead. `slots.auto-summary-unlimited`
  was confirmed against `SlotsMachine.spinLimitDisplay` (it fills
  `{spins}`) before changing it to `nieograniczony`. 40 keys patched,
  rechecked after. Findings: `reviews/pl_PL-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `pl_PL: name: "Polski"`.
- Native-speaker review: not performed; recommended before release.

### it_IT -- Italiano (Italian)

- Final catalog SHA-256: `7998073d25daf19d2100eebb55c5242dd5cb9436696fa471fbdd85072309fcea` (identical in the run directory and
  `src/main/resources/lang/it_IT.yml`; NFC-normalized)
- Voice: informal `tu`; no gender-revealing agreement for the player;
  placeholders never take an article; dealer `croupier`, Baccarat Banker
  side `Banco`, so the house edge is `vantaggio della casa` (recorded in
  the guide).
- Structural: helper strict check 0 errors (48 residue warnings, all false
  positives from Italian `in`/`a`/`chat`/`round`, plus the two
  `Timer … (PvP)` lore lines using the accepted loanword);
  `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all ten new locales registered: every one of
  the 16 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0; Tier 2 = 2, Tier 3 = 43, all applied. `blackjack-settings.enabled`
  / `disabled` were confirmed against `BlackjackMenu` to fill labels of
  both genders before switching them to `Sì` / `No`; the other on/off
  states moved to participles. 64 keys patched, rechecked after. Findings:
  `reviews/it_IT-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `it_IT: name: "Italiano"`.
- Native-speaker review: not performed; recommended before release.

### id_ID -- Bahasa Indonesia (Indonesian)

- Final catalog SHA-256: `e754ce27d47b5511d0054a3bb603f995d9ba809cfed7b1ca05d960e383c1f8c7` (identical in the run directory and
  `src/main/resources/lang/id_ID.yml`; NFC-normalized)
- Voice: casual `kamu`; dealer `bandar`, Baccarat Banker side `Bankir`,
  house edge `keunggulan kasino`; chip `keping`, multiplier `pengali`
  (recorded in the guide).
- Structural: helper strict check 0 errors (2 residue warnings: the shared
  `/ncc create|delete` usage text); `localizationCandidateCheck`
  CANDIDATE OK (1184); full `localizationCheck` with all eleven new
  locales registered: every one of the 17 locales OK (1184), no new
  warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0; Tier 2 = 1, Tier 3 = 23, all applied, including the optional
  rename of Dragon Descent from `Penurunan Naga` ("dragon's decline") to
  `Turun ke Sarang Naga` across every key. 26 keys patched plus the
  catalog-wide rename, rechecked after. Findings:
  `reviews/id_ID-findings-*.md`.
- Not registered in `locales.yml`. Registry line:
  `id_ID: name: "Bahasa Indonesia"`.
- Native-speaker review: not performed; recommended before release.
