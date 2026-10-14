package dev.mlua.modules.javabridge;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.modules.javabridge.Service.MLuaJavaService;
import java.util.List;

public final class JavaModule implements MLuaModule {
    @Override
    public String id() {
        return "java";
    }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder java = ServiceEntry.builder("Java", MLuaJavaService::new)
            .description("Raw Java package and class bridge for loading classes, calling methods, and creating interface proxies.");
        MLuaJavaService.serviceMethodDescriptions().forEach(java::function);
        return List.of(java.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("Java", "MLuaJavaService",
            "Raw Java package and class bridge for loading classes, calling methods, and creating interface proxies.",
            "services/java.html"));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:LoadClass(className)", "Loads a JVM class by fully qualified name.",
            "className: Java class name", "JavaClass or JavaPackage", "shared:Shared",
            "local ArrayList = Minecraft:GetService(\"Java\"):LoadClass(\"java.util.ArrayList\")"));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:Package(packageName)", "Returns a lazy proxy for navigating a Java package.",
            "packageName: package path", "JavaPackage", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:New(classNameOrClass, ...)", "Constructs an object using a compatible public Java constructor.",
            "classNameOrClass: class name or JavaClass; remaining arguments match a public constructor", "JavaObject", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:Proxy(interface1, ..., implementationTable)", "Creates a Java interface implementation backed by Lua functions.",
            "One or more interfaces followed by a table of matching Lua functions. The same conversion also happens when a Lua table is passed to a Java method expecting an interface. Java arguments are exposed as live Java proxies, and Lua return values are converted using the Java method's declared return type.", "JavaObject", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:IsInstance(value, classNameOrClass)", "Checks whether a Java object matches a class or interface.",
            "value: JavaObject; classNameOrClass: class name or JavaClass", "boolean", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:Cast(value, classNameOrClass)", "Checks a Java object's type and returns its live proxy.",
            "value: JavaObject; classNameOrClass: class name or JavaClass", "JavaObject", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:TypeOf(value)", "Returns the runtime Java class name for a proxied object or class. Returns nil for nil.",
            "value: JavaObject or JavaClass", "string or nil", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:GetMethods(classOrObject)", "Lists Java method names, signatures, return types, and static status.",
            "classOrObject: JavaClass or JavaObject", "table", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:GetFields(classOrObject)", "Lists Java fields, types, and whether they are static or final.",
            "classOrObject: JavaClass or JavaObject", "table", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:GetField(classOrObject, fieldName)", "Reads an exact Java field from a live object or class proxy. Use this when a field name conflicts with an exposed method.",
            "classOrObject: JavaObject or JavaClass; fieldName: exact Java field name", "Java value or nil", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:SetField(classOrObject, fieldName, value)", "Writes a non-final exact Java field on a live object or class proxy and returns the value read back from Java.",
            "classOrObject: JavaObject or JavaClass; fieldName: exact Java field name; value: value convertible to the field type", "Java value", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:Inspect(classOrObject)", "Returns structured metadata for a Java class.",
            "classOrObject: JavaClass or JavaObject", "table", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:ViewClass(classOrObject)", "Returns a Lua-oriented textual view of a Java class.",
            "classOrObject: JavaClass or JavaObject", "string", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:GetEnumConstants(enumClass)", "Returns the constants of a Java enum class.",
            "enumClass: JavaClass or fully qualified class name", "table", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:ToTable(value)", "Copies a Java array, collection, or map into a Lua table. It raises an error for null entries, unsupported key types, or keys that collide after conversion.",
            "value: Java array, collection, or map proxy", "table", "shared:Shared",
            "local snapshot = Minecraft:GetService(\"Java\"):ToTable(javaList)"));
        docs.register(MLuaDocBuilder.Registration.method("Java", "Java:FromTable(table, targetClass, elementClassOrKeyClass, valueClass)", "Converts a Lua table into a Java array, collection, or map and returns a live Java proxy. Optionally provide the element class for collections, or key and value classes for maps.",
            "table: Lua table; targetClass: Java class or class name; elementClassOrKeyClass: optional Java class; valueClass: optional Java class for map values", "JavaObject", "shared:Shared",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local list = Java:FromTable({\"knife\", \"key\"}, \"java.util.ArrayList\", \"java.lang.String\")\n" +
            "local counts = Java:FromTable({knife = 2}, \"java.util.HashMap\", \"java.lang.String\", \"java.lang.Integer\")"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Load a class", "Load a Java type by its fully qualified name. The returned class proxy is reusable.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local ArrayList = Java:LoadClass(\"java.util.ArrayList\")"));
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
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Implement a Java interface in Lua", "Java:Proxy creates a live implementation for Java APIs that accept an interface. Implement each required method by its Java name; arguments arrive as live Java values and results are converted to the declared Java return type.",
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
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Choose a live view or a Lua copy", "Keep the Java proxy to retain live Java access, including null collection entries. ToTable makes a detached Lua table and errors if an entry or key cannot be represented without loss.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local ArrayList = Java:LoadClass(\"java.util.ArrayList\")\n" +
            "local list = ArrayList:new()\n" +
            "list:add(\"medkit\") -- changes the Java list\n" +
            "local snapshot = Java:ToTable(list) -- detached Lua table"));
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
        docs.register(MLuaDocBuilder.Registration.guideStep("Java", "Create a Java array from a Lua array", "Use the Java component type followed by [] when FromTable should create a JVM array instead of a collection.",
            "local Java = Minecraft:GetService(\"Java\")\n" +
            "local names = Java:FromTable({\"knife\", \"key\"}, \"java.lang.String[]\")\n" +
            "names[2] = \"flare\" -- updates the Java array slot"));
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
