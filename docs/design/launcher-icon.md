# Minescape launcher icon

Generated with the built-in image generation tool. The original transparent artwork is preserved in `app/src/main/res/drawable-nodpi/minescape_icon_art.png`.

Android uses a violet background (#6554D9), the artwork inset by 20% in its adaptive foreground, and a matching vector monochrome tile/flag emblem for themed launchers. Both manifest icon references use `@mipmap/ic_minescape`. The previous icon is preserved as `docs/design/ic-launcher-previous.xml`.

## Generation prompt

Use case: logo-brand.
Asset type: transparent foreground artwork for the Android launcher icon of Minescape, a polished minimalist Minesweeper puzzle game.
Primary request: Create a beautiful small-scale readable emblem of four softly rounded Minesweeper tiles arranged in a compact 2 by 2 square. The upper right lavender tile carries a bold coral-red waving triangular flag on a dark indigo pole. The lower left mint tile is revealed and carries a single simple dark indigo numeral "1". The other two tiles are lavender, blank. Flag may rise slightly above its tile.
Style/medium: exceptionally clean contemporary app-icon illustration, smooth rounded geometry, near-front view with a very subtle playful perspective and gentle bevels, restrained soft shadows, premium tactile puzzle-piece feel. Match a calm Material 3 game UI.
Color palette: lavender #DBD5F5 and #EAE6FF, mint #B8E9D7, coral flag #FF7969, dark indigo #282640. Subtle violet #6554D9 shaded edges. No other colors beyond soft highlights.
Composition/framing: one centered cohesive emblem, four equal rounded tiles with clean narrow gaps, main silhouette occupies about 80 percent of the square image width and height with transparent margins. Each tile clearly separated. Flag is the main identifying feature and must be readable at 48px.
Scene/backdrop: genuinely transparent background, no surrounding icon container, no background square, no colored backdrop.
Constraints: no app name, no words, no extra numerals, no watermark, no stars, no decorative sparks, no explosions, no mascot. Clean edges and true alpha transparency. Square output.

## Verification

Run `./gradlew.bat testDebugUnitTest --tests com.camer.minesweeper.LauncherIconTest assembleDebug lintDebug`. The native Android renderer saves launcher previews at 48 and 512 pixels, plus a monochrome preview, in `app/build/outputs/screenshots/`.

The installable build with this icon is `artifacts/minescape-icon-debug.apk`; its checksum is in `artifacts/SHA256SUMS-icon.txt`. Native launcher previews are saved as `artifacts/minescape-icon-preview.png`, `artifacts/minescape-icon-preview-48.png`, and `artifacts/minescape-icon-monochrome.png`. No device installation was performed.
