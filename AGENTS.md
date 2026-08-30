# NCCasino repository guidance

These instructions apply to the entire repository. Keep this file concise; use
the linked task guides for specialized workflows.

## Project basics

- NCCasino is a Java 21 Bukkit/Spigot plugin built with the Gradle wrapper.
- Production code is under `src/main/java`, resources under
  `src/main/resources`, tests under `src/test/java`, and development-only
  localization tooling under `src/localizationTools/java`.
- Prefer the current Server/Client game architecture. For a new game, inspect
  Rock Paper Scissors first and Coin Flip second; older `*Inventory`/`*Table`
  games predate that architecture.
- Search for the nearest existing implementation before inventing a new
  convention. Player-visible text belongs in the localization catalogs, not in
  hardcoded Java strings.

## Editing and Git safety

- Preserve unrelated user changes. Inspect `git status` and the relevant diff
  before editing overlapping files.
- Do not commit, push, rewrite history, delete user work, or switch branches
  unless the user asks.
- Keep changes scoped to the request. Do not silently replace a user's manual
  translation or generated benchmark candidate.
- Do not add bulk machine-translation providers or credentials. The running
  plugin JAR must not call an external translation service.

## Verification

- Use `rg`/`rg --files` for repository searches.
- Run the narrowest relevant tests while iterating, then the broader suite when
  proportionate to the change. On Windows, use `./gradlew.bat` or
  `.\gradlew.bat`.
- Common gates are `.\gradlew.bat compileJava compileTestJava`,
  `.\gradlew.bat test`, and `.\gradlew.bat localizationCheck`.
- A successful compile is not a substitute for Minecraft server testing. State
  what still requires an in-game check.

## Localization trigger

Before adding, changing, generating, or reviewing player-visible text or locale
files, read and follow `tools/localization/TRANSLATION_GUIDE.md` (translation
rules and semantics). For any multi-candidate comparison, benchmark, or
promotion, also read `tools/localization/REVIEW_PROTOCOL.md` (review process).
Detailed rules belong in those two files, not here.

**Classify the operation before editing anything.** Every localization task is
exactly one of:

- **Approved English delta** — translate specific new/meaningfully-changed
  English keys straight into production catalogs
  (`src/main/resources/lang`); normally triggered by the user's "ready to
  translate" handoff.
- **Targeted refinement** — improve specific existing keys against an exact
  dotted-key allowlist; produces a candidate plus a change ledger, not a
  direct production edit.
- **New-locale generation** — produce a complete candidate for a
  not-yet-registered locale under `tools/localization/benchmarks`.
- **Full benchmark/rewrite** — an explicitly authorized experiment across a
  whole catalog, not an implicit production replacement.
- **Candidate promotion** — copy reviewed candidate values into production;
  only happens on explicit user authorization.

An operation must not silently change type — a targeted refinement must not
become a full rewrite merely because the agent notices other weak strings
along the way (report them out of scope; do not fix them). Concise rules:

- Targeted refinements and benchmarks do not modify
  `src/main/resources/lang` by default.
- A benchmark candidate (anything under `tools/localization/benchmarks`) is
  not a production translation, however complete or well-reviewed it looks.
- A frozen/reviewed benchmark candidate must not be modified in place — start
  a new run instead.
- Promotion into production requires explicit user approval every time; an
  earlier approval does not cover a later, different promotion.
- Localization work must not silently expand beyond its approved scope
  (extra keys, extra locales, or extra editorial polish nobody asked for).

**Semantic context is not always recoverable from English wording alone.**
Before translating or reviewing a key whose meaning depends on actor or
recipient, timing, payout behavior, parser semantics, shared PvP/PvE phrasing,
arbitrary `{game}` name insertion, chip denomination/value, or the physical
dealer versus a Baccarat-style banker (or another game mechanic that changes
intended meaning), inspect the Java call site rather than inferring intent
from the English string alone. See `TRANSLATION_GUIDE.md`'s semantic context
registry for verified examples, and extend that registry instead of
re-deriving the same facts from scratch on a future task.

Mechanical validation (`.\gradlew.bat localizationCheck`) is necessary but not
sufficient. For every changed value, also check that protected literals
(`NCCasino`, `Vault`, `PvP`, `PvE`, the `-1` sentinel, `/ncc` command tokens)
are preserved verbatim and that no source-language (English) residue was left
behind — see the guide for the specific gates and detection signals.

The user phrase **“ready to translate”** is an explicit handoff for an
**approved English delta**: treat the affected English copy as approved,
discover its exact changed keys, and perform the delta-translation workflow.
Do not reinterpret it as approval for a full rewrite, a targeted refinement of
unrelated keys, promotion of a benchmark candidate, or a commit/push.
