#!/usr/bin/env python3
"""Check that the manual Actions selector and both matrices share one target manifest."""

import json
import os
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / ".github/scripts/ci-targets.json"
SELECTOR = ROOT / ".github/scripts/select-ci-targets.py"
WORKFLOW = ROOT / ".github/workflows/compile-java.yml"
REQUIRED_FIELDS = {
    "testTarget", "target", "loader", "java", "gradle", "loaderVersion",
    "fabricApi", "loom", "forgeVersion", "forgeLoaderRange", "neoForgeVersion",
}


def fail(message: str) -> None:
    print(f"CI target contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


try:
    targets = json.loads(MANIFEST.read_text(encoding="utf-8"))
    workflow = WORKFLOW.read_text(encoding="utf-8")
except (OSError, json.JSONDecodeError) as error:
    fail(str(error))

if not isinstance(targets, list) or not targets:
    fail("target manifest must be a non-empty array")

ids = []
pairs = []
for target in targets:
    if not isinstance(target, dict) or REQUIRED_FIELDS - target.keys():
        fail("every target must define all build and loader properties")
    if not all(isinstance(target[field], str) for field in REQUIRED_FIELDS):
        fail(f"target {target.get('testTarget')} contains a non-string property")
    if not all(target[field] for field in ("testTarget", "target", "loader", "java", "gradle")):
        fail(f"target {target.get('testTarget')} is missing a required value")
    if target["loader"] not in {"fabric", "forge", "neoforge"}:
        fail(f"unsupported loader in target {target['testTarget']}")
    if target["testTarget"] != f"{target['target']}-{target['loader']}":
        fail(f"target selector key does not match target and loader: {target['testTarget']}")
    ids.append(target["testTarget"])
    pairs.append((target["target"], target["loader"]))

if len(ids) != len(set(ids)) or len(pairs) != len(set(pairs)):
    fail("target selector keys and loader/version pairs must be unique")

options_block = re.search(r"(?ms)^        options:\n(?P<options>(?:          - [^\n]+\n)+)", workflow)
if options_block is None:
    fail("workflow_dispatch must expose a finite target selection")
options = [line.strip()[2:].strip() for line in options_block.group("options").splitlines()]
if options != ["all", *ids]:
    fail("workflow_dispatch options must be 'all' followed by every manifest target in order")

matrix_expression = "matrix: ${{ fromJSON(needs.select-targets.outputs.matrix) }}"
if workflow.count(matrix_expression) != 2:
    fail("both the Linux build and Windows player jobs must use the selected shared matrix")
if "needs: select-targets" not in workflow or "needs: [select-targets, build]" not in workflow:
    fail("build and player jobs must depend on target selection")
if "Boot packaged client and server, then simulate player gameplay" not in workflow \
        or "Boot Minecraft client with the packaged MLua JAR" in workflow \
        or "Boot dedicated server with the packaged MLua JAR" in workflow:
    fail("the Windows smoke must boot both packaged sides inside the player simulation without duplicate launches")

def run_selector(selection: str) -> tuple[int, dict | None]:
    environment = dict(os.environ)
    environment.pop("GITHUB_OUTPUT", None)
    environment["MLUA_TEST_TARGET"] = selection
    result = subprocess.run(
        [sys.executable, str(SELECTOR)], cwd=ROOT, env=environment,
        capture_output=True, text=True, check=False,
    )
    if result.returncode != 0:
        return result.returncode, None
    try:
        return result.returncode, json.loads(result.stdout)
    except json.JSONDecodeError:
        fail(f"selector emitted invalid JSON for {selection!r}")


return_code, all_matrix = run_selector("all")
if return_code != 0 or all_matrix != {"include": targets}:
    fail("'all' selection did not emit the complete target manifest")

for target in targets:
    return_code, one_matrix = run_selector(target["testTarget"])
    if return_code != 0 or one_matrix != {"include": [target]}:
        fail(f"manual selection failed to isolate {target['testTarget']}")

return_code, _ = run_selector("unknown-target")
if return_code != 2:
    fail("unknown selections must fail before matrix jobs are scheduled")

print(f"CI target contract passed ({len(targets)} selectable loader/version targets).")
