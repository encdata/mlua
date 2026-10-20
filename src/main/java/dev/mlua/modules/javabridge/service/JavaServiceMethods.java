package dev.mlua.modules.javabridge.service;

import dev.mlua.api.MLuaLuaCoercions;
import java.util.LinkedHashMap;
import java.util.Map;

/** Central descriptions for the Java service API. */
final class JavaServiceMethods {
    private static final Map<String, String> SERVICE_METHOD_DESCRIPTIONS = createServiceMethodDescriptions();

    private JavaServiceMethods() { }

    static Map<String, String> serviceMethodDescriptions() { return SERVICE_METHOD_DESCRIPTIONS; }

    private static Map<String, String> createServiceMethodDescriptions() {
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("LoadClass", "Loads a JVM class by name, including Java source array names and JVM array names returned by reflection metadata.");
        descriptions.put("Import", "Loads a JVM class by name, including array types; compatibility alias for LoadClass.");
        descriptions.put("import", "Loads a JVM class by name, including array types; compatibility alias for LoadClass.");
        descriptions.put("bindClass", "Loads a JVM class by name, including array types; compatibility alias for LoadClass.");
        descriptions.put("Package", "Returns a lazy proxy for navigating a Java package.");
        descriptions.put("package", "Returns a lazy proxy for navigating a Java package; alias for Package.");
        descriptions.put("New", "Constructs an object using a compatible public Java constructor. Tables across all arguments are limited to " + MLuaJavaService.MAX_CONVERSION_ENTRIES + " entries for the complete call.");
        descriptions.put("newInstance", "Constructs an object using a compatible public Java constructor; alias for New. Tables across all arguments are limited to " + MLuaJavaService.MAX_CONVERSION_ENTRIES + " entries for the complete call.");
        descriptions.put("NewExact", "Constructs an object using the exact public Java constructor descriptor listed by Java:Inspect. Varargs constructors accept either one packed array or expanded trailing arguments. A final nil means a null varargs array; use { Java.Null } to pass one null element. Tables across all arguments are limited to " + MLuaJavaService.MAX_CONVERSION_ENTRIES + " entries for the complete call.");
        descriptions.put("Proxy", "Creates a Java interface implementation backed by Lua functions. Required abstract methods are checked at creation; default and static methods do not need Lua functions. Overloaded methods can use a table key made from the method name followed by its exact JvmDescriptor from GetMethods; a plain method-name function handles all overloads as a fallback. Calls run synchronously on the Java caller thread under the owning script's execution lock and Lua context; MLua does not move them to the Minecraft thread. Runtime or addon cleanup invalidates the proxy and releases its Lua callbacks.");
        descriptions.put("createProxy", "Creates a Java interface implementation backed by Lua functions; alias for Proxy. Required abstract methods are checked at creation; default and static methods do not need Lua functions. Overloaded methods can use a table key made from the method name followed by its exact JvmDescriptor from GetMethods; a plain method-name function handles all overloads as a fallback. Calls run synchronously on the Java caller thread under the owning script's execution lock and Lua context; MLua does not move them to the Minecraft thread. Runtime or addon cleanup invalidates the proxy and releases its Lua callbacks.");
        descriptions.put("GetProxyLimits", "Returns active Lua-backed Java proxy usage and the global and per-addon runtime limits.");
        descriptions.put("IsInstance", "Checks whether a Java object matches a class or interface.");
        descriptions.put("Cast", "Checks a Java object's type and returns its live proxy.");
        descriptions.put("TypeOf", "Returns the runtime Java class name of a Java value, object proxy, or class proxy. Returns nil for nil.");
        descriptions.put("GetMethods", "Lists Java methods with structured parameter and return types, readable signatures, JVM descriptors, declaring classes, and abstract, default, varargs, and static status.");
        descriptions.put("Invoke", "Calls an exact Java method using its JVM descriptor. Use a Java class proxy or class name for static calls, and a Java object proxy for instance calls. Varargs methods accept either one packed array or expanded trailing arguments. A final nil means a null varargs array; use { Java.Null } to pass one null element. Tables across all arguments are limited to " + MLuaJavaService.MAX_CONVERSION_ENTRIES + " entries for the complete call.");
        descriptions.put("GetFields", "Lists Java fields with raw and generic types, declaring classes, and whether they are static or final.");
        descriptions.put("GetField", "Reads a Java instance or static field by its exact Java name, including inherited superclass and interface fields.");
        descriptions.put("SetField", "Writes a non-final Java instance or static field and returns its new value.");
        descriptions.put("Inspect", "Returns structured metadata for a Java class.");
        descriptions.put("ViewClass", "Returns a Lua-oriented textual view of a Java class.");
        descriptions.put("ClassViewer", "Returns a Lua-oriented textual view of a Java class; alias for ViewClass.");
        descriptions.put("GetEnumConstants", "Returns the constants of a Java enum class.");
        descriptions.put("ToTable", "Copies Java arrays, Iterable values, and maps into Lua tables. Iterable values become one-based arrays in iteration order; lazy or unbounded iterables stop at the conversion limit. Java null values become Java.Null so array and map entries retain their positions. A complete conversion is limited to " + MLuaJavaService.MAX_CONVERSION_ENTRIES + " entries across nested values. It raises an error for null map keys, unsupported key types, colliding keys, cycles, or nesting deeper than " + MLuaLuaCoercions.MAX_CONVERSION_DEPTH + " levels.");
        descriptions.put("FromTable", "Converts a Lua table into a Java array, collection, or map and returns a live Java proxy. Java.Null converts to Java null inside nested values. Optionally provide the element class for collections, or key and value classes for maps. A complete conversion is limited to " + MLuaJavaService.MAX_CONVERSION_ENTRIES + " entries across nested tables. Map-key collisions after conversion, incompatible values, cycles, and nesting deeper than " + MLuaLuaCoercions.MAX_CONVERSION_DEPTH + " levels raise a Lua error instead of discarding entries.");
        return java.util.Collections.unmodifiableMap(descriptions);
    }

}
