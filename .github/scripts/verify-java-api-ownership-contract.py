#!/usr/bin/env python3
"""Keep Java service table assembly separate from its public API groups."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVICE_DIR = ROOT / "src/main/java/dev/mlua/modules/javabridge/service"
FACADE = SERVICE_DIR / "MLuaJavaApi.java"
GROUPS = {
    "MLuaJavaClassApi.java": ("LoadClass", "NewExact"),
    "MLuaJavaProxyApi.java": ("Proxy", "GetProxyLimits"),
    "MLuaJavaReflectionApi.java": ("GetMethods", "Invoke", "GetField", "SetField"),
    "MLuaJavaConversionApi.java": ("ToTable", "FromTable"),
}
REFLECTION_CATALOG = SERVICE_DIR / "MLuaJavaReflectionCatalog.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


facade = FACADE.read_text(encoding="utf-8")
for filename, methods in GROUPS.items():
    owner = SERVICE_DIR / filename
    require(owner.is_file(), f"Java API group has no owner: {filename}")
    source = owner.read_text(encoding="utf-8")
    require("static void install(LuaTable table, MLuaJavaService service)" in source,
            f"{filename} must install into the shared service table")
    class_name = filename.removesuffix(".java")
    require(f"{class_name}.install(table, service)" in facade,
            f"Java API facade no longer installs {class_name}")
    for method in methods:
        require(f'table.set("{method}"' in source,
                f"{filename} no longer owns its {method} entry point")

require("MLuaServiceBase.normalizeServiceCalls(table, service.name())" in facade,
        "Java API facade must preserve shared colon/dot service call normalization")
require("MLuaServiceMetadata.attachMethodDescriptions" in facade,
        "Java API facade must preserve shared method documentation metadata")
catalog = REFLECTION_CATALOG.read_text(encoding="utf-8")
reflection_api = (SERVICE_DIR / "MLuaJavaReflectionApi.java").read_text(encoding="utf-8")
require("exactMethodLookups" in catalog and "Method exactMethod(" in catalog,
        "Java exact reflection calls must use the bounded member lookup cache")
require("service.reflection.exactMethod(clazz, methodName, descriptor, staticCall)" in reflection_api,
        "Java:Invoke must delegate exact method lookup to the cached reflection catalog")
require("service.reflection.methods(clazz).stream()" not in reflection_api,
        "Java:Invoke must not rebuild candidate streams on every reflective call")

print("Java service API is assembled from stable, cohesive API groups.")
