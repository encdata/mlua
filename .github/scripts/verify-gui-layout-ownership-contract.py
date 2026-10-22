#!/usr/bin/env python3
"""Keep native GUI layout state and lifecycle in their owning component."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
service_path = ROOT / "src/main/java/dev/mlua/modules/coregui/service/MLuaGuiService.java"
controller_path = ROOT / "src/main/java/dev/mlua/modules/coregui/service/MLuaGuiNativeLayoutController.java"
targets_path = ROOT / "src/main/java/dev/mlua/modules/coregui/service/MLuaGuiScreenTargetRegistry.java"
screens_path = ROOT / "src/main/java/dev/mlua/modules/coregui/service/MLuaGuiScreenRegistry.java"
controls_path = ROOT / "src/main/java/dev/mlua/modules/coregui/service/MLuaGuiNativeControlRegistry.java"
screen_path = ROOT / "src/main/java/dev/mlua/modules/coregui/service/MLuaScriptScreen.java"
builder_path = ROOT / "src/main/java/dev/mlua/modules/coregui/service/MLuaScriptScreenBuilder.java"
callbacks_path = ROOT / "src/main/java/dev/mlua/modules/coregui/service/MLuaScriptScreenCallbacks.java"
input_path = ROOT / "src/main/java/dev/mlua/modules/coregui/service/MLuaScriptScreenInput.java"
service = service_path.read_text(encoding="utf-8")
controller = controller_path.read_text(encoding="utf-8")
targets = targets_path.read_text(encoding="utf-8")
screens = screens_path.read_text(encoding="utf-8")
controls = controls_path.read_text(encoding="utf-8")
screen = screen_path.read_text(encoding="utf-8")
builder = builder_path.read_text(encoding="utf-8")
callbacks = callbacks_path.read_text(encoding="utf-8")
screen_input = input_path.read_text(encoding="utf-8")


def fail(message: str) -> None:
    print(f"GUI ownership contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


if "final MLuaGuiNativeLayoutController nativeLayoutController" not in service:
    fail("GUI service does not own a per-runtime native layout controller")
if "private final MLuaGuiScreenTargetRegistry screenTargetRegistry" not in service:
    fail("GUI service does not own a per-runtime screen target registry")
if "private final MLuaGuiScreenRegistry screenRegistry" not in service:
    fail("GUI service does not own its script-created ScreenGui and CoreGui registry")

for method, call in (
    ("addScreenGui", "screenRegistry.add"),
    ("removeScreenGui", "screenRegistry.remove"),
    ("getScreenGuis", "screenRegistry.all"),
    ("getCoreGui", "screenRegistry.core"),
    ("createCoreGui", "screenRegistry.createCore"),
    ("isCoreGuiEnabled", "screenRegistry.isCoreEnabled"),
    ("setCoreGuiEnabled", "screenRegistry.setCoreEnabled"),
    ("removeScreen", "screenRegistry.remove"),
):
    if call not in service:
        fail(f"GUI service compatibility entry point {method} no longer delegates to its screen registry")
if "state.screens" in service or "state.disabledTypes" in service:
    fail("ScreenGui ownership or CoreGui enablement remains implemented in the GUI facade")
for operation in ("createCore", "setCoreEnabled", "removeOwnedChildren", "ScreenGui"):
    if operation not in screens:
        fail(f"GUI screen registry is missing {operation}")
if "private final MLuaGuiNativeControlRegistry nativeControlRegistry" not in service:
    fail("GUI service does not delegate native widget discovery to its control registry")
if "nativeControlRegistry.find(selector, firstOnly, screen)" not in service or \
        "MLuaGuiNativeControlRegistry.requireSelector(selector)" not in service:
    fail("native control compatibility methods must delegate to their registry")
if "MLuaNativeWidgetAccess.isNativeControl" in service:
    fail("native control discovery remains implemented in the GUI service facade")
for operation in ("MLuaNativeWidgetAccess.isNativeControl", "MLuaNativeControl.Backing",
                  "findNativeLayout", "requireSelector", "matchesWidget"):
    if operation not in controls:
        fail(f"native control registry is missing {operation}")

for method, call in (
    ("findNativeLayout", "nativeLayoutController.findNativeLayout"),
    ("syncLayoutObjects", "nativeLayoutController.syncLayoutObjects"),
    ("addNativeLayout", "nativeLayoutController.addNativeLayout"),
    ("removeNativeLayout", "nativeLayoutController.removeNativeLayout"),
    ("removeNativeLayoutEntry", "nativeLayoutController.removeNativeLayoutEntry"),
    ("recomposeLayouts", "nativeLayoutController.recomposeLayouts"),
    ("recomposeTrackedScreens", "nativeLayoutController.recomposeTrackedScreens"),
):
    if call not in service:
        fail(f"service compatibility entry point {method} no longer delegates to its controller")

for operation in (
    "restoreNativeLayouts",
    "applyLayoutToWidgets",
    "recomposeLayouts",
    "removeNativeLayoutEntry",
    "layout.capture(screen, widget, properties)",
    "MLuaNativeWidgetAccess.applyWidgetProperties",
):
    if operation not in controller:
        fail(f"native layout controller is missing {operation}")

for operation in ("register(String alias, String className)", "unregister(String alias)", "matches(String alias, Object screen)"):
    if operation not in targets:
        fail(f"screen target registry is missing {operation}")

if re.search(r"(?m)^\s*private static void applyLayoutToWidgets\s*\(", service):
    fail("native widget layout application remains implemented inside the GUI service")

if "MLuaScriptScreenBuilder.create(this)" not in screen:
    fail("script screen must delegate its Lua initialization builder to MLuaScriptScreenBuilder")
if "classCallbacks.invokeLifecycle(" not in screen or "classCallbacks.clear()" not in screen:
    fail("script screen must delegate Lua lifecycle callback state to its callback dispatcher")
if "MLuaScriptScreenInput input" not in screen or "this.input." not in screen:
    fail("script screen must delegate Minecraft input conversion to MLuaScriptScreenInput")
for versioned in ("KeyEvent", "CharacterEvent", "MouseButtonEvent", "mouseScrolled(", "filesDrop("):
    if versioned not in screen_input:
        fail(f"script screen input adapter is missing {versioned}")
if "this.callbacks.invokeInputMethod(" not in screen_input:
    fail("Minecraft input conversion is not routed through the shared Lua callback dispatcher")
for operation in ("failedInputMethods", "failedLifecycleMethods", "invokeLifecycle", "invokeInputResult", "isPauseScreen"):
    if operation not in callbacks:
        fail(f"script screen callback dispatcher is missing {operation}")
for method in ("SetOnClose", "SetOnTick", "SetOnRender", "AddText", "AddButton", "AddEditBox", "AddRenderableWidget"):
    if f'builder.set("{method}"' not in builder:
        fail(f"script screen builder is missing {method}")
    if f'rawset("{method}"' not in screen:
        fail(f"class based script screens do not expose builder method {method}")
for operation in ("interface Host", "requireCallback(callback, \"Screen:SetOnClose\")",
                  "host.addButton(label, position, size, callback)",
                  "host.addEditBox(position, size, initialText, changed)"):
    if operation not in builder:
        fail(f"script screen builder is missing its host contract: {operation}")

print("GUI layout, script screen builder, and screen target contracts have dedicated owners.")
