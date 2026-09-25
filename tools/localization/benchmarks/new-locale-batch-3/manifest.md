# Run new-locale-batch-3

- Date: 2026-09-25
- Operation type (guide §A): **New locale** generation, then candidate
  promotion into `src/main/resources/lang/`, for locales chosen by the agent.
- Authorization: the repository user asked in chat to keep choosing more
  languages "in the same style" once the requested ones were done; this run
  continues batch 2 under that instruction. Registration in `locales.yml` is
  again left to the user.
- Selection (next Minecraft server communities not yet covered, in this
  order): hu_HU, ro_RO, pt_PT, da_DK, nb_NO, el_GR, sk_SK, bg_BG.
- Base commit for this run: `1cca47daa78e19282dc81d017db59b8c4aa95199`
- English source: `src/main/resources/lang/en_US.yml`
  SHA-256 `3c95538cdff1143654851e228290f165f97826d53431986029ae3ba9b49e6670`
  (unchanged since batch 1; 1184 translatable entries)
- Process, provenance and the isolation limitation are identical to
  `new-locale-batch-1/manifest.md`; reviewers also receive the rubric
  additions made in batch 2 (chain-win pot, paying lines).

## Per-locale record

### hu_HU -- Magyar (Hungarian)

- Final catalog SHA-256: `149c626e96946f87a647c30424236db713a4f5813e5b437affeddb00bbc96675` (identical in the run directory and
  `src/main/resources/lang/hu_HU.yml`; NFC-normalized)
- Voice: informal `te`; suffixes never attached to placeholders (colon
  labels, parentheses, suffixed following nouns); dealer `osztó`, Baccarat
  `Játékos` / `Bankár`, house edge `házelőny`; Slots game `Nyerőgép`
  (recorded in the guide, in a new second continuation of the §H table).
- Structural: helper strict check 0 errors (102 residue warnings, all
  Hungarian `a`/`be`/`is` false positives); `localizationCandidateCheck`
  CANDIDATE OK (1184); full `localizationCheck` with all sixteen new
  locales registered: every one of the 22 locales OK (1184), no new
  warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 2 (`max-chain-hit` read as the dealer's own record streak rather
  than a configured cap; fixed), Tier 2 = 8, Tier 3 = 28, all applied, plus
  the overflow "drop" wording moved to `leejtés`. 47 keys patched, rechecked
  after. Findings: `reviews/hu_HU-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `hu_HU: name: "Magyar"`.
- Native-speaker review: not performed; recommended before release.
