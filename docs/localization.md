# Localization

Player-facing copy lives in Android resources. The unqualified
`app/src/main/res/values/strings.xml` remains the complete English fallback.
Spanish, Brazilian Portuguese, and French are prepared locally alongside it;
their presence in this checkout does not mean a translated build has been published.

| Language | Resource folder | Intended wording |
| --- | --- | --- |
| English | `values` | Existing English fallback |
| Spanish | `values-es` | Neutral Spanish, including Spain and Latin America |
| Portuguese | `values-pt-rBR` | Brazilian Portuguese |
| French | `values-fr` | Neutral French for France and Canada |

Android selects the matching language from the device configuration. On Android
13 and later, `locales_config.xml` also exposes these four languages in Android's
per-app language settings. There is no custom in-app language picker. Earlier
Android versions continue to use device language selection.

All three translated catalogs cover the 225 translatable strings and 12 quantity
resources. The Minescape brand is inherited unchanged. Spanish, Portuguese, and
French quantity resources include `one`, `many`, and `other`; Portuguese uses
label-and-number wording where its zero/one category would otherwise produce
awkward counts. Compound history summaries also use label-and-number wording.

Translations are AI-authored, with independent source review and automated
checks; review by fluent speakers remains pending. In particular, review the
guided lesson, opening surrounding cells, and the deduction explanations before
public release. See each listing's glossary in
[the prepared store materials](../store-listing/localized/README.md).

## Adding or changing copy

- Use complete string templates with numbered arguments. Do not concatenate
  words or choose English singular/plural forms in Kotlin. Quantity resources
  cover hints, undos, mines hit, adjacent mines, board summaries, backup metrics,
  and solver conclusions. Add context comments for ambiguous placeholders.
- Compose screens use `textResources()`, which tracks `LocalConfiguration` and
  creates a configuration-specific resource snapshot. That snapshot also works
  in semantics callbacks and background share-card rendering. Format numbers and
  times using its locale; dates retain their existing styles and puzzle dates.
- ViewModel notifications carry `UiMessage` values, resolved at display time.
  Progress cancellation is an explicit flag, independent of the message text.
- `Difficulty` retains its serialized enum names. Its display labels are mapped
  in the presentation layer. Save and backup schemas remain unchanged.
- Solver hints carry structured evidence, not English sentences or Android
  resource IDs. Presentation formats clue, board-total, and comparison evidence
  with a 600-character cap at each constraint, followed by a quantity-aware
  conclusion. Iterative rendering handles deep and shared evidence safely.
- Daily-share text, preview accessibility text, and PNG labels use the same
  resource configuration. An open preview regenerates when that configuration
  changes. The image remains 1080 × 720 and contains no actual puzzle layout.

## Intentionally untranslated literals

The source audit leaves these outside the translation catalog:

- Navigation destinations, lazy-list keys, test tags (including `tutorial-`),
  enum names, JSON keys, asset names, and saved-file identities.
- MIME types, URI authorities, the Google Play URL, cache paths, export filenames,
  and document-provider file modes.
- Developer-only validation and I/O exception messages. The player sees separate
  resource-backed error messages instead of these exception details.
- Font-family identifiers, date/number format patterns, and the fixed `UTC`
  designation in backup timestamps. Date words and number formatting use the
  current configuration's locale.
- Drawing glyphs and board symbols (`+`, `−`, arrows, question marks, clue digits),
  and punctuation-only empty or missing-value markers. Their accessibility
  labels are translated independently. Minescape's brand resource is explicitly
  marked `translatable="false"`.

## Verification

Run:

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Debug builds enable Android's generated `en-XA` and `ar-XB` pseudolocales. Release
builds do not enable them. Localization tests cover quantity grammar, formatted
cell descriptions, fallback, deep evidence, resource changes with pending
messages and an open share preview, expanded phone/landscape/large-text layouts,
and RTL board targeting. PNG previews are written under
`app/build/outputs/screenshots/localization-*.png`.

Pseudolocale checks help prepare translations; they do not certify support for
Arabic or other actual languages. A future translation release still needs
language review, store-listing work, and on-device TalkBack/share-sheet checks.

Foundation verification on 2026-11-01: the full suite passed 214 tests with one
Android-only provider test skipped. Lint and both debug APK builds passed.
After the final resource-name cleanup, the affected localization and daily-screen
tests, lint, and both builds passed again. Lint has zero errors and nine remaining
tooling/dependency/style warnings. Expanded-text and RTL screenshots were visually
inspected. No emulator or device was connected, so physical TalkBack, share-sheet,
and receiving-app checks remain unverified.

Translation verification on 2026-11-01: the clean full suite passed 224 tests
with one Android-only provider test skipped. Lint and both debug builds passed.
After correcting the French settings heading at 200% text, all 23 affected
localization/settings/catalog/screenshot tests and both builds passed again.
Final lint has zero errors and ten warnings; the additional warning is the
intentional Android 13+ `localeConfig` attribute on an app that also supports
older Android versions. The generated listing ZIP contains five Play locale
variants, each with validated text and five opaque 1080 × 1920 RGB phone PNGs.
