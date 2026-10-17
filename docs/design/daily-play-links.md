# Daily play links

Status: store link implemented; dated puzzle links planned. Shared result text includes the Google Play listing at https://play.google.com/store/apps/details?id=com.cbulock.minescape through `DailyShare`, covering both the finished-result and daily-history entry points. This installs or opens the app; it does not open a specific dated puzzle. The result image remains spoiler-free and unchanged.

## Link contract

- Once a domain and landing page are available, use an HTTPS URL with a versioned puzzle identity, such as `/daily/v1/2026-10-09` (path proposal only). Keep the ISO date in the URL; localize only the visible date. Version 1 identifies the existing frozen catalog and transformations.
- Add the link to shared text centrally in `DailyShare` so both sharing entry points agree. Optionally add a short URL or QR code to the image once it resolves. Keep the link free of results, board data, personal information, or tracking parameters.
- Configure verified Android App Links on the chosen domain. Installed users open the matching dated puzzle; other visitors get a useful landing page with installation instructions and, when published, the real Play Store listing. Do not promise automatic post-install puzzle restoration without implementing and testing it; keep the dated link available to reopen.

## Game behavior

- Validate the catalog version and date before navigation. Unsupported versions, invalid dates, and future puzzles need a clear explanation without changing saved games.
- Today's unplayed puzzle starts the official daily attempt; an in-progress attempt resumes; a completed attempt offers practice. Past puzzles open as practice and must not replace today's official record.
- Follow the existing saved-game replacement confirmation flow. Receiving or reopening a link alone must never overwrite progress. Preserve puzzle identity through installation, cold start, and activity recreation.

## Launch checks

Verify installed and uninstalled flows, app-link domain verification, malformed and unsupported links, date rollover across timezones, repeated opens, saved games, first-attempt rules, and spoiler-free sharing. Enable links only after the landing page and Android routing are live; a Play Store release can follow independently.
