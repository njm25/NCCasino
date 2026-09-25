# Run final-audit-1

- Date: 2026-09-25
- Operation type (guide §A): a correction pass over the 55 catalogs this
  project generated and promoted in `new-locale-batch-1` … `-8`, applied
  directly to `src/main/resources/lang/`. The repository user asked for it in
  chat: "do one pass over every single language done, check each part
  everything up to snuff ready to go, fix anything that you find and you're
  confident about the fix". That instruction is the authorization for these
  production edits. The approved scope is exactly the 140 dotted keys in
  `ledger.jsonl`; nothing else was rewritten.
- Out of scope, untouched: `en_US`, `de_DE`, `es_ES`, `fr_FR`, `pt_BR`,
  `zh_CN` (pre-existing catalogs) and `locales.yml` (the user registers the
  new locales).
- Base commit: `35a93afaffb60f1ed37fe4455b94d8f8fd52f24e`
- English source: `src/main/resources/lang/en_US.yml`
  SHA-256 `3c95538cdff1143654851e228290f165f97826d53431986029ae3ba9b49e6670`
  (unchanged; 1184 translatable entries)

## Frozen candidates vs production

The per-locale catalogs under `new-locale-batch-N/` are frozen review
candidates and were **not** modified (guide §N). For the 44 locales listed
below with a non-zero change count, the production catalog now differs
from its batch candidate by exactly the ledger entries, and the "Final
catalog SHA-256" in that batch's manifest describes the pre-audit state.
The table at the end gives the current production SHA-256 for all 55.

## Method

1. **Phase 1: scripted checks over all 55 catalogs.** Scans covered:
   invisible and control characters, words that mix scripts (e.g. Latin
   letters inside Cyrillic words), double spaces, leading and trailing
   space parity with English, unbalanced brackets, stray `&`, values
   byte-identical to English, quote style and decimal style per locale,
   distinct English strings that share one translation, and punctuation
   added relative to English. Placeholders, colour codes and protected
   literals (`NCCasino`, `NCCASINO`, `Vault`, `VAULT`, `PvP`, `PvE`, `-1`,
   `/ncc …`) are enforced by `localizationCheck` and re-checked by the fix
   script, which also NFC-normalizes. Hits were reviewed by hand; most were
   intentional (shared loanwords, locale abbreviations). Result: 14 fixes
   (Latin `è` in Macedonian `сѐ` ×11; cy_GB decimal comma → UK decimal
   point ×3, with the cy_GB voice entry corrected to match).
2. **Phase 2: semantic-trap keys across all 55 locales.** Every value of the
   §C registry keys was compared side by side and against the Java call
   sites: `blackjack.resplit-offer` (completed action), RTP labels
   (`slots.guide-machine-rtp`, `admin.slots-rtp-lore`),
   `slots-settings.variance-tradeoff` (lines pay less often), the
   `seat-unavailable` pair (empty locked chair), the `chain-win` pair
   (`{amount}` is the current pot), `*.updated-detailed` (receives a finished
   sentence, §K), `mob-settings.none` / `admin.none` agreement and the
   profile-name length messages (`{max}` = 24). Result: 114 fixes.
3. **Phase 3: a full read of every line of all 55 catalogs.** English was
   checked before an apparent inconsistency was "fixed" (e.g. English
   `max-chain-hit` says "games", `mob-settings.dealer-label` has no colon).
   Result: 12 fixes (Vault wording that implied NCCasino requires Vault ×8,
   RTP label ×2, `invalid-action-capitalized` emphasis ×2).

Each fix was applied by a script that re-checks placeholders, colour codes
and protected literals before writing, and appends to `ledger.jsonl`. The
ledger was then checked against git: every `before` equals the base-commit
value, every `after` equals the committed value, no key sets changed and no
unledgered value differs.

## Results

- 140 value changes in 44 locales (phase 1: 14, phase 2: 114, phase 3: 12).
  Human-readable: `ledger.md`; machine-readable: `ledger.jsonl`.
- 11 locales needed no change: eo_UY, es_MX, ga_IE, gl_ES, hy_AM, is_IS,
  ka_GE, lv_LV, nn_NO, sw_KE, uz_UZ.
- `TRANSLATION_GUIDE.md`: cy_GB voice entry now says decimal point (UK
  style); four verified §C registry entries were added (RTP labels vs
  refund wording; Vault is optional; `invalid-action-capitalized` in
  scripts without letter case; `max-chain-hit` counts "games").

## Validation (all 55 new locales temporarily registered; `locales.yml`
restored afterwards)

- `./gradlew localizationCheck compileJava compileTestJava`: BUILD
  SUCCESSFUL; all 61 locales OK (1184 entries each). 8 non-fatal warnings,
  identical to the pre-audit baseline: 7 in pre-existing catalogs
  (`es_ES`/`pt_BR`/`fr_FR` `player-turn` `&o` count, `de_DE`
  byte-identical hand summary) and nl_NL
  `blackjack.round-summary-hand-blackjack` ("Hand {number}: Blackjack!",
  identical words in Dutch, intentionally shared).
- `./gradlew test`: BUILD SUCCESSFUL, 1722 tests, 0 failures, 0 errors,
  0 skipped.
- All 55 catalogs are NFC-normalized.

## Noted, not changed (not confident, or acceptable as is)

- `mob-settings.none` / `admin.none` in es_MX, it_IT, ro_RO, el_GR, is_IS:
  one form must cover both the decor and the colour slot, whose nouns differ
  in gender; the current neutral/default choice is defensible.
- el_GR keeps some Latin gaming jargon; several non-Latin locales keep the
  Latin `K-K` / `K-Q` examples in `split-matching-desc-*` (Latin card
  initials are widely used on card faces).
- da_DK "Klik på Nulstil" style button references; eo_UY "lia sidloko"
  (gender-marked possessive); arrow-style `*.updated-detailed` variants
  (§K source defect: the key receives a finished sentence).
- Plain return verbs in non-label RTP sentences (gl_ES, id_ID, kk_KZ, ro_RO,
  sk_SK, pt_PT, sl_SI) mirror English "returns {rtp}"; bg_BG / cs_CZ / sk_SK
  RTP labels use abstract return-ratio nouns that do not read as a refund.
- pt_PT `standard-mode-fallback` "modo normal" (fine; "padrão" would also
  do).
- uz_UZ `dragon-descent.dragon-name` "Ajdarho Drangus": a phonetic
  respelling of "Drungus" (as in ka/hy/ja/bn/mk), not a clear error.

## Still required

- In-game check on a Minecraft server (chat, inventory titles, lore line
  lengths, the language menu) for the new locales.
- Native-speaker review before release, as recorded per locale in the batch
  manifests.
- Registration of the 55 locales in `src/main/resources/lang/locales.yml`
  (done by the user; the lines are in the batch manifests).

## Production catalog SHA-256 after this audit

| Locale | Changes | SHA-256 |
|---|---|---|
| nl_NL | 4 | `133248d9d67cbdb759d4afc9f3465d22d27565411c9a661fd4bbb36c25c750e9` |
| fi_FI | 8 | `a7299dfb36a01e3ca774c3a38aeea6bd93d8b9dd68a8a7a70fc27c1381e020ca` |
| ja_JP | 4 | `d92b26273e42d70f84601fd08b58a51c1a675e74c5e4708c5b3dd1a536a12b09` |
| ru_RU | 5 | `4de8219f048c9b1cae1b65a85f4d3e3a751b92e5db16fe591b458068d8315106` |
| th_TH | 6 | `64557f3849867cbced976db68841c776b278b3b69e97afca28f53537f1a8c4b9` |
| tr_TR | 2 | `c3d0687479b8106fa549fe1044d72f80b2d86fa8473d10614385319a1b18c75f` |
| vi_VN | 6 | `7b92194e019653e6d4f90ebc3a8e75a95607af23b5df1380011e868986f1fb0f` |
| ko_KR | 2 | `cf31a11a8852bb910815a0069c7ccb87181edf61db27c4375aaeab97bcb1fc94` |
| pl_PL | 4 | `8f4ae5c356e055e6175dc45d6633b9a0a25bc6eef88613496de1884ba22e041f` |
| it_IT | 2 | `6982c41bb6fd2b0abcd4ce1dcc1684eee6decdb136b34c9819eeaf48e792cc9f` |
| id_ID | 6 | `1fd8f7fe374ce3343ee8734edd340005649fd4d2db22e0fad9d72a06973c7d1b` |
| zh_TW | 2 | `8aaeae2b9ac4310a460ca01591836bb920d9bfb506b5446f09939d08e40ae1ff` |
| uk_UA | 4 | `6b9962e38007472da0f694dc4fc33340ab20d61f74c2166f31c1ba2f93774752` |
| cs_CZ | 3 | `853891a6b4099670874bf8c43dcebafeeed2882c252468c106210e009142b839` |
| sv_SE | 4 | `5623a65ba57125003dff59d773d4a10c9ef11889dae1716ca945b797040eec20` |
| hu_HU | 3 | `d75f3e3c4d80c933a9f44d3e0fc650694bbaaf0ea4258682297ac470dd0753d7` |
| ro_RO | 1 | `159167a76e4f370dc32edceb2bc80be807b5aa1615f7aec81d0c8d7ae8565b26` |
| pt_PT | 3 | `e750c39dda00186f2978902efb6c05943e104a691bd72ddc842041b0f643105e` |
| da_DK | 3 | `bde3ad0a50293498a9671d92b7fd74396df0d3d3e8fcd0e85ae0452e2e8851df` |
| nb_NO | 3 | `db4eedc13d51a39eb517739392b2abcf28970d2cb28921071e2ae5d04134721e` |
| el_GR | 1 | `8ee6973b4e9fe1252b1d3dde3629272ae4df95a5ce06fd88c41f64738a6a40bd` |
| sk_SK | 5 | `fcc52099f74bca6e9117fc99e347056eadf4bda2425f91a96a0997f072990071` |
| bg_BG | 3 | `fed5683e6323460a43da3c224cd4c67977d80b8add1bd099c4980a2675e7c83f` |
| es_MX | 0 | `13c4e3ee691f361b5264529b32c8e5b845044286e1857ecd1d93a35e083317f3` |
| hr_HR | 5 | `7998674bc742307fd9f9f9c6494dcd6b6b8dbc0bc633c1560fa4f003ba4a1484` |
| sl_SI | 3 | `3747fa0dd02ca74a3ebb13a5ba946dbac6ac0a7800b4756db892a75ff0999f3b` |
| sr_RS | 3 | `5535aa06246eb5792c405372847543c7b103d9cc0de8d5889b025f6bb88bd739` |
| lt_LT | 1 | `03d94933d1962cdbfaf557a73b0aa76e5fdf85e9ccc38bbeb9944999565ab7a6` |
| lv_LV | 0 | `07a47a4c1fc85d636f922ccf94f40a0390c841730786605e5ba23557b5743a0f` |
| et_EE | 1 | `8fc14a4bd5e1cd2d1f0d1c6f9033c7b9bfa2fcdf9263832b181ecd1491912d55` |
| ca_ES | 1 | `8c06275c8a1a3948ffa34ebf76b25efc986ad7643b42f0304ea955ad8881b750` |
| ms_MY | 1 | `f68e4a604072ae48d0775862545d6f38c79f7f849eb2aa1dc5cb1c541f49e859` |
| fil_PH | 1 | `ae6353c94c96a6db8aa639f7d0c52239ae3e9429f488443828844f3fee844a74` |
| gl_ES | 0 | `9d4a6b4eeca9420760c03f4aa40346dd06774581d3270fe6659e9ce5939cd89d` |
| af_ZA | 2 | `1d06bec0d52f94a8a8e156dc290d1b27bc921a1fe2b9772e9e543273bcd58d18` |
| be_BY | 2 | `690488540ece81fc4863265f53ce9cfbe6fcaf315bdc87372dfac57afef44bd3` |
| hi_IN | 2 | `ea003c8442edeb12834075ac446db266a7c663af076face710716263d4f64789` |
| mk_MK | 14 | `084828a3b1ad933a978b6e9ebe8ed6952560115fc7165d0f56942d282329fbbd` |
| az_AZ | 2 | `f3f885bc12800e7ad3bdf03131342809bfb48d94a091d91505a4d2c5b42ad625` |
| eu_ES | 2 | `ee64b445aa3dd7131ad4f281531cdf39281123eaa454b0a7eb08938dd08e733e` |
| sq_AL | 2 | `f236694492469191068796c15425f921e4b0daa34fc261825358d6bde5a3f215` |
| is_IS | 0 | `db820a5ab48d5deada8b6f0e8c74e976ae3f2fbdc6c431a31d1bb565de884ab5` |
| kk_KZ | 2 | `5d976cae6538ade2b0e3bf84e162a3b7705333096d1c3cb47d654e098fdd15ed` |
| ka_GE | 0 | `5bd7305723a5adf2698d7de4b6e99037421358a82542f2474dc8ff57a0aacef4` |
| cy_GB | 3 | `17841401305c11c29ccd8e91a91d1b210536cf3c253b226107aaded8f183d4d4` |
| bs_BA | 1 | `b74431fc63af1ef4cc6026add9fafe753eb84cb677ccac0189e5439883b44c55` |
| hy_AM | 0 | `beb078a9d5987d4b15d065adfa82b71eda0bc1dd43820625e8a73dd7a3d42b65` |
| uz_UZ | 0 | `ec196795f25cdffd562cae5880499e767c1bea0fb1b5d591713b3c13796c540b` |
| sw_KE | 0 | `d51f3a5b2eacdee8bef5e66a9ed6d8ec09180afa3ba5683b2176bf80c9da387e` |
| ga_IE | 0 | `aa45c47d426c083280be99f2a1a922944ae861098aee73f9d17b699b18a698fc` |
| mn_MN | 3 | `37b4e6d34c4d3b9343a84ab32d7ddff483144c4f4400dfd2ddf3a1a42fd7001a` |
| bn_BD | 2 | `f957209b53182411302a6cb4cf4ba0b7b37e86599111924eae4a61c6d782bcce` |
| ta_IN | 3 | `6f17889d75f4e5f4507c513276858914ab3153760906d09e6adf886e1b0b9c16` |
| nn_NO | 0 | `f388f6c99c8c703728782f7bc1173035d35a8d76bfc7a72492da80fc16af48ae` |
| eo_UY | 0 | `3b671be7bfd66b419064675864795a2c301d2ecb44b4296b32130b129c18e3b3` |
