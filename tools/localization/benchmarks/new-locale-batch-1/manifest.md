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
