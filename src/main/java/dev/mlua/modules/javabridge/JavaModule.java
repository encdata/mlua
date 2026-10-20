package dev.mlua.modules.javabridge;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.common.enums.Side;
import dev.mlua.modules.javabridge.service.MLuaJavaService;
import java.util.List;

public final class JavaModule implements MLuaModule {
    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        MLuaJavaService.clearRuntimeProxies(runtimeSession == null ? runtime : runtimeSession, side);
    }

    @Override
    public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession,
                                Side side, String ownerModId) {
        MLuaJavaService.clearOwnedProxies(runtimeSession == null ? runtime : runtimeSession, side, ownerModId);
    }

    @Override
    public String id() {
        return "java";
    }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder java = ServiceEntry.builder("Java", MLuaJavaService::new)
            .description("Raw Java package and class bridge for loading classes, calling methods, and creating interface proxies. Non-primitive Java values returned during a render callback are limited to that callback and its render thread; do not retain them.");
        MLuaJavaService.serviceMethodDescriptions().forEach(java::function);
        return List.of(java.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("Java", "MLuaJavaService",
            "Raw Java package and class bridge for loading classes, calling methods, and creating interface proxies. During a render callback, non-primitive Java values returned through this bridge are limited to that callback and its render thread; do not retain them. Numeric values become Lua numbers only when their value is represented exactly; oversized integers and high-precision decimals remain JavaObject proxies so they can be passed back into Java without rounding.",
            "services/java.html"));
        docs.register(MLuaDocBuilder.Registration.type("JavaNull", null,
            "Sentinel exposed as Java.Null for storing Java null inside Lua tables and converting it back in typed Java values."));
        docs.register(MLuaDocBuilder.Registration.field("Java", "Null", "JavaNull",
            "Use this value where a Lua table needs to preserve a Java null entry. It converts to Java null in typed nested values; direct nil arguments still pass Java null as usual."));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:LoadClass(className)", "Loads a JVM class by name. Accepts Java source array names such as int[][] and JVM array names such as [I or [Ljava.lang.String; returned by Java reflection metadata.",
            "className: Java class name or array type name", "JavaClass or JavaPackage", "shared:Shared",
            "local ArrayList = Minecraft:GetService(\"Java\"):LoadClass(\"java.util.ArrayList\")"));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:Package(packageName)", "Returns a lazy proxy for navigating a Java package.",
            "packageName: package path", "JavaPackage", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:New(classNameOrClass, ...)", "Constructs an object using a compatible public Java constructor. Each nested Java collection conversion shares a limit of " + MLuaJavaService.MAX_CONVERSION_ENTRIES + " entries.",
            "classNameOrClass: class name or JavaClass; remaining arguments match a public constructor", "JavaObject", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:NewExact(classNameOrClass, jvmDescriptor, ...)", "Constructs an object through one exact public constructor. Use the constructor descriptor from Java:Inspect when nil or another value makes automatic overload selection ambiguous. Arguments still use MLua's normal declared-type conversions; varargs constructors accept a packed array or expanded trailing arguments.",
            "classNameOrClass: class name or JavaClass; jvmDescriptor: constructor descriptor from Inspect(...).Constructors; remaining arguments match that constructor", "JavaObject", "shared:Shared",
            "local Java = Minecraft:GetService(\"Java\")\nlocal ArrayList = Java:LoadClass(\"java.util.ArrayList\")\nlocal constructors = Java:Inspect(ArrayList).Constructors\nlocal list = Java:NewExact(ArrayList, constructors[1])"));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:Proxy(interface1, ..., implementationTable)", "Creates a Java interface implementation backed by Lua functions. Required abstract methods are checked at creation; default and static methods do not need Lua functions. For overload-specific behavior, use a table key made from the Java method name followed by its exact JvmDescriptor from Java:GetMethods(), for example `accept(Ljava/lang/String;)V`; a plain method-name function handles every overload as a fallback. Calls run synchronously on the Java caller thread under the owning script's execution lock and Lua context; MLua does not move them to the Minecraft thread. Runtime or addon cleanup invalidates the proxy and releases its Lua callbacks.",
            "One or more interfaces followed by a table containing a function for every required abstract method. The same conversion also happens when a Lua table is passed to a Java method expecting an interface. Java arguments are exposed as live Java proxies, and Lua return values are converted using the Java method's declared return type. If Java calls from a worker thread, the Lua callback runs on that worker thread; schedule game mutations onto Minecraft's main thread. Interface method calls after runtime or addon cleanup throw IllegalStateException. Identity-based Object methods (equals, hashCode, and toString) remain available and do not call Lua.", "JavaObject", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:GetProxyLimits()", "Returns the active Lua-backed Java proxy count, the count created by this addon runtime, and the configured limits. Proxies are tracked weakly and counted only while Java still retains them.",
            "No arguments", "table with Active, ActiveOwned, MaxTotal, and MaxPerOwner", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:IsInstance(value, classNameOrClass)", "Checks whether a Java object matches a class or interface.",
            "value: JavaObject; classNameOrClass: class name or JavaClass", "boolean", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:Cast(value, classNameOrClass)", "Checks a Java object's type and returns its live proxy.",
            "value: JavaObject; classNameOrClass: class name or JavaClass", "JavaObject", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:TypeOf(value)", "Returns the runtime Java class name of a Java value, object proxy, or class proxy. Returns nil for nil.",
            "value: Java value, JavaObject, or JavaClass", "string or nil", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:GetMethods(classOrObject)", "Lists Java methods with declaring class, abstract, default, static and varargs status, structured parameter and return types, a readable signature, and a JVM descriptor. Parameter names fall back to arg1, arg2, etc. when Java debug names are unavailable.",
            "classOrObject: JavaClass or JavaObject", "Array of records with Name, DeclaringClass, Static, Abstract, Default, VarArgs, ParameterCount, Parameters, ReturnType, GenericReturnType, Descriptor, Signature, and JvmDescriptor. Descriptor and Signature are readable name strings. JvmDescriptor is the exact JVM descriptor used by Java:Invoke.", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:Invoke(classOrObject, methodName, jvmDescriptor, ...)", "Invokes an exact Java method by JVM descriptor when automatic overload resolution is ambiguous, including overloads that accept nil as different reference types. A class proxy or class name selects a static method; an object proxy selects an instance method. Varargs methods accept a packed array or expanded trailing arguments. Java long results outside Lua's exact integer range remain exact JavaObject proxies rather than being rounded. Each nested Java collection conversion shares a limit of " + MLuaJavaService.MAX_CONVERSION_ENTRIES + " entries.",
            "classOrObject: JavaClass, JavaObject, or class name; methodName: exact Java method name; jvmDescriptor: descriptor from Java:GetMethods(). Remaining arguments are converted to the declared Java parameter types.", "Java return value or nil for void", "shared:Shared",
            "local Java = Minecraft:GetService(\"Java\")\nlocal Fixture = Java:LoadClass(\"your.package.Fixture\")\nlocal instance = Fixture:new()\nlocal methods = Java:GetMethods(instance)\n-- Use the JvmDescriptor shown for the overload you want.\nlocal result = Java:Invoke(instance, \"pick\", \"(Ljava/lang/Double;)Ljava/lang/String;\", 5)"));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:GetFields(classOrObject)", "Lists Java fields, raw and generic types, declaring classes, and whether they are static or final.",
            "classOrObject: JavaClass or JavaObject", "Array of records with Name, DeclaringClass, Static, Final, Type, and GenericType", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:GetField(classOrObject, fieldName)", "Reads an exact Java field from a live object or class proxy. Use this when a field name conflicts with an exposed method.",
            "classOrObject: JavaObject or JavaClass; fieldName: exact Java field name", "Java value or nil", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:SetField(classOrObject, fieldName, value)", "Writes a non-final exact Java field on a live object or class proxy and returns the value read back from Java.",
            "classOrObject: JavaObject or JavaClass; fieldName: exact Java field name; value: value convertible to the field type", "Java value", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:Inspect(classOrObject)", "Returns structured class metadata, including public constructor JVM descriptors in Constructors. Pass one of those descriptors to Java:NewExact when automatic constructor selection is ambiguous.",
            "classOrObject: JavaClass or JavaObject", "table with Name, SimpleName, Modifiers, Superclass, Interfaces, Fields, Methods, and Constructors", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:ViewClass(classOrObject)", "Returns a Lua-oriented textual view of a Java class.",
            "classOrObject: JavaClass or JavaObject", "string", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:GetEnumConstants(enumClass)", "Returns the constants of a Java enum class.",
            "enumClass: JavaClass or fully qualified class name", "table", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:ToTable(value)", "Copies a Java array, Iterable, or map into a Lua table. Iterable values become one-based Lua arrays in iteration order; lazy or unbounded iterables are stopped at the conversion limit. Java null values become Java.Null so array and map entries remain present. A complete copy is limited to " + MLuaJavaService.MAX_CONVERSION_ENTRIES + " entries across nested values. Null map keys, unsupported key types, colliding keys, cycles, or nesting deeper than 64 levels raise a Lua error.",
            "value: Java array, Iterable, or map proxy", "table", "shared:Shared",
            "local snapshot = Minecraft:GetService(\"Java\"):ToTable(javaList)"));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:FromTable(table, targetClass, elementClassOrKeyClass, valueClass)", "Converts a Lua table into a Java array, collection, or map and returns a live Java proxy. Java.Null becomes Java null inside nested values. Optionally provide the element class for collections, or key and value classes for maps. A complete conversion is limited to " + MLuaJavaService.MAX_CONVERSION_ENTRIES + " entries across nested tables; cycles and nesting deeper than 64 levels raise a Lua error.",
            "table: Lua table; targetClass: Java class or class name; elementClassOrKeyClass: optional Java class; valueClass: optional Java class for map values", "JavaObject", "shared:Shared",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local list = Java:FromTable({\"knife\", \"key\"}, \"java.util.ArrayList\", \"java.lang.String\")\n" +
            "local counts = Java:FromTable({knife = 2}, \"java.util.HashMap\", \"java.lang.String\", \"java.lang.Integer\")"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Load a class", "Load a Java type by its fully qualified name. The returned class proxy is reusable.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local ArrayList = Java:LoadClass(\"java.util.ArrayList\")"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Pass nil and Optional values", "Lua nil becomes Java null for reference parameters and is rejected for primitive parameters. For a declared Java Optional<T> parameter, nil becomes Optional.empty(); a non-nil value is converted to T and wrapped. Java null and Optional.empty() results both become Lua nil, while a present Optional result becomes its contained Lua value. If nil matches multiple overloads, use Java:Invoke with the exact JVM descriptor from Java:GetMethods().",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local Methods = Java:GetMethods(someObject)\n" +
            "-- nil is ambiguous if multiple reference overloads accept it.\n" +
            "local result = Java:Invoke(someObject, \"find\", \"(Ljava/lang/String;)Ljava/util/Optional;\", nil)\n" +
            "-- Optional.empty() is Lua nil; a present Optional is its contained value."));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Read a live Java object", "Call a static factory, then read fields and methods from the live object proxy.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local MinecraftClass = Java:LoadClass(\"net.minecraft.client.Minecraft\")\n" +
            "local mc = MinecraftClass:getInstance()\n" +
            "if mc.screen then\n" +
            "    print(mc.screen:getClass():getName())\n" +
            "end"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Read and write Java fields", "Object fields can be read and written directly. GetField and SetField are explicit forms for Java fields whose names collide with methods; final fields remain read-only.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local Fixture = Java:LoadClass(\"your.package.YourJavaType\")\n" +
            "local object = Fixture:new()\n" +
            "local oldValue = object.value\n" +
            "object.value = \"changed\"\n" +
            "local current = Java:GetField(object, \"value\")\n" +
            "Java:SetField(object, \"value\", \"updated\")"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Navigate packages", "Package proxies resolve lazily, so you can chain package and nested class names.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local Entry = Java:LoadClass(\"java\").util.Map.Entry\n" +
            "local HashMap = Java:LoadClass(\"java.util.HashMap\")\n" +
            "local map = HashMap:new()\n" +
            "map:put(\"hello\", \"world\")"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Build a Java list from a Lua array", "Pass a dense Lua array to a Java collection constructor. Indexing is one-based in Lua and writes update the live Java list.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local ArrayList = Java:LoadClass(\"java.util.ArrayList\")\n" +
            "local list = ArrayList:new({\"knife\", \"key\"})\n" +
            "print(list[1]) -- knife\n" +
            "list[2] = \"flare\""));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Implement a Java interface in Lua", "Java:Proxy creates a live implementation for Java APIs that accept an interface. Implement each required method by its Java name; arguments arrive as live Java values and results are converted to the declared Java return type. If the interface overloads a method, an exact table key such as `accept(Ljava/lang/String;)V` selects that overload; a plain method-name entry remains a shared fallback.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local Runnable = Java:LoadClass(\"java.lang.Runnable\")\n" +
            "local task = Java:Proxy(Runnable, {\n" +
            "    run = function()\n" +
            "        print(\"Lua task ran\")\n" +
            "    end\n" +
            "})\n" +
            "-- Pass task to a Java method that accepts Runnable"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Pass a Lua table where Java expects an interface", "For a Java parameter declared as an interface, MLua can build the Lua-backed proxy from the method table automatically. Use Java:Proxy when you want to create and retain the interface object explicitly.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local task = Java:New(\"java.lang.Thread\", {\n" +
            "    run = function()\n" +
            "        print(\"Lua task ran\")\n" +
            "    end\n" +
            "})\n" +
            "task:start()"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Inspect unfamiliar classes", "Use method and field metadata to discover exact Java names before invoking a method.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local ArrayList = Java:LoadClass(\"java.util.ArrayList\")\n" +
            "local list = ArrayList:new()\n" +
            "list:add(\"a\")\n" +
            "print(Java:TypeOf(list))\n" +
            "print(Java:GetMethods(list)[1].Name)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Call addon services with typed arrays", "Java reflective services convert dense Lua arrays to the method's declared List, Iterable, collection, or array parameter type.",
            "local Weapons = Minecraft:GetService(\"MyWeapons\")\n" +
            "Weapons:SetItems({\"knife\", \"key\", \"flare\"})"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Copy nullable Java collections", "ToTable returns a detached Lua table and uses Java.Null for Java null values so list positions and map entries are preserved. Pass that table back through FromTable with the intended Java element or value type to restore Java nulls. Null map keys cannot be represented in Lua.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local ArrayList = Java:LoadClass(\"java.util.ArrayList\")\n" +
            "local list = ArrayList:new()\n" +
            "list:add(\"medkit\")\n" +
            "list:add(nil) -- direct Java calls can pass null\n" +
            "local snapshot = Java:ToTable(list)\n" +
            "assert(snapshot[2] == Java.Null)\n" +
            "local copy = Java:FromTable(snapshot, \"java.util.ArrayList\", \"java.lang.String\")"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Convert a Lua table to a Java collection", "Choose the Java collection type explicitly. Dense arrays become ordered collection values; keyed tables become map entries. Sorted and concurrent map interfaces receive matching Java implementations. The returned proxy edits the Java collection in place.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local list = Java:FromTable({\"knife\", \"key\"}, \"java.util.ArrayList\")\n" +
            "list[2] = \"flare\" -- update the Java list\n" +
            "local values = Java:ToTable(list)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Convert a keyed table to a Java map", "Use a map class when the Lua table has named keys. TreeMap provides sorted keys; ConcurrentHashMap provides concurrent access. For reflective Java service methods, declared generic key and value types guide conversion.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local fear = Java:FromTable({Fear = 10, Panic = 2}, \"java.util.TreeMap\")\n" +
            "print(fear:get(\"Fear\"))\n" +
            "fear:put(\"Fear\", 12) -- updates the Java map"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Create and use a Java array", "Use the Java component type followed by [] when FromTable should create a JVM array instead of a collection. Java arrays keep one-based indexing in Lua, expose their read-only Java length property, and also support Lua's length operator.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local names = Java:FromTable({\"knife\", \"key\"}, \"java.lang.String[]\")\n" +
            "names[2] = \"flare\" -- updates the Java array slot\n" +
            "print(names.length) -- Java array length; #names also works"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Pass an empty Lua table to a typed Java parameter", "An empty Lua table has no keys to show whether it is a list or map. Java method and constructor parameter types resolve that ambiguity; an empty table can be passed to a Map parameter as an empty map.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local HashMap = Java:LoadClass(\"java.util.HashMap\")\n" +
            "local map = HashMap:new({})\n" +
            "print(map:isEmpty())"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Implement a Java interface in Lua", "Create a JVM interface proxy from a Lua table whose functions match the interface methods.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local Runnable = Java:LoadClass(\"java.lang.Runnable\")\n" +
            "local callback = Java:Proxy(Runnable, {\n" +
            "    run = function() print(\"called by Java\") end\n" +
            "})"));
    }
}
