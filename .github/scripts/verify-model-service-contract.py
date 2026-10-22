#!/usr/bin/env python3
"""Keep model override mutation and validation in its cohesive owner."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "src/main/java/dev/mlua/modules/model/service/MLuaModelService.java"
OVERRIDES = ROOT / "src/main/java/dev/mlua/modules/model/service/MLuaModelPartOverrides.java"
ADAPTERS = ROOT / "src/main/java/dev/mlua/modules/model/service/MLuaModelAdapterOperations.java"
service = SERVICE.read_text(encoding="utf-8")
overrides = OVERRIDES.read_text(encoding="utf-8")
adapters = ADAPTERS.read_text(encoding="utf-8")


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
adapter_delegates = {
    "ResolvePartName": "resolvePartName",
    "RegisterAdapter": "registerAdapter",
    "RegisterAdapterBone": "registerAdapterBone",
    "RegisterAdapterResolver": "registerAdapterResolver",
    "RegisterCustomAdapter": "registerCustomAdapter",
    "UnregisterCustomAdapter": "unregisterCustomAdapter",
    "UnregisterAdapterResolver": "unregisterAdapterResolver",
    "UnregisterAdapter": "unregisterAdapter",
    "UnregisterAdapterBone": "unregisterAdapterBone",
    "RegisterRendererAdapter": "registerRendererAdapter",
    "UnregisterRendererAdapter": "unregisterRendererAdapter",
    "GetRendererAdapter": "getRendererAdapter",
    "GetAdapter": "getAdapter",
    "GetCustomAdapter": "getCustomAdapter",
    "GetAppliedAdapter": "getAppliedAdapter",
    "GetAdapterLimits": "getAdapterLimits",
}
if "private final MLuaModelAdapterOperations adapterOperations" not in service:
    fail("model service does not own the adapter operations component")
for public_name, operation in adapter_delegates.items():
    method = re.search(
        rf"@MLuaDescription\([^\n]*\)\s+public\s+[^{{]+\b{public_name}\s*\([^{{]*\)\s*\{{([^{{}}]*)\}}",
        service,
        re.DOTALL,
    )
    if method is None:
        fail(f"annotated adapter compatibility method {public_name} is missing")
    if f"this.adapterOperations.{operation}(" not in method.group(1):
        fail(f"compatibility method {public_name} no longer delegates to {operation}")
if "MLuaClientModelManager.setPartProperties" in service or "PART_PROPERTIES" in service \
        or "validatedVector(" in service or "getState(scope" in service \
        or "MLuaModelAdapterRegistry.register(" in service:
    fail("model adapter or part override behavior remains in the model service facade")
for operation in (
    "setPartProperties(", "validatedVector(", "validateFinite(", "getModelState(",
    "clearOwned(", "getPartNames(",
):
    if operation not in overrides:
        fail(f"model part override component is missing {operation}")
for operation in (
    "MLuaModelAdapterRegistry.register(", "registerCustomAdapter(", "getAdapterLimits(",
    "adapterOwner()", "appliedAdapter(",
):
    if operation not in adapters:
        fail(f"model adapter operations component is missing {operation}")

print("Model service contract passed (adapter and override APIs delegate to their components).")
