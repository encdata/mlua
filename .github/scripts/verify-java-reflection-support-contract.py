#!/usr/bin/env python3
"""Keep Java member diagnostics and primitive boxing in one bridge support component."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "src/main/java/dev/mlua/modules/javabridge/service/MLuaJavaService.java"
SUPPORT = ROOT / "src/main/java/dev/mlua/modules/javabridge/service/MLuaJavaReflectionSupport.java"
service = SERVICE.read_text(encoding="utf-8")
support = SUPPORT.read_text(encoding="utf-8")


def fail(message: str) -> None:
    print(f"Java reflection support contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


for method in ("rawClass", "describeArgs", "describeMethods", "describeConstructors",
               "rootMessage", "methodSignature", "boxType"):
    if f"MLuaJavaReflectionSupport.{method}(" not in service:
        fail(f"Java service compatibility seam no longer delegates {method}")
    if f"static " not in support or f" {method}(" not in support:
        fail(f"reflection support component is missing {method}")
if "if (type == int.class) return Integer.class;" not in support or "return cause.getClass().getName()" not in support:
    fail("reflection support component is missing boxing or root exception formatting")
if "if (type == int.class)" in service or "instanceof InvocationTargetException" in service:
    fail("primitive boxing or reflection diagnostic formatting remains embedded in the Java service")

print("Java reflection support contract passed (diagnostics and primitive boxing have a shared owner).")
