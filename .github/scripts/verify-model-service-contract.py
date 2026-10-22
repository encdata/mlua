#!/usr/bin/env python3
"""Keep model override mutation and validation in its cohesive owner."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "src/main/java/dev/mlua/modules/model/service/MLuaModelService.java"
OVERRIDES = ROOT / "src/main/java/dev/mlua/modules/model/service/MLuaModelPartOverrides.java"
service = SERVICE.read_text(encoding="utf-8")
overrides = OVERRIDES.read_text(encoding="utf-8")


def fail(message: str) -> None:
    print(f"Model service contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


delegates = {
    "GetOverrideLimits": "getLimits",
    "SetPartCFrame": "setCFrame",
    "SetPartOffset": "setOffset",
    "SetPartScale": "setScale",
    "SetPartVisible": "setVisible",
    "SetPart": "setPart",
    "ClearPart": "clearPart",
    "ClearModel": "clearModel",
    "ClearAll": "clearAll",
    "GetModelState": "getModelState",
    "GetPartNames": "getPartNames",
}

for public_name, operation in delegates.items():
    method = re.search(
        rf"@MLuaDescription\([^\n]*\)\s+public\s+[^{{]+\b{public_name}\s*\([^{{]*\)\s*\{{([^{{}}]*)\}}",
        service,
        re.DOTALL,
    )
    if method is None:
        fail(f"annotated compatibility method {public_name} is missing")
    if f"this.partOverrides.{operation}(" not in method.group(1):
        fail(f"compatibility method {public_name} no longer delegates to {operation}")

if "private final MLuaModelPartOverrides partOverrides" not in service:
    fail("model service does not own the part override component")
if "MLuaClientModelManager.setPartProperties" in service or "PART_PROPERTIES" in service \
        or "validatedVector(" in service or "getState(scope" in service:
    fail("part override validation or mutation remains in the model service facade")
for operation in (
    "setPartProperties(", "validatedVector(", "validateFinite(", "getModelState(",
    "clearOwned(", "getPartNames(",
):
    if operation not in overrides:
        fail(f"model part override component is missing {operation}")

print("Model service contract passed (public override methods delegate to their component).")
