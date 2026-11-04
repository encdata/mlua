package dev.mlua.modules.javabridge.service;

import dev.mlua.api.MLuaLuaCoercions;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.common.enums.Side;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaJavaService extends MLuaServiceBase {
    public static final int MAX_LUA_PROXIES = MLuaJavaProxyRegistry.MAX_TOTAL;
    public static final int MAX_LUA_PROXIES_PER_OWNER = MLuaJavaProxyRegistry.MAX_PER_OWNER;
    public static final int MAX_CONVERSION_ENTRIES = MLuaLuaCoercions.MAX_CONVERSION_ENTRIES;
    static final LuaValue JAVA_NULL = MLuaLuaCoercions.javaNullValue();
    static final MLuaJavaProxyRegistry LUA_PROXY_REGISTRY = new MLuaJavaProxyRegistry();
    private final MLuaJavaClassResolver classResolver;
    private final MLuaJavaPackageProxyFactory packageProxies;
    final MLuaJavaReflectionCatalog reflection = new MLuaJavaReflectionCatalog();
    final MLuaJavaMemberResolver members = new MLuaJavaMemberResolver(reflection);
    private final MLuaJavaOverloadResolver overloads = new MLuaJavaOverloadResolver(this);
    private final MLuaJavaCoercionScorer coercionScorer = new MLuaJavaCoercionScorer(this);
    private final MLuaJavaTypedValueConverter typedValueConverter = new MLuaJavaTypedValueConverter(this);
    private final MLuaJavaInputConverter inputConverter = new MLuaJavaInputConverter(this, this.typedValueConverter);
    private final MLuaJavaToLuaConverter toLuaConverter = new MLuaJavaToLuaConverter(this);
    private final MLuaJavaObjectProxyFactory objectProxies = new MLuaJavaObjectProxyFactory(this);
    private final MLuaJavaOutputAdapter outputAdapter = new MLuaJavaOutputAdapter(this);

    public MLuaJavaService(MLuaService.Context context) {
        super(context);
        this.classResolver = new MLuaJavaClassResolver(() -> new ClassLoader[]{
            Thread.currentThread().getContextClassLoader(),
            context.getClass().getClassLoader(),
            this.getClass().getClassLoader(),
            ClassLoader.getSystemClassLoader()
        });
        this.packageProxies = new MLuaJavaPackageProxyFactory(this);
    }

    public static Map<String, String> serviceMethodDescriptions() {
        return JavaServiceMethods.serviceMethodDescriptions();
    }

    @Override
    public String name() {
        return "Java";
    }

    @Override
    public LuaTable toLua() {
        return MLuaJavaApi.build(this);
    }

    LuaValue packageProxy(String path) {
        return this.packageProxies.create(path);
    }

    Class<?> resolveClassArg(LuaValue value) {
        if (value.type() == LuaValue.TSTRING) {
            Class<?> resolved = tryResolveClass(value.tojstring());
            if (resolved == null) {
                throw new LuaError("[MLua] Could not resolve Java class '" + value.tojstring() + "'");
            }
            return resolved;
        }

        if (value.istable()) {
            LuaValue proxiedClass = javaProxyField(value, "__javaClass");
            if (proxiedClass.isuserdata(Class.class)) {
                return (Class<?>) proxiedClass.touserdata(Class.class);
            }
        }

        Object obj = value.touserdata();
        if (obj instanceof Class<?> clazz) {
            return clazz;
        }

        throw new LuaError("[MLua] Expected a Java class or class name");
    }

    Class<?> resolveTargetClass(LuaValue value) {
        Object raw = unwrapJavaValue(value);
        if (raw == null) {
            throw new LuaError("[MLua] Expected a Java class or object, got nil");
        }
        return raw instanceof Class<?> clazz ? clazz : raw.getClass();
    }

    MLuaContext activeProxyContext() {
        MLuaContext active = MLuaLuaBindings.activeContext(this.context.mlua().registration());
        if (active == null) {
            throw new LuaError("[MLua] Java:Proxy must be created while an MLua script context is active.");
        }
        return active;
    }

    ClassLoader serviceContextClassLoader() {
        return this.context.getClass().getClassLoader();
    }

    Object createLuaInterfaceProxy(Class<?>[] interfaces, LuaTable implementation, MLuaContext ownerContext) {
        return MLuaJavaInterfaceProxyFactory.create(this, LUA_PROXY_REGISTRY, interfaces, implementation, ownerContext);
    }

    LuaTable proxyLimits() {
        return LUA_PROXY_REGISTRY.limits(this.activeProxyContext());
    }

    public static void clearRuntimeProxies(Object runtimeSession, Side side) {
        LUA_PROXY_REGISTRY.clearRuntime(runtimeSession, side);
    }

    public static void clearOwnedProxies(Object runtimeSession, Side side, String ownerModId) {
        LUA_PROXY_REGISTRY.clearOwner(runtimeSession, side, ownerModId);
    }

    Object unwrapJavaValue(LuaValue value) {
        return this.inputConverter.unwrapJavaValue(value);
    }

    Object unwrapJavaValue(LuaValue value, IdentityHashMap<LuaTable, Boolean> activeTables, int depth) {
        return this.inputConverter.unwrapJavaValue(value, activeTables, depth);
    }

    Object convertLuaValue(LuaValue value, Type targetType) {
        return this.inputConverter.convertLuaValue(value, targetType);
    }

    Object convertLuaValue(LuaValue value, Type targetType, MLuaJavaConversionBudget budget) {
        return this.inputConverter.convertLuaValue(value, targetType, budget);
    }

    Object convertLuaValue(LuaValue value, Type targetType, IdentityHashMap<LuaTable, Boolean> activeTables, int depth) {
        return this.inputConverter.convertLuaValue(value, targetType, activeTables, depth);
    }

    void validateLuaConversionBudget(LuaValue value, MLuaJavaConversionBudget budget) {
        this.inputConverter.validateLuaConversionBudget(value, budget);
    }

    Class<?> rawClass(Type type) {
        return MLuaJavaReflectionSupport.rawClass(type);
    }

    Type typeArgument(Type type, int index) {
        return MLuaJavaOutputAdapter.typeArgument(type, index);
    }

    static void requireConversionDepth(int depth, String direction) {
        if (depth >= MLuaLuaCoercions.MAX_CONVERSION_DEPTH) {
            throw new LuaError("[MLua] " + direction + " table conversion exceeds "
                + MLuaLuaCoercions.MAX_CONVERSION_DEPTH + " levels of nesting");
        }
    }

    static void requireConversionEntries(long count, String direction) {
        if (count > MAX_CONVERSION_ENTRIES) {
            throw new LuaError("[MLua] " + direction + " table conversion exceeds "
                + MAX_CONVERSION_ENTRIES + " entries");
        }
    }

    static List<Object> copyBoundedIterable(Iterable<?> iterable, String direction) {
        return copyBoundedIterable(iterable, direction, new MLuaJavaConversionBudget());
    }

    static List<Object> copyBoundedIterable(Iterable<?> iterable, String direction,
                                                    MLuaJavaConversionBudget budget) {
        List<Object> values = new ArrayList<>();
        for (Object value : iterable) {
            budget.add(1, direction);
            values.add(value);
        }
        return values;
    }

    LuaValue wrapJavaValue(Object value) {
        return this.outputAdapter.wrap(value);
    }

    boolean isJavaProxy(LuaValue value) {
        return this.objectProxies.isProxy(value);
    }

    boolean isJavaClassProxy(LuaValue value) {
        return isJavaProxy(value) && !javaProxyField(value, "__javaClass").isnil()
            && javaProxyField(value, "__javaObject").isnil();
    }

    LuaValue javaProxyField(LuaValue value, String name) {
        return this.objectProxies.proxyField(value, name);
    }

    Object unwrapJavaMapKey(LuaValue key) {
        return this.objectProxies.unwrapMapKey(key);
    }

    LuaValue wrapJavaValue(Object value, Type declaredType) {
        return this.outputAdapter.wrap(value, declaredType);
    }

    LuaValue wrapJavaValue(Object value, Type declaredType,
                                   dev.mlua.runtime.client.MLuaRenderFrameScope.View frameScope) {
        return this.outputAdapter.wrap(value, declaredType, frameScope);
    }

    static dev.mlua.runtime.client.MLuaRenderFrameScope.View frameScope(LuaValue value) {
        return MLuaJavaOutputAdapter.frameScope(value);
    }

    MLuaJavaObjectProxyFactory objectProxies() { return this.objectProxies; }

    Object[] extractInvocationArgs(Varargs args, int startIndex) {
        int count = Math.max(0, args.narg() - startIndex + 1);
        List<LuaValue> tableArguments = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LuaValue argument = args.arg(startIndex + i);
            if (isPlainLuaTable(argument)) tableArguments.add(argument);
        }
        try {
            MLuaLuaCoercions.validateLuaValues(tableArguments);
        } catch (IllegalArgumentException invalidTable) {
            throw new LuaError("[MLua] Java call arguments contain an invalid or oversized Lua table: "
                + invalidTable.getMessage());
        }
        Object[] rawArgs = new Object[count];
        for (int i = 0; i < count; i++) {
            LuaValue argument = args.arg(startIndex + i);
            rawArgs[i] = isPlainLuaTable(argument)
                ? new MLuaJavaTableArgument(argument, () -> this.unwrapJavaValue(argument))
                : unwrapJavaValue(argument);
        }
        return rawArgs;
    }

    Object deferLuaTable(LuaValue value) {
        return new MLuaJavaTableArgument(value, () -> this.unwrapJavaValue(value));
    }

    boolean isPlainLuaTable(LuaValue value) {
        return value.istable() && !isJavaProxy(value);
    }

    boolean isDeferredLuaTable(Object value) {
        return value instanceof MLuaJavaTableArgument;
    }

    Constructor<?> resolveConstructor(Class<?> clazz, Object[] rawArgs) {
        return this.overloads.resolveConstructor(clazz, rawArgs);
    }

    Method resolveMethod(List<Method> candidates, Object[] rawArgs) {
        return this.overloads.resolveMethod(candidates, rawArgs);
    }

    void requireExactCallArgumentCount(String operation, String executable,
                                              Executable selected, int actualCount) {
        int parameterCount = selected.getParameterCount();
        int minimumCount = selected.isVarArgs() ? parameterCount - 1 : parameterCount;
        boolean valid = actualCount >= minimumCount
            && (selected.isVarArgs() || actualCount == parameterCount);
        if (!valid) {
            String expected = selected.isVarArgs()
                ? "at least " + minimumCount : Integer.toString(parameterCount);
            throw new LuaError("[MLua] Java:" + operation + " expected " + expected + " arguments for "
                + executable + " but received " + actualCount);
        }
    }

    Object[] convertArguments(Object[] rawArgs, Executable executable) {
        return this.inputConverter.convertArguments(rawArgs, executable);
    }

    boolean hasPackedVarargs(Class<?>[] parameterTypes, Object[] rawArgs) {
        if (rawArgs.length != parameterTypes.length) return false;
        int last = parameterTypes.length - 1;
        Object value = rawArgs[last];
        return value == null || parameterTypes[last].isInstance(value) || value instanceof Iterable<?>
            || value instanceof MLuaJavaTableArgument && coercionScore(value, parameterTypes[last]) >= 0;
    }

    Object convertValue(Object rawValue, Class<?> targetType) {
        return this.typedValueConverter.convertValue(rawValue, targetType);
    }

    Object convertValue(Object rawValue, Class<?> targetType, MLuaJavaConversionBudget budget) {
        return this.typedValueConverter.convertValue(rawValue, targetType, budget);
    }

    int coercionScore(Object value, Class<?> parameterType) {
        return coercionScore(value, parameterType, parameterType);
    }

    int coercionScore(Object value, Class<?> parameterType, Type targetType) {
        return this.coercionScorer.score(value, parameterType, targetType);
    }

    Object convertNumber(Number number, Class<?> targetType) {
        return this.typedValueConverter.convertNumber(number, targetType);
    }

    LuaValue toLuaTable(Object value, dev.mlua.runtime.client.MLuaRenderFrameScope.View frameScope) {
        return this.toLuaConverter.convert(value, frameScope);
    }

    String describeArgs(Object[] rawArgs) {
        return MLuaJavaReflectionSupport.describeArgs(rawArgs);
    }

    String describeMethods(List<Method> methods) {
        return MLuaJavaReflectionSupport.describeMethods(methods);
    }

    String describeConstructors(Class<?> clazz) {
        return MLuaJavaReflectionSupport.describeConstructors(clazz);
    }

    String rootMessage(Exception exception) {
        return MLuaJavaReflectionSupport.rootMessage(exception);
    }

    String methodSignature(Method method) {
        return MLuaJavaReflectionSupport.methodSignature(method);
    }

    Class<?> boxType(Class<?> type) {
        return MLuaJavaReflectionSupport.boxType(type);
    }

    Class<?> tryResolveClass(String name) {
        return this.classResolver.resolve(name);
    }

}
