#!/usr/bin/env python3
"""Require client services that touch live Minecraft state to share owner-thread dispatch."""

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
CAMERA = ROOT / "src/main/java/dev/mlua/modules/camera/service/MLuaCameraService.java"
CLIENT = ROOT / "src/main/java/dev/mlua/modules/client/service/MLuaClientService.java"
PARTICLES = ROOT / "src/main/java/dev/mlua/modules/particles/service/MLuaParticleService.java"
TOAST = ROOT / "src/main/java/dev/mlua/modules/toast/service/MLuaToastService.java"
ANIMATION = ROOT / "src/main/java/dev/mlua/modules/animation/service/MLuaAnimationService.java"
SOUND = ROOT / "src/main/java/dev/mlua/modules/sound/service/MLuaSoundService.java"
SOUND_HANDLE_FACTORY = ROOT / "src/main/java/dev/mlua/modules/sound/service/MLuaSoundHandleFactory.java"
GAME_THREAD_BASE = ROOT / "src/main/java/dev/mlua/core/service/MLuaGameThreadReflectiveService.java"
REFLECTIVE_BASE = ROOT / "mlua-api/src/main/java/dev/mlua/core/service/MLuaReflectiveService.java"


def fail(message: str) -> None:
    raise SystemExit(f"Client service thread contract error: {message}")


camera = CAMERA.read_text(encoding="utf-8")
client = CLIENT.read_text(encoding="utf-8")
particles = PARTICLES.read_text(encoding="utf-8")
toast = TOAST.read_text(encoding="utf-8")
animation = ANIMATION.read_text(encoding="utf-8")
sound = SOUND.read_text(encoding="utf-8")
sound_handle_factory = SOUND_HANDLE_FACTORY.read_text(encoding="utf-8")
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
animation_methods = (
    "Create", "RegisterAnimation", "GetAnimation", "HasAnimation", "RemoveAnimation",
    "ClearAnimations", "GetAnimationIds", "GetAnimationCount", "GetRegisteredAnimations",
    "GetPose", "SetPose", "ClearPose", "ClearPoses", "GetAnimationPriority",
)
animation_live_state_methods = {"GetPose", "SetPose", "ClearPose"}
for method_name in animation_methods:
    method = re.search(rf"public [^\n]+\b{method_name}\([^\n]*\) \{{(.*?)\n    \}}", animation, re.DOTALL)
    if method is None or "requireClientSide(this.name())" not in method.group(1):
        fail(f"Animation:{method_name} must enforce its client-only side at the service boundary")
    if method_name in animation_live_state_methods \
            and not re.search(r"MLuaGameThreadAccess\.callUnchecked\(\s*this\.context\s*,", method.group(1)):
        fail(f"Animation:{method_name} must dispatch live entity access through the owner-thread boundary")
if "extends MLuaGameThreadReflectiveService" in animation:
    fail("Animation catalog and keyframe operations must stay off the client thread")
if "MLuaGameThreadAccess.callUnchecked(this.context, action)" not in sound:
    fail("SoundService live sound-manager access must use the shared client owner dispatcher")
sound_operations = {
    "PlayLocal": "MLuaClientSoundManager.playLocalId(",
    "PlayAtPlayer": "MLuaClientSoundManager.playAtPlayerId(",
    "Play": "MLuaClientSoundManager.play(",
    "Clear": "MLuaClientSoundManager.clearOwned(",
    "GetAll": "MLuaClientSoundManager.activeIds(",
    "GetCount": "MLuaClientSoundManager.activeIds(",
}
for method_name in (*sound_operations, "GetCategories"):
    method = re.search(rf"public [^\n]+\b{method_name}\([^\n]*\) \{{(.*?)\n    \}}", sound, re.DOTALL)
    if method is None or "requireClientSide(this.name())" not in method.group(1):
        fail(f"SoundService:{method_name} must enforce its client-only side at the service boundary")
for method_name, operation in sound_operations.items():
    method = re.search(rf"public [^\n]+\b{method_name}\([^\n]*\) \{{(.*?)\n    \}}", sound, re.DOTALL)
    if method is None or operation not in method.group(1) or "onClientThread(() ->" not in method.group(1):
        fail(f"SoundService:{method_name} must dispatch {operation.split('.')[-1].rstrip('(')} to the client thread")
if "MLuaGameThreadAccess.callUnchecked(context" not in sound_handle_factory:
    fail("SoundHandle live sound state must use the same owner-thread dispatcher")
for operation in ("Stop", "GetIsPlaying", "GetIsDestroyed", "GetLooped", "SetLooped", "GetVolume", "SetVolume", "GetPitch", "SetPitch",
                  "GetPosition", "SetPosition", "GetFollowEntity", "FollowEntity", "ClearFollowEntity",
                  "GetFollowOffset", "SetFollowOffset"):
    if f'"{operation}"' not in sound_handle_factory:
        fail(f"SoundHandle must dispatch its {operation} operation on the client thread")
if "minecraft.execute(show)" in toast or "Runnable show" in toast:
    fail("Toast.Show must finish on the client thread before the Lua service call returns")

print("Client service thread contract passed (live client state is dispatched selectively or through the shared base).")
