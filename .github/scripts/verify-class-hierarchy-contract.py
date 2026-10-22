#!/usr/bin/env python3
"""Keep class inheritance ownership indexed for targeted metadata updates."""

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
HIERARCHY = ROOT / "src/main/java/dev/mlua/core/classes/MLuaClassHierarchy.java"
REGISTRY = ROOT / "src/main/java/dev/mlua/core/classes/MLuaClassRegistry.java"


def fail(message: str) -> None:
    raise SystemExit(f"Class hierarchy contract error: {message}")


hierarchy = HIERARCHY.read_text(encoding="utf-8")
registry = REGISTRY.read_text(encoding="utf-8")
for owner_field in ("Map<String, String> parents", "Map<String, Set<String>> children"):
    if owner_field not in hierarchy:
        fail(f"MLuaClassHierarchy must own {owner_field}")
for method in ("boolean hasChildren(String className)", "List<String> descendantsOf(String className)"):
    if method not in hierarchy:
        fail(f"MLuaClassHierarchy is missing {method}")
if "Map<String, Set<LuaTable>> instancesByClass" not in registry:
    fail("live instances must be indexed by class without replacing the weak instance ownership map")
if "Collections.newSetFromMap(new WeakHashMap<>())" not in registry:
    fail("the per-class instance index must not retain unattached Lua instances")
if "instancesOfOrDescendants(className)" not in registry:
    fail("class field validation and metadata refresh must query only instances of the affected class subtree")
if "for (Map.Entry<LuaTable, LuaTable> entry : this.instanceClasses.entrySet())" in registry:
    fail("class-scoped instance refreshes must not rescan every live instance")
if "this.refreshInheritedMethodDescriptions(descendant);" not in registry \
        or "this.hierarchy.descendantsOf(changedClass)" not in registry:
    fail("class metadata refresh must traverse only the changed class and indexed descendants")
if "affectedClasses.addAll(this.hierarchy.descendantsOf(mixinOwner))" not in registry:
    fail("mixin changes must refresh only affected classes and their descendants")

full_refresh_calls = re.findall(r"this\.refreshInheritedMethodDescriptions\(\);", registry)
if len(full_refresh_calls) != 1:
    fail("full metadata refresh should remain only for global mixin removal")
if "this.refreshInheritedMethodDescriptions();" not in registry.split("private void removeMixinEntry", 1)[1]:
    fail("global mixin removal must refresh all affected class metadata")

print("Class hierarchy contract passed (indexed descendants and scoped metadata refresh).")
