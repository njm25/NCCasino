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
