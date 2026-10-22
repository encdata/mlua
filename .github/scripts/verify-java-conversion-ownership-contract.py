from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "src/main/java/dev/mlua/modules/javabridge/service/MLuaJavaService.java"
CONVERTER = ROOT / "src/main/java/dev/mlua/modules/javabridge/service/MLuaJavaInputConverter.java"
TYPED_CONVERTER = ROOT / "src/main/java/dev/mlua/modules/javabridge/service/MLuaJavaTypedValueConverter.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


require(CONVERTER.is_file(), "Java input conversion must have a dedicated owner")
require(TYPED_CONVERTER.is_file(), "Java target-type conversion must have a dedicated owner")
service = SERVICE.read_text(encoding="utf-8")
converter = CONVERTER.read_text(encoding="utf-8")
typed_converter = TYPED_CONVERTER.read_text(encoding="utf-8")
require("private final MLuaJavaInputConverter inputConverter" in service
        and "private final MLuaJavaTypedValueConverter typedValueConverter" in service,
        "Java service must own separate input and target-type converters")

for signature in (
    "Object unwrapJavaValue(LuaValue value)",
    "Object convertLuaValue(LuaValue value, Type targetType)",
    "Object[] convertArguments(Object[] rawArgs, Executable executable)",
    "Object convertValue(Object rawValue, Class<?> targetType)",
):
    require(signature in service, f"Java service compatibility entry point changed: {signature}")

for operation in ("unwrapJavaValue", "convertLuaValue", "convertArguments"):
    require(f"this.inputConverter.{operation}(" in service,
            f"Java service must delegate {operation} to the input converter")

for operation in ("convertValue", "convertNumber"):
    require(f"this.typedValueConverter.{operation}(" in service,
            f"Java service must delegate {operation} to the typed value converter")

for implementation in ("luaTableValue", "luaArrayValues", "convertVarArgs"):
    require(implementation in converter, f"Input converter is missing {implementation}")
    require(f" {implementation}(" not in service,
            f"Java service must not retain an implementation of {implementation}")

for implementation in ("convertTypedValue", "convertArrayValue"):
    require(implementation in typed_converter, f"Typed value converter is missing {implementation}")
    require(f" {implementation}(" not in service,
            f"Java service must not retain an implementation of {implementation}")

print("Java input and target-type conversion have separate owners with stable service entry points.")
