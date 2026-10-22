package dev.mlua.modules.javabridge.service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import java.lang.reflect.Type;
import java.util.Map;

/** Installs Java collection conversion methods. */
final class MLuaJavaConversionApi {
    private MLuaJavaConversionApi() { }

    static void install(LuaTable table, MLuaJavaService service) {
        table.set("ToTable", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue source = args.arg(2);
                Object javaValue = service.unwrapJavaValue(source);
                if (javaValue == null) {
                    throw new LuaError("[MLua] Java:ToTable expects a non-null Java array, Iterable, or map.");
                }
                return service.toLuaTable(javaValue, service.frameScope(source));
            }
        });
        table.set("FromTable", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int valueIndex = 2;
                LuaValue source = args.arg(valueIndex);
                if (!source.istable()) {
                    throw new LuaError("[MLua] Java:FromTable expects a Lua table as its first argument");
                }
                Class<?> targetType = service.resolveClassArg(args.arg(valueIndex + 1));
                if (!targetType.isArray() && !Iterable.class.isAssignableFrom(targetType)
                    && !Map.class.isAssignableFrom(targetType)) {
                    throw new LuaError("[MLua] Java:FromTable target must be an array, Iterable, or Map type: "
                        + targetType.getName());
                }
                Type conversionType = targetType;
                int typeArgumentCount = args.narg() - valueIndex - 1;
                if (Map.class.isAssignableFrom(targetType)) {
                    if (typeArgumentCount > 2) {
                        throw new LuaError("[MLua] Java:FromTable map conversion accepts optional key and value classes");
                    }
                    if (typeArgumentCount > 0) {
                        Type keyType = service.resolveClassArg(args.arg(valueIndex + 2));
                        Type valueType = typeArgumentCount == 2
                            ? service.resolveClassArg(args.arg(valueIndex + 3)) : Object.class;
                        conversionType = new MLuaJavaParameterizedType(targetType, keyType, valueType);
                    }
                } else if (Iterable.class.isAssignableFrom(targetType)) {
                    if (typeArgumentCount > 1) {
                        throw new LuaError("[MLua] Java:FromTable iterable conversion accepts one optional element class");
                    }
                    if (typeArgumentCount == 1) {
                        conversionType = new MLuaJavaParameterizedType(targetType,
                            service.resolveClassArg(args.arg(valueIndex + 2)));
                    }
                } else if (typeArgumentCount != 0) {
                    throw new LuaError("[MLua] Java:FromTable array conversion uses the component type declared by the array class");
                }
                return service.wrapJavaValue(service.convertLuaValue(source, conversionType), conversionType);
            }
        });
    }
}
