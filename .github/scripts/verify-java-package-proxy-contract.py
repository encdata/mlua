#!/usr/bin/env python3
"""Keep lazy Java package proxy behavior outside the Java service coordinator."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "src/main/java/dev/mlua/modules/javabridge/service/MLuaJavaService.java"
FACTORY = ROOT / "src/main/java/dev/mlua/modules/javabridge/service/MLuaJavaPackageProxyFactory.java"
service = SERVICE.read_text(encoding="utf-8")
factory = FACTORY.read_text(encoding="utf-8")


def fail(message: str) -> None:
    print(f"Java package proxy contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


if "private final MLuaJavaPackageProxyFactory packageProxies" not in service:
    fail("Java service does not own the package proxy factory")
if "return this.packageProxies.create(path);" not in service:
    fail("Java package API no longer delegates proxy creation")
for token in ("service.tryResolveClass(nextPath)", "service.wrapJavaValue(resolved)", "create(nextPath)",
              "Unresolved Java package path", "JavaPackage("):
    if token not in factory:
        fail(f"lazy package proxy factory is missing {token}")
if "metatable.set(\"__index\"" in service or "metatable.set(\"__tostring\"" in service:
    fail("Java package proxy behavior remains in the service coordinator")

print("Java package proxy contract passed (lazy package traversal has a dedicated owner).")
