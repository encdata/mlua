#!/usr/bin/env python3
"""Keep addon keybind definition parsing separate from input state and Lua endpoints."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "src/main/java/dev/mlua/modules/input/service/MLuaInputService.java"
DEFINITIONS = ROOT / "src/main/java/dev/mlua/modules/input/service/MLuaKeybindDefinitions.java"
ACTIONS = ROOT / "src/main/java/dev/mlua/modules/input/service/MLuaInputActions.java"
service = SERVICE.read_text(encoding="utf-8")
definitions = DEFINITIONS.read_text(encoding="utf-8")
actions = ACTIONS.read_text(encoding="utf-8")


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
if "private final MLuaInputActions actions" not in service:
    fail("input service does not own its client key action component")
if "MLuaClientThread.callOnClientThread(action)" not in actions:
    fail("Minecraft key state mutations must run on the actual client thread")
if "MLuaClientThread.callOnClientThread(action)" not in service:
    fail("Minecraft keybinding registration and lookup must run on the actual client thread")
for method in ("RegisterKeybinds", "IsKeyDown", "SetKeybind", "GetKeybind", "GetKeybinds",
               "GetMinecraftKeybind", "GetMinecraftKeybinds"):
    match = re.search(rf"public\s+[^{{]+\b{method}\s*\(", service)
    if match is None:
        fail(f"{method} public endpoint is missing")
    next_method = service.find("\n   @MLuaDescription", match.end())
    body = service[match.start():] if next_method < 0 else service[match.start():next_method]
    if "this.onClientThread(" not in body:
        fail(f"{method} must access Minecraft key mappings on the client thread")
for method, delegate in (
    ("SetKeyDown", "setKeyDown"), ("SetKeyStates", "setKeyStates"),
    ("ReleasePressedKeys", "releasePressedKeys"), ("ClickKey", "clickKey"),
    ("SetMinecraftKeyDown", "setMinecraftKeyDown"), ("ClickMinecraftKey", "clickMinecraftKey"),
    ("SetMouseButtonDown", "setMouseButtonDown"), ("ClickMouseButton", "clickMouseButton"),
):
    match = re.search(rf"@MLuaDescription\([^\n]*\)\s+public\s+[^{{]+\b{method}\s*\([^{{]*\)\s*\{{([^{{}}]*)\}}", service, re.DOTALL)
    if match is None or "requireClientSide(this.name())" not in match.group(1):
        fail(f"{method} lost its compatibility endpoint or client-side guard")
    if f"this.actions.{delegate}(" not in match.group(1):
        fail(f"{method} no longer delegates to its action component")
for method in ("setKeyStates(", "setKeyDown(", "releasePressedKeys(", "clickMinecraftKey("):
    if method not in actions:
        fail(f"input action component is missing {method}")

print("Input service contract passed (keybind definitions have a dedicated parser).")
