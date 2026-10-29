# Automatic Google Play Internal Releases Implementation Plan

**Goal:** Automatically send tested main builds to Play internal testing.

**Architecture:** Extend the existing private polling workflow; sign bundles after checks and publish using the Google Play Developer API. Preserve unsigned private builds until credentials are configured.

**Tech Stack:** GitHub Actions, Android Gradle, JDK jarsigner/keytool, Python, google-auth.

**Spec:** `docs/superpowers/specs/2026-10-29-play-internal-design.md`

## Global Constraints

- AABs and signing/API credentials stay in `cbulock/minescape-builds`.
- Only `com.cbulock.minescape` / `internal` may be published.
- Retain Git-derived versions and the existing 30-minute polling schedule.
- Keep production promotion manual.

## Review Focus

- Missing credentials must not silently produce a successful Play deployment.
- Reruns after a completed Play commit must not upload a duplicate version.
- Old builds must not replace newer releases; reused version codes must match bundle contents.
- Google API failures must not produce a deployment success marker.
- The selected source commit must have passed main checks before release.

## Tasks

- [ ] Add publisher behavior tests for upload, retry, checksum mismatch, rollback, and API errors; run them before implementation.
- [ ] Implement the internal-only publisher and pin its authentication dependencies.
- [ ] Extend the private workflow with source check gating, credential validation, signing verification, private release assets, and publishing/retry markers; add publisher tests to public CI.
- [ ] Document one-time setup and promotion. Validate Python, workflows, signing commands, and Android build/checks.
- [ ] Install the updated private workflow and verify its setup state. Enable and perform a live upload only when the registered key and service account are available.
