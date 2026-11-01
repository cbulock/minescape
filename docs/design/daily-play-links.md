# Shared board and daily play links

Implemented in the Android app and `docs/play/`. Direct opening requires publishing the landing page, host-root association, and app update. Local tests cannot establish live domain verification.

## User flow

- Updated app installed and domain verification enabled: a matching HTTPS link opens the board in Minescape. Confirm before replacing an unfinished destination game.
- App missing, links disabled, or messaging app uses its own browser: the landing page offers **Open in Minescape**, Google Play, and **Copy puzzle link**. The app menu's **Open shared board** accepts pasted links.
- After installation, reopen the original link. No deferred post-install restoration service is used.
- Regular shared boards start as practice with a fresh identity and clock. Today's daily starts/resumes the official attempt; completed and past dailies start practice. Explicit resume actions retain access to a saved previous-day attempt.

## Versioned contract

Canonical URL: `https://cbulock.github.io/minescape/play/#d1.2026-11-01` or `https://cbulock.github.io/minescape/play/#b1.PAYLOAD`.

Fallback: `minescape://play/#` with the same fragment. HTTPS accepts only the exact host/path, without query, port, or user info. Unsupported versions, malformed/future dates, and invalid payloads leave saves unchanged. Input is capped at 1,400 characters.

Daily v1 uses the existing frozen catalog and transformations. Daily links contain no board or result data, and official result images remain decorative.

Regular b1 is unpadded base64url of big-endian bytes:

| Bytes | Value |
| --- | --- |
| 0-1 | Columns, rows (5-50 each) |
| 2-3 | Mine count |
| 4-5 | Safe original opening index |
| 6-7 | Bitmap byte count, ceil(cells / 8) |
| Next bitmap | Mine positions, low bit first within each byte |
| Next bitmap | Original revealed opening positions |
| Final 4 | CRC32 of preceding bytes |

Maximum payload: 638 bytes. Counts, lengths, spare bits, safe opening, and CRC are validated before reconstruction. Adjacencies are recomputed. Imports never assert no-guess certification or qualify for normal records. The opening bitmap preserves openings affected by pre-opening flags without exporting the flags. Missing legacy openings use the safe retry fallback, explained in the share dialog.

Fragments are not sent in HTTP requests. Regular board layouts are encoded, not encrypted: this is casual sharing, not cheating-resistant competition. The page displays no solution or moves. No backend, accounts, analytics, or Android internet permission is added.

## Required host-root deployment

Android requires `https://cbulock.github.io/.well-known/assetlinks.json`. A file under `/minescape/.well-known/` does not verify the host. Manage the host-root site separately (normally the `cbulock.github.io` repository), or deliberately migrate the canonical URL and manifest host to a controlled domain.

1. Obtain the **app signing key certificate SHA-256** from Play Console's app integrity/signing page. An upload-key fingerprint alone does not verify Play-installed builds.
2. Generate the root association using the helper in this repository:

   ```powershell
   python scripts/prepare-app-links.py --fingerprint 'ACTUAL_SHA256_FROM_PLAY' --output 'PATH_TO_HOST_SITE/.well-known/assetlinks.json'
   ```

   Repeat `--fingerprint` for all supported signing certificates. Other applications' associations are preserved. If using Jekyll, explicitly include `.well-known` or disable Jekyll so the dot-directory is published.
3. Publish the root file over HTTPS, with JSON content type and no redirects. Publish `docs/play/` through the existing Pages site.
4. Release the updated app and verify a Play-installed build on a device:

   ```powershell
   adb shell pm verify-app-links --re-verify com.cbulock.minescape
   adb shell pm get-app-links com.cbulock.minescape
   adb shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://cbulock.github.io/minescape/play/#d1.2026-11-01'
   ```

   Use an available date and allow verification to finish; the host must report verified. Test actual messaging/browser taps, cold/warm launch, disabled supported links, installation/reopen, cancellation, date rollover, and both unfinished game slots.

The implementation session could not access the root repository through the GitHub connector (404). It did not publish an association or invent a Play fingerprint. Publication and real-device verification remain release requirements.
