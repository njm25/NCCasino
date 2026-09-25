# Run new-locale-batch-1

- Date: 2026-09-25
- Operation type (guide §A): **New locale** generation for each listed locale,
  followed by **candidate promotion** into `src/main/resources/lang/` under
  explicit pre-authorization from the repository user for exactly these
  locales. Registration in `locales.yml` is intentionally left to the user's
  own follow-up (the user asked to wire the language menu themselves).
- Base commit: `e7a5f098eac854d56225607af1002b8e341c1921` (main tip at run start)
- English source: `src/main/resources/lang/en_US.yml`
  SHA-256 `3c95538cdff1143654851e228290f165f97826d53431986029ae3ba9b49e6670`
  (1184 translatable entries)
- Registry at run start: `src/main/resources/lang/locales.yml`
  SHA-256 `6f46ab59ee4cea7383147b17aa2b3da0d30e196e476fa838865b2a45662a1c78`
- Production baseline: none for any listed locale (all are new).
- Provenance: translator = the coordinating coding agent session; independent
  reviewers = fresh sub-agent contexts that received only a neutral rubric
  (guide §L tiers + §C context notes) and key / English / value packets --
  no filenames, hashes, provenance, or generation prompt.
- Isolation limitation (REVIEW_PROTOCOL.md §3.2 item 5): reviewer isolation is
  instruction-based. The reviewer sub-agents technically had filesystem tools
  and were told to read only the rubric and their packet. With a single
  candidate per locale there is no identity map to protect, but this is still
  disclosed rather than claimed as tool-enforced isolation.
- Protocol: TRANSLATION_GUIDE.md §A (new locale) + REVIEW_PROTOCOL.md §3–§7
  adapted for a single candidate per locale.

## Per-locale record

Each entry is appended when that locale is finished.

### nl_NL -- Nederlands (Dutch)

- Final catalog SHA-256: `31c9911939ca60cd1a99522abc1e77c03b25e4b3a99d65473970b6ec5863b49c` (identical bytes in the run directory and
  `src/main/resources/lang/nl_NL.yml`)
- Voice: informal `je`/`jij`, Netherlands Dutch (recorded in the guide).
- Structural: helper strict check 0 errors; `localizationCandidateCheck`
  CANDIDATE OK (1184); full `localizationCheck` with nl_NL temporarily
  registered: nl_NL OK (1184), all six existing locales unchanged.
- Remaining warning: `blackjack.round-summary-hand-blackjack` is identical to
  English because "Hand {number}: Blackjack! +{amount}" is also correct Dutch
  (same precedent as de_DE).
- Independent review (2 isolated reviewers, 592 keys each, every key):
  Tier 0 = 0, Tier 1 = 0, Tier 2 = 10, Tier 3 = 30. All 10 Tier 2 and all 30
  Tier 3 suggestions were applied, plus one coordinator-found Tier 2 term drift
  (`mines.dealer-cannot-cover`): 41 keys patched, candidate rechecked after
  patching. Findings: `reviews/nl_NL-findings-*.md`.
- Not registered in `locales.yml` (user's follow-up). Registry line:
  `nl_NL: name: "Nederlands"`.
- Native-speaker review: not performed; recommended before release.

### fi_FI -- Suomi (Finnish)

- Final catalog SHA-256: `bd89ab232bf7e3179646f906decbe3685cdedc16241ab9f5c2dad623a89edce9` (identical in the run directory and
  `src/main/resources/lang/fi_FI.yml`)
- Voice: informal `sinä`, standard written Finnish; placeholders never take
  case endings (recorded in the guide).
- Structural: helper strict check 0 errors / 0 residue warnings;
  `localizationCandidateCheck` CANDIDATE OK (1184); full `localizationCheck`
  with nl_NL + fi_FI temporarily registered: fi_FI OK (1184) with no warnings,
  all other locales unchanged.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 6, Tier 2 = 12, Tier 3 = 52. All Tier 1 fixed (one with a
  different wording than suggested, because the suggestion would have added a
  protected `Vault` literal); all Tier 2 fixed; Tier 3 applied except keeping
  the parser-verified "2,5 %" example. 71 keys patched, rechecked after.
  Findings: `reviews/fi_FI-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `fi_FI: name: "Suomi"`.
- Native-speaker review: not performed; recommended before release.

### ja_JP -- 日本語 (Japanese)

- Final catalog SHA-256: `8c2dabf8b4522cfbd5add6f61de375fc08d306cf04da5484ac18c4d66204eeeb` (identical in the run directory and
  `src/main/resources/lang/ja_JP.yml`)
- Voice: polite です/ます; noun-style labels; half-width space after a
  placeholder before a particle/counter (recorded in the guide).
- Structural: helper strict check 0 errors (only "Shift" key-name residue
  flags); `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with nl_NL + fi_FI + ja_JP registered: ja_JP OK (1184),
  no ja_JP warnings, all other locales unchanged.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 1 (profile-name character rule, confirmed against
  `SlotsProfileName` and fixed), Tier 2 = 6, Tier 3 = 41; all applied, plus
  a coordinator consistency sweep of " -- " in Slots keys. 54 keys patched,
  rechecked after. Findings: `reviews/ja_JP-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `ja_JP: name: "日本語"`.
- Native-speaker review: not performed; recommended before release.

### ru_RU -- Русский (Russian)

- Final catalog SHA-256: `dc43e4e5a12857e7dcf2795ce347fe291b43169451c2dbf14cb25be105552fb7` (identical in the run directory and
  `src/main/resources/lang/ru_RU.yml`)
- Voice: polite `вы`; ЛКМ/ПКМ; placeholders never declined (recorded in the
  guide).
- Structural: helper strict check 0 errors (only "Shift" key-name flags);
  `localizationCandidateCheck` CANDIDATE OK (1184); full `localizationCheck`
  with the four new locales registered: ru_RU OK (1184), no ru_RU warnings,
  all other locales unchanged.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 1 (`betting.inventory-full` called refunds "winnings"; confirmed
  against `creditPlayer` and fixed), Tier 2 = 13, Tier 3 = 69; all applied,
  plus coordinator consistency fixes. 89 keys patched, rechecked after.
  Findings: `reviews/ru_RU-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `ru_RU: name: "Русский"`.
- Native-speaker review: not performed; recommended before release.

### th_TH -- ไทย (Thai)

- Final catalog SHA-256: `783ca78d650d45ac46246cdbdb10d4d21552e8b889098c6e1efbbd8f61dd7614` (identical in the run directory and
  `src/main/resources/lang/th_TH.yml`)
- Voice: neutral polite UI Thai, no gendered particles; Banker = แบงเกอร์ kept
  distinct from dealer ดีลเลอร์ (recorded in the guide).
- Structural: helper strict check 0 errors (only "Shift" key-name flags);
  `localizationCandidateCheck` CANDIDATE OK (1184); full `localizationCheck`
  with the five new locales registered: th_TH OK (1184), no th_TH warnings,
  all other locales unchanged.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 1 (roulette "fewer bets" read as "smaller bets"; fixed),
  Tier 2 = 2, Tier 3 = 44; all applied, with the reviewers' phrase groups
  (invalid-action, RTP term) extended catalog-wide. 63 keys patched,
  rechecked after. Findings: `reviews/th_TH-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `th_TH: name: "ไทย"`.
- Native-speaker review: not performed; recommended before release.
