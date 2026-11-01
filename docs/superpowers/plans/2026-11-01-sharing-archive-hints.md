# Sharing, Archive, and Hints Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement task-by-task.

**Goal:** Exact-board sharing, an offline daily calendar, and visual two-stage hints.
**Architecture:** Pure codecs and date policy feed serialized ViewModel commands; Compose owns presentation. A static landing page and Android intent filters share one versioned link contract.
**Tech Stack:** Existing Kotlin, Compose Material 3, JUnit/Robolectric, static HTML/JS.
**Spec:** ../specs/2026-11-01-sharing-archive-hints-design.md

## Global Constraints
- No backend, accounts, analytics, or new Android internet permission.
- Preserve frozen daily v1 layouts and all existing saves/official records.
- Invalid links and cancelled replacements never mutate saved games.
- Untrusted imported boards are practice and do not assert no-guess certification.

## Review Focus
- Pending imports must survive recreation without repeating a consumed launch intent (task 2 interaction test).
- Maximum custom boards need bounded decode and safe openings (task 1 unit test).
- Past unplayed dates must never become official records (task 3 routing test).
- Solver evidence must not consult mine truth or trust user flags (task 4 unit test).
- Visual hints must retain a usable board at large text and narrow width (task 4 render test).

## Task 1: Link codec and policy
Files: game/ChallengeLink.kt, test/game/ChallengeLinkTest.kt.
Interfaces: ChallengeLink.encode(Game): String; parse(String, LocalDate): SharedChallenge; SharedChallenge.Board.game(): Game; SharedChallenge.Daily.date: String.
- [x] Write round-trip, invalid payload/date, and large-board tests; run to observe failure.
- [x] Implement bounded binary bitmaps, CRC32, exact opening preservation, and version/date validation.
- [x] Run focused unit tests, confirm practice isolation and clean counters.

## Task 2: Share/open lifecycle and landing page
Files: MainActivity.kt, GameViewModel.kt, ui/App.kt, ui/ChallengeSharing.kt, AndroidManifest.xml, ui/DailyShare.kt, docs/play/index.html, docs/play/play.js, docs/design/daily-play-links.md, scripts/prepare-app-links.py.
Consumes task 1 codec; ViewModel exposes pending link state and receive/consume operations via SavedStateHandle. App parses before routing through replace confirmation.
- [x] Write cold/warm intent, confirmation/cancel/recreation, and daily-link share assertions.
- [x] Add share action, paste dialog, pending-link processing, HTTPS and fallback intent filters, and serial import command with expected slot identity checks.
- [x] Implement static landing page with explicit Open/install/copy controls; prepare domain-root verification tooling and deployment instructions.
- [x] Run interaction/manifest/share tests and static JS checks.

## Task 3: Daily archive
Files: ui/DailyArchive.kt, ui/DailyChallengesScreen.kt, ui/App.kt, GameViewModel.kt, test/DailyArchiveTest.kt.
Interfaces: calendar callback routes dates through existing daily chooser, with past dates forced to practice unless explicitly resuming the saved official slot.
- [x] Write future-date disabling, month navigation, past practice, and record-preservation tests.
- [x] Add accessible month grid with date/state labels, day selection preview and Play action; preserve selected month/date with rememberSaveable.
- [x] Route past puzzles safely and expose archive from daily screen/home navigation.
- [x] Run calendar and daily flow tests.

## Task 4: Visual hints
Files: game/LogicSolver.kt, ui/Board.kt, ui/HintPanel.kt, ui/App.kt, test/game/VisualHintTest.kt, androidTest/ChallengeFeaturesTest.kt.
Interfaces: Hint adds clueIndices and coveredIndices with defaults; solver constraints propagate evidence. Board accepts evidence sets and separate answer target. Hint panel owns saveable answer visibility keyed to hint/game.
- [x] Write provenance/hidden-truth independence and two-stage UI tests.
- [x] Track constraint evidence, render board outlines/semantics, add inline hint panel and separate Show answer/apply actions.
- [x] Keep timer paused and gestures for pan/zoom usable without playing moves during hint presentation.
- [x] Run new tests and existing learning/solver/catalog suites.

## Task 5: Review and verification
- [x] Run testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest.
- [x] Inspect screenshots and request fresh whole-change review, resolve material issues and retest.
- [x] Update README and release instructions with verified limits; report local artifacts and remaining host/device release requirements.

## Execution ledger
- User approved features and direct App Links in conversation. Implement inline without another approval round.
- Worktree created through Codex native tool; build commands require elevated access to its directory/cache.
- Domain-root repository/file lookups returned 404; prepare deployment material locally and do not claim live verification.

- Final validation: testDebugUnitTest, lintDebug, assembleDebug, and assembleDebugAndroidTest succeeded together. 218 tests: 217 passed, 1 Android-only test skipped; no failures/errors. Lint: 0 errors, 2 existing warnings.
- Static landing-page Node checks and app-association Python checks passed. Phone/large-text archive and hint screenshots and the landing-page preview were inspected.
- Fresh review fixes: completed daily imports now confirm replacement of regular practice; landing-page density validation uses exact integer arithmetic. Added regression coverage.
- Warm-intent test cleanup restores ActivityScenario's original launch intent after assertions; cold/pending tests retain recreation coverage.
- git diff --check passed. Debug application and instrumentation APKs are in .tools/feature-build/app/outputs/apk/. No connected Android device was available.
- Implementation remains local and uncommitted. Public direct opening requires publishing the app and landing page, hosting root /.well-known/assetlinks.json with the Play app-signing certificate, and verifying links on a device.
