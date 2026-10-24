#!/usr/bin/env python3
"""Keep Java-backed Lua callback exception handling in one shared owner."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


errors = read("mlua-api/src/main/java/dev/mlua/api/MLuaLuaErrors.java")
if "public static VarArgFunction operation(String operation" not in errors:
    raise SystemExit("Lua callback wrappers must be owned by MLuaLuaErrors.operation")
if "catch (LuaError failure)" not in errors or "catch (Exception failure)" not in errors:
    raise SystemExit("Shared Lua callback wrapper must preserve Lua errors and map Java exceptions")

delegates = {
    "src/main/java/dev/mlua/modules/bossbar/service/MLuaBossBarService.java": 'MLuaLuaErrors.operation("BossBar service call", body)',
    "src/main/java/dev/mlua/modules/render/service/MLuaRenderAnimationBinding.java": 'MLuaLuaErrors.operation("Render animation operation", body)',
    "src/main/java/dev/mlua/modules/world/service/MLuaWorldService.java": 'MLuaLuaErrors.varargsOperation("World service call", args ->',
    "mlua-versions/common/26.1/dev/mlua/modules/world/service/MLuaWorldService.java": 'MLuaLuaErrors.varargsOperation("World service call", args ->',
}
for relative, expected in delegates.items():
    if expected not in read(relative):
        raise SystemExit(f"{relative} must delegate callback wrapping to MLuaLuaErrors")

particle_service = read("src/main/java/dev/mlua/modules/particles/service/MLuaParticleService.java")
if 'MLuaLuaErrors.operation("ParticleEmitter:" + operation, body,' not in particle_service:
    raise SystemExit("ParticleEmitter operations must use the shared wrapper and specialized error mapping")

sound_handles = read("src/main/java/dev/mlua/modules/sound/service/MLuaSoundHandleFactory.java")
for token in (
    'MLuaLuaErrors.operation("SoundHandle:" + operation, args ->',
    'if (!usesClientState(operation)) return body.apply(args);',
    'MLuaGameThreadAccess.callUnchecked(context, () -> body.apply(args))',
    'failure -> {',
    'MLuaLogger.LOGGER.error("SoundHandle:{} failed", operation, failure);',
    'new LuaError("[MLua] SoundHandle:" + operation + " failed: "',
):
    if token not in sound_handles:
        raise SystemExit(
            "SoundHandle callbacks must share error mapping and dispatch only live client state access"
        )

object_ops = read("src/main/java/dev/mlua/modules/render/service/MLuaRenderObjectOperations.java")
if 'MLuaLuaErrors.operation("Render service call"' not in object_ops:
    raise SystemExit("Render object callbacks must use the shared Lua callback boundary")
if "MLuaRenderLuaSupport" in object_ops:
    raise SystemExit("Render callback support must not be duplicated in a render-local wrapper")
world_26 = read("mlua-versions/common/26.1/dev/mlua/modules/world/service/MLuaWorldService.java")
if 'MLuaLuaErrors.operation("World service call", action)' not in world_26:
    raise SystemExit("26.1 World single-value callbacks must use the shared Lua callback boundary")

print("Lua callback exception boundary contract passed (BossBar, render, particles, and sound).")
