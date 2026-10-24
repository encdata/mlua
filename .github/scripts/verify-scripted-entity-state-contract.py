from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SHARED_STATE = ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaScriptCallbackState.java"
SHARED_AI = ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaScriptedEntityAI.java"
SHARED_DATA = ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaScriptedEntityPersistentData.java"
ENTITY_SOURCES = [
    ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaScriptedEntity.java",
    ROOT / "mlua-versions/common/26.1/dev/mlua/minecraft/scripted/MLuaScriptedEntity.java",
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def versioned_source(source: str, target: str) -> str:
    output = []
    active = True
    for line in source.splitlines():
        marker = line.strip()
        if marker.startswith("//--version "):
            active = target in {version.strip() for version in marker[len("//--version "):].split(",")}
        elif marker == "//--endversion":
            active = True
        elif active:
            output.append(line)
    return "\n".join(output)


require(SHARED_STATE.is_file(), "Scripted entity callback state must have one shared owner")
require(SHARED_AI.is_file(), "Scripted entity AI state machine must have one shared owner")
require(SHARED_DATA.is_file(), "Scripted entity Lua/NBT conversion must have one shared owner")
shared = SHARED_STATE.read_text(encoding="utf-8")
data = SHARED_DATA.read_text(encoding="utf-8")
for contract in (
    "boolean shouldInvoke(String event, LuaValue callback)",
    "boolean refreshDefinition(Object identity)",
    "void disable(String event)",
    "void clear()",
    "void clearState(String state)",
):
    require(contract in shared, f"Shared callback state is missing {contract}")

for contract in (
    "LuaTable toLua()",
    "void replaceFromLua(LuaTable table)",
    "void load(CompoundTag data)",
    "CompoundTag value()",
    "MAX_DEPTH = 64",
    "MAX_ENTRIES = 16_384",
    "cannot save cyclic Lua tables",
    "arrays must use dense indexes starting at one",
    "//--version 26.1",
    "return tag.keySet()",
    "return tag.getAllKeys()",
):
    require(contract in data, f"Shared scripted entity data owner is missing {contract}")
for version, expected, excluded in (
    ("1.20.1", ("tag.getAllKeys()", "tag.getAsByte()", "tag.getAsDouble()", "tag.getAsString()"),
     ("tag.keySet()", "tag.byteValue()", "tag.doubleValue()", "tag.value()")),
    ("1.21.1", ("tag.getAllKeys()", "tag.getAsByte()", "tag.getAsDouble()", "tag.getAsString()"),
     ("tag.keySet()", "tag.byteValue()", "tag.doubleValue()", "tag.value()")),
    ("26.1", ("tag.keySet()", "tag.byteValue()", "tag.doubleValue()", "tag.value()"),
     ("tag.getAllKeys()", "tag.getAsByte()", "tag.getAsDouble()", "tag.getAsString()")),
):
    selected = versioned_source(data, version)
    for method in expected:
        require(method in selected, f"{version} persistent data adapter is missing {method}")
    for method in excluded:
        require(method not in selected, f"{version} persistent data adapter retained incompatible {method}")

for source in ENTITY_SOURCES:
    text = source.read_text(encoding="utf-8")
    require(
        "private final MLuaScriptCallbackState callbackState = new MLuaScriptCallbackState();" in text,
        f"{source.relative_to(ROOT)} must delegate callback state to the shared owner",
    )
    require("callbackState.shouldInvoke(event, callback)" in text,
            f"{source.relative_to(ROOT)} must use shared callback replacement handling")
    require("callbackState.disable(event)" in text,
            f"{source.relative_to(ROOT)} must use shared callback failure handling")
    require("this.callbackState.clear();" in text,
            f"{source.relative_to(ROOT)} must release callback state when an entity is removed")
    require("activeEventCallbacks" not in text and "disabledEventCallbacks" not in text,
            f"{source.relative_to(ROOT)} must not own duplicate callback state collections")

    require("private final MLuaScriptedEntityAI scriptedAI = new MLuaScriptedEntityAI();" in text,
            f"{source.relative_to(ROOT)} must delegate AI state to the shared controller")
    require("this.scriptedAI.tick(" in text and "this.scriptedAI.setState(" in text,
            f"{source.relative_to(ROOT)} must delegate AI ticking and explicit transitions")
    require("private final MLuaScriptedEntityPersistentData entityData = new MLuaScriptedEntityPersistentData();" in text,
            f"{source.relative_to(ROOT)} must delegate persistent Lua data conversion to the shared owner")
    require("this.entityData.toLua()" in text and "this.entityData.replaceFromLua(data)" in text,
            f"{source.relative_to(ROOT)} must use the shared Lua/NBT conversion owner")
    require("this.entityData.value()" in text and "this.entityData.load(" in text,
            f"{source.relative_to(ROOT)} must use the shared persistent data storage for save/load")
    for duplicate in ("compoundToLua(", "luaToCompound(", "PersistentDataBudget", "MAX_PERSISTENT_DATA_DEPTH"):
        require(duplicate not in text,
                f"{source.relative_to(ROOT)} still owns persistent data conversion: {duplicate}")
    for local_state_machine_method in (
        "transitionAIState(", "applyAIStateTransition(", "invokeAIStateUpdate(", "aiStateContext(",
    ):
        require(local_state_machine_method not in text,
                f"{source.relative_to(ROOT)} still owns AI state-machine logic: {local_state_machine_method}")

ai = SHARED_AI.read_text(encoding="utf-8")
for contract in (
    "public void tick(LuaValue definition, CallbackHost callbacks)",
    "public void setState(LuaTable config, String state, LuaValue definition, CallbackHost callbacks)",
    "Varargs invokeDefinition(LuaValue callback, LuaValue... arguments)",
    "Varargs invokeState(LuaValue definition, LuaValue callback, LuaValue... arguments)",
):
    require(contract in ai, f"Shared scripted entity AI is missing {contract}")

base = ENTITY_SOURCES[0].read_text(encoding="utf-8")
versioned = ENTITY_SOURCES[1].read_text(encoding="utf-8")
require("invokeBehavior(behaviors, callback, args)" in base,
        "Base entity AI adapter must invoke against its captured behavior snapshot")
require("invokeBehavior(behaviors, callback, args)" in versioned,
        "26.1 entity AI adapter must invoke against its captured behavior snapshot")
for source, text in ((ENTITY_SOURCES[0], base), (ENTITY_SOURCES[1], versioned)):
    require("snapshotBehaviors(\"entity" in text,
            f"{source.relative_to(ROOT)} must take one immutable behavior snapshot for each entity tick")
    require("private void refreshBehaviorDefinition(MLuaBehaviorRegistry.Snapshot behaviors)" in text,
            f"{source.relative_to(ROOT)} must reset per-entity callback state when a definition is replaced")
    require("callbackState.refreshDefinition(behaviors.identity())" in text,
            f"{source.relative_to(ROOT)} must delegate definition identity tracking to shared callback state")
    require("this.scriptedAI.clearState();" in text,
            f"{source.relative_to(ROOT)} must restart AI state when a live behavior definition is replaced")
require("MLuaBehaviorRegistry.getBehavior(\"entity\", this.behaviorKey" not in versioned,
        "26.1 scripted entity must not perform repeated per-callback registry lookups")

print("Scripted entity AI, callback lifecycle, and Lua/NBT persistent data have shared owners across base and 26.1 implementations.")
