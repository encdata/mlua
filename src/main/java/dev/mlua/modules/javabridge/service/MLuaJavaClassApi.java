package dev.mlua.modules.javabridge.service;

import dev.mlua.common.mixin.MLuaClassViewer;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/** Installs Java class loading, package traversal, and constructor methods. */
final class MLuaJavaClassApi {
    private MLuaJavaClassApi() { }

    static void install(LuaTable table, MLuaJavaService service) {
        LuaValue loadClass = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String rootName = args.checkjstring(2);
                Class<?> direct = service.tryResolveClass(rootName);
                if (direct == null) {
                    throw new LuaError("[MLua] Could not resolve Java class '" + rootName + "'");
                }
                return service.wrapJavaValue(direct);
            }
        };
        table.set("LoadClass", loadClass);
        table.set("Import", loadClass);
        table.set("import", loadClass);
        table.set("bindClass", loadClass);

        LuaValue packageFn = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String rootName = args.checkjstring(2);
                return service.packageProxy(rootName);
            }
        };
        table.set("Package", packageFn);
        table.set("package", packageFn);

        LuaValue newFn = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int classIndex = 2;
                Class<?> clazz = service.resolveClassArg(args.arg(classIndex));
                LuaValue constructor = service.wrapJavaValue(clazz).get("new");
                if (constructor.isnil()) {
                    throw new LuaError("[MLua] No public constructor found for " + clazz.getName());
                }
                return constructor.invoke(args.subargs(classIndex + 1));
            }
        };
        table.set("New", newFn);
        table.set("newInstance", newFn);
        table.set("NewExact", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int classIndex = 2;
                Class<?> clazz = service.resolveClassArg(args.arg(classIndex));
                String descriptor = args.checkjstring(classIndex + 1);
                Constructor<?> constructor = null;
                for (Constructor<?> candidate : service.reflection.constructors(clazz)) {
                    if (MLuaClassViewer.constructorDescriptor(candidate).equals(descriptor)) {
                        constructor = candidate;
                        break;
                    }
                }
                if (constructor == null) {
                    throw new LuaError("[MLua] No public constructor " + clazz.getName() + descriptor
                        + ". Inspect the class with Java:Inspect to see available constructor descriptors.");
                }
                Object[] rawArgs = service.extractInvocationArgs(args, classIndex + 2);
                service.requireExactCallArgumentCount("NewExact", clazz.getName() + descriptor,
                    constructor, rawArgs.length);
                try {
                    return service.wrapJavaValue(constructor.newInstance(service.convertArguments(rawArgs, constructor)));
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                         | InvocationTargetException failure) {
                    throw new LuaError("[MLua] Failed to construct " + clazz.getName() + descriptor
                        + ": " + service.rootMessage(failure));
                }
            }
        });
    }
}
