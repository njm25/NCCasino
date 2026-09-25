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

### fil_PH -- Filipino

- Final catalog SHA-256: `28fbae4b66e3652d60c7dde826cbdc6549fa1cc32bbead45c1ecff4dadb19f94` (identical in the run directory and
  `src/main/resources/lang/fil_PH.yml`; NFC-normalized)
- Voice: standard Tagalog-based Filipino, informal `ka` / `mo`, decimal point,
  with only the loanwords Philippine players use in UIs (dealer, chip, timer,
  chat, reel, jackpot); no gender, gendered vocatives avoided; `taya`,
  `Kunin ang Panalo`, `Bangkero`, `Kara o Krus`, `Awtomatikong Ikot`, run
  `sunuran` vs streak `sunod-sunod`, Spanish-derived card names. Recorded in
  the fourth continuation of the §H table.
- Structural: helper strict check 0 errors (the residue warnings are the
  accepted loanwords and a few short labels such as "Timer:"; the full check
  confirms no new warnings); `localizationCandidateCheck` CANDIDATE OK
  (1184); full `localizationCheck` with all thirty-three new locales
  registered: every one of the 39 locales OK (1184), no new warnings;
  `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 3 (`click-leave-chair` read as "stand on the chair"), Tier 2 = 5
  (English word order in "Admin Menu", "even", clipped "Auto"), Tier 3 = 54
  (standard affixes, the "talong" linker collision, "Balanse" collision),
  all applied; 1 follow-up. 62 review keys plus 6 self-review fixes
  (including a gendered "pare" vocative), rechecked after. Findings:
  `reviews/fil_PH-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `fil_PH: name: "Filipino"`.
- Native-speaker review: not performed; recommended before release.

### gl_ES -- Galego (Galician)

- Final catalog SHA-256: `9d4a6b4eeca9420760c03f4aa40346dd06774581d3270fe6659e9ce5939cd89d` (identical in the run directory and
  `src/main/resources/lang/gl_ES.yml`; NFC-normalized)
- Voice: normative Galician (RAG/ILG), informal `ti`, decimal comma, `«»`
  quotes; written from the English source with a Spanish-interference scan
  (clitic placement, `Bieeen`, `jackpots`); player-neutral forms; label
  forms where a singular amount would break plural agreement; Blackjack
  buttons as infinitives; `crupier`, Baccarat `Xogador` / `Banca`, `aposta`,
  `Cobrar`, Coin Flip `Cara ou cruz`, Slots `Tragaperras` with `rolos`,
  run `secuencia` vs streak `racha`. Recorded in the fourth continuation of
  the §H table.
- Structural: helper strict check 0 errors (the 31 residue warnings are the
  Galician article `a` and the `chat` loanword); `localizationCandidateCheck`
  CANDIDATE OK (1184); full `localizationCheck` with all thirty-four new
  locales registered: every one of the 40 locales OK (1184), no new
  warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 1 (Spanish `Bieeen`), Tier 2 = 1 (unlimited spin limit shown two
  ways), Tier 3 = 23 (colon after `de`, `preto` = near/black ambiguity,
  proclisis after `así que` / `todo`, chip value vs size, singular-amount
  agreement, "None" agreement verified in Java and added to guide §C), all
  applied, plus the reviewer's optional `doutra persoa` note. 26 review
  keys plus 6 self-review fixes, rechecked after. Findings:
  `reviews/gl_ES-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `gl_ES: name: "Galego"`.
- Native-speaker review: not performed; recommended before release.

### af_ZA -- Afrikaans

- Final catalog SHA-256: `7df0e4fb9eeb78f38f8c3819f60bfd5d9ce09ac464430e5267af9f2b8ff1492d` (identical in the run directory and
  `src/main/resources/lang/af_ZA.yml`; NFC-normalized)
- Voice: standard Afrikaans (AWS / Taalkommissie), informal `jy` / `jou`,
  decimal comma, `“”` quotes; written from the English source, not adapted
  from nl_NL; double negation, verb-final subordinate clauses, AWS
  compounding; neutral opponents; `kroepier`, Baccarat `Speler` / `Bankier`,
  `weddenskap` vs `inset`, `Betaal uit`, `ruitens`, sentence-case game names
  (`Kop of stert`, `Draak se afdaling`), run `opeenvolging` vs streak
  `reeks`, `Outodraai`. Recorded in the fourth continuation of the §H table.
- Structural: helper strict check 0 errors (the residue warnings are
  function words shared with English: `is`, `in`, `of`, `by`);
  `localizationCandidateCheck` CANDIDATE OK (1184); full `localizationCheck`
  with all thirty-five new locales registered: every one of the 41 locales
  OK (1184), no new warnings; `compileJava` succeeds. The English-identical
  values are shared words (`Pot`, `Hand`, `Variant`, `Jazz`, game names).
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0, Tier 2 = 8 (`diamante` calque, payout-queue and "afhaal"
  drift, spin-limit and re-split wording), Tier 3 = 38 (AWS compounds,
  number agreement with `lyn(e)` / labels, clause-as-label toggles,
  `occupations.*` double "vir"), 45 applied; 1 declined with reason
  (settlement = delivery, per guide §C). 45 review keys plus 10 self-review
  fixes (including a gendered "sy of haar keuse" neutralized before review),
  rechecked after. Findings: `reviews/af_ZA-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `af_ZA: name: "Afrikaans"`.
- Native-speaker review: not performed; recommended before release.

### be_BY -- Беларуская (Belarusian)

- Final catalog SHA-256: `3e1c84db6092a580e3d697cdf05592a9924502f5d23b0c632eb2bbd91398e9fd` (identical in the run directory and
  `src/main/resources/lang/be_BY.yml`; NFC-normalized)
- Voice: standard Belarusian, official 2008 orthography; polite plural
  `вы`, so past forms addressed to the player are plural and never
  gendered; `«»` quotes, decimal comma; written from the English source,
  not adapted from ru_RU / uk_UA (no и/щ/ъ, ў rule checked by script);
  `крупье`, Baccarat `Гулец` / `Банкір`, `Забраць выйгрыш`, `шуз`, slots
  run `камбінацыя` vs streak `серыя`, `Аўтакручэнне`. Recorded in the
  fourth continuation of the §H table.
- Structural: helper strict check 0 errors, 0 residue warnings;
  `localizationCandidateCheck` CANDIDATE OK (1184); full `localizationCheck`
  with all thirty-six new locales registered: every one of the 42 locales
  OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 3 (Russian-pattern conditional `пры` calques), Tier 2 = 0,
  Tier 3 = 26 (Baccarat side capitalization, `мэта па прыбытку` calque,
  label forms, `гуляць у гульню {game}` apposition), all applied. 29 review
  keys plus 11 self-review fixes (the remaining conditional `пры` phrasings
  and the capitalized odds labels), rechecked after. Findings:
  `reviews/be_BY-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `be_BY: name: "Беларуская"`.
- Native-speaker review: not performed; recommended before release.

### hi_IN -- हिन्दी (Hindi)

- Final catalog SHA-256: `4327e78b7092b20c4b280f93526db583bd5aafa07d7a239dba215695b5f17632` (identical in the run directory and
  `src/main/resources/lang/hi_IN.yml`; NFC-normalized)
- Voice: standard conversational Hindi in Devanagari with the loanwords
  Indian game UIs use; polite `आप`, danda, `“”` quotes, Western digits;
  gender-neutral toward the player through ergative, dative, nominal,
  passive and existential constructions (Hindi verbs otherwise agree with
  the subject's gender); SOV order arranged so placeholders keep the
  English order; traditional card names; run `क्रम` vs streak `लगातार
  जीत`. Recorded in the fourth continuation of the §H table.
- Structural: helper strict check 0 errors, 0 residue warnings (two
  token-order slips from SOV order caught and fixed before review);
  `localizationCandidateCheck` CANDIDATE OK (1184); full `localizationCheck`
  with all thirty-seven new locales registered: every one of the 43 locales
  OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 2 (gendered `लेंगे` future and a generic-player `कर सकते हैं`),
  Tier 2 = 2 ("Return to" drift), Tier 3 = 19 (awkward object-less
  ergative, `मंज़िल` collision, range order, mob-name agreement), all
  applied. 23 review keys plus 5 self-review fixes, rechecked after.
  Findings: `reviews/hi_IN-findings-*.md`.
- Rendering caveat: Minecraft's font renderer does not apply complex-script
  shaping, so Devanagari matras (e.g. the pre-base `ि`) and conjuncts may
  display in logical rather than visual order. This must be checked in game
  before the locale is exposed; the text itself is correct Unicode.
- Not registered in `locales.yml`. Registry line: `hi_IN: name: "हिन्दी"`.
- Native-speaker review: not performed; recommended before release.

## Batch summary

All six batch-5 locales are complete. To expose them in the language menu,
append to `src/main/resources/lang/locales.yml` (after the batch-1 to
batch-4 lines):

```yaml
  ms_MY:
    name: "Bahasa Melayu"
  fil_PH:
    name: "Filipino"
  gl_ES:
    name: "Galego"
  af_ZA:
    name: "Afrikaans"
  be_BY:
    name: "Беларуская"
  hi_IN:
    name: "हिन्दी"
```

With the batch-1 to batch-5 lines appended, `localizationCheck` reports all
43 locales OK at 1184 entries (verified in this run) and `compileJava`
succeeds. Remaining warnings are the seven pre-existing ones plus the
de_DE-precedent identity in nl_NL; batch 5 adds none. One semantic fact was
verified in Java during this batch and added to the guide's §C registry:
`mob-settings.none` / `admin.none` only fill the llama-decor and
wolf-collar-colour slots (gendered languages agree "None" with those nouns).

Out-of-scope observations (reported, not changed -- each would be a
targeted refinement needing its own run and approval):

- `mob-settings.none` / `admin.none` in older gendered catalogs were not
  audited against the new registry entry; a Romance/Slavic sweep (masculine
  "None" filling feminine decor/colour nouns) would be a separate targeted
  refinement.
- The batch-4 observations (`resplit-offer` wording in 28 older catalogs,
  "not free" `seat-unavailable` renderings, hr_HR "znakova", es_ES
  `chain-win` / "sentado") still stand.

Still needed before release: an in-game check (Devanagari shaping for
hi_IN, Belarusian ў and Cyrillic in inventory titles, line wrapping of the
longer Filipino and Galician strings) and native-speaker review.
