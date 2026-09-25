# Run new-locale-batch-5

- Date: 2026-09-25
- Operation type (guide §A): **New locale** generation, then candidate
  promotion into `src/main/resources/lang/`, for locales chosen by the agent.
- Authorization: the repository user asked in chat to keep choosing more
  languages "in the same style" once the requested ones were done; this run
  continues batch 4 under that instruction. Registration in `locales.yml` is
  again left to the user.
- Selection (Minecraft server communities not yet covered, left-to-right
  scripts first; right-to-left languages are deferred until chat and
  inventory-title bidi rendering has been checked in game): ms_MY, fil_PH,
  gl_ES, af_ZA, be_BY, hi_IN.
- Base commit for this run: `e6fdaca1473a97d8df52aaf6eaeb0e1ce9bdc925`
- English source: `src/main/resources/lang/en_US.yml`
  SHA-256 `3c95538cdff1143654851e228290f165f97826d53431986029ae3ba9b49e6670`
  (unchanged since batch 1; 1184 translatable entries)
- Process, provenance and the isolation limitation are identical to
  `new-locale-batch-1/manifest.md`; reviewers receive the batch-4 rubric,
  which now also carries the verified `seat-unavailable` note. Glossary
  columns go into a fourth continuation of the §H table.

## Per-locale record

### ms_MY -- Bahasa Melayu (Malaysian Malay)

- Final catalog SHA-256: `9003cc0433f1079c22483a91244ac256cbd52c8f68aa6229fa1dfc22cf98e542` (identical in the run directory and
  `src/main/resources/lang/ms_MY.yml`; NFC-normalized)
- Voice: standard Malaysian Malay (DBP spelling), `anda` address, decimal
  point; written from the English source with Malaysian vocabulary, not
  adapted from id_ID (Indonesian-form scan clean); no gender or number
  agreement; dealer `pengendali` (not `bandar`), Baccarat `Pemain` /
  `Jurubank`, `taruhan`, cash-out `Tunaikan`, Coin Flip `Lambung Syiling`,
  Mines `Periuk Api`, Slots `Mesin Slot` with `gelendong`, run `urutan` vs
  streak `berturut-turut`. Recorded in a new fourth continuation of the §H
  table.
- Structural: helper strict check 0 errors (two token-order slips in the
  mode-switch cash-out notices caught and fixed before review; 2 residue
  warnings are the `/ncc` usage lines); `localizationCandidateCheck`
  CANDIDATE OK (1184); full `localizationCheck` with all thirty-two new
  locales registered: every one of the 38 locales OK (1184), no new
  warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0, Tier 2 = 5 (rebet and cash-out term drift, slots run vs
  streak), Tier 3 = 34 (Coin Flip renamed to match the left/right mechanic,
  verified in `CoinFlipClient`; object-less buttons; capitalized game
  names), all applied; Coin Flip rename carried into 4 settings keys. 39
  review keys plus 3 self-review fixes, rechecked after. Findings:
  `reviews/ms_MY-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `ms_MY: name: "Bahasa Melayu"`.
- Native-speaker review: not performed; recommended before release.
