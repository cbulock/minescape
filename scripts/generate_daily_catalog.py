"""Generate the frozen v1 daily catalog. Runtime Kotlin tests independently certify every layout.

Run deliberately, never during a normal build. Do not replace v1 after release.
"""
import json
import random
from pathlib import Path

N, OPENING = 256, 119
NEIGHBORS = [
    [y * 16 + x for y in range(max(0, i // 16 - 1), min(16, i // 16 + 2))
     for x in range(max(0, i % 16 - 1), min(16, i % 16 + 2)) if y * 16 + x != i]
    for i in range(N)
]
MASKS = [sum(1 << j for j in near) for near in NEIGHBORS]
ALL = (1 << N) - 1

def certify(mines):
    mine_mask = sum(1 << i for i in mines)
    clues = [(mask & mine_mask).bit_count() for mask in MASKS]
    revealed = 0
    known = 0
    def reveal(mask):
        nonlocal revealed
        stack = [i for i in range(N) if mask >> i & 1]
        while stack:
            i = stack.pop()
            if revealed >> i & 1:
                continue
            if mine_mask >> i & 1:
                raise AssertionError("Unsound deduction")
            revealed |= 1 << i
            if clues[i] == 0:
                stack.extend(NEIGHBORS[i])
    reveal(1 << OPENING)
    for _ in range(N * 2):
        if revealed.bit_count() == N - 40:
            return True
        hidden = ALL & ~(revealed | known)
        constraints = {}
        for i in range(N):
            if revealed >> i & 1:
                mask = MASKS[i] & hidden
                if mask:
                    constraints[mask] = clues[i] - (MASKS[i] & known).bit_count()
        constraints[hidden] = 40 - known.bit_count()
        progress = False
        while True:
            for mask, count in list(constraints.items()):
                if count == 0:
                    reveal(mask); progress = True; break
                if count == mask.bit_count():
                    known |= mask; progress = True; break
            if progress:
                break
            derived = {}
            items = [(mask, count, mask.bit_count()) for mask, count in constraints.items()]
            for small, sc, sn in items:
                for large, lc, ln in items:
                    if sn < ln <= 16 and small & large == small:
                        diff = large ^ small
                        if diff not in constraints:
                            derived[diff] = lc - sc
            if not derived or len(constraints) + len(derived) > 2048:
                return False
            constraints.update(derived)
        if not progress:
            return False
    return False

if __name__ == "__main__":
    destination = Path(__file__).resolve().parents[1] / "app/src/main/assets/daily-v1.json"
    if destination.exists():
        raise SystemExit("v1 already exists: preserve its published identities")
    rng = random.Random(20261009)
    allowed = [i for i in range(N) if i not in NEIGHBORS[OPENING] + [OPENING]]
    catalog, unique, tried = [], set(), 0
    while len(catalog) < 366:
        mines = tuple(sorted(rng.sample(allowed, 40)))
        tried += 1
        if mines not in unique and certify(mines):
            catalog.append(list(mines)); unique.add(mines)
            if len(catalog) % 25 == 0:
                print(f"Certified {len(catalog)}/366 ({tried} candidates)", flush=True)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(catalog, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"Saved {len(catalog)} layouts, {tried} candidates", flush=True)
