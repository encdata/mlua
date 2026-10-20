#!/usr/bin/env python3
"""Select the shared build/player-test matrix for a workflow run."""

import json
import os
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
TARGETS_FILE = ROOT / ".github/scripts/ci-targets.json"


def main() -> int:
    try:
        targets = json.loads(TARGETS_FILE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        print(f"Could not load CI target manifest: {error}", file=sys.stderr)
        return 1

    selected = os.environ.get("MLUA_TEST_TARGET", "all").strip() or "all"
    if selected == "all":
        matrix = targets
    else:
        matrix = [target for target in targets if target.get("testTarget") == selected]
        if not matrix:
            choices = ", ".join(target["testTarget"] for target in targets)
            print(f"Unknown CI target {selected!r}. Choose 'all' or one of: {choices}", file=sys.stderr)
            return 2

    if not matrix:
        print("CI target manifest contains no targets", file=sys.stderr)
        return 1

    output = os.environ.get("GITHUB_OUTPUT")
    value = {"include": matrix}
    if not output:
        print(json.dumps(value, separators=(",", ":")))
        return 0
    with Path(output).open("a", encoding="utf-8") as result:
        result.write("matrix=" + json.dumps(value, separators=(",", ":")) + "\n")
    print(f"Selected {len(matrix)} CI target(s): " + ", ".join(row["testTarget"] for row in matrix))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
