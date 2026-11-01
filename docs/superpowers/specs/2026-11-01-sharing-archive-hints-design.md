# Sharing, daily archive, and visual hints

## Intent and approval
The user selected board sharing, a daily archive, and visual hints, then approved verified Android App Links with a website fallback. Preserve the simple offline Minesweeper rules, controls, saves, and official records. Implement locally; publication is a separate release action.

## Approaches
Copy/paste-only challenge codes were considered. The approved approach is HTTPS links that open directly in the updated app when domain verification succeeds, with a webpage offering Open in Minescape, installation, and copy/paste fallback. No backend, accounts, analytics, or new Android internet permission.

## Sharing
- A generated regular board shares its exact mine layout and original revealed opening, not the sender's moves, flags, elapsed time, or personal data. Use a bounded versioned binary payload in a URL fragment, with dimensions, mine bitmap, opening bitmap, and checksum. The checksum detects accidental corruption, not tampering. Imports are always practice and never claim solver certification from untrusted input.
- Daily links contain only frozen catalog version 1 and ISO date. Today starts/resumes the official attempt or offers practice after completion; past dates start practice. An existing unfinished official attempt for the same past date may resume explicitly. Reject future, malformed, and unsupported links.
- HTTPS route: https://cbulock.github.io/minescape/play/ with fragment d1.DATE or b1.PAYLOAD. The fallback button uses minescape://play/ with the same fragment. The app also accepts pasted HTTPS links.
- Receiving a link never silently replaces an unfinished slot. Confirm replacement and retain pending link through activity recreation/process restoration. Reopening a consumed launch intent must not import again. Validate before changing saved state.
- Sharing is available after the first reveal. Original openings missing in legacy saves use the existing safe retry fallback, explained in the UI. Share preview remains spoiler-free; ordinary and daily links are separate from sharing an official daily result.
- Verified App Links require the host-root /.well-known/assetlinks.json and the actual Play app-signing certificate fingerprint. Prepare a deployment file and clear instructions; do not invent fingerprints or claim direct opening has been verified without a device and deployed host.

## Daily archive
Add a month calendar accessible from Daily challenges. Permit past dates back to 2026-01-01 (builder choice: a bounded first-year archive consistent with the frozen catalog). Disable future dates. Show official attempted/won/lost states accessibly; practice does not manufacture official records. Today uses normal official rules. Keep the explicit previous unfinished challenge resume action. Use the existing regular-slot replacement confirmation for archived practice.

## Visual hints
Retain solver evidence as sets of supporting revealed clues and covered cells, including provenance from subset deductions and proven mines; never read hidden mine truth. First show the region without disclosing the exact answer, then Show answer highlights the target and explains deduction. Offer existing apply action only at the answer stage. Draw distinct labeled clue/covered/answer outlines on the board, with accessible labels. Put the hint in a compact panel so the board remains visible. Pause the timer during hints, keep pan/zoom usable, and invalidate hints after moves, navigation, or game changes. Count a two-stage hint once. No-deduction messages remain honest about solver limitations.

## Validation
Unit tests cover binary round trips including 50x50 boards, corruption/bounds, date routing, frozen daily identities, solver evidence independent of hidden truth/flags, and record isolation. Interaction tests cover calendar navigation, past practice, imported-board confirmation/cancel/recreation, cold/warm intents, two-stage hints, and accessibility. Run existing tests, lint, and APK builds. Verify rendered calendar and hint panel at phone/large-text sizes. Document unavailable device/domain checks.
