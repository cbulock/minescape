"""Prepare the HOST-ROOT App Links association using verified signing fingerprints."""
import argparse
import json
import pathlib
import re

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--fingerprint", action="append", required=True, help="SHA-256 app-signing certificate fingerprint from Play Console; repeat for additional supported certificates")
parser.add_argument("--output", type=pathlib.Path, required=True, help="Destination .well-known/assetlinks.json in the host-root site checkout")
args = parser.parse_args()
fingerprints = []
for value in args.fingerprint:
    normalized = value.replace(":", "").upper()
    if not re.fullmatch(r"[0-9A-F]{64}", normalized):
        parser.error("Every fingerprint must contain exactly 32 SHA-256 bytes.")
    fingerprints.append(":".join(normalized[i:i+2] for i in range(0, 64, 2)))
statement = [{"relation": ["delegate_permission/common.handle_all_urls"], "target": {
    "namespace": "android_app", "package_name": "com.cbulock.minescape",
    "sha256_cert_fingerprints": sorted(set(fingerprints)),
}}]
if args.output.exists():
    existing = json.loads(args.output.read_text(encoding="utf-8"))
    if not isinstance(existing, list):
        parser.error("Existing assetlinks.json must be an array.")
    statement = [item for item in existing if not (
        item.get("target", {}).get("package_name") == "com.cbulock.minescape"
        and item.get("relation") == ["delegate_permission/common.handle_all_urls"])] + statement
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(statement, indent=2) + "\n", encoding="utf-8")
print(f"Prepared {args.output}. Publish at https://cbulock.github.io/.well-known/assetlinks.json")
