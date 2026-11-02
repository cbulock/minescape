# Android build quality and edge-to-edge

## Release optimization

Release builds use AGP 9.0.1 and Gradle 9.1.0, with JDK 17 or newer and Android Build Tools 36.0.0. AGP supplies built-in Kotlin; the separate `org.jetbrains.kotlin.android` plugin must not be reapplied. Compose and serialization compiler plugins remain enabled.

`isMinifyEnabled = true`, `isShrinkResources = true`, and `proguard-android-optimize.txt` enable R8 code optimization and resource shrinking. In AGP 9, optimized resource shrinking is enabled by default. No `android.r8.optimizedResourceShrinking` opt-in or optimization-disabling keep rules are needed. See [Android's R8 guidance](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization) and [AGP 9 compatibility requirements](https://developer.android.com/build/releases/agp-9-0-0-release-notes).

Both the public APK workflow and the private AAB workflow template install Build Tools 36.0.0. The public workflow also lints and assembles an optimized release APK to exercise R8 on pull requests; only the existing debug APK is published by that workflow. When deploying this change, copy `scripts/private-builds/build-aab.yml` to `.github/workflows/build-aab.yml` in `cbulock/minescape-builds`, as described in the release documentation. Editing the template here does not update the installed private workflow.

## Play Console edge-to-edge recommendations

The app targets SDK 36 and already calls AndroidX Activity's `enableEdgeToEdge()` before composing content. `MinescapeApp` paints its background across the window, then applies `safeDrawingPadding()` to interactive content. The settings, statistics, daily-challenge, tutorial, and result dialogs also handle safe drawing insets. System-bar icon appearance follows the app's selected theme through `WindowInsetsControllerCompat`.

The release mapping available during the November 1, 2026 investigation identifies Play's reported locations as AndroidX compatibility implementations:

| Reported location | Original implementation |
| --- | --- |
| `b.r.b` | `androidx.activity.EdgeToEdgeApi26.setUp` |
| `b.u.b` | `androidx.activity.EdgeToEdgeApi29.setUp` |
| `b.s.t` | R8-generated outline of `EdgeToEdgeApi28.adjustLayoutInDisplayCutoutMode` |

The library uses status/navigation bar color setters for older Android versions and `SHORT_EDGES` for API 28–29; API 30+ uses `ALWAYS`. These are library compatibility references, rather than direct application calls. [AndroidX's source](https://github.com/androidx/androidx/blob/androidx-main/activity/activity/src/main/java/androidx/activity/EdgeToEdge.kt) still contains them. Even the newer `WindowCompat.enableEdgeToEdge` implementation retains these compatibility references. Updating dependencies or switching helpers does not by itself guarantee that a static Play scan stops reporting them.

Retain the supported helper instead of suppressing lint or replacing it with custom window handling solely to remove a static warning. Android [recommends the helper for backward compatibility](https://developer.android.com/develop/ui/views/layout/edge-to-edge-manually). The deprecated-API recommendation can therefore remain after this build upgrade; its clearance must be checked after Google Play processes a newly uploaded release.

## Verification

Run the complete JVM interaction suite, lint, and an optimized release bundle:

```powershell
./gradlew.bat testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest bundleRelease
```

`EdgeToEdgeTest` exercises the actual activity on APIs 30 and 35. It injects status-bar and side-navigation insets in landscape and checks that the app's title/menu stay within them. It also checks system-bar icons after selecting dark and light app themes. Existing layout and interaction tests cover the board and dialogs.

Local validation on November 1, 2026, used an isolated checkout to exclude concurrent localization edits: 205 tests ran, with 204 passed, zero failures/errors, and the Android-only provider test skipped on Windows. Removing `safeDrawingPadding()` deliberately made the inset test fail on both APIs; restoring it passed. Debug/release lint reported zero errors (two warnings each), and debug, release, instrumentation APK, and release AAB builds succeeded. The generated AAB's `BUNDLE-METADATA/com.android.tools/r8.json` confirms `resourceOptimization.isOptimizedShrinkingEnabled = true`; its app metadata reports AGP 9.0.1.

The shared checkout, including the concurrent localization changes, also passed debug/release lint and all four artifact builds using a separate build directory. A missing Kotlin incremental output directory during that additional check required a rerun with `'-Pkotlin.incremental=false'` to generate fresh compiler outputs. Standard-layout validation in the isolated checkout passed without that option. No Android device was connected, and emulator acceleration was unavailable on this host, so real window-manager checks were not run.

Before promoting a release, check a device or emulator on Android 15/16 and an older supported version. Exercise gesture and three-button navigation, portrait/landscape, a display cutout, app light/dark overrides, and the keyboard in custom-board/backup dialogs. Confirm the board, menus, dialog close buttons, and bottom controls remain accessible. JVM inset injection is not a substitute for these real window-manager checks.
