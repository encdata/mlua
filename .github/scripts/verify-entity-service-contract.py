#!/usr/bin/env python3
"""Keep entity filtering and lookup flow in its dedicated component."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "src/main/java/dev/mlua/modules/entity/service/MLuaEntityService.java"
QUERIES = ROOT / "src/main/java/dev/mlua/modules/entity/service/MLuaEntityQueryOperations.java"
service = SERVICE.read_text(encoding="utf-8")
queries = QUERIES.read_text(encoding="utf-8")


def fail(message: str) -> None:
    print(f"Entity service contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


delegates = {
    "GetById": "getById",
    "FindByUUID": "getByUuid",
    "GetByUUID": "getByUuid",
    "GetAll": "getAll",
    "GetFirst": "getFirst",
    "GetInWorld": "getInWorld",
    "GetFirstInWorld": "getFirstInWorld",
    "CountInWorld": "countInWorld",
    "Count": "count",
    "GetNearby": "getNearby",
    "GetNearest": "getNearest",
}

if "private final MLuaEntityQueryOperations queryOperations" not in service:
    fail("EntityService does not own its query component")
for public_name, operation in delegates.items():
    method = re.search(
        rf"@MLuaDescription\([^\n]*\)\s+public\s+[^{{]+\b{public_name}\s*\([^{{]*\)\s*\{{([^{{}}]*)\}}",
        service,
        re.DOTALL,
    )
    if method is None:
        fail(f"annotated compatibility method {public_name} is missing")
    if f"this.queryOperations.{operation}(" not in method.group(1):
        fail(f"compatibility method {public_name} no longer delegates to {operation}")

for operation in (
    "MLuaEntityQuery.compileFilter(", "forEachWithinRadius(", "resolveQueryLevel(",
    "findFirstLoadedEntity(", "allEntities(",
):
    if operation not in queries:
        fail(f"entity query component is missing {operation}")
if "MLuaEntityQuery.compileFilter(" in service or "MLuaEntityQuery.forEachWithinRadius(" in service:
    fail("filter compilation or spatial query logic remains in the EntityService facade")

print("Entity service contract passed (query and lookup endpoints delegate to their component).")
