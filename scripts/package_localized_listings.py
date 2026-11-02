"""Validate and package the prepared localized Google Play listing files.

Run the LocalizedScreenshotsTest Gradle tests before packaging screenshots.
This script writes local artifacts only; it never contacts Google Play.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import struct
import zipfile


ROOT = Path(__file__).resolve().parents[1]
PROFILES = {"es-ES": "es", "es-419": "es", "pt-BR": "pt-BR", "fr-FR": "fr", "fr-CA": "fr"}
SCREENSHOT_NAMES = (
    "01-classic-gameplay.png", "02-dark-theme-gameplay.png", "03-winning-result.png",
    "04-choose-your-board.png", "05-learn-to-play.png",
)


def listing_fields(path: Path) -> dict[str, str]:
    markdown = path.read_text(encoding="utf-8")
    sections = re.split(r"^## (.+?)\s*$", markdown, flags=re.MULTILINE)
    content = dict(zip(sections[1::2], sections[2::2]))
    fields = {
        "title": content["App name"].strip(),
        "short-description": content["Short description"].strip().split("\n\n")[0].strip(),
        "full-description": content["Full description"].strip(),
    }
    for name, limit in (("title", 30), ("short-description", 80), ("full-description", 4000)):
        if not 1 <= len(fields[name]) <= limit:
            raise ValueError(f"{path}: {name} has {len(fields[name])} characters; limit {limit}")
    if fields["title"] != "Minescape":
        raise ValueError(f"{path}: keep the Minescape brand unchanged")
    return fields


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts/localized-store-listings")
    parser.add_argument("--text-only", action="store_true", help="Prepare text without requiring rendered screenshots")
    args = parser.parse_args()
    output = args.output.resolve()
    # Validate every input before writing any package files.
    source = {language: listing_fields(ROOT / f"store-listing/localized/{language}/store-listing.md")
              for language in set(PROFILES.values())}
    screenshots: dict[str, list[Path]] = {}
    if not args.text_only:
        for language in set(PROFILES.values()):
            screenshots[language] = []
            for name in SCREENSHOT_NAMES:
                path = ROOT / f"app/build/outputs/localized-store-listings/{language}/phone-screenshots/{name}"
                header = path.read_bytes()[:26]
                if (len(header) < 26 or header[:8] != b"\x89PNG\r\n\x1a\n"
                        or struct.unpack(">II", header[16:24]) != (1080, 1920)
                        or header[24:26] != bytes((8, 2))):
                    raise ValueError(f"Expected a native 1080 × 1920 24-bit RGB PNG: {path}")
                screenshots[language].append(path)
    output.mkdir(parents=True, exist_ok=True)
    manifest = {}
    for play_locale, language in PROFILES.items():
        folder = output / play_locale
        folder.mkdir(parents=True, exist_ok=True)
        for field, value in source[language].items():
            (folder / f"{field}.txt").write_text(value + "\n", encoding="utf-8", newline="\n")
        manifest[play_locale] = {"app_language": language, "characters": {k: len(v) for k, v in source[language].items()},
                                 "screenshots": [] if args.text_only else list(SCREENSHOT_NAMES)}
        if not args.text_only:
            images = folder / "phone-screenshots"
            images.mkdir(exist_ok=True)
            for path in screenshots[language]:
                shutil.copy2(path, images / path.name)
    instructions = (ROOT / "store-listing/localized/README.md").read_text(encoding="utf-8")
    (output / "README.md").write_text(instructions, encoding="utf-8", newline="\n")
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    archive = output.with_suffix(".zip")
    # Explicit file list avoids carrying stale or unreviewed files into an upload package.
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as bundle:
        for name in ("README.md", "manifest.json"):
            bundle.write(output / name, name)
        for play_locale in PROFILES:
            for field in source[PROFILES[play_locale]]:
                path = output / play_locale / f"{field}.txt"
                bundle.write(path, path.relative_to(output))
            if not args.text_only:
                for name in SCREENSHOT_NAMES:
                    path = output / play_locale / "phone-screenshots" / name
                    bundle.write(path, path.relative_to(output))
    print(f"Prepared {len(PROFILES)} Play locale packages: {archive}")
    for locale, item in manifest.items():
        print(f"{locale}: {item['characters']}; {len(item['screenshots'])} phone screenshots")


if __name__ == "__main__":
    main()
