# NCCasino localization review protocol

This document defines the provider-neutral *process* for reviewing,
comparing, and promoting localization work: targeted refinements,
multi-candidate benchmarks, blind adjudication, freezing, and promotion.

`TRANSLATION_GUIDE.md` owns *what a correct translation is* (semantics,
protected tokens, glossary, severity tiers). This document owns *how a
change gets from a draft to production safely and reproducibly*. Read the
guide first — this protocol assumes its operation model (§A), severity
tiers (§L), and terms (Class A/B/C/D tokens, Tier 0–3) without repeating
them.

This protocol is **provider-neutral**. It never refers to a specific model
or vendor as part of the normative workflow. Use generic roles instead:

- **translator** — produces a candidate value or catalog.
- **candidate** — a produced-but-not-yet-promoted set of values (a
  targeted-refinement candidate or a full benchmark candidate).
- **independent reviewer** — evaluates a candidate without having produced
  it.
- **adjudicator** — resolves disagreement between reviewers, or between
  candidates, when the ordinary review process doesn't produce a clear
  answer.

Provider/model identity may be recorded as **provenance metadata** (§4) for
auditability. It must never appear inside a blind review artifact, and a
reviewer must never be told or allowed to infer which provider/model
produced which candidate before finishing its judgment (§3).

## 1. When this protocol applies

| Operation (guide §A) | Applies? |
| --- | --- |
| Approved English delta | No — self-review per guide §M is sufficient. |
| Targeted refinement | Yes — §2 (single-candidate review path). |
| New locale | Yes — §3–§7 (full comparison path), even with only one candidate, since it's unreviewed by construction. |
| Full benchmark/rewrite | Yes — §3–§7 (full comparison path). |
| Candidate promotion | Yes — §8–§9 (promotion path), always after §2 or §3–§7 has completed. |

## 2. Targeted refinement review

A targeted refinement (guide §A, §B) starts from an exact dotted-key
allowlist and produces a candidate plus a change ledger. Review it as
follows:

1. **Translator** produces the candidate and a ledger entry (guide §B
   schema) for every changed key.
2. **Independent reviewer** — a fresh context that did not produce the
   candidate — reviews **every** changed key (never a sample; a targeted
   refinement is small enough that sampling isn't justified). For each key,
   the reviewer records: agree/disagree with the proposed value, severity
   tier if it disagrees (guide §L), and a cited rationale (exact key,
   exact English source, exact production baseline, exact proposed value).
3. Any Tier 0/Tier 1 disagreement blocks promotion until resolved — either
   the translator revises, or an adjudicator (§5) breaks the tie.
4. Once every changed key is at Tier 2 or better with reviewer agreement
   (or an adjudicator's resolution), the candidate is ready for promotion
   (§8).

A targeted refinement does not need blind labeling (§3) — with a single
candidate and an independent reviewer, there's no authorship-guessing risk
to control for. Blind labeling is for multi-candidate comparison.

## 3. Multi-candidate comparison

Use this path for comparing two or more candidates (e.g. two independently
generated full-catalog regenerations, or a new candidate against current
production) — this is what full benchmark/rewrite operations (guide §A)
almost always need, since the whole point is deciding which source is
strongest per key.

### 3.1 Freeze inputs first

Before any comparison begins:

1. Record the SHA-256 hash of the exact English source (`en_US.yml`) used.
2. Record the SHA-256 hash of the exact production baseline used, per
   locale (a "run-start snapshot").
3. Record the SHA-256 hash of every candidate file being compared, and
   cross-check those hashes against the run's own manifest (§4) — if a
   candidate's manifest hash and its actual file hash ever disagree, stop
   and report the mismatch; do not repair or regenerate the file yourself.

None of these inputs are edited during comparison. If a comparison needs a
changed English source or production baseline, that's a new run, not an
edit to the current one.

### 3.2 Blind candidate labeling

To prevent reviewers from reasoning about candidate quality based on
assumed authorship/style instead of the actual text:

1. Assign each candidate a **neutral label** — "Candidate A" / "Candidate
   B" (or "Variant K" / "Variant Q" / "Variant Z" for three or more,
   avoiding ordered-sounding labels like "baseline"/"challenger" that hint
   at a default winner).
2. Build one **blind packet per locale**: for every translatable key, the
   dotted key, the exact English source, and every candidate's exact value
   under its opaque label — with **no** filenames, hashes, model names, or
   words like "production," "original," or any provider/model name anywhere in
   the packet.
3. Fix the label-to-candidate mapping for the whole packet (so a reviewer
   can validly reason "does Candidate A behave consistently across keys"),
   but randomize the *physical display order* of the labels per key to
   reduce first-item bias.
4. Store the label-to-real-identity mapping in a single identity map file,
   accessible only to the coordinator, never to a reviewer or adjudicator
   until §6 (unblinding).
5. Give each reviewer a genuinely isolated context — no access to the
   identity map, production catalogs, sibling candidates outside its own
   packet, prior review reports, or benchmark generation prompts. If the
   environment cannot provide real isolation (e.g. a reviewer could read
   arbitrary repository files including the identity map), stop before
   linguistic review and report the limitation rather than performing a
   review that isn't actually blind. An instruction telling the reviewer not
   to look ("explicit read boundaries") is not real isolation and does not
   satisfy this requirement by itself — the reviewer's actual tool/file
   access must be incapable of reaching the identity map, not merely told
   not to use it. A run that proceeds on instruction-only boundaries must
   disclose this explicitly and must not be cited as protocol-compliant
   blind-review evidence for a promotion decision.
6. Instruct every reviewer explicitly: if it thinks it recognizes a
   provider's style, it must ignore that impression and judge only the
   text and Java evidence in front of it. A rationale that references
   assumed authorship is a rubric violation, regardless of whether the
   underlying conclusion happens to be right.

### 3.3 Independent review

One fresh, isolated reviewer per locale (never the translator who produced
any candidate in the packet) triages **every** translatable key in that
locale's packet — not a sample, for a full benchmark/rewrite (guide §M).
For each key, the reviewer records a ledger entry:

```
key, section, disposition, preferred_candidates, severity, categories,
confidence, context_checked, rationale
```

- `disposition`: one of `identical-all`, `equivalent`, `shared-best`,
  `single-best`, `hybrid-needed`, `uncertain-native-review`.
- `severity`: `none`, `runtime-breaking` (Tier 0), `meaning-changing`
  (Tier 1), `consistency` (Tier 2), or `naturalness` (Tier 3) — see guide
  §L for exact tier definitions.
- `rationale`: required for every record except `identical-all`; must cite
  the exact key, exact English source, and exact value for every candidate
  being compared.

There is no winner quota. Ties, `shared-best`, and `hybrid-needed` are all
valid, expected outcomes — do not force a single-best pick where the
evidence doesn't support one.

### 3.4 Blind audit

After all locale ledgers/reports are frozen (hashed), a fresh, isolated
audit worker — with the same blindness constraints as §3.2, and without
having produced any of the reviews — checks the review itself, not the
candidates directly:

1. Verify exact key coverage (every packet key has exactly one ledger
   line, no duplicates, no gaps).
2. Recompute ledger totals (by disposition/severity/confidence) directly
   from the raw ledger data and compare against what the review report
   claims — flag any arithmetic discrepancy.
3. Spot-check a meaningful sample of non-identical decisions per locale
   (as a floor, not a ceiling), weighted so that **every** Tier 0/Tier 1
   claim is individually re-verified, not sampled — those are the
   highest-consequence claims and the ones a promotion decision leans on
   most.
4. Flag unsupported claims, internally inconsistent claims, and any
   authorship-guessing language, whether or not the underlying conclusion
   turns out to be correct.
5. Issue a verdict per locale: PASS, PASS WITH CORRECTIONS (listing exact
   line/key corrections needed), or FAIL (review could not be trusted as
   evidence — explain why).

Corrections found by the audit are applied to the ledger/report in place
(with the correction visibly noted, not silently rewritten) and the
artifacts are refrozen (new hashes) before unblinding. Do not unblind until
every locale's audit has passed (with corrections applied where needed).

**A coordinator who finds an additional, directly-provable error while
unblinding** (e.g. by literally re-reading the cited text) may still
correct it, even though the audit didn't catch it — but must disclose this
exactly like an audit-found correction: what was wrong, what the fix was,
and that it was coordinator-found rather than audit-found. Never apply an
unblinding-stage correction based on a *linguistic re-judgment* made with
identity now known — only based on evidence a blind reviewer could also
have used.

## 4. Freezing, manifests, and hashes

A benchmark run's manifest (`manifest.md` alongside its candidate files, by
existing repository convention — see §7) records, at minimum:

- Run ID and date
- Operation type (guide §A)
- English source path and SHA-256 hash
- Production baseline SHA-256 hash per locale, at run start
- Each candidate file's SHA-256 hash
- Provider/model provenance per candidate (kept out of any blind artifact)
- The generation prompt/protocol version used
- Locale(s) covered
- Mechanical validation result (leaf-key coverage, Class A token checks —
  see guide §E)

Once a manifest records a candidate's hash, that candidate file is frozen:
do not edit it in place (guide §N). Re-verify every recorded hash against
the actual file at both the start and the end of any comparison work that
depends on it — if a hash ever fails to match, stop and report the exact
path, expected hash, and actual hash rather than silently proceeding or
"fixing" the file.

## 5. Adjudication and tie handling

When independent reviewers disagree, or a single reviewer flags a key as
genuinely uncertain:

- **Tier 0/Tier 1 disagreement** always needs adjudication before
  promotion — never split the difference by picking whichever candidate
  "seems more confident."
- **Tier 2/Tier 3 disagreement** may be adjudicated, or may simply be
  recorded as `equivalent`/`shared-best` and left as a legitimate tie —
  ties are a valid final state, not a problem to be resolved at all costs
  (guide §P, hybrid promotion explicitly supports this).
- An **adjudicator** is a fresh reviewer (or the coordinator, once
  unblinded, applying guide §L's tier hierarchy mechanically) who resolves
  a specific flagged disagreement by re-examining the cited English source,
  candidate values, and any Java context — never by preferring a candidate
  based on assumed authorship or a raw preference-count tally across
  unrelated keys (guide §L: "a lower-tier advantage never compensates for
  a higher-tier defect").
- If adjudication still leaves a materially semantic disagreement, or every
  candidate is questionable on the same key, mark it
  `uncertain-native-review` and require native-speaker review before that
  key is promoted (§6).

## 6. When native review is warranted

Escalate to a native-speaker reviewer (not just an independent LLM
reviewer) when:

- A key is marked `uncertain-native-review` after adjudication (§5).
- A locale-voice/register question can't be resolved from the guide's
  documented voice (guide's "Locale voice" section) plus Java context
  alone — e.g. a genuinely ambiguous idiom or regional-variant call.
- A candidate disagreement is confidence-`low` on a Tier 1 or Tier 2 claim
  and stays that way after adjudication.
- The user specifically asks for it before promoting a new locale or a
  full-catalog rewrite.

Do not treat automated review (however thorough) as a substitute for native
review on these cases — the guide requires disclosing non-native-reviewer
confidence limitations in any final report (§7).

## 7. Unblinding and reporting

Only after every locale's blind audit (§3.4) has passed may the coordinator
open the identity map and substitute real provenance for opaque labels in
the aggregate report. No blind judgment may be altered by unblinding —
identity is attached to already-frozen conclusions, never used to reach
new ones (the one narrow exception is the coordinator-found-correction case
in §3.4, which must be disclosed exactly as described there).

A comparison run's final report should cover, at minimum:

- Exact input and artifact hashes (§4).
- Full mechanical validation results (guide §E), attributed to real
  identities (mechanical facts, unlike linguistic judgment, are not
  blinded).
- The blind-review and audit method actually used, including any isolation
  limitation encountered.
- Per-locale ledger counts by real identity, disposition, severity,
  category, and confidence.
- Cited examples (exact key, exact English source, exact value for every
  candidate) for every high-risk finding and every claimed systemic
  advantage or defect.
- Cross-locale patterns, clearly separated from locale-specific evidence —
  a defect isolated to one locale's run should not be reported as if it
  characterizes a provider generally.
- An explicit check of whether conclusions would change if identities were
  swapped — authorship must never be part of the rationale for any
  individual finding.
- A concrete per-locale outcome: a named "best base," "comparable," a
  hybrid recommendation with exact keys, or "native review required" —
  never an unsupported single winner where the evidence is genuinely mixed.
- An entry-level hybrid plan (guide §P): exact keys to take from a
  non-base source, exact keys needing new human wording.
- Limitations: non-native-reviewer confidence, any incomplete context, any
  interrupted/resumed review work.

Store the report alongside the run's other artifacts (§7 directory
convention below). Never promote a candidate as a side effect of writing
this report — promotion is a separate, explicit step (§8).

## 8. Stale-baseline guard (process view)

Before promoting any previously-reviewed key, mechanically re-check (guide
§O):

1. Does the current production value still match the production baseline
   recorded when that key was reviewed?
2. Does the current English source still match the English source snapshot
   the review was performed against?

If either has moved, the key is **stale**: do not promote the old reviewed
value. Re-review the key against the current baseline/source first. This
check is cheap (a hash comparison) and must never be skipped because "it
was fine last time this ran."

## 9. Promotion records

A promotion (guide §A, "candidate promotion") requires explicit user
authorization and, once authorized, should record:

- Exactly which keys were promoted, and from which source each came
  (production-unchanged, Candidate A, Candidate B, or a manual override) —
  key-level provenance, not just "candidate X was promoted."
- The stale-baseline check result (§8) for every promoted key.
- The final validation result *after* merging into production (guide §M —
  validating a candidate in isolation is not sufficient once merged).
- Confirmation that every promoted key was reviewed at Tier 0/Tier 1 and
  cleared (guide §L) — a key that was never reviewed must never be
  promoted, however good it looks.

Never promote silently as a side effect of comparison, review, or an
approved-delta task. Promotion is always its own explicit, user-authorized
step.

## Recommended benchmark run layout

Adapt this to the repository's existing conventions rather than forcing a
conflicting layout — only adopt a new directory/file when the existing flat
convention doesn't already cover it. Real runs under
`tools/localization/benchmarks/` currently use a flat per-run directory:

```text
tools/localization/benchmarks/<run-id>/
  manifest.md          # §4: hashes, provenance, scope, validation summary
  validation.md         # mechanical validation results (guide §E)
  comparison.md          # (single-candidate/production-aware runs)
  <locale>.<label>.yml   # one frozen candidate file per locale
  run-notes.md            # timestamps, worker durations, retries, limitations
```

A multi-candidate blind comparison run (§3) additionally needs somewhere to
put the blind machinery, which does not yet have an established
convention — use subdirectories under the run's own directory rather than
inventing a new top-level layout:

```text
tools/localization/benchmarks/<run-id>/
  manifest.md
  validation.md              # mechanical validation, all candidates
  blind-packets/<locale>.json      # §3.2
  private-identity-map.json         # §3.2 -- coordinator-only until unblinding
  ledgers/<locale>.jsonl              # §3.3
  blind-reviews/<locale>.md            # §3.3
  blind-audit.md                        # §3.4
  final-comparison.md                    # §7
  run-notes.md
```

Do not adopt the deeper `source/`, `candidates/`, `reviews/`, `merged/`,
`promotion.yml` split some external conventions use unless a future run
actually needs that much separation — it doesn't match how this repository
already organizes benchmark output, and splitting it up for its own sake
adds navigation cost without adding safety. If a promotion needs its own
record (§9), a `promotion.md` (or `.yml`, matching whichever the rest of
the run uses) inside the same run directory is enough.
