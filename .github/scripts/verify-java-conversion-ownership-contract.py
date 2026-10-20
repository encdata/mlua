from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "src/main/java/dev/mlua/modules/javabridge/service/MLuaJavaService.java"
CONVERTER = ROOT / "src/main/java/dev/mlua/modules/javabridge/service/MLuaJavaInputConverter.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


require(CONVERTER.is_file(), "Java input conversion must have a dedicated owner")
service = SERVICE.read_text(encoding="utf-8")
converter = CONVERTER.read_text(encoding="utf-8")
require("private final MLuaJavaInputConverter inputConverter" in service,
        "Java service must own one input converter")

for signature in (
    "Object unwrapJavaValue(LuaValue value)",
    "Object convertLuaValue(LuaValue value, Type targetType)",
    "Object[] convertArguments(Object[] rawArgs, Executable executable)",
    "Object convertValue(Object rawValue, Class<?> targetType)",
):
    require(signature in service, f"Java service compatibility entry point changed: {signature}")

for operation in ("unwrapJavaValue", "convertLuaValue", "convertArguments", "convertValue"):
    require(f"this.inputConverter.{operation}(" in service,
            f"Java service must delegate {operation} to the input converter")

for implementation in ("luaTableValue", "luaArrayValues", "convertVarArgs", "convertTypedValue", "convertArrayValue"):
    require(implementation in converter, f"Input converter is missing {implementation}")
    require(f" {implementation}(" not in service,
            f"Java service must not retain an implementation of {implementation}")

print("Java input conversion has one dedicated owner with stable service entry points.")
