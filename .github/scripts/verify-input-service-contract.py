#!/usr/bin/env python3
"""Keep addon keybind definition parsing separate from input state and Lua endpoints."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "src/main/java/dev/mlua/modules/input/service/MLuaInputService.java"
DEFINITIONS = ROOT / "src/main/java/dev/mlua/modules/input/service/MLuaKeybindDefinitions.java"
service = SERVICE.read_text(encoding="utf-8")
definitions = DEFINITIONS.read_text(encoding="utf-8")


def fail(message: str) -> None:
    print(f"Input service contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


for endpoint, delegate in (
    ("RegisterKeybind", "MLuaKeybindDefinitions.parse(name, props, \"RegisterKeybind\")"),
    ("RegisterKeybinds", "MLuaKeybindDefinitions.parse(name, value.checktable(), \"RegisterKeybinds\")"),
):
    if f"public LuaTable {endpoint}(" not in service:
        fail(f"public compatibility endpoint {endpoint} is missing")
    if delegate not in service:
        fail(f"{endpoint} no longer delegates definition parsing")
if "private static KeybindDefinition" in service or "private static String optionalString" in service:
    fail("keybind definition parsing remains embedded in the service")
for token in ("aliasedValue(", "optionalString(", "validateKey(", "defaultKey", "category", "KeybindDefinition"):
    if token not in definitions:
        fail(f"keybind definition parser is missing {token}")
if "KeyMapping mapping = MLuaKeybindController.minecraftKeybind(name);" not in service:
    fail("Minecraft keybind lookup must share the controller resolver")

print("Input service contract passed (keybind definitions have a dedicated parser).")
