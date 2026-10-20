from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SHARED_STATE = ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaScriptCallbackState.java"
SHARED_AI = ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaScriptedEntityAI.java"
ENTITY_SOURCES = [
    ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaScriptedEntity.java",
    ROOT / "mlua-versions/common/26.1/dev/mlua/minecraft/scripted/MLuaScriptedEntity.java",
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


require(SHARED_STATE.is_file(), "Scripted entity callback state must have one shared owner")
require(SHARED_AI.is_file(), "Scripted entity AI state machine must have one shared owner")
shared = SHARED_STATE.read_text(encoding="utf-8")
for contract in (
    "boolean shouldInvoke(String event, LuaValue callback)",
    "void disable(String event)",
    "void clear()",
    "void clearState(String state)",
):
    require(contract in shared, f"Shared callback state is missing {contract}")

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
require("invokeNestedCallback(\"entity\", MLuaScriptedEntity.this.behaviorKey" in versioned,
        "26.1 entity AI adapter must preserve nested callback registry invocation")

print("Scripted entity callback state and AI state machine have shared owners across base and 26.1 implementations.")
