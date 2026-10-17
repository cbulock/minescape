# Minescape for Android

A small, offline Minesweeper game built with Kotlin, Jetpack Compose, and Material 3. Android 8.0 (API 26) and later.

## Download

**[Get Minescape on Google Play](https://play.google.com/store/apps/details?id=com.cbulock.minescape)**

For direct installation: [Download the latest APK](https://github.com/cbulock/minescape/releases/latest/download/minescape.apk) · [All APK releases](https://github.com/cbulock/minescape/releases). The website keeps these under **Other download options**.

Open the APK on your Android device and allow installation from your browser or file manager when prompted. Android 8.0 or later is required. These are debug-signed preview builds, using a stable signing key so updates can replace earlier builds. Each release includes a SHA-256 checksum.

GitHub Actions tests, lints, builds, and publishes an APK after each successful push to `main`. Pull requests run the same checks without publishing a release. The download link becomes available after the first successful build.

Release AABs are built and stored only in the **private [minescape-builds repository](https://github.com/cbulock/minescape-builds/releases)**. Its workflow checks `main` for new commits every 30 minutes (GitHub may delay scheduled runs), skips commits already built, and can be run manually. Bundles and checksums remain in private releases without artifact expiry. The AABs are unsigned; Play Store upload signing and publication are not configured.

The public APK workflow requires the `ANDROID_DEBUG_KEYSTORE_BASE64` Actions secret, containing the existing debug keystore, to preserve update compatibility. The private workflow source is [scripts/private-builds/build-aab.yml](scripts/private-builds/build-aab.yml); install it as `.github/workflows/build-aab.yml` in the private repository. It refuses to upload if that destination is public. GitHub Releases hosts the downloadable files; GitHub Packages does not provide a standalone APK registry.

## Play

- The home screen groups the three preset boards for one-tap play. **Game options** holds the saved Practice and No-guess switches, with the current mode shown beside it. Custom boards, Daily challenge, learning, and statistics are below; an existing game's return card appears first.
- Beginner: 9 × 9, 10 mines. Intermediate: 16 × 16, 40 mines. Expert: 30 × 16, 99 mines. Custom: 5–50 columns and rows; 1–35% mines, with the last valid configuration remembered.
- Tap to reveal, hold to flag, or use Flag mode. A tap on a revealed number chords when its surrounding flag count matches. Incorrect flags can still make an intentional chord lose.
- A hold flags once and never reveals or chords. Targets stay anchored to the starting cell; crossing a cell boundary cancels activation. Dragging, pinching, cancellation, and changes to the game, input mode, layout, or viewport cancel pending moves.
- When cells are below 32 dp, the first touch only zooms in. Lift and use a fresh gesture to play. Pinch/drag and the zoom/Fit board buttons remain available; accessibility actions work at any zoom.
- Fit board is the minimum zoom: pinching out and the zoom-out button stop at the fitted board size, including after layout changes.
- After a win or loss, drag and pinch to inspect the board, or use the zoom/Fit board buttons. Finished cells cannot be revealed, flagged, or chorded.
- Classic is the default. Optional **No-guess presets** are certified solvable using deduction from your safe opening. Generation runs in the background, capped at 500 candidates or 10 seconds, with cancellation, retry, and explicit classic fallback. Custom boards use classic generation.
- **Practice** provides explained safe-square/mine hints and undo, including fatal moves. Player flags are not assumed correct. Undo keeps elapsed time and assistance counters, persists the latest 100 moves, and cannot regenerate the initial opening. Practice does not affect normal records.
- **Play again** starts a fresh board at the same difficulty, generation mode, and practice setting.
- **Retry same board** on a finished regular board preserves its exact mine layout and original revealed opening, with a fresh identity, clock, markers, and assistance counters in practice. Older saves use a safe opening when the original wasn't stored. Daily replays retain their fixed opening. Retries never affect normal or official daily records.
- **Learn to play** on the home screen, or the guided-board button in How to play, teaches revealing, flagging, and chording on an interactive three-step board. The lesson preserves both saved games and all records; its step survives activity recreation.
- Optional **Question marks** in Settings make holds and Flag mode cycle flag → question mark → clear. Question marks don't contribute to the mine counter or satisfy chords; revealing clears them, and practice undo restores them. Turning the option off preserves existing marks until cleared.
- **Controls** in Settings offers the device's default hold duration or 300/500/800/1200 ms, and a toggle for tap-to-chord. Changing controls cancels pending gestures. With tap-to-chord off, revealed-number taps do nothing; an explicit accessibility action remains available.
- Preset wins show **New personal best** when strictly faster than the prior record for that difficulty and generation mode (including the first recorded win). The result card shows time, safe squares cleared, the previous best, and practice assistance.
- The result card opens automatically after the win/loss animation (immediately with reduced motion), led by **You won** or **Game over**. Its illustrated outcome, stat tiles, progress bar and record highlight sit above **Play again**, retry, practice undo and daily sharing. **View board** dismisses it without reopening on navigation or rotation; **View result** opens it again. Finished boards use one compact header and hide the mine counter, practice status row and disabled Flag control. Large text stacks the tiles, and short screens scroll. During play, **New game** remains a filled tonal action with a plus symbol; it is also available in the menu.
- A fresh win launches colorful, tumbling **confetti** from both lower board corners for 2.8 seconds. Hitting a mine creates a 950 ms **explosion** at that cell with sparks, expanding rings, and fading smoke. These drawing-only effects leave controls usable and the board steady, follow the device animation scale, and are suppressed when animations are disabled. Menus/backgrounding cancel them; opening or restoring a completed game never replays them. Practice undo permits a new burst on the next result.
- **Daily challenge** is one intermediate no-guess board per local date, with a fixed safe opening. The first official attempt counts; subsequent replays are practice. Its 366-layout catalog and date mapping are frozen for version 1, including rotation/reflection across cycles.
- Regular/practice and daily games save in separate slots, including time, flags, undo history, and viewport. Switch through the menu without losing either game. Replacing an unfinished slot requires confirmation; replacing a previous-day challenge archives it as abandoned. Menus, dialogs, generation, and backgrounding pause the clock.
- Android **Back** closes the top menu or dialog first, returns pages and the guided lesson to the screen that opened them, and returns New game to the saved board. Back cancels replacement/import confirmations without changing progress and cancels no-guess generation or hint calculations. At the main board or initial home screen it uses Android's normal back-to-home behavior. Backup reads/writes finish before their progress dialog closes.
- Normal statistics include wins/losses, win percentage, current/best winning streak, best preset times, and the latest 100 results, filterable by difficulty and classic/no-guess mode. Custom boards share aggregate totals without a combined best time. Daily history is separate; abandoned boards do not count as normal losses.
- Daily challenges opens with a status-aware today card and localized dates. History uses separate result cards with share previews and practice actions, ten attempts at a time. An earlier unfinished challenge has its own resume action; large text stacks actions, and wide screens keep a centered reading width.
- Statistics opens with an all-time win-rate overview, difficulty record cards, and dated recent results shown ten at a time. Mode and difficulty filters survive recreation; empty filters explain how records begin. Large text stacks metrics, wide screens use a centered reading width, and counting details expand on demand.
- **Share result** on a finished official daily attempt or in daily history opens an in-app preview of the styled PNG card; **Share image** then opens Android's share chooser with the card and text showing a localized date, outcome, and time, plus a link to install Minescape from Google Play. The share action stays visible beneath the daily result details, including the win screen. Each export has a fresh URI to prevent stale thumbnails. The date keeps the daily puzzle's identity without a timezone label. The tile illustration is decorative and includes no board layout or moves. Practice does not produce an official share result. Sharing grants read access only to the generated card through a non-exported file provider. Future play links are outlined in [the daily-link plan](docs/design/daily-play-links.md).
- Settings offers **Export progress** and **Import progress** through the Android document picker. Backups include both slots, preferences, statistics, histories, and undo. Imports validate before showing a preview and asking to replace progress; invalid files and failed writes preserve current progress. Maximum file size: 16 MiB.
- Settings uses a full-screen layout with appearance, controls, and backup cards, a fixed Done action, and a centered reading width on tablets. Toggle rows are tappable, theme choices stack with large text, and hold-duration choices wrap. Preferences save as you change them.
- Light, dark, and device themes, with Material You wallpaper colors on Android 12 and later across screens and board tiles (the original palette on older Android versions); optional haptics; accessible cell labels/actions. Board numbers retain distinct colors for readability. Compose animations follow the device animation scale.

## Build and install

Open this folder in Android Studio, let Gradle sync, and run `app` on a device or emulator. Install Android SDK Platform 36.1 and Build Tools 35.0.0 through SDK Manager if prompted. Use JDK 17 or newer (Android Studio's bundled JDK works).

On Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat assembleDebug
& "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r ./app/build/outputs/apk/debug/app-debug.apk
```

If SDK discovery fails, set `ANDROID_HOME` to your SDK location or create a local `local.properties` file containing `sdk.dir=C\:/Users/YOUR_NAME/AppData/Local/Android/Sdk`. This machine-specific file is ignored.

The installable debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Version 1.2.1 (version code 4) is delivered in `artifacts/minescape-debug.apk`, with a compatibility filename `minesweeper-debug.apk`, SHA-256 checksums, and game/tutorial/controls/result/share/confetti/explosion previews alongside it. It uses Android's debug signing key and needs no account or network permission. The application ID, Android namespace, and Kotlin package root are `com.cbulock.minescape`; source and test files live under `com/cbulock/minescape`. The share provider authority is `com.cbulock.minescape.files`. The internal save filename remains `minesweeper.json` to preserve existing progress across updates. Minescape is also available on Google Play through the store link above.

## Verify

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./gradlew.bat connectedDebugAndroidTest
```

The first command also runs the Compose interaction and layout tests locally with Robolectric 4.17, Android API 35, and native graphics. Test-only dependencies do not ship in the APK. Screenshots are written to `app/build/outputs/screenshots/`.

The second command runs the shared interaction tests on a booted emulator or USB-debugging-enabled Android device. Instrumented app tests reset **this app's** local data, so use a test device rather than a saved game you want to retain.

JUnit tests cover rules, the shared deduction solver, all 366 frozen daily layouts, transformations and local-midnight rollover, no-guess generation limits, practice undo, question marks and their undo/backup behavior, exact-layout retries (including pre-opening markers), strict personal-best comparisons, mode-isolated scoring, migration, bounded backup parsing, disk restoration, and partial-write rollback. Shared UI tests exercise exact hold thresholds, jitter, adjacent-cell crossing, cancellation, multi-touch transitions, known-mine holds, tiny-cell zoom, changes during a pending press or control setting, replay, practice, tutorial progress/recreation, saved control settings, personal-best feedback, daily switching/abandonment, and backup preview/cancel/restore/failure. Native-render screenshots cover phones and tablets, landscape, both themes, 200% text, and the new screens. Device test captures are saved under the app's external-files directory.

Local share tests verify the generated PNG, spoiler-free official text, scoped provider configuration, URI grants, Android chooser intent, and exclusion of practice results. AndroidX FileProvider's path containment assumes Android slash separators, so Windows JVM tests shadow URI creation at that OS boundary. `ShareProviderTest` reads the card through the real provider and rejects other cache files on Android; that test explicitly skips on Windows. Receiving-app access and actual chooser interaction remain device checks.

### Validation boundary

Final local verification: **125 tests passed**, with zero failures/errors; one Android-only provider test was skipped on Windows (126 total). Coverage includes fresh confetti/explosion transitions, effect completion, usable zoom controls during playback, practice undo/re-hit, suppression on completed saves, recreation during celebration, menu/background cancellation, disabled system animations, and native-render portrait/landscape effects, alongside the six expanded features, safe gestures, minimum zoom, save migration, and phone/landscape/200% text layouts. `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` passed. APK v2 signature and version 1.2.1/code 4 were verified. The debug APK is approximately 12.6 MiB. Lint reported zero errors; remaining warnings concern dependency versions and the existing launcher resource folder. The packaged manifest retains backup disablement and has no internet permission.

No Android device or emulator was connected during this feature pass (`adb devices` returned an empty list), so `connectedDebugAndroidTest` was not run. Animation smoothness on hardware, real touch latency, TalkBack traversal, the OS document picker, and actual OS process-kill/relaunch remain device checks. Local Android-runtime interaction tests, actual backup/file I/O, disk save/reopen, activity recreation, and native-render screenshots passed.

## Structure

- `game/`: immutable game models, explicit touch intents/state machine, rules/undo engine, shared deduction solver, certified generation, frozen daily catalog, and viewport transforms.
- `data/`: versioned typed JSON DataStore, migration/validation, bounded backup codec, corruption recovery, and transactional result recording.
- `GameViewModel`: serializes game commands and checkpoints active elapsed time once per second using a monotonic clock.
- `ui/`: Material 3 screens, shared board rendering/hit-test/semantics transform, and touch gesture arbitration.

No backend, ads, analytics, accounts, or internet permission. Progress lives in app-private storage; uninstalling or clearing app data removes it. Save format version 3 migrates version 1 and 2 boards/settings/totals without inventing old streaks, openings, or personal-best comparisons. New fields and control settings are included in backups, with the device hold duration, tap-to-chord on, and question marks off as migration defaults. Unsupported or invalid internal saves recover with an explanation; invalid imported backups never reset existing progress. Export a backup before uninstalling or clearing app data.

The independently generated daily catalog is `app/src/main/assets/daily-v1.json`. `scripts/generate_daily_catalog.py` documents its creation and refuses to overwrite an existing v1 catalog; runtime-solver tests pin its checksum and certify every layout. Preserve v1 identities after distribution.
