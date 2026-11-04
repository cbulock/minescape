# Prepared Google Play translations

These are review/upload materials. German and Italian descriptions and five phone
screenshots per language were saved as Play Console drafts on 2026-11-04;
earlier Spanish, Portuguese, and French listings were also entered as drafts.
European Portuguese (`pt-PT`) text and five phone screenshots were also saved as
a Play Console draft on 2026-11-04, separately from Brazilian Portuguese.
Country availability remains managed separately. Translations are AI-authored and have not yet had
independent fluent-speaker review. Review the tutorial, deduction terminology,
and screenshots before publication.

| App resources | Listing source | Play Console languages |
| --- | --- | --- |
| `values-es` (neutral Spanish) | `es/store-listing.md` | Spanish (Spain), `es-ES`; Spanish (Latin America), `es-419`; Spanish (United States), `es-US` |
| `values-pt-rBR` (Brazilian Portuguese) | `pt-BR/store-listing.md` | Portuguese (Brazil), `pt-BR` |
| `values-pt-rPT` (European Portuguese) | `pt-PT/store-listing.md` | Portuguese (Portugal), `pt-PT` |
| `values-fr` (neutral French) | `fr/store-listing.md` | French (France), `fr-FR`; French (Canada), `fr-CA` |
| `values-de` (German) | `de/store-listing.md` | German, `de-DE` |
| `values-it` (Italian) | `it/store-listing.md` | Italian, `it-IT` |

The shared Spanish and French text is deliberately neutral. Regional wording can
be refined separately without changing the app's shared language resources.

## Rebuild the upload package

From the repository root:

```powershell
./gradlew.bat testDebugUnitTest --tests '*LocalizedScreenshotsTest'
python scripts/package_localized_listings.py
```

The output is `artifacts/localized-store-listings.zip`. Each Play locale contains
UTF-8 `title.txt`, `short-description.txt`, and `full-description.txt`, plus five
1080 × 1920 phone screenshots rendered from the actual localized app. The title
remains Minescape. Text limits are checked at 30, 80, and 4000 characters.
German, Italian, and European Portuguese packages also include `release-notes.txt`
(500-character limit). Combined release-note blocks are included as
`release-notes-*.txt`; the newest is `release-notes-european-portuguese.txt`.
Use `--text-only` to validate/package only the text before screenshot generation.

The first three phone images show classic gameplay, dark-theme gameplay, and a
winning result. Board selection and the guided lesson follow. These prepared
assets cover phone portrait only; translated tablet and landscape sets are not
included. The standalone daily-result PNG under the test output is a QA preview,
not a phone screenshot to upload.

## Apply after review

1. In Play Console, open Minescape's store listing and add the desired languages
   under Manage translations / Select languages.
2. Copy the matching package's three text fields into its localized listing.
3. Add its phone screenshots in numbered order. Preview any retained screenshots
   or other assets so the listing does not mix translated and English UI images.
4. Publish the translated app build and listing together when ready.

Adding a translation does not enable additional countries. Production country
availability remains a separate setting. Do not enable Play-managed automatic
app-string translation for these same languages unless deliberately replacing
these repository-managed translations.

References: [Google's localization guidance](https://support.google.com/googleplay/android-developer/answer/9844778?hl=en),
[store field limits](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en),
[localized graphic guidance](https://support.google.com/googleplay/android-developer/answer/13393723?hl=en).
