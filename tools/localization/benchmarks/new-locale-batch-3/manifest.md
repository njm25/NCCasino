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

### nb_NO -- Norsk bokmål (Norwegian Bokmål)

- Final catalog SHA-256: `18f7591aea0d5c76aafe44aa44228a3df7fbab00f162131579ab4fe5b301f41f` (identical in the run directory and
  `src/main/resources/lang/nb_NO.yml`; NFC-normalized)
- Voice: moderate Bokmål, informal `du`, written from the English source
  rather than adapted from da_DK; `dealer`, Baccarat `Spiller` / `Bank`,
  cash-out `Ta ut`, `All-in`; `Preferanser` vs `innstillinger`; Slots
  `Spilleautomat` with run `serie` ("etter hverandre") vs streak
  `rekke`; dealer timer `nedtelling`; turn lines `Turen til {player}`
  (Bokmål `ha tur` = be lucky). Recorded in the guide.
- Structural: helper strict check 0 errors (residue warnings are Norwegian
  `for`/`at`/`to`, the loanword `All-in`, and the shared `/ncc` usage
  and `Blackjack!` lines -- false positives); Danish-form scan clean;
  `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all twenty new locales registered: every one of
  the 26 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 1 (`{player} har tur` reads as "{player} is lucky"; fixed in all
  five turn announcements), Tier 2 = 0, Tier 3 = 13, all applied.
  18 keys patched, rechecked after. Findings: `reviews/nb_NO-findings-*.md`.
- Not registered in `locales.yml`. Registry line:
  `nb_NO: name: "Norsk bokmål"`.
- Native-speaker review: not performed; recommended before release.

### el_GR -- Ελληνικά (Greek)

- Final catalog SHA-256: `496b96a7ed9d3e1aa8e0c6db0535c9455b9060f164642feefd30c599619ccd60` (identical in the run directory and
  `src/main/resources/lang/el_GR.yml`; NFC-normalized; no Latin homoglyphs
  inside Greek words)
- Voice: monotonic Greek, informal `εσύ`, gender-neutral toward the player;
  no article directly on an inserted name (`στο παιχνίδι {game}`,
  `Παίζει: {player}`); genitive fillers for `occupations.*` and the Dragon
  `{setting}` slot; `ντίλερ`, Baccarat `Παίκτης` / `Τράπεζα`, house edge
  `πλεονέκτημα του καζίνο`; Slots `Κουλοχέρης` with run `αλληλουχία` vs
  streak `σερί`. Recorded in the guide.
- Structural: helper strict check 0 errors, 0 residue warnings;
  `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all twenty-one new locales registered: every one
  of the 27 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 1 (`roulette.category-straight-up` "Μονός αριθμός" = odd
  number), Tier 2 = 7 (ON/OFF state wording, all-in wording), Tier 3 = 38,
  all applied, plus the `τραπουλών` spelling and the Roulette/Blackjack
  currency notices aligned; the guide's ON/OFF cell was corrected to match.
  49 keys patched, rechecked after. Findings: `reviews/el_GR-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `el_GR: name: "Ελληνικά"`.
- Native-speaker review: not performed; recommended before release.

### sk_SK -- Slovenčina (Slovak)

- Final catalog SHA-256: `7b817fea6b01b9b7da8c1559e41b1f24d04bedf055aaf1bfdf625c0fb305d4f7` (identical in the run directory and
  `src/main/resources/lang/sk_SK.yml`; NFC-normalized)
- Voice: informal `ty`, written from the English source rather than
  adapted from cs_CZ (Czech-letter scan clean); gender-neutral toward the
  player (no gendered second-person past tense: outcomes as nouns or present
  tense); number placeholders kept out of 1/2-4/5+ agreement; genitive
  fillers for `occupations.*` and the Dragon `{setting}` slot; `krupiér`,
  Baccarat `Hráč` / `Bankár`, Slots `Výherný automat` with run `rad` vs
  streak `séria`. Recorded in the guide.
- Structural: helper strict check 0 errors (3 residue warnings are Slovak
  `a`/`to` -- false positives); `localizationCandidateCheck` CANDIDATE OK
  (1184); full `localizationCheck` with all twenty-two new locales
  registered: every one of the 28 locales OK (1184), no new warnings;
  `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0, Tier 2 = 1 (Demo spin naming), Tier 3 = 39, all applied
  (genitive `lián`, "menu"/"meny" currency ambiguity, missing verbs,
  masculine `Neobmedzený` to agree with the limit). 42 keys patched,
  rechecked after. Findings: `reviews/sk_SK-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `sk_SK: name: "Slovenčina"`.
- Native-speaker review: not performed; recommended before release.

### bg_BG -- Български (Bulgarian)

- Final catalog SHA-256: `67adc4051a6577b6604eb2ea0f1517203cb4d6379b3422bc39029b71da8c61ce` (identical in the run directory and
  `src/main/resources/lang/bg_BG.yml`; NFC-normalized; no Russian-only
  letters, no Latin homoglyphs)
- Voice: informal `ти`, gender-neutral toward the player (aorist, present
  or nouns; plural `Добре дошли`); no article on inserted names; turns
  `ход`, slots rows `ред`, the RPS throw `избор`; `крупие`, Baccarat
  `Играч` / `Банкер`; Slots `Слот машина` with run `поредица` vs
  streak `серия`; the card shoe described as `кутията с тестетата`.
  Recorded in the guide.
- Structural: helper strict check 0 errors, 0 residue warnings;
  `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all twenty-three new locales registered: every
  one of the 29 locales OK (1184), no new warnings; `compileJava`
  succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0, Tier 2 = 9 (cancel as "decline", the RPS throw colliding with
  the turn term `ход`, the Auto Spin batch colliding with the run term),
  Tier 3 = 25, all applied, plus sibling seated notices, the Game Options
  reset prompt and the Baccarat odds labels aligned. 43 keys patched,
  rechecked after. Findings: `reviews/bg_BG-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `bg_BG: name: "Български"`.
- Native-speaker review: not performed; recommended before release.

## Batch summary

All eight batch-3 locales are complete. To expose them in the language menu,
append to `src/main/resources/lang/locales.yml` (after the batch-1 and
batch-2 lines):

```yaml
  hu_HU:
    name: "Magyar"
  ro_RO:
    name: "Română"
  pt_PT:
    name: "Português (Portugal)"
  da_DK:
    name: "Dansk"
  nb_NO:
    name: "Norsk bokmål"
  el_GR:
    name: "Ελληνικά"
  sk_SK:
    name: "Slovenčina"
  bg_BG:
    name: "Български"
```

With the batch-1, batch-2 and batch-3 lines appended, `localizationCheck`
reports all 29 locales OK at 1184 entries (verified in this run) and
`compileJava` succeeds. Remaining warnings are the seven pre-existing ones
plus the de_DE-precedent identity in nl_NL; batch 3 adds none. Every batch-3
locale was written from the English source (pt_PT, nb_NO and sk_SK were not
adapted from pt_BR, da_DK or cs_CZ) and passed a regional-variant scan.
Out-of-scope observation (not changed): da_DK was committed before the
Norwegian finding that `{player} har tur` can read as "is lucky" in
Bokmål; in Danish `har tur` is the ordinary game-rule phrasing for a turn,
so da_DK is left as is, but a Danish native check is worth adding to the
release review. Still needed before release: an in-game check (Greek and
Cyrillic glyph widths in inventory titles, line wrapping) and
native-speaker review.
