package dev.mlua.runtime.client;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.require.MLuaModuleCache;
import dev.mlua.core.scheduler.MLuaTaskScheduler;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.core.service.MLuaServiceRegistry;
import dev.mlua.core.signal.MLuaConnection;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaModelAdapterRegistryTest {
    @Test
    void adapterMappingsMergeRejectConflictsAndRespectOwnerTeardown() {
        Object firstSession = new Object();
        Object secondSession = new Object();
        var firstOwner = new MLuaModelAdapterRegistry.Owner(firstSession, "first_addon");
        var secondOwner = new MLuaModelAdapterRegistry.Owner(secondSession, "second_addon");
        String modelClass = "mlua.test.render.ModelAdapterOwnershipFixture";

        try {
            MLuaModelAdapterRegistry.register(modelClass, Map.of("head", "root.head"), firstOwner);
            MLuaModelAdapterRegistry.register(modelClass, Map.of("tail", "root.body.tail"), secondOwner);

            assertEquals(Map.of("head", "root.head", "tail", "root.body.tail"),
                MLuaModelAdapterRegistry.registeredMappings(modelClass));
            assertThrows(IllegalStateException.class,
                () -> MLuaModelAdapterRegistry.register(modelClass, Map.of("head", "root.other"), secondOwner));
            assertEquals(Map.of("head", "root.head", "tail", "root.body.tail"),
                MLuaModelAdapterRegistry.registeredMappings(modelClass),
                "a rejected conflicting mapping must not partially replace either addon's adapter");

            MLuaModelAdapterRegistry.clearOwned(firstOwner);
            assertEquals(Map.of("tail", "root.body.tail"), MLuaModelAdapterRegistry.registeredMappings(modelClass));
            assertEquals(0, MLuaModelAdapterRegistry.registeredPartsForOwner(firstOwner));
            assertEquals(1, MLuaModelAdapterRegistry.registeredPartsForOwner(secondOwner));

            MLuaModelAdapterRegistry.clearRuntime(secondSession);
            assertTrue(MLuaModelAdapterRegistry.registeredMappings(modelClass).isEmpty());
        } finally {
            MLuaModelAdapterRegistry.clearRuntime(firstSession);
            MLuaModelAdapterRegistry.clearRuntime(secondSession);
        }
    }

    @Test
    void addonAndRuntimeTeardownRemoveLuaResolversAndCustomAdapters() {
        MLuaRuntimeSession runtime = new MLuaRuntimeSession();
        var addon = new MLuaModelAdapterRegistry.Owner(runtime, "adapter_addon");
        var otherAddon = new MLuaModelAdapterRegistry.Owner(runtime, "other_adapter_addon");
        MLuaContext addonContext = context(runtime, "adapter_addon");
        MLuaContext otherContext = context(runtime, "other_adapter_addon");
        String modelClass = "mlua.test.render.LuaResolverOwnershipFixture";
        String customClass = "mlua.test.render.CustomAdapterOwnershipFixture";
        String rendererClass = "mlua.test.render.RendererAdapterOwnershipFixture";
        try {
            MLuaConnection resolverHandle = MLuaModelAdapterRegistry.registerResolver(
                modelClass, callback(), addonContext, addon);
            MLuaConnection customAdapterHandle = MLuaModelAdapterRegistry.registerCustomAdapter(
                customClass, java.util.List.of("root"), callback(), null, addonContext, addon);
            MLuaModelAdapterRegistry.registerRendererAdapter(rendererClass, "model", addon);
            MLuaModelAdapterRegistry.registerResolver(modelClass, callback(), otherContext, otherAddon);

            assertEquals(1, MLuaModelAdapterRegistry.registeredResolverCount(addon));
            assertEquals(1, MLuaModelAdapterRegistry.registeredCustomAdapterCount(addon));
            assertEquals(1, MLuaModelAdapterRegistry.registeredRendererAdapterCount(addon));
            assertEquals(1, MLuaModelAdapterRegistry.registeredCustomPartCount());

            MLuaModelAdapterRegistry.clearOwned(addon);

            assertFalse(resolverHandle.connected(), "addon teardown invalidates the resolver connection");
            assertFalse(customAdapterHandle.connected(), "addon teardown invalidates the custom adapter connection");
            assertEquals(0, MLuaModelAdapterRegistry.registeredResolverCount(addon));
            assertEquals(0, MLuaModelAdapterRegistry.registeredCustomAdapterCount(addon));
            assertEquals(0, MLuaModelAdapterRegistry.registeredRendererAdapterCount(addon));
            assertEquals(0, MLuaModelAdapterRegistry.registeredCustomPartCount());
            assertEquals(1, MLuaModelAdapterRegistry.registeredResolverCount(otherAddon),
                "unloading one addon must preserve another addon's resolver in the same runtime");

            MLuaModelAdapterRegistry.clearRuntime(runtime);
            assertEquals(0, MLuaModelAdapterRegistry.registeredResolverCount(otherAddon));
        } finally {
            MLuaModelAdapterRegistry.clearRuntime(runtime);
        }
    }

    @Test
    void resolverAndCustomAdapterConnectionsOnlyRemoveTheirOwnRegistration() {
        MLuaRuntimeSession runtime = new MLuaRuntimeSession();
        var owner = new MLuaModelAdapterRegistry.Owner(runtime, "replaceable_adapter_addon");
        MLuaContext context = context(runtime, "replaceable_adapter_addon");
        String modelClass = "mlua.test.render.ReplaceableResolverFixture";
        String customClass = "mlua.test.render.ReplaceableCustomAdapterFixture";
        try {
            MLuaConnection oldResolver = MLuaModelAdapterRegistry.registerResolver(
                modelClass, callback(), context, owner);
            MLuaConnection newResolver = MLuaModelAdapterRegistry.registerResolver(
                modelClass, callback(), context, owner);

            assertFalse(oldResolver.connected(), "replacing a resolver invalidates its previous handle");
            oldResolver.disconnect();
            assertTrue(newResolver.connected(), "a stale handle cannot disconnect its replacement");
            assertEquals(1, MLuaModelAdapterRegistry.registeredResolverCount(owner));
            newResolver.disconnect();
            assertEquals(0, MLuaModelAdapterRegistry.registeredResolverCount(owner));

            MLuaConnection oldAdapter = MLuaModelAdapterRegistry.registerCustomAdapter(
                customClass, java.util.List.of("root", "head"), callback(), null, context, owner);
            MLuaConnection newAdapter = MLuaModelAdapterRegistry.registerCustomAdapter(
                customClass, java.util.List.of("root", "head", "tail"), callback(), null, context, owner);

            assertFalse(oldAdapter.connected(), "replacing a custom adapter invalidates its previous handle");
            oldAdapter.disconnect();
            assertTrue(newAdapter.connected(), "a stale handle cannot disconnect its replacement");
            assertEquals(1, MLuaModelAdapterRegistry.registeredCustomAdapterCount(owner));
            newAdapter.disconnect();
            assertEquals(0, MLuaModelAdapterRegistry.registeredCustomAdapterCount(owner));
        } finally {
            MLuaModelAdapterRegistry.clearRuntime(runtime);
        }
    }

    @Test
    void rendererModelFieldCacheTracksAdapterReplacement() {
        Object runtime = new Object();
        var owner = new MLuaModelAdapterRegistry.Owner(runtime, "renderer_path_addon");
        RendererFixture renderer = new RendererFixture();
        String modelClass = AdapterModelFixture.class.getName();
        try {
            MLuaModelAdapterRegistry.register(modelClass, Map.of("root", "root"), owner);
            MLuaModelAdapterRegistry.registerRendererAdapter(RendererFixture.class.getName(), "model", owner);
            assertSame(renderer.model, MLuaModelAdapterRegistry.findAdaptedModel(renderer));

            MLuaModelAdapterRegistry.registerRendererAdapter(RendererFixture.class.getName(), "alternate", owner);
            assertSame(renderer.alternate, MLuaModelAdapterRegistry.findAdaptedModel(renderer),
                "replacing a renderer field path must invalidate the cached lookup");
        } finally {
            MLuaModelAdapterRegistry.clearRuntime(runtime);
        }
    }

    public static final class AdapterModelFixture { }

    public static final class RendererFixture {
        public final AdapterModelFixture model = new AdapterModelFixture();
        public final AdapterModelFixture alternate = new AdapterModelFixture();
    }

    private static LuaValue callback() {
        return new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.NIL; }
        };
    }

    private static MLuaContext context(MLuaRuntimeSession session, String owner) {
        Path root = Path.of(".").toAbsolutePath().normalize();
        MLuaScript script = new MLuaScript(owner, ScriptType.CLIENT, root,
            root.resolve(owner + ".client.lua"), owner, "");
        return new MLuaContext(script, Side.CLIENT, RegistrationPhase.RUNTIME, new MLuaServiceRegistry(),
            new MLuaTaskScheduler(), new MLuaModuleCache(), null, root.resolve("data"), null, session);
    }
}
