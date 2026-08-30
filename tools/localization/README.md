# NCCasino localization tooling

`src/main/resources/lang/en_US.yml` is the canonical catalog. Translation is
performed contextually by the maintainer; this repository intentionally does
not include a bulk machine-translation provider or external translation API.

For translation rules and semantics, read `TRANSLATION_GUIDE.md`. For
candidate comparison, review, and promotion, read `REVIEW_PROTOCOL.md`.

## Offline validation

Validate every registered production catalog without network access:

```powershell
.\gradlew.bat localizationCheck
```

Limit validation to selected locales:

```powershell
.\gradlew.bat localizationCheck '-Plocales=de_DE,fr_FR'
```

Strictly validate a complete local candidate before review or promotion:

```powershell
.\gradlew.bat localizationCandidateCheck '-Plocale=de_DE' '-Pcandidate=tools/localization/benchmarks/run/de_DE.yml'
```

The check fails on structural defects: missing or extra keys, damaged or
reordered placeholders and command tokens, changed parser sentinels, invalid
locale metadata, or altered protected literals such as `NCCasino`, `Vault`,
`PvP`, and `PvE`.

It reports non-fatal warnings for formatting-code count differences and values
that may contain untranslated English. Warnings require deliberate review but
do not fail unrelated work when the defect already exists in production.
Candidate validation instead enforces the complete placeholder and formatting
token sequence as a hard failure. The candidate locale does not need to be in
the production registry yet.

## Adding a locale

1. Add the locale ID and native display name to
   `src/main/resources/lang/locales.yml`.
2. Create a complete candidate locally under `tools/localization/benchmarks/`.
3. Review it using `REVIEW_PROTOCOL.md`.
4. Promote it to `src/main/resources/lang/<locale>.yml` only after explicit
   approval.
5. Run `localizationCheck` on the final production catalog.

The benchmark directory is ignored by Git. Generated candidates, comparison
packets, ledgers, audits, and model-specific working material remain local;
only approved production catalogs and reusable workflow documentation belong
in the repository.
