#!/usr/bin/env python3
"""Keep block entity lifecycle callbacks from synchronously reloading their owning chunk."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
block_wrapper_path = ROOT / "src/main/java/dev/mlua/minecraft/binding/MLuaBlockWrapper.java"
block_entity_class_path = ROOT / "src/main/java/dev/mlua/minecraft/binding/MLuaBlockEntityClass.java"
block_entity_path = ROOT / "src/main/java/dev/mlua/runtime/client/MLuaVisualBlockEntity.java"
workflow_path = ROOT / ".github/workflows/compile-java.yml"
block_wrapper = block_wrapper_path.read_text(encoding="utf-8")
block_entity_class = block_entity_class_path.read_text(encoding="utf-8")
block_entity = block_entity_path.read_text(encoding="utf-8")
workflow = workflow_path.read_text(encoding="utf-8")


def fail(message: str) -> None:
    print(f"Block entity lifecycle contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


if "blockTable(Level level, BlockPos pos, BlockState state, MLuaRegistration registration)" not in block_wrapper:
    fail("block wrappers need a constructor path that accepts the already-known state")
if "level.getBlockState(pos), registration" not in block_wrapper:
    fail("ordinary block lookups must still resolve the live state before delegating")
if "MLuaBlockWrapper.blockTable(level, this.worldPosition, this.getBlockState(), registration)" not in block_entity:
    fail("block entity lifecycle must use its cached state rather than querying its level")
if "MLuaBlockEntityClass.register(registration);" not in block_wrapper:
    fail("Block class setup must delegate BlockEntity API ownership to its class registrar")
if "MLuaBlockWrapper.blockTable((Level)" not in block_entity_class:
    fail("BlockEntity:GetBlock must resolve the block through the wrapper-owned table constructor")
if "return blockTable(" in block_entity_class:
    fail("BlockEntity class registrar must not call an unowned blockTable helper")
for method in ("GetPosition", "GetLevel", "GetBlock", "GetContainer", "SetNbt"):
    if f'"BlockEntity", "{method}"' not in block_entity_class:
        fail(f"BlockEntity class registrar is missing {method}")
if "Lifecycle callbacks also run while chunks are being attached or removed" not in block_entity:
    fail("the chunk lifecycle safety constraint must stay documented beside the adapter")

state_overload = block_wrapper.split(
    "public static LuaTable blockTable(Level level, BlockPos pos, BlockState state, MLuaRegistration registration)", 1
)[1].split("/** Returns the typed live block entity", 1)[0]
if "level.getBlockState(" in state_overload:
    fail("known-state block wrapper path must not load or wait on a chunk")
if "verify-block-entity-lifecycle-contract.py" not in workflow:
    fail("the Linux and loader compile workflow must run this lifecycle contract")

print("Block entity lifecycle contract passed (callbacks reuse cached state without reloading chunks).")
