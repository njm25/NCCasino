# Run new-locale-batch-8

- Date: 2026-09-25
- Operation type (guide §A): **New locale** generation, then candidate
  promotion into `src/main/resources/lang/`, for locales chosen by the agent.
- Authorization: the repository user asked in chat to keep choosing more
  languages "in the same style" once the requested ones were done; this run
  continues batch 7 under that instruction. Registration in `locales.yml` is
  again left to the user.
- Selection (left-to-right scripts only; right-to-left languages remain
  deferred until chat and inventory-title bidi rendering has been checked in
  game): nn_NO, eo_UY, lb_LU, mt_MT, ky_KG, tg_TJ, tk_TM, mr_IN. `eo_UY` is
  Minecraft's own locale code for Esperanto.
- Base commit for this run: `dc774a45397f4adcf429212dcab3846fdd9fd5b8`
- English source: `src/main/resources/lang/en_US.yml`
  SHA-256 `3c95538cdff1143654851e228290f165f97826d53431986029ae3ba9b49e6670`
  (unchanged since batch 1; 1184 translatable entries)
- Process, provenance and the isolation limitation are identical to
  `new-locale-batch-1/manifest.md`; reviewers receive the batch-4 rubric
  with the `mob-settings.none` / `admin.none` and house-edge parser notes.
  Glossary columns go into a seventh continuation of the §H table.

## Per-locale record

### nn_NO -- Norsk nynorsk (Norwegian Nynorsk)

- Final catalog SHA-256: `f388f6c99c8c703728782f7bc1173035d35a8d76bfc7a72492da80fc16af48ae` (identical in the run directory and
  `src/main/resources/lang/nn_NO.yml`; NFC-normalized)
- Voice: Norwegian Nynorsk (current official norm), `du` with plain
  imperatives, « » quotes, decimal comma; Nynorsk forms only (never
  Bokmål), prepositional genitives instead of s-genitives, pronouns by
  grammatical gender, neuter agreement with `spinn` / `autospinn`;
  `dealer`, Baccarat `Spelar` / `Bank`, `innsats`, slots run `serie` vs PvE
  streak `rekkje` vs auto-spin batch `omgang`, vines `klatreplantar`, RTP
  `tilbakebetaling til spelaren` kept apart from `refundert`. The nb_NO
  catalog was read as a terminology reference only; every line was
  translated from English, and the nb_NO "not free" seat label and
  "Splitt igjen!" re-split line were not carried over. Recorded in the
  seventh continuation of the §H table.
- Structural: helper strict check 0 errors; 2 heuristic shared-substring
  notes remain on `commands.usage-create` / `usage-delete` (the `/ncc`
  token plus the Nynorsk placeholder word `<namn>`), not residue. Nynorsk
  `for`, `at`, `to`, `set` added to the helper's function-word allowlist.
  `localizationCandidateCheck` CANDIDATE OK (1184); full
  `localizationCheck` with all fifty-four new locales registered: every
  one of the 60 locales OK (1184), no new warnings; `compileJava` succeeds.
- Independent review (2 isolated reviewers, every key): Tier 0 = 0,
  Tier 1 = 0, Tier 2 = 1 ("dealardata" spelling), Tier 3 = 17 (Bokmål plural
  "klatreplanter", neuter "aktivt", "vald" agreement, "alt ... alt",
  "trekkje tilbake innsatsen", "spelarbyte" read as a player substitution);
  17 applied, 1 declined with a reason (a capitalised hand-summary line
  would be byte-identical to English). 17 review keys plus 6 self-review
  follow-ups (the other vine forms, "vanilla-gjenstand"), rechecked after.
  Findings: `reviews/nn_NO-findings-*.md`.
- Not registered in `locales.yml`. Registry line: `nn_NO: name: "Norsk nynorsk"`.
- Native-speaker review: not performed; recommended before release.
