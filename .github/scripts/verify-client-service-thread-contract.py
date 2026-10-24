#!/usr/bin/env python3
"""Require client services that touch live Minecraft state to share owner-thread dispatch."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CAMERA = ROOT / "src/main/java/dev/mlua/modules/camera/service/MLuaCameraService.java"
CLIENT = ROOT / "src/main/java/dev/mlua/modules/client/service/MLuaClientService.java"
PARTICLES = ROOT / "src/main/java/dev/mlua/modules/particles/service/MLuaParticleService.java"
TOAST = ROOT / "src/main/java/dev/mlua/modules/toast/service/MLuaToastService.java"
GAME_THREAD_BASE = ROOT / "src/main/java/dev/mlua/core/service/MLuaGameThreadReflectiveService.java"
REFLECTIVE_BASE = ROOT / "mlua-api/src/main/java/dev/mlua/core/service/MLuaReflectiveService.java"


def fail(message: str) -> None:
    raise SystemExit(f"Client service thread contract error: {message}")


camera = CAMERA.read_text(encoding="utf-8")
client = CLIENT.read_text(encoding="utf-8")
particles = PARTICLES.read_text(encoding="utf-8")
toast = TOAST.read_text(encoding="utf-8")
game_thread_base = GAME_THREAD_BASE.read_text(encoding="utf-8")
reflective_base = REFLECTIVE_BASE.read_text(encoding="utf-8")
for service_name, source in (("Camera", camera), ("Client", client),
                             ("Particle", particles), ("Toast", toast)):
    if "extends MLuaGameThreadReflectiveService" not in source:
        fail(f"{service_name} service must access live Minecraft client state on its owner thread")
if "MLuaGameThreadAccess.call(context, invocation::invoke)" not in game_thread_base:
    fail("game-thread reflective services must use the shared client/server owner dispatcher")
if "this.invokeServiceMethod(" not in reflective_base:
    fail("reflective service methods must pass through the overridable owner-thread boundary")
for service_name, source in (("Camera", camera), ("Client", client),
                             ("Particle", particles), ("Toast", toast)):
    if "extends MLuaReflectiveService" in source:
        fail(f"{service_name} service bypasses the shared owner-thread boundary")
if "minecraft.execute(show)" in toast or "Runnable show" in toast:
    fail("Toast.Show must finish on the client thread before the Lua service call returns")

print("Client service thread contract passed (Camera, Client, Particle, and Toast share the game-thread boundary).")
