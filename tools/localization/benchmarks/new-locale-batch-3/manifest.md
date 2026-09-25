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

### ro_RO -- Română (Romanian)

- Final catalog SHA-256: `0a392ebe82c60d6e7dae95fbe24dccb86e89da9f9e93a82f9bc5cbab790023ec` (identical in the run directory and
  `src/main/resources/lang/ro_RO.yml`; NFC-normalized, comma-below ș/ț only)
- Voice: informal `tu`, gender-neutral toward the player; number
  placeholders kept out of noun-agreement positions (colon labels,
  parentheses); dealer `crupier`, Baccarat `Jucător` / `Bancher`, house
  edge `avantajul casei`; Coin Flip `Cap sau pajură`, Mines `Câmp minat`
  (`Mine` collides with `la mine`, "at my place"), Slots `Sloturi` with a
  run `șir` distinct from the PvE streak `serie` (recorded in the guide).
- Structural: helper strict check 0 errors (residue warnings are Romanian
  infinitive `a`, the loanword `chat`, and the shared `/ncc create` usage
  lines -- all false positives); `localizationCandidateCheck` CANDIDATE OK
  (1184); full `localizationCheck` with all seventeen new locales
  registered: every one of the 23 locales OK (1184), no new warnings;
  `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0, Tier 2 = 1 (`slots.auto-settings-reset` Auto Spin drift),
  Tier 3 = 45, all applied (numeral agreement for `{rounds}`/`{max}`/
  `{amount}`, "Pot" read as a verb, `Constantă` variance label, rail
  "below" controls, timer prompts, refund agreement), plus the three sibling
  `inventory-full` keys aligned. 49 keys patched, rechecked after.
  Findings: `reviews/ro_RO-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `ro_RO: name: "Română"`.
- Native-speaker review: not performed; recommended before release.

### pt_PT -- Português (Portugal) (European Portuguese)

- Final catalog SHA-256: `9651acda7b36dbfee3e6fbdb61b4d816e2f951a91d480bb5aac088f2bceb21af` (identical in the run directory and
  `src/main/resources/lang/pt_PT.yml`; NFC-normalized)
- Voice: European Portuguese, AO90 spelling, informal `tu` with European
  clitics and `a + infinitive`; written from the English source, not
  adapted from pt_BR (`ronda`, `definições`, `guardar`, `eliminar`,
  `croupier`); gender-neutral welcome `Boas-vindas a {game}`; cash-out
  `Retirar`, Baccarat `Jogador` / `Banca`, Slots `Slots` with `rolos`,
  `filas`, `linhas de pagamento`, run `sequência` vs streak `série`
  (recorded in the guide).
- Structural: helper strict check 0 errors (residue warnings are Portuguese
  `a` and the loanword `chat` -- false positives); Brazilian-form scan
  clean; `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all eighteen new locales registered: every one
  of the 24 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 2 (`dragon-descent.rebet-reset` "reposta" read as the wager
  being put back -- the stack is cleared; `roulette.spin-results`
  "rodada" read as a pt_BR round -> "giro"), Tier 2 = 2 (Auto Spin settings
  name, rebet feature name), Tier 3 = 26, all applied, plus the sibling
  Baccarat rebet and seated notices and the loss-limit pair aligned.
  35 keys patched, rechecked after. Findings: `reviews/pt_PT-findings-*.md`.
- Not registered in `locales.yml`. Registry line:
  `pt_PT: name: "Português (Portugal)"`.
- Native-speaker review: not performed; recommended before release.

### da_DK -- Dansk (Danish)

- Final catalog SHA-256: `4bf07f5950ff182bb459d7b05c424b9659692e858210e12488acfe3f0db1d9b1` (identical in the run directory and
  `src/main/resources/lang/da_DK.yml`; NFC-normalized)
- Voice: informal `du`, closed compounds; `dealer`, Baccarat `Spiller` /
  `Bank`, bet `indsats`, cash-out `Indkasser`, all in `All-in` (Den
  Danske Ordbog loanword; both reviewers rejected the calque `Alt ind`);
  Slots `Spilleautomat` with `hjul`, `rækker`, `gevinstlinjer` and run
  `stribe` vs streak `stime`; dealer timer `nedtælling` (`timer` means
  hours); turn lines `{player} har tur` (no genitive `-s` on a
  placeholder). Recorded in the guide.
- Structural: helper strict check 0 errors (residue warnings are Danish
  `for`/`at`/`to`/`have`, the loanwords `dealer` and `All-in`, and the
  shared `/ncc` usage and `Blackjack!` lines -- false positives);
  `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all nineteen new locales registered: every one
  of the 25 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0, Tier 2 = 4 (cash-out notice wording/conditional, leftmost
  reel, `Standardhjul`), Tier 3 = 43, all applied, with catalog-wide
  alignment of `All-in`, "tage plads", "Indsatsrunden er lukket" and
  unaccented imperatives. 56 keys patched, rechecked after.
  Findings: `reviews/da_DK-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `da_DK: name: "Dansk"`.
- Native-speaker review: not performed; recommended before release.
