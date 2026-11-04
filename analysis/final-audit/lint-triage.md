# Lint triage for the final audit

Source: `app/build/reports/lint-results-debug.sarif` and `core/cloud/build/reports/lint-results-debug.sarif`, generated with `lintDebug` before the modernization. Status: 0 errors, 38 app warnings and 1 core warning.

## Functional or maintenance fixes

- `PrivateResource`: rename the own `selected` resource so it does not overwrite a private Compose resource. Update references and the plural if applicable.
- `PluralsCandidate`: turn the selected count into an ES/EN plural if the UI keeps that text.
- `DataExtractionRules`: define an explicit backup and transfer policy consistent with secrets, sessions and Keystore. Do not copy the insecure policies of the original APK.
- `TypographyEllipsis`: replace three dots with the typographic ellipsis character in own resources.
- `IconMissingDensityFolder`: review rasterized resources and density. Do not duplicate images without need, prefer own vectors or `nodpi` when that is correct.
- `UseTomlInstead`: move test dependencies declared by hand to the catalog when each module is touched.

## Version warnings

There are warnings for Gradle, AGP, AndroidX, coroutines, serialization and Ktor. They are not updated automatically during protocol or UI changes. Each update must be independent, with build, unit tests, instrumentation and compatibility review. A new-version warning does not prove that the current version is insecure or incompatible.

## Closure criterion

The final review must run `lintDebug` again. The remaining warnings must be classified and it cannot be claimed that a warning is irrelevant without reviewing its location and effect.
