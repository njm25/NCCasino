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

### uz_UZ -- Oʻzbekcha (Uzbek)

- Final catalog SHA-256: `ec196795f25cdffd562cae5880499e767c1bea0fb1b5d591713b3c13796c540b` (identical in the run directory and
  `src/main/resources/lang/uz_UZ.yml`; NFC-normalized)
- Voice: standard literary Uzbek in the official Latin alphabet (`oʻ` /
  `gʻ` with U+02BB, tutuq belgisi U+02BC, no ASCII apostrophes), polite
  `Siz`, `«»` quotes, decimal comma; no grammatical gender; nothing attaches
  to a placeholder (governing nouns or postpositions carry the case, the
  counter `ta` follows numbers, en-dash ranges, digit ordinals keep their
  hyphen); `diler`, Baccarat `Oʻyinchi` / `Bankir`, `stavka`,
  `ketma-ketlik` vs `seriya`. Recorded in the sixth continuation of the §H
  table.
- Structural: helper strict check 0 errors, 0 residue warnings; the
  apostrophe code-point audit found only U+02BB (741 at draft time) and
  U+02BC (8), no ASCII or curly quotes; no suffix after a placeholder;
  `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all forty-eight new locales registered: every
  one of the 54 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 6 (misspelled suit "qarga", ungrammatical "Stavkalar qabul
  qilish yopildi" ×3, intransitive "urinmoq" with an object, the variance
  trade-off read as smaller payouts), Tier 2 = 2 (spin label, drag verb),
  Tier 3 = 28 ("duyjina", suffix written apart in "Vault dan", unmarked
  genitives, chip value vs size), all applied. 36 review keys plus 6
  self-review fixes (sibling currency and inventory lines), rechecked
  after. Findings: `reviews/uz_UZ-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `uz_UZ: name: "Oʻzbekcha"`.
- Native-speaker review: not performed; recommended before release, with
  an in-game check that the font renders U+02BB / U+02BC.

### sw_KE -- Kiswahili (Swahili)

- Final catalog SHA-256: `d51f3a5b2eacdee8bef5e66a9ed6d8ec09180afa3ba5683b2176bf80c9da387e` (identical in the run directory and
  `src/main/resources/lang/sw_KE.yml`; NFC-normalized)
- Voice: standard Kiswahili sanifu as used in Kenyan software, plain
  imperatives (`Bofya`, `Chagua`, `Andika`), `“ ”` quotes, decimal point;
  no grammatical gender and no gendered vocatives; noun-class agreement
  never depends on a placeholder (a governing noun carries the concord:
  `Kiasi cha {currency} hakitoshi`, `Zamu ya {player}`); `mgawaji`,
  Baccarat `Mchezaji` / `Benki`, `dau` / `madau`, `chungu` for the pot,
  slots run `msururu` vs PvE streak `mfululizo`, vines `mitambaa`.
  Recorded in the sixth continuation of the §H table.
- Structural: helper strict check 0 errors, 0 residue warnings;
  `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all forty-nine new locales registered: every
  one of the 55 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 8 (a class-10 concord agreeing with `{currency}`, the leftmost
  rule addressed to the player, "kushindwa" reversing "already won", a
  class-7 concord with no antecedent ×4, the move-dealer destination
  given to the admin), Tier 2 = 4 (`nambari` vs `namba` ×3, deck term),
  Tier 3 = 30 ("Sasa:" labels, "Rulet", "Kodi" read as tax, calqued "kwa
  bila kikomo", headline-style mode-switching label), all applied; one
  suggestion reworded so the protected `Vault` count stays equal to the
  English. 42 review keys plus 28 self-review follow-ups (sibling labels,
  titles and inventory lines), rechecked after. Findings:
  `reviews/sw_KE-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `sw_KE: name: "Kiswahili"`.
- Native-speaker review: not performed; recommended before release.

### ga_IE -- Gaeilge (Irish)

- Final catalog SHA-256: `aa45c47d426c083280be99f2a1a922944ae861098aee73f9d17b699b18a698fc` (identical in the run directory and
  `src/main/resources/lang/ga_IE.yml`; NFC-normalized)
- Voice: standard Irish (An Caighdeán Oifigiúil), singular `tú` with plain
  imperatives, `“ ”` quotes, decimal point; Caighdeán mutations (eclipsis
  after `leis an` / `chuig an` / `ar an`), but no mutating word or numeral
  rule ever governs a placeholder (colon labels, and `{n} líne` / `{n} ró`
  / `{n} ríl`, whose initials never show a mutation); `déileálaí`,
  Baccarat `Imreoir` / `Baincéir`, `geall`, `airgead buaite`, slots run
  `seicheamh` vs PvE streak `sraith`, vines `féithleoga`, RTP `ráta
  íocaíochta` kept apart from the refund word `aisíoc`. Recorded in the
  sixth continuation of the §H table.
- Structural: helper strict check 0 errors, 0 residue warnings (Irish `a`,
  `an`, `in`, `is` added to the helper's per-locale function-word
  allowlist); `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all fifty new locales registered: every one of
  the 56 locales OK (1184), no new warnings; `compileJava` succeeds.
- Self-review before the independent review replaced the gender-uncertain
  noun `buachan` with `airgead buaite` / `bua` throughout, fixed the
  genitive `an ghill`, made the overflow pronouns agree with `airgead`, and
  rephrased `variance-tradeoff` so it cannot read as fewer paylines.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0, Tier 2 = 2 (English rank initials K / Q in the split-rule
  examples), Tier 3 = 19 ("oir" = suit used for "fit", "Slán" read as
  "Goodbye", a missing t-prefix, "le haghaidh gan teorainn" calque,
  "ag an am céanna" for "in one payout", "comhoiriúnacha" for identical
  symbols), all applied. 21 review keys plus 19 self-review follow-ups
  (sibling "-1" prompts, rebet wording, "uaslíon" for counts), rechecked
  after. Findings: `reviews/ga_IE-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `ga_IE: name: "Gaeilge"`.
- Native-speaker review: not performed; recommended before release.

### mn_MN -- Монгол (Mongolian)

- Final catalog SHA-256: `bfc606cb6e112ae80a6e82aeb0b216f1fc1cb66a36f9e7d9242fa3aeab5a34e8` (identical in the run directory and
  `src/main/resources/lang/mn_MN.yml`; NFC-normalized)
- Voice: standard Khalkha Mongolian in Cyrillic, polite `Та` with polite
  imperatives (`дарна уу`, `бичнэ үү`) and verbal nouns for buttons, `«»`
  quotes, decimal comma; no grammatical gender; no case ending is ever
  attached to a placeholder (colon labels, governing nouns and
  postpositions, en-dash ranges, only the invariant digit marker `-р`);
  `дилер`, Baccarat `Тоглогч` / `Банкир`, `бооцоо`, `сан`, slots run
  `дараалал` vs PvE streak `цуврал` vs auto-spin batch `багц`, vines
  `ороонго`, RTP `тоглогчид олгох төлбөрийн хувь` kept apart from the refund
  `буцаан олгох`, traditional card names. Recorded in the sixth
  continuation of the §H table.
- Structural: helper strict check 0 errors, 0 residue warnings;
  `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all fifty-one new locales registered: every one
  of the 57 locales OK (1184), no new warnings; `compileJava` succeeds.
- Self-review before the independent review: loanword case forms
  replaced with a governing noun ("Блэкжек тоглоомд", "Рулет тоглоом
  руу"), "хүрэх" / "хэтрэх" given their dative / ablative objects through
  "дараах дүнд / дүнгээс" labels, genitive titles for the Coin Flip and
  RPS settings, and "Тоглогчийн горим солих эрх".
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0, Tier 2 = 3 (two short RTP forms, "шинэчлэх" used for reset),
  Tier 3 = 25 (ь-stem past tense "тавилаа" / "барилаа", "эсрэг" with the
  genitive ×4, "хаяж байна" without "ойролцоо" ×3, "болсон юм болсон",
  countdown wording), all applied. 28 review keys plus 7 self-review
  follow-ups (the same spelling in blackjack, test-game reset, the
  split-rule toggles), rechecked after. Findings:
  `reviews/mn_MN-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `mn_MN: name: "Монгол"`.
- Native-speaker review: not performed; recommended before release.
