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
