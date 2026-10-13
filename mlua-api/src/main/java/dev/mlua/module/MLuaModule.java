package dev.mlua.module;

import dev.mlua.common.enums.Side;
import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.core.MLuaContextView;
import dev.mlua.core.service.MLuaService;
import dev.mlua.loaderapi.EventBridge;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.luaj.vm2.Globals;

public interface MLuaModule extends MLuaReloadable {
    String id();

    /** Return false to disable this feature without removing its implementation. */
    default boolean enabled() {
        return true;
    }

    /** Module ids that must be loaded before this module is initialized. */
    default List<String> dependencies() {
        return List.of();
    }

    default int priority() {
        return 0;
    }

    default Side side() {
        return Side.BOTH;
    }

    @Override
    default void onReload(Side reloadSide) {
        if (reloadSide == Side.BOTH || side() == reloadSide || (reloadSide == Side.SERVER && side() == Side.BOTH)) {
            onReload();
        }
    }

    default List<ServiceEntry> services() {
        return List.of();
    }

    /** Registers this feature's Lua classes in the runtime's class registry. */
    default void registerClasses(MLuaRegistration registration) {
    }

    default void contributeGlobals(Globals globals) {
    }

    default void contributeGlobals(Globals globals, MLuaContextView context) {
        this.contributeContextGlobals(globals, context);
    }

    /** @deprecated Override {@link #contributeGlobals(Globals, MLuaContextView)} instead. */
    @Deprecated
    default void contributeContextGlobals(Globals globals, MLuaContextView context) {
    }

    default void registerEvents(EventBridge events) {
    }

    default void contributeDocs(MLuaDocBuilder docs) {
    }

    default void onInitialize() {
    }

    /** Clears runtime-owned state when this runtime reloads scripts or stops. */
    default void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
    }

    /** Clears state created by one addon during a scoped script reload. */
    default void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
    }

    /** Called after a runtime side finishes its tick, for side-specific service work. */
    default void onRuntimeTick(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, double deltaTime) {
    }

    /** Called after all modules have completed a side tick, for publishing runtime signals. */
    default void afterRuntimeTick(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, double deltaTime) {
    }

    /** Called for a client render frame when a module needs its measured delta time. */
    default void onRenderFrame(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, double deltaTime) {
    }

    /** Called after all modules have updated their render-frame state. */
    default void afterRenderFrame(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, double deltaTime) {
    }

    /** Called after a client render frame has ended. */
    default void onRenderFrameEnd(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession) {
    }

    /** Called immediately before the runtime executes scripts for a registration phase. */
    default void beforeScriptPhase(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side,
                                   RegistrationPhase phase, Object phaseContext) {
    }

    /** Called after the runtime executes scripts for a registration phase. */
    default void afterScriptPhase(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side,
                                  RegistrationPhase phase, Object phaseContext) {
    }

    /**
     * Declares one Lua service. Function descriptions define the service class contract: every live
     * instance returned by the factory must expose each declared function with that description.
     */
    record ServiceEntry(String name, String className, Function<MLuaService.Context, MLuaService> factory,
                        String description, Map<String, String> functionDescriptions) {
        /** Starts a typed service declaration with descriptions attached per exposed method. */
        public static Builder builder(String name, Function<MLuaService.Context, MLuaService> factory) {
            return new Builder(name, factory);
        }

        @Deprecated(forRemoval = false)
        public ServiceEntry(String name, Function<MLuaService.Context, MLuaService> factory) {
            this(name, null, factory, null, Map.of());
        }

        @Deprecated(forRemoval = false)
        public ServiceEntry(String name, Function<MLuaService.Context, MLuaService> factory,
                            String description) {
            this(name, null, factory, description, Map.of());
        }

        public ServiceEntry(String name, Function<MLuaService.Context, MLuaService> factory,
                            String description, Map<String, String> functionDescriptions) {
            this(name, null, factory, description, functionDescriptions);
        }

        /** Declares a service under a specific Lua class name. */
        @Deprecated(forRemoval = false)
        public ServiceEntry(String name, String className, Function<MLuaService.Context, MLuaService> factory,
                            String description) {
            this(name, className, factory, description, Map.of());
        }

        @Deprecated(forRemoval = false)
        public ServiceEntry(String name, String className, Function<MLuaService.Context, MLuaService> factory) {
            this(name, className, factory, null, Map.of());
        }

        public ServiceEntry {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Service name must be non-empty");
            // The service lookup name is its Lua class name unless an explicit class is selected.
            if (className == null) className = name;
            if (className.isBlank()) throw new IllegalArgumentException("Service class name must be non-empty");
            Objects.requireNonNull(factory, "factory");
            if (description != null && description.isBlank()) throw new IllegalArgumentException("Service description must be non-empty");
            functionDescriptions = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(functionDescriptions, "functionDescriptions")));
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("Service '" + name + "' must have a description");
            }
            if (functionDescriptions.isEmpty()) {
                throw new IllegalArgumentException("Service '" + name
                    + "' must declare at least one function with a description");
            }
            functionDescriptions.forEach((method, methodDescription) -> {
                if (method == null || method.isBlank() || methodDescription == null || methodDescription.isBlank()) {
                    throw new IllegalArgumentException("Service function names and descriptions must be non-empty");
                }
                if (MLuaRegistration.isReservedFunctionName(method)) {
                    throw new IllegalArgumentException("Service function name is reserved for MLua metadata: " + method);
                }
            });
        }

        /** Mutable declaration builder; {@link #build()} returns an immutable service contract. */
        public static final class Builder {
            private final String name;
            private final Function<MLuaService.Context, MLuaService> factory;
            private String className;
            private String description;
            private final java.util.Map<String, String> functions = new java.util.LinkedHashMap<>();

            private Builder(String name, Function<MLuaService.Context, MLuaService> factory) {
                this.name = name;
                this.factory = factory;
            }

            public Builder className(String className) {
                this.className = className;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public Builder function(String name, String description) {
                if (name == null || name.isBlank()) throw new IllegalArgumentException("Service function name must be non-empty");
                if (description == null || description.isBlank()) throw new IllegalArgumentException("Service function description must be non-empty");
                if (this.functions.putIfAbsent(name, description) != null) {
                    throw new IllegalArgumentException("Service function '" + name + "' is already declared");
                }
                return this;
            }

            public ServiceEntry build() {
                if (this.description == null || this.description.isBlank()) {
                    throw new IllegalStateException("Service '" + this.name + "' must have a description before it is built");
                }
                if (this.functions.isEmpty()) {
                    throw new IllegalStateException("Service '" + this.name
                        + "' must declare at least one function with a description before it is built");
                }
                return new ServiceEntry(this.name, this.className, this.factory, this.description, this.functions);
            }
        }
    }
}
