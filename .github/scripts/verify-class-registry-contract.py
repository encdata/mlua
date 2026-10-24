#!/usr/bin/env python3
"""Keep class inheritance ownership indexed for targeted metadata updates."""

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
HIERARCHY = ROOT / "src/main/java/dev/mlua/core/classes/MLuaClassHierarchy.java"
INSTANCES = ROOT / "src/main/java/dev/mlua/core/classes/MLuaClassLiveInstances.java"
METADATA = ROOT / "src/main/java/dev/mlua/core/classes/MLuaClassMetadata.java"
MIXINS = ROOT / "src/main/java/dev/mlua/core/classes/MLuaClassMixinApplications.java"
REGISTRY = ROOT / "src/main/java/dev/mlua/core/classes/MLuaClassRegistry.java"
INSTANCE_LOOKUP = ROOT / "src/main/java/dev/mlua/core/classes/MLuaClassInstanceLookup.java"


def fail(message: str) -> None:
    raise SystemExit(f"Class registry contract error: {message}")


hierarchy = HIERARCHY.read_text(encoding="utf-8")
instances = INSTANCES.read_text(encoding="utf-8")
metadata = METADATA.read_text(encoding="utf-8")
mixins = MIXINS.read_text(encoding="utf-8")
registry = REGISTRY.read_text(encoding="utf-8")
instance_lookup = INSTANCE_LOOKUP.read_text(encoding="utf-8")
for owner_field in ("Map<String, String> parents", "Map<String, Set<String>> children"):
    if owner_field not in hierarchy:
        fail(f"MLuaClassHierarchy must own {owner_field}")
for method in ("boolean hasChildren(String className)", "List<String> descendantsOf(String className)"):
    if method not in hierarchy:
        fail(f"MLuaClassHierarchy is missing {method}")
if "Map<LuaTable, LuaTable> classesByInstance" not in instances \
        or "Map<String, Set<LuaTable>> instancesByClass" not in instances:
    fail("MLuaClassLiveInstances must own weak instance identity and its per-class index")
if "Collections.newSetFromMap(new WeakHashMap<>())" not in instances:
    fail("the per-class instance index must not retain unattached Lua instances")
if "private final MLuaClassMetadata metadata" not in registry \
        or "new MLuaClassMetadata(this, this.classes, this.hierarchy" not in registry:
    fail("MLuaClassRegistry must delegate inherited docs and property metadata to MLuaClassMetadata")
if "private final Map<LuaTable, InstanceDescription> instanceDescriptions" not in metadata \
        or "void attachInstanceDocumentation(" not in metadata:
    fail("MLuaClassMetadata must own weak instance description state and attachment documentation")
if "private final Map<String, List<String>> appliedMixins" not in mixins \
        or "private final Map<String, List<String>> effectiveMixins" not in mixins \
        or "this.mixinApplications.directlyApplied(className)" not in metadata \
        or "this.invalidateEffectiveMixins(className)" not in mixins \
        or "this.hierarchy.descendantsOf(className)" not in mixins:
    fail("mixin application and resolved inheritance state must have a dedicated owner")
if "appliedMixins" in registry or "appliedMixins" in metadata:
    fail("class registry and metadata must query applied mixins through their dedicated owner")
if "private final Object registryLock" not in metadata \
        or "new MLuaClassMetadata(this, this.classes" not in registry:
    fail("metadata lookups must share the registry lock used for class and property mutations")
if "private final MLuaClassInstanceLookup instanceLookup" not in registry \
        or "this.instanceLookup.compose(meta, classTable);" not in registry:
    fail("MLuaClassRegistry must delegate Lua instance metatable composition to its dedicated owner")
if "void compose(LuaTable meta, LuaTable classTable)" not in instance_lookup \
        or "__mlua_instance_fallback" not in instance_lookup \
        or "__mlua_instance_newindex_fallback" not in instance_lookup \
        or "findPropertyDescriptor" not in instance_lookup \
        or "MLuaClassThreadDispatcher.bind" not in instance_lookup:
    fail("MLuaClassInstanceLookup must own property, method, and fallback lookup semantics")
if "new VarArgFunction()" in registry.split("private void composeInstanceLookup", 1)[1].split(
        "private void refreshParentLookup", 1)[0]:
    fail("MLuaClassRegistry must not retain inline instance metatable dispatch")
for method in ("refreshInheritedMethodDescriptionsFrom", "findPropertyDescriptor",
               "hasPropertyDefinition", "collectMethodDescriptions", "collectPropertyDescriptions"):
    if re.search(r"^\s*private\s+(?:synchronized\s+)?[^\n]+\b" + re.escape(method)
                 + r"\s*\(", registry, re.M):
        fail(f"MLuaClassRegistry still owns metadata operation {method}")
if "this.liveInstances.ofClassAndDescendants(" not in metadata:
    fail("class field validation and metadata refresh must query only instances of the affected class subtree")
if "instanceClasses.entrySet()" in registry:
    fail("class-scoped instance refreshes must not rescan every live instance")
dependents = registry.split("private boolean hasClassDependents(String name)", 1)[1].split(
    "private void removePendingClassIfUnused", 1)[0]
if "this.liveInstances.hasInstances(name)" not in dependents or "instanceClasses.values()" in dependents:
    fail("class removal checks must inspect only instances indexed to the candidate class")
if "this.refreshInheritedMethodDescriptions(descendant);" not in metadata \
        or "this.hierarchy.descendantsOf(changedClass)" not in metadata:
    fail("class metadata refresh must traverse only the changed class and indexed descendants")
if "affectedClasses.addAll(this.hierarchy.descendantsOf(mixinOwner))" not in mixins \
        or "this.mixinApplications.refreshClassesUsingMixin(mixinName)" not in registry:
    fail("mixin changes must refresh only affected classes and their descendants")

if "this.host.refreshAllInheritedMethodDescriptions();" not in mixins.split(
        "private void removeDefinition", 1)[1].split("private void validateApplication", 1)[0]:
    fail("global mixin removal must refresh all affected class metadata")

print("Class registry contract passed (indexed hierarchy, live instances, and scoped metadata refresh).")
