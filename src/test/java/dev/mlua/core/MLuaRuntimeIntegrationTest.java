package dev.mlua.core;

import dev.mlua.api.MLuaLuaCoercions;
import dev.mlua.core.engine.LuaJEngineBackend;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.engine.MLuaValue;
import dev.mlua.core.require.MLuaModuleCache;
import dev.mlua.core.scheduler.MLuaTaskScheduler;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.core.service.MLuaServiceRegistry;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.LuaBackedService;
import dev.mlua.module.MLuaModule;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.CoreGuiType;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.modules.javabridge.JavaModule;
import dev.mlua.modules.coregui.service.MLuaGuiService;
import dev.mlua.modules.modal.service.MLuaModalService;
import dev.mlua.minecraft.ui.element.ScreenGui;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.loaderapi.JavaClassBridge;
import dev.mlua.loaderapi.PlatformBridge;
import dev.mlua.common.mixin.MLuaMappingResolver;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaRuntimeIntegrationTest {
    @BeforeAll
    static void installLoaderBridgeForJavaServiceTests() {
        LoaderApi.install((LoaderApi.LoaderProvider) Proxy.newProxyInstance(
            LoaderApi.LoaderProvider.class.getClassLoader(), new Class<?>[]{LoaderApi.LoaderProvider.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "platform" -> new PlatformBridge() {
                    @Override public Path gameDir() { return Path.of(".").toAbsolutePath().normalize(); }
                    @Override public Path configDir() { return gameDir().resolve("config"); }
                    @Override public boolean hasMod(String modId) { return false; }
                    @Override public boolean isClient() { return false; }
                    @Override public boolean isProduction() { return false; }
                    @Override public String loaderName() { return "test"; }
                    @Override public java.util.Collection<dev.mlua.loaderapi.ScriptRootRef> bundledScriptRoots() { return List.of(); }
                };
                case "mappings" -> new MLuaMappingResolver() {
                    @Override public String loaderId() { return "test"; }
                };
                case "javaClasses" -> (JavaClassBridge) (canonicalName, preferredLoaders) -> {
                    if (preferredLoaders != null) for (ClassLoader loader : preferredLoaders) {
                        if (loader == null) continue;
                        try { return Class.forName(canonicalName, false, loader); }
                        catch (ClassNotFoundException ignored) { }
                    }
                    try { return Class.forName(canonicalName, false, ClassLoader.getSystemClassLoader()); }
                    catch (ClassNotFoundException ignored) { return null; }
                };
                default -> null;
            }
        ));
    }

    @TempDir
    Path temp;

    @Test
    void baseRegistersServicesAndClassesForLuaJLookup() {
        MLuaRuntime base = new MLuaRuntime();
        base.registerClass("Object", null, "Root class for test fixtures.");
        base.registerInstanceBaseClass("Instance", "Object", "Base class for live test instances.");
        base.registerServiceBaseClass("Service", "Instance", "Base class for live test services.");
        base.registerService("BaseProbe", Side.BOTH, BaseProbeService::new,
            "Base service registration fixture.", java.util.Map.of("GetValue", "Returns the fixture value."));

        MLuaScript main = script("mlua", ScriptType.SERVER, "base_registration.server.lua", "base_registration", """
                local service = Minecraft:GetService("BaseProbe")
                assert(service.Value == "registered")
                return { service:GetValue(), Minecraft.Classes.BasePlayer.ClassName }
                """);
        MLuaContext context = context(base, main, Side.SERVER);

        base.registerClass("BaseEntity", "Instance", "Base class for player fixture classes.");
        LuaTable playerClass = base.registerClass("BasePlayer", new LuaTable(), "BaseEntity");
        LuaTable instance = new LuaTable();
        base.attachClassInstance(instance, "BasePlayer", null);
        assertTrue(base.isInstanceOf(instance, "BaseEntity"));
        assertEquals("BasePlayer", playerClass.get("ClassName").tojstring());

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(main));
        LuaValue result = (LuaValue) engine.execute(main, context).raw();
        assertEquals("registered", result.get(1).tojstring());
        assertEquals("BasePlayer", result.get(2).tojstring());
    }

    @Test
    void executesLuaAndResolvesRequireModules() {
        MLuaScript main = script("mlua", ScriptType.SERVER, "main.server.lua", "main", """
                local Counter = Require("shared.Counter")
                return Counter:Increment()
                """);
        MLuaScript module = script("mlua", ScriptType.MODULE, "shared/Counter.lua", "shared.Counter", """
                local Counter = {}
                Counter.Value = 0
                function Counter:Increment()
                    self.Value = self.Value + 1
                    return self.Value
                end
                return Counter
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(main, module));
        MLuaValue result = engine.execute(main, context(main));

        assertEquals(1, ((LuaValue) result.raw()).toint());
    }

    @Test
    void keepsServerAndClientLuaGlobalsIsolated() {
        MLuaScript main = script("mlua", ScriptType.SERVER, "side_state.lua", "side_state", """
                sideState = (sideState or 0) + 1
                return sideState
                """);
        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(main));
        MLuaTaskScheduler serverScheduler = new MLuaTaskScheduler();
        MLuaTaskScheduler clientScheduler = new MLuaTaskScheduler();

        assertEquals(1, executeAs(engine, main, Side.SERVER, serverScheduler));
        assertEquals(1, executeAs(engine, main, Side.CLIENT, clientScheduler));
        assertEquals(2, executeAs(engine, main, Side.SERVER, serverScheduler));
        assertEquals(2, executeAs(engine, main, Side.CLIENT, clientScheduler));
    }

    @Test
    void resettingOneSidePreservesTheOtherLuaEnvironment() {
        MLuaScript main = script("mlua", ScriptType.SERVER, "reset_side.lua", "reset_side", "state = (state or 0) + 1; return state");
        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(main));
        MLuaTaskScheduler serverScheduler = new MLuaTaskScheduler();
        MLuaTaskScheduler clientScheduler = new MLuaTaskScheduler();

        assertEquals(1, executeAs(engine, main, Side.SERVER, serverScheduler));
        assertEquals(1, executeAs(engine, main, Side.CLIENT, clientScheduler));
        engine.resetSide(Side.CLIENT);

        assertEquals(2, executeAs(engine, main, Side.SERVER, serverScheduler));
        assertEquals(1, executeAs(engine, main, Side.CLIENT, clientScheduler));
    }

    @Test
    void clearingOneSessionSidePreservesTheOtherModuleCache() {
        MLuaRuntimeSession session = new MLuaRuntimeSession();
        MLuaValue value = new MLuaValue(LuaValue.valueOf("cached"));
        session.serverModules().put("probe", value);
        session.clientModules().put("probe", value);

        session.clearScriptState(Side.CLIENT);

        assertTrue(session.serverModules().contains("probe"));
        assertEquals(null, session.clientModules().get("probe"));
    }

    @Test
    void sharedModuleLegacyCleanupRunsOnlyForServerScopedReloads() {
        ReloadProbeModule module = new ReloadProbeModule();

        module.onReload(Side.CLIENT);
        assertEquals(0, module.reloadCount);
        module.onReload(Side.SERVER);
        assertEquals(1, module.reloadCount);
    }

    @Test
    void schedulerRemovesOnlyTasksOwnedByTheRequestedModAndSide() {
        MLuaScript serverScript = script("server-mod", ScriptType.SERVER, "owner_task.lua", "owner_task", "return true");
        MLuaScript clientScript = script("client-mod", ScriptType.CLIENT, "owner_task.lua", "owner_task", "return true");
        MLuaTaskScheduler scheduler = new MLuaTaskScheduler();
        Globals serverGlobals = JsePlatform.standardGlobals();
        Globals clientGlobals = JsePlatform.standardGlobals();
        MLuaLuaBindings.bindContext(serverGlobals, context(serverScript, Side.SERVER, new MLuaServiceRegistry(), scheduler));
        MLuaLuaBindings.bindContext(clientGlobals, context(clientScript, Side.CLIENT, new MLuaServiceRegistry(), scheduler));
        scheduler.spawn(serverGlobals, serverGlobals.load("return true"), LuaValue.NONE);
        scheduler.spawn(clientGlobals, clientGlobals.load("return true"), LuaValue.NONE);

        assertEquals(1, scheduler.removeOwned(Side.SERVER, "server-mod"));
        assertEquals(0, scheduler.removeOwned(Side.SERVER, "client-mod"));
        assertEquals(1, scheduler.removeOwned(Side.CLIENT, "client-mod"));
    }

    @Test
    void signalCleanupKeepsOtherSidesOwnerListeners() {
        Globals globals = JsePlatform.standardGlobals();
        globals.load("serverCount = 0; clientCount = 0").call();
        MLuaSignal signal = new MLuaSignal();
        signal.bind(globals, new MLuaTaskScheduler());
        signal.connectOwned(globals.load("serverCount = serverCount + 1"), "probe", Side.SERVER);
        signal.connectOwned(globals.load("clientCount = clientCount + 1"), "probe", Side.CLIENT);

        signal.destroyOwned("probe", Side.CLIENT);
        signal.fire(LuaValue.NONE);

        assertEquals(1, globals.get("serverCount").toint());
        assertEquals(0, globals.get("clientCount").toint());
    }

    @Test
    void sideDispatchKeepsSharedEventSignalsInsideTheirRuntime() {
        Globals globals = JsePlatform.standardGlobals();
        globals.load("serverCount = 0; clientCount = 0").call();
        MLuaSignal signal = new MLuaSignal();
        signal.bind(globals, new MLuaTaskScheduler());
        signal.connectOwned(globals.load("serverCount = serverCount + 1"), "server-mod", Side.SERVER);
        signal.connectOwned(globals.load("clientCount = clientCount + 1"), "client-mod", Side.CLIENT);

        signal.fireOnSide(Side.SERVER, LuaValue.NONE);
        assertEquals(1, globals.get("serverCount").toint());
        assertEquals(0, globals.get("clientCount").toint());

        signal.fireOnSide(Side.CLIENT, LuaValue.NONE);
        assertEquals(1, globals.get("serverCount").toint());
        assertEquals(1, globals.get("clientCount").toint());
    }

    @Test
    void inlineSignalDispatchRunsListenerOnTheLifecycleOwnerThread() {
        Globals globals = JsePlatform.standardGlobals();
        MLuaSignal signal = new MLuaSignal();
        MLuaTaskScheduler scheduler = new MLuaTaskScheduler();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        signal.connectOwned(new ZeroArgFunction() {
            @Override public LuaValue call() {
                callbackThread.set(Thread.currentThread());
                return LuaValue.NONE;
            }
        }, "lifecycle-test", Side.SERVER, globals, scheduler);

        Thread ownerThread = Thread.currentThread();
        signal.fireInlineOnSession(null, Side.SERVER, LuaValue.NONE);

        assertSame(ownerThread, callbackThread.get());
    }

    @Test
    void cancellableSideDispatchIgnoresOtherSideResults() {
        Globals globals = JsePlatform.standardGlobals();
        MLuaSignal signal = new MLuaSignal();
        signal.connectOwned(globals.load("return false"), "server-mod", Side.SERVER);
        signal.connectOwned(globals.load("return true"), "client-mod", Side.CLIENT);

        assertTrue(signal.fireCancellableOnSide(Side.SERVER, LuaValue.NONE));
        assertEquals(false, signal.fireCancellableOnSide(Side.CLIENT, LuaValue.NONE));
    }


    @Test
    void signalCallbacksScheduleResumableWorkOnTheirCreatingSide() {
        MLuaScript serverScript = script("server-mod", ScriptType.SERVER, "owned_signal.lua", "owned_signal", "return true");
        MLuaScript clientScript = script("client-mod", ScriptType.CLIENT, "owned_signal.lua", "owned_signal", "return true");
        MLuaTaskScheduler scheduler = new MLuaTaskScheduler();
        Globals serverGlobals = JsePlatform.standardGlobals();
        Globals clientGlobals = JsePlatform.standardGlobals();
        MLuaContext serverContext = context(serverScript, Side.SERVER, new MLuaServiceRegistry(), scheduler);
        MLuaContext clientContext = context(clientScript, Side.CLIENT, new MLuaServiceRegistry(), scheduler);
        MLuaLuaBindings.bindContext(serverGlobals, serverContext);
        MLuaLuaBindings.bindContext(clientGlobals, clientContext);
        serverGlobals.set("task", MLuaLuaBindings.taskTable(scheduler, serverGlobals));
        clientGlobals.set("task", MLuaLuaBindings.taskTable(scheduler, clientGlobals));
        MLuaSignal signal = new MLuaSignal();
        serverGlobals.set("signal", MLuaLuaBindings.signalInstance(signal, serverGlobals, scheduler, "server-mod", Side.SERVER));
        clientGlobals.set("signal", MLuaLuaBindings.signalInstance(signal, clientGlobals, scheduler, "client-mod", Side.CLIENT));
        serverGlobals.load("signal:Connect(function() task.wait(5) end)").call();
        clientGlobals.load("signal:Connect(function() task.wait(5) end)").call();

        signal.fire(LuaValue.NONE);

        assertEquals(1, scheduler.removeOwned(Side.SERVER, "server-mod"));
        assertEquals(1, scheduler.removeOwned(Side.CLIENT, "client-mod"));
    }

    @Test
    void asynchronousFailuresAreReportedToTheirOwningRuntime() {
        MLuaRuntime first = new MLuaRuntime(new MLuaRuntimeSession());
        MLuaRuntime second = new MLuaRuntime(new MLuaRuntimeSession());
        Globals taskGlobals = JsePlatform.standardGlobals();
        first.session().serverScheduler().spawn(taskGlobals, taskGlobals.load("error('first task failed')"), LuaValue.NONE);
        first.session().serverScheduler().tick();

        MLuaScript firstScript = script("first-mod", ScriptType.SERVER, "signal.lua", "signal", "return true");
        MLuaContext firstContext = context(first, firstScript, Side.SERVER);
        Globals signalGlobals = JsePlatform.standardGlobals();
        MLuaLuaBindings.bindContext(signalGlobals, firstContext);
        MLuaSignal signal = new MLuaSignal();
        signal.connectOwned(signalGlobals.load("error('first signal failed')"), "first-mod", Side.SERVER,
                signalGlobals, first.session().serverScheduler(), firstContext);
        signal.fireOnSide(Side.SERVER, LuaValue.NONE);

        assertTrue(recentDiagnostics(first).stream().anyMatch(message -> message.contains("Scheduled MLua task crashed")));
        assertTrue(recentDiagnostics(first).stream().anyMatch(message -> message.contains("Signal listener crashed")));
        assertEquals(List.of(), recentDiagnostics(second));
    }

    @Test
    void runtimeSessionResolvesItsOwningRegistration() {
        MLuaRuntime first = new MLuaRuntime(new MLuaRuntimeSession());
        MLuaRuntime second = new MLuaRuntime(new MLuaRuntimeSession());

        assertSame(first, first.session().registration());
        assertSame(second, second.session().registration());
        assertNotSame(first.session().registration(), second.session().registration());
    }

    @Test
    void clearingOneRuntimeSessionPreservesSharedSignalListenersFromAnother() {
        MLuaRuntime first = new MLuaRuntime(new MLuaRuntimeSession());
        MLuaRuntime second = new MLuaRuntime(new MLuaRuntimeSession());
        Globals firstGlobals = JsePlatform.standardGlobals();
        Globals secondGlobals = JsePlatform.standardGlobals();
        firstGlobals.load("count = 0").call();
        secondGlobals.load("count = 0").call();
        MLuaSignal signal = new MLuaSignal();
        MLuaContext firstContext = context(first, script("first-mod", ScriptType.SERVER, "signal.lua", "signal", "return true"), Side.SERVER);
        MLuaContext secondContext = context(second, script("second-mod", ScriptType.SERVER, "signal.lua", "signal", "return true"), Side.SERVER);
        MLuaLuaBindings.bindContext(firstGlobals, firstContext);
        MLuaLuaBindings.bindContext(secondGlobals, secondContext);
        signal.connectOwned(firstGlobals.load("count = count + 1"), "first-mod", Side.SERVER,
                firstGlobals, first.session().serverScheduler(), firstContext);
        signal.connectOwned(secondGlobals.load("count = count + 1"), "second-mod", Side.SERVER,
                secondGlobals, second.session().serverScheduler(), secondContext);

        first.session().clearScriptState(Side.SERVER);
        signal.fireOnSide(Side.SERVER, LuaValue.NONE);

        assertEquals(0, firstGlobals.get("count").toint());
        assertEquals(1, secondGlobals.get("count").toint());
    }

    @Test
    void firingSharedSignalTargetsOnlyTheSelectedRuntimeSession() {
        MLuaRuntime first = new MLuaRuntime(new MLuaRuntimeSession());
        MLuaRuntime second = new MLuaRuntime(new MLuaRuntimeSession());
        Globals firstGlobals = JsePlatform.standardGlobals();
        Globals secondGlobals = JsePlatform.standardGlobals();
        firstGlobals.load("count = 0").call();
        secondGlobals.load("count = 0").call();
        MLuaSignal signal = new MLuaSignal();
        MLuaContext firstContext = context(first, script("first-mod", ScriptType.SERVER, "signal.lua", "signal", "return true"), Side.SERVER);
        MLuaContext secondContext = context(second, script("second-mod", ScriptType.SERVER, "signal.lua", "signal", "return true"), Side.SERVER);
        signal.connectOwned(firstGlobals.load("count = count + 1"), "first-mod", Side.SERVER,
                firstGlobals, first.session().serverScheduler(), firstContext);
        signal.connectOwned(secondGlobals.load("count = count + 1"), "second-mod", Side.SERVER,
                secondGlobals, second.session().serverScheduler(), secondContext);

        signal.fireOnSession(first.session(), Side.SERVER, LuaValue.NONE);
        assertEquals(1, firstGlobals.get("count").toint());
        assertEquals(0, secondGlobals.get("count").toint());

        signal.fireOnSession(second.session(), Side.SERVER, LuaValue.NONE);
        assertEquals(1, firstGlobals.get("count").toint());
        assertEquals(1, secondGlobals.get("count").toint());
    }

    @Test
    void guiScreensAreScopedToTheirRuntimeSchedulers() {
        MLuaRuntime first = new MLuaRuntime(new MLuaRuntimeSession());
        MLuaRuntime second = new MLuaRuntime(new MLuaRuntimeSession());
        MLuaGuiService firstGui = new MLuaGuiService(new MLuaService.Context(context(first,
                script("first-mod", ScriptType.CLIENT, "gui.lua", "gui", "return true"), Side.CLIENT)));
        MLuaGuiService secondGui = new MLuaGuiService(new MLuaService.Context(context(second,
                script("second-mod", ScriptType.CLIENT, "gui.lua", "gui", "return true"), Side.CLIENT)));
        LuaTable firstTable = firstGui.toLua();
        LuaTable secondTable = secondGui.toLua();
        firstTable.get("CreateScreen").invoke(LuaValue.varargsOf(new LuaValue[]{firstTable, LuaValue.valueOf("first")}));
        secondTable.get("CreateScreen").invoke(LuaValue.varargsOf(new LuaValue[]{secondTable, LuaValue.valueOf("second")}));
        ScreenGui firstScreen = firstGui.getScreenGuis().get(0);
        ScreenGui secondScreen = secondGui.getScreenGuis().get(0);

        assertEquals(List.of(firstScreen), MLuaGuiService.getForScheduler(first.session().clientScheduler()).getScreenGuis());
        assertEquals(List.of(secondScreen), MLuaGuiService.getForScheduler(second.session().clientScheduler()).getScreenGuis());
        assertEquals("first-mod", firstScreen.ownerModId());
        assertEquals("second-mod", secondScreen.ownerModId());

        MLuaGuiService.clearForScheduler(first.session().clientScheduler());
        assertEquals(List.of(), MLuaGuiService.getForScheduler(first.session().clientScheduler()).getScreenGuis());
        assertEquals(List.of(secondScreen), MLuaGuiService.getForScheduler(second.session().clientScheduler()).getScreenGuis());
    }

    @Test
    void coreGuiRegistryKeepsCreateEnableAndRemoveBehavior() {
        MLuaRuntime runtime = new MLuaRuntime(new MLuaRuntimeSession());
        MLuaGuiService gui = new MLuaGuiService(new MLuaService.Context(context(runtime,
            script("gui-mod", ScriptType.CLIENT, "core_gui.lua", "core_gui", "return true"), Side.CLIENT)));
        LuaTable api = gui.toLua();

        ScreenGui chat = gui.createCoreGui(CoreGuiType.Chat, "chat-overlay");
        assertSame(chat, gui.createCoreGui(CoreGuiType.Chat, "ignored-second-name"));
        assertSame(chat, gui.getCoreGui(CoreGuiType.Chat));
        assertTrue(gui.isCoreGuiEnabled(CoreGuiType.Chat));
        gui.setCoreGuiEnabled(CoreGuiType.Chat, false);
        assertFalse(gui.isCoreGuiEnabled(CoreGuiType.Chat));
        gui.setCoreGuiEnabled(CoreGuiType.All, true);
        assertTrue(gui.isCoreGuiEnabled(CoreGuiType.Chat));

        LuaValue removed = api.get("RemoveScreenGui").invoke(LuaValue.varargsOf(new LuaValue[]{
            api, LuaValue.valueOf("chat-overlay")
        })).arg1();
        assertTrue(removed.toboolean());
        assertNull(gui.getCoreGui(CoreGuiType.Chat));
    }

    @Test
    void modalStateIsClearedOnlyForItsRuntimeSession() {
        MLuaRuntime first = new MLuaRuntime(new MLuaRuntimeSession());
        MLuaRuntime second = new MLuaRuntime(new MLuaRuntimeSession());
        MLuaModalService firstModal = new MLuaModalService(new MLuaService.Context(context(first,
                script("first-mod", ScriptType.CLIENT, "modal.lua", "modal", "return true"), Side.CLIENT)));
        MLuaModalService secondModal = new MLuaModalService(new MLuaService.Context(context(second,
                script("second-mod", ScriptType.CLIENT, "modal.lua", "modal", "return true"), Side.CLIENT)));
        firstModal.SetModal(true);
        secondModal.SetModal(true);

        MLuaModalService.clearForSession(first.session(), Side.CLIENT);

        assertEquals(false, firstModal.IsModal());
        assertTrue(secondModal.IsModal());
    }

    private static final class ReloadProbeModule implements MLuaModule {
        private int reloadCount;

        @Override public String id() { return "reload-probe"; }
        @Override public Side side() { return Side.SERVER; }
        @Override public void onReload() { reloadCount++; }
    }

    @Test
    void rejectsServicesOutsideTheirRegisteredSide() {
        MLuaScript main = script("mlua", ScriptType.SERVER, "side_service.server.lua", "side_service", "return true");
        MLuaServiceRegistry services = new MLuaServiceRegistry();
        services.register("ClientOnly", Side.CLIENT, BaseProbeService::new,
            "Client-only test service.", java.util.Map.of("GetValue", "Returns the fixture value."));
        MLuaContext context = context(main, Side.SERVER, services, new MLuaTaskScheduler());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> services.create("ClientOnly", context));
        assertTrue(error.getMessage().contains("not available on SERVER"));
    }

    @Test
    void supportsDifferentServiceImplementationsPerSide() {
        MLuaScript main = script("mlua", ScriptType.SERVER, "side_service.server.lua", "side_service", "return true");
        MLuaServiceRegistry services = new MLuaServiceRegistry();
        services.register("SideService", Side.SERVER, context -> new NamedService("server"),
            "Side-specific test service.", java.util.Map.of("Probe", "Returns the value for this side."));
        services.register("SideService", Side.CLIENT, context -> new NamedService("client"),
            "Side-specific test service.", java.util.Map.of("Probe", "Returns the value for this side."));

        assertEquals("server", services.create("SideService", context(main, Side.SERVER, services, new MLuaTaskScheduler())).toLua().get("Value").tojstring());
        assertEquals("client", services.create("SideService", context(main, Side.CLIENT, services, new MLuaTaskScheduler())).toLua().get("Value").tojstring());
    }

    private int executeAs(LuaJEngineBackend engine, MLuaScript script, Side side, MLuaTaskScheduler scheduler) {
        MLuaContext context = context(script, side, new MLuaServiceRegistry(), scheduler);
        return ((LuaValue) engine.execute(script, context).raw()).toint();
    }

    private MLuaContext context(MLuaScript script, Side side, MLuaServiceRegistry services, MLuaTaskScheduler scheduler) {
        return new MLuaContext(
                script,
                side,
                RegistrationPhase.RUNTIME,
                services,
                scheduler,
                new MLuaModuleCache(),
                null,
                temp.resolve("data")
        );
    }

    private MLuaContext context(MLuaRuntime runtime, MLuaScript script, Side side) {
        return new MLuaContext(
                script,
                side,
                RegistrationPhase.RUNTIME,
                runtime.services(),
                side == Side.SERVER ? runtime.session().serverScheduler() : runtime.session().clientScheduler(),
                side == Side.SERVER ? runtime.session().serverModules() : runtime.session().clientModules(),
                null,
                temp.resolve("data"),
                runtime::reportRuntimeFailure,
                runtime.session(),
                runtime
        );
    }

    private static List<String> recentDiagnostics(MLuaRuntime runtime) {
        return runtime.recentServerDiagnostics();
    }

    @Test
    void blocksRelativeRequireEscapingScriptRoot() {
        MLuaScript main = script("mlua", ScriptType.SERVER, "main.server.lua", "main", """
                return Require("../outside")
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(main));

        LuaError error = assertThrows(LuaError.class, () -> engine.execute(main, context(main)));
        assertTrue(error.getMessage().contains("escaped script root"));
    }

    @Test
    void schedulerRunsDelayedTasksOnTicks() {
        MLuaScript main = script("mlua", ScriptType.SERVER, "main.server.lua", "main", """
                local value = 0
                task.delay(2, function()
                    value = 7
                end)
                return function()
                    return value
                end
                """);
        MLuaTaskScheduler scheduler = new MLuaTaskScheduler();
        MLuaContext context = context(main, scheduler);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(main));
        LuaValue probe = (LuaValue) engine.execute(main, context).raw();

        assertEquals(0, probe.call().toint());
        scheduler.tick();
        assertEquals(0, probe.call().toint());
        scheduler.tick();
        assertEquals(7, probe.call().toint());
    }

    @Test
    void schedulerRunsEqualDeadlineTasksInInsertionOrder() {
        MLuaScript main = script("mlua", ScriptType.SERVER, "main.server.lua", "main", """
                local order = ""
                task.delay(3, function() order = order .. "first" end)
                task.delay(3, function() order = order .. "second" end)
                return function() return order end
                """);
        MLuaTaskScheduler scheduler = new MLuaTaskScheduler();
        MLuaContext context = context(main, scheduler);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(main));
        LuaValue probe = (LuaValue) engine.execute(main, context).raw();

        scheduler.tick();
        scheduler.tick();
        assertEquals("", probe.call().tojstring());
        scheduler.tick();
        assertEquals("firstsecond", probe.call().tojstring());
    }

    @Test
    void taskSpawnPassesArguments() {
        MLuaScript main = script("mlua", ScriptType.SERVER, "main.server.lua", "main", """
                local value = "unset"
                task.spawn(function(a, b)
                    value = a .. ":" .. b
                end, "hello", "world")
                return function()
                    return value
                end
                """);
        MLuaTaskScheduler scheduler = new MLuaTaskScheduler();
        MLuaContext context = context(main, scheduler);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(main));
        LuaValue probe = (LuaValue) engine.execute(main, context).raw();

        assertEquals("unset", probe.call().tojstring());
        scheduler.tick();
        assertEquals("hello:world", probe.call().tojstring());
    }

    @Test
    void taskWaitYieldsAndResumesAfterTicks() {
        MLuaScript main = script("mlua", ScriptType.SERVER, "main.server.lua", "main", """
                local value = 0
                task.spawn(function()
                    value = 1
                    local waited = task.wait(2)
                    value = value + waited
                end)
                return function()
                    return value
                end
                """);
        MLuaTaskScheduler scheduler = new MLuaTaskScheduler();
        MLuaContext context = context(main, scheduler);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(main));
        LuaValue probe = (LuaValue) engine.execute(main, context).raw();

        assertEquals(0, probe.call().toint());
        scheduler.tick();
        assertEquals(1, probe.call().toint());
        scheduler.tick();
        assertEquals(1, probe.call().toint());
        scheduler.tick();
        assertEquals(3, probe.call().toint());
    }

    @Test
    void resumableRuntimeScriptCanYieldFromTopLevel() {
        MLuaScript main = script("mlua", ScriptType.CLIENT, "main.client.lua", "main", """
                ticks = 0
                while true do
                    ticks = ticks + 1
                    task.wait(1)
                end
                """);
        MLuaScript probeScript = script("mlua", ScriptType.MODULE, "probe.lua", "probe", """
                return ticks
                """);
        MLuaTaskScheduler scheduler = new MLuaTaskScheduler();
        MLuaContext context = new MLuaContext(
                main,
                Side.CLIENT,
                RegistrationPhase.RUNTIME,
                new MLuaServiceRegistry(),
                scheduler,
                new MLuaModuleCache(),
                null,
                temp.resolve("data")
        );

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(main, probeScript));
        engine.executeResumable(main, context);

        assertEquals(1, ((LuaValue) engine.execute(probeScript, context.withScript(probeScript)).raw()).toint());
        scheduler.tick();
        assertEquals(2, ((LuaValue) engine.execute(probeScript, context.withScript(probeScript)).raw()).toint());
    }

    @Test
    void entrypointScriptsCanRunServerAndClientHandlers() {
        MLuaScript main = script("mlua", ScriptType.ENTRYPOINT, "main.lua", "main", """
                local state = { server = 0, client = 0 }

                run_server = function(ctx)
                    state.server = state.server + 1
                    state.lastServerSide = ctx.Minecraft:GetSide()
                end

                run_client = function(ctx)
                    state.client = state.client + 1
                    state.lastClientSide = ctx.Minecraft:GetSide()
                end

                return state
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(main));
        MLuaRuntime runtime = new MLuaRuntime();
        MLuaContext serverContext = context(runtime, main, Side.SERVER);
        MLuaContext clientContext = context(runtime, main, Side.CLIENT);

        // First execute the script to get the state object and define entrypoints
        LuaValue state = (LuaValue) engine.execute(main, serverContext).raw();
        LuaValue clientState = (LuaValue) engine.execute(main, clientContext).raw();

        // Entrypoints share state within each side, while client and server have separate script environments.
        assertTrue(engine.executeEntrypoint(main, serverContext, "run_server"));
        assertTrue(engine.executeEntrypoint(main, clientContext, "run_client"));

        assertEquals(1, state.get("server").toint());
        assertEquals(1, clientState.get("client").toint());
        assertEquals("SERVER", state.get("lastServerSide").tojstring());
        assertEquals("CLIENT", clientState.get("lastClientSide").tojstring());
    }

    @Test
    void ifExpressionsWork() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "test.server.lua", "test", """
                local r1 = if true then 1 else 2 end
                local r2 = if false then 1 else 2 end
                local r3 = if true then 10 elseif true then 20 else 30 end
                local r4 = if false then 10 elseif true then 20 else 30 end
                local r5 = if false then 10 elseif false then 20 else 30 end
                local r6 = if 5 > 3 then "big" else "small" end
                return { r1, r2, r3, r4, r5, r6 }
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, context(test)).raw();
        assertEquals(1, result.get(1).toint());
        assertEquals(2, result.get(2).toint());
        assertEquals(10, result.get(3).toint());
        assertEquals(20, result.get(4).toint());
        assertEquals(30, result.get(5).toint());
        assertEquals("big", result.get(6).tojstring());
    }

    @Test
    void bit32FunctionsWork() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "test.server.lua", "test", """
                -- bit32.band, bor, bxor
                local r1 = bit32.band(0xFF, 0x0F)
                local r2 = bit32.bor(0xF0, 0x0F)
                local r3 = bit32.bxor(0xFF, 0x0F)
                local r4 = bit32.bnot(0xFFFFFFFF)
                -- bit32 shifts
                local r5 = bit32.lshift(1, 4)
                local r6 = bit32.rshift(16, 2)
                local r7 = bit32.arshift(-8, 2)
                -- bit32 rotate
                local r8 = bit32.lrotate(0x80000000, 1)
                -- bit32 extract / replace
                local r9 = bit32.extract(0x1234, 8, 8)
                local r10 = bit32.replace(0xFFFF, 0xAB, 8, 8)
                -- bit32 countlz / countrz
                local r11 = bit32.countlz(1)
                local r12 = bit32.countrz(8)
                -- btest
                local r13 = bit32.btest(0xFF, 0x01)
                local r14 = bit32.btest(0xFF, 0x100)
                -- table.pack
                local p = table.pack("a", "b", "c")
                local r15 = p.n
                local r16 = p[3]
                -- table.move
                local src = {"x", "y", "z"}
                local dst = {}
                table.move(src, 1, 3, 1, dst)
                local r17 = dst[2]
                return { r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, r12, r13, r14, r15, r16, r17 }
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, context(test)).raw();
        assertEquals(0x0F, result.get(1).toint());       // band
        assertEquals(0xFF, result.get(2).toint());       // bor
        assertEquals(0xF0, result.get(3).toint());       // bxor
        assertEquals(0, result.get(4).toint());          // bnot(-1) = 0
        assertEquals(16, result.get(5).toint());         // lshift(1,4) = 16
        assertEquals(4, result.get(6).toint());          // rshift(16,2) = 4
        assertTrue(result.get(7).toint() < 0);           // arshift(-8,2) = -2 (sign-extended)
        assertEquals(1, result.get(8).toint());          // lrotate(0x80000000,1) = 1
        assertEquals(0x12, result.get(9).toint());       // extract(0x1234,8,8) = 0x12
        assertEquals(0xABFF, result.get(10).toint());    // replace(0xFFFF,0xAB,8) = 0xABFF
        assertEquals(31, result.get(11).toint());        // countlz(1) = 31
        assertEquals(3, result.get(12).toint());         // countrz(8) = 3
        assertEquals(true, result.get(13).toboolean());  // btest(0xFF,0x01) = true
        assertEquals(false, result.get(14).toboolean()); // btest(0xFF,0x100) = false
        assertEquals(3, result.get(15).toint());         // pack.n = 3
        assertEquals("c", result.get(16).tojstring());   // pack[3] = "c"
        assertEquals("y", result.get(17).tojstring());   // move dst[2] = "y"
    }

    @Test
    void javaBridgeSupportsIntrospectionConversionAndOverloads() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "java_bridge.server.lua", "java_bridge", """
                local Java = Minecraft:GetService("Java")
                local Fixture = Java:LoadClass("dev.mlua.core.MLuaRuntimeIntegrationTest$JavaBridgeFixture")
                local EnumType = Java:LoadClass("dev.mlua.core.MLuaRuntimeIntegrationTest$JavaBridgeEnum")

                local instance = Fixture:new("alpha")
                local methods = Java:GetMethods(Fixture)
                local fields = Java:GetFields(Fixture)
                local enumValues = Java:GetEnumConstants(EnumType)
                local exactStaticDescriptor
                local exactInstanceDescriptor
                for i = 1, #methods do
                    local method = methods[i]
                    if method.Name == "choose" and method.Static and method.Parameters[1].Type == "java.lang.String" then
                        exactStaticDescriptor = method.JvmDescriptor
                    elseif method.Name == "pick" and not method.Static and method.Parameters[1].Type == "java.lang.Double" then
                        exactInstanceDescriptor = method.JvmDescriptor
                    end
                end

                local list = Java:New("java.util.ArrayList")
                list:add("one")
                list:add("two")
                local secondBefore = list[2]
                list[2] = "changed"
                local secondAfter = list[2]
                local copied = Java:ToTable(list)

                local map = Java:New("java.util.HashMap")
                map["name"] = "EncData"
                map["level"] = 10
                map["nullable"] = Java.Null
                local copiedMap = Java:ToTable(map)
                local nullList = Java:New("java.util.ArrayList")
                nullList:add(Java.Null)
                nullList:add("present")
                local copiedNullList = Java:ToTable(nullList)
                local restoredNullList = Java:FromTable(
                    copiedNullList, "java.util.ArrayList", Java:LoadClass("java.lang.Object"))
                local copiedRestoredNullList = Java:ToTable(restoredNullList)
                local copiedNullableArray = Java:ToTable(instance:makeNullableArray())
                local restoredNullableArray = Java:FromTable(copiedNullableArray, "java.lang.Object[]")

                local array = instance:makeArray()
                local arrayBefore = array[2]
                array[2] = "delta"
                local arrayAfter = array[2]

                return {
                    Java:TypeOf(instance),
                    Fixture:choose("hello"),
                    instance:pick("hello"),
                    instance:pick(5),
                    instance.ctorUsed,
                    Java:IsInstance(instance, Fixture),
                    enumValues[1]:name(),
                    secondBefore,
                    secondAfter,
                    #list,
                    copied[2],
                    map["name"],
                    map["level"],
                    arrayBefore,
                    arrayAfter,
                    #array,
                    methods[1].Name ~= nil,
                    fields[1].Name ~= nil,
                    Java:Invoke("dev.mlua.core.MLuaRuntimeIntegrationTest$JavaBridgeFixture", "choose", exactStaticDescriptor, "hello"),
                    Java:Invoke(instance, "pick", exactInstanceDescriptor, 5),
                    exactStaticDescriptor ~= nil and exactInstanceDescriptor ~= nil,
                    instance:join("expanded", "one", "two"),
                    instance:join("packed", { "one", "two" }),
                    instance:summarize({ "red", "blue" }),
                    Java:Invoke(instance, "join", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/String;",
                        "invoke-expanded", "one", "two"),
                    Java:Invoke(instance, "join", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/String;",
                        "invoke-packed", { "one", "two" }),
                    copiedMap.nullable == Java.Null,
                    copiedNullList[1] == Java.Null,
                    copiedNullList[2],
                    copiedRestoredNullList[1] == Java.Null,
                    copiedRestoredNullList[2],
                    #copiedNullableArray,
                    copiedNullableArray[1],
                    copiedNullableArray[2] == Java.Null,
                    restoredNullableArray[2] == Java.Null
                }
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, contextWithJava(test)).raw();

        assertEquals("dev.mlua.core.MLuaRuntimeIntegrationTest$JavaBridgeFixture", result.get(1).tojstring());
        assertEquals("string", result.get(2).tojstring());
        assertEquals("string", result.get(3).tojstring());
        assertEquals("number", result.get(4).tojstring());
        assertEquals("string", result.get(5).tojstring());
        assertTrue(result.get(6).toboolean());
        assertEquals("FIRST", result.get(7).tojstring());
        assertEquals("two", result.get(8).tojstring());
        assertEquals("changed", result.get(9).tojstring());
        assertEquals(2, result.get(10).toint());
        assertEquals("changed", result.get(11).tojstring());
        assertEquals("EncData", result.get(12).tojstring());
        assertEquals(10, result.get(13).toint());
        assertEquals("beta", result.get(14).tojstring());
        assertEquals("delta", result.get(15).tojstring());
        assertEquals(3, result.get(16).toint());
        assertTrue(result.get(17).toboolean());
        assertTrue(result.get(18).toboolean());
        assertEquals("string", result.get(19).tojstring());
        assertEquals("number", result.get(20).tojstring());
        assertTrue(result.get(21).toboolean());
        assertEquals("expanded:one,two", result.get(22).tojstring());
        assertEquals("packed:one,two", result.get(23).tojstring());
        assertEquals("2:red,blue", result.get(24).tojstring());
        assertEquals("invoke-expanded:one,two", result.get(25).tojstring());
        assertEquals("invoke-packed:one,two", result.get(26).tojstring());
        assertTrue(result.get(27).toboolean());
        assertTrue(result.get(28).toboolean());
        assertEquals("present", result.get(29).tojstring());
        assertTrue(result.get(30).toboolean());
        assertEquals("present", result.get(31).tojstring());
        assertEquals(2, result.get(32).toint());
        assertEquals("array-value", result.get(33).tojstring());
        assertTrue(result.get(34).toboolean());
        assertTrue(result.get(35).toboolean());
    }

    @Test
    void javaBridgeResolvesSourceAndJvmArrayClassNames() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "java_array_class_names.server.lua", "java_array_class_names", """
                local Java = Minecraft:GetService("Java")
                local primitiveArray = Java:TypeOf(Java:LoadClass("int[]"))
                local primitiveMatrix = Java:TypeOf(Java:LoadClass("int[][]"))
                local primitiveJvmArray = Java:TypeOf(Java:LoadClass("[I"))
                local objectJvmMatrix = Java:TypeOf(Java:LoadClass("[[Ljava.lang.String;"))
                local sourceObjectArray = Java:TypeOf(Java:LoadClass("java.lang.String[]"))
                local voidClass = Java:TypeOf(Java:LoadClass("void"))
                local invalidVoidArray = pcall(function() Java:LoadClass("void[]") end)
                local invalidJvmVoidArray = pcall(function() Java:LoadClass("[V") end)
                return { primitiveArray, primitiveMatrix, primitiveJvmArray, objectJvmMatrix,
                    sourceObjectArray, voidClass, invalidVoidArray, invalidJvmVoidArray }
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, contextWithJava(test)).raw();

        assertEquals("[I", result.get(1).tojstring());
        assertEquals("[[I", result.get(2).tojstring());
        assertEquals("[I", result.get(3).tojstring());
        assertEquals("[[Ljava.lang.String;", result.get(4).tojstring());
        assertEquals("[Ljava.lang.String;", result.get(5).tojstring());
        assertEquals("void", result.get(6).tojstring());
        assertFalse(result.get(7).toboolean());
        assertFalse(result.get(8).toboolean());
    }

    @Test
    void javaBridgeReportsUsefulOverloadErrors() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "java_bridge_error.server.lua", "java_bridge_error", """
                local Java = Minecraft:GetService("Java")
                local Fixture = Java:LoadClass("dev.mlua.core.MLuaRuntimeIntegrationTest$JavaBridgeFixture")
                local instance = Fixture:new("alpha")
                return instance:pick(true)
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaError error = assertThrows(LuaError.class, () -> engine.execute(test, contextWithJava(test)));
        assertTrue(error.getMessage().contains("No matching instance method"));
        assertTrue(error.getMessage().contains("Candidates"));
    }

    @Test
    void javaBridgeReportsAmbiguousNilOverloadsInsteadOfChoosingOne() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "java_bridge_nil_ambiguity.server.lua", "java_bridge_nil_ambiguity", """
                local Java = Minecraft:GetService("Java")
                local Fixture = Java:LoadClass("dev.mlua.core.MLuaRuntimeIntegrationTest$JavaBridgeFixture")
                local instance = Fixture:new("alpha")
                local ok, message = pcall(function() return instance:pick(nil) end)
                local exactInstance = Java:Invoke(
                    instance, "pick", "(Ljava/lang/String;)Ljava/lang/String;", nil)
                local exactStatic = Java:Invoke(
                    Fixture, "choose", "(Ljava/lang/String;)Ljava/lang/String;", nil)
                local exactConstructor = Java:NewExact(
                    Fixture, "(Ljava/lang/String;)V", nil)
                return { ok, message, exactInstance, exactStatic, exactConstructor.ctorUsed,
                    exactConstructor.label == nil }
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, contextWithJava(test)).raw();

        assertFalse(result.get(1).toboolean());
        assertTrue(result.get(2).tojstring().contains("Ambiguous Java call"));
        assertTrue(result.get(2).tojstring().contains("java.lang.String"));
        assertTrue(result.get(2).tojstring().contains("java.lang.Double"));
        assertEquals("string", result.get(3).tojstring());
        assertEquals("string", result.get(4).tojstring());
        assertEquals("string", result.get(5).tojstring());
        assertTrue(result.get(6).toboolean());
    }

    @Test
    void registeredJavaLuaAdaptersParticipateInReflectiveArgumentsAndResults() {
        try (var registration = MLuaLuaCoercions.registerAdapter(new MLuaLuaCoercions.Adapter<JavaBridgeAdaptedValue>() {
            @Override public Class<JavaBridgeAdaptedValue> type() { return JavaBridgeAdaptedValue.class; }
            @Override public JavaBridgeAdaptedValue fromLua(LuaValue value) {
                if (value.isstring()) return new JavaBridgeAdaptedValue(value.checkjstring());
                return new JavaBridgeAdaptedValue(value.checktable().get("Value").checkjstring());
            }
            @Override public LuaValue toLua(JavaBridgeAdaptedValue value) {
                return LuaValue.valueOf("adapted:" + value.value());
            }
        })) {
            MLuaScript test = script("mlua", ScriptType.SERVER, "java_bridge_adapter.server.lua",
                "java_bridge_adapter", """
                    local Java = Minecraft:GetService("Java")
                    local Fixture = Java:LoadClass("dev.mlua.core.MLuaRuntimeIntegrationTest$JavaBridgeFixture")
                    local instance = Fixture:new("alpha")
                    local scalarRoundTrip = instance:echoAdaptedValue("round trip")
                    local tableRoundTrip = instance:echoAdaptedValue({ Value = "table" })
                    local nestedValues = Java:ToTable(instance:adaptedValues())
                    return { scalarRoundTrip, tableRoundTrip, nestedValues[1] }
                    """);

            LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
            LuaValue result = (LuaValue) engine.execute(test, contextWithJava(test)).raw();
            assertEquals("adapted:round trip", result.get(1).tojstring());
            assertEquals("adapted:table", result.get(2).tojstring());
            assertEquals("adapted:nested", result.get(3).tojstring());
        }
    }

    @Test
    void javaBridgeRejectsMapKeysThatCollapseDuringTypedConversion() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "java_map_key_collision.server.lua", "java_map_key_collision", """
                local Java = Minecraft:GetService("Java")
                local EnumType = Java:LoadClass("dev.mlua.core.MLuaRuntimeIntegrationTest$JavaBridgeEnum")
                local enumValue = Java:GetEnumConstants(EnumType)[1]
                local entries = { ["FIRST"] = "lua key", [enumValue] = "Java enum key" }
                local ok, message = pcall(function()
                    return Java:FromTable(entries, "java.util.Map", EnumType, "java.lang.String")
                end)
                return { ok, message }
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, contextWithJava(test)).raw();

        assertFalse(result.get(1).toboolean());
        assertTrue(result.get(2).tojstring().contains("same key more than once"));
    }

    @Test
    void javaBridgeDotCallSyntaxWithTable() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "dot_table.server.lua", "dot_table", """
                local Java = Minecraft:GetService("Java")
                local list = Java:New("java.util.ArrayList")
                list:add("one")
                list:add("two")
                local copied = Java.ToTable(list)
                return { #copied, copied[1], copied[2] }
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, contextWithJava(test)).raw();

        assertEquals(2, result.get(1).toint());
        assertEquals("one", result.get(2).tojstring());
        assertEquals("two", result.get(3).tojstring());
    }

    @Test
    void javaBridgeDotCallWithStringArg() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "dot_string.server.lua", "dot_string", """
                local Java = Minecraft:GetService("Java")
                local ArrayList = Java.LoadClass("java.util.ArrayList")
                local list = ArrayList:new()
                list:add("dot-call")
                return list[1]
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, contextWithJava(test)).raw();

        assertEquals("dot-call", result.tojstring());
    }

    @Test
    void javaBridgeDotCallWithNumberArg() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "dot_number.server.lua", "dot_number", """
                local Java = Minecraft:GetService("Java")
                local ArrayList = Java:LoadClass("java.util.ArrayList")
                local first = Java:New(ArrayList, 5)
                local second = Java.New(ArrayList, 5)
                first:add("colon")
                second:add("dot")
                return { first:size(), second:size(), first:get(0), second:get(0) }
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, contextWithJava(test)).raw();

        assertEquals(1, result.get(1).toint());
        assertEquals(1, result.get(2).toint());
        assertEquals("colon", result.get(3).tojstring());
        assertEquals("dot", result.get(4).tojstring());
    }

    @Test
    void javaBridgeDotCallWithTwoTableArgs() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "dot_two_table.server.lua", "dot_two_table", """
                local Java = Minecraft:GetService("Java")
                local Fixture = Java:LoadClass("dev.mlua.core.MLuaRuntimeIntegrationTest$JavaBridgeFixture")
                local instance = Fixture:new("test")
                local r1 = Java.IsInstance(instance, Fixture)
                local r2 = Java.IsInstance(instance, Java:LoadClass("java.lang.Object"))
                return { r1, r2 }
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, contextWithJava(test)).raw();

        assertTrue(result.get(1).toboolean());
        assertTrue(result.get(2).toboolean());
    }

    @Test
    void javaBridgeNilArguments() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "nil_args.server.lua", "nil_args", """
                local Java = Minecraft:GetService("Java")
                local Fixture = Java:LoadClass("dev.mlua.core.MLuaRuntimeIntegrationTest$JavaBridgeFixture")
                local instance = Fixture:new("test")
                local r1 = instance:acceptObject(nil)
                local r2 = instance:acceptObject("hello")
                return { r1, r2 }
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, contextWithJava(test)).raw();

        assertEquals("null", result.get(1).tojstring());
        assertEquals("string:hello", result.get(2).tojstring());
    }

    @Test
    void javaBridgeDistinguishesNullVarargsArrayFromOneNullElement() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "java_bridge_null_varargs.server.lua",
            "java_bridge_null_varargs", """
                local Java = Minecraft:GetService("Java")
                local Fixture = Java:LoadClass("dev.mlua.core.MLuaRuntimeIntegrationTest$JavaBridgeFixture")
                local instance = Fixture:new("varargs")
                local emptyArray = Java:Invoke(instance, "nullableVarargs", "([Ljava/lang/String;)Ljava/lang/String;")
                local nullArray = Java:Invoke(instance, "nullableVarargs", "([Ljava/lang/String;)Ljava/lang/String;", nil)
                local oneNullElement = Java:Invoke(instance, "nullableVarargs", "([Ljava/lang/String;)Ljava/lang/String;",
                    { Java.Null })
                return { emptyArray, nullArray, oneNullElement }
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, contextWithJava(test)).raw();

        assertEquals("0", result.get(1).tojstring());
        assertEquals("null-array", result.get(2).tojstring());
        assertEquals("1:null-element", result.get(3).tojstring());
    }

    @Test
    void javaBridgePreservesInexactBigNumbersAsJavaObjects() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "java_bridge_exact_numbers.server.lua",
            "java_bridge_exact_numbers", """
                local Java = Minecraft:GetService("Java")
                local Fixture = Java:LoadClass("dev.mlua.core.MLuaRuntimeIntegrationTest$JavaBridgeFixture")
                local instance = Fixture:new("numbers")
                local integer = instance:largeInteger()
                local decimal = instance:preciseDecimal()
                return {
                    Java:TypeOf(integer),
                    Java:Invoke(integer, "toString", "()Ljava/lang/String;"),
                    Java:TypeOf(decimal),
                    Java:Invoke(decimal, "toPlainString", "()Ljava/lang/String;")
                }
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, contextWithJava(test)).raw();

        assertEquals("java.math.BigInteger", result.get(1).tojstring());
        assertEquals("9007199254740993", result.get(2).tojstring());
        assertEquals("java.math.BigDecimal", result.get(3).tojstring());
        assertEquals("0.100000000000000000000000000000000000001", result.get(4).tojstring());
    }

    @Test
    void javaBridgeProxyTest() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "proxy_test.server.lua", "proxy_test", """
                local Java = Minecraft:GetService("Java")
                local Runnable = Java:LoadClass("java.lang.Runnable")
                local ran = false
                local proxy = Java:Proxy(Runnable, { run = function() ran = true end })
                proxy:run()
                return ran
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, contextWithJava(test)).raw();

        assertTrue(result.toboolean());
    }

    @Test
    void javaBridgeProxyDispatchesOverloadsByJvmDescriptor() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "proxy_overloads.server.lua", "proxy_overloads", """
                local Java = Minecraft:GetService("Java")
                local Interface = Java:LoadClass("dev.mlua.core.MLuaRuntimeIntegrationTest$JavaBridgeOverloaded")
                local proxy = Java:Proxy(Interface, {
                    ["format(Ljava/lang/String;)Ljava/lang/String;"] = function(value)
                        return "string:" .. value
                    end,
                    ["format(Ljava/lang/Integer;)Ljava/lang/String;"] = function(value)
                        return "integer:" .. value
                    end,
                })
                return {
                    proxy:format("hello"),
                    proxy:format(7),
                    proxy:formatInBrackets("default"),
                    proxy:equals(proxy),
                    proxy:hashCode() == proxy:hashCode(),
                    proxy:toString()
                }
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, contextWithJava(test)).raw();

        assertEquals("string:hello", result.get(1).tojstring());
        assertEquals("integer:7", result.get(2).tojstring());
        assertEquals("[string:default]", result.get(3).tojstring());
        assertTrue(result.get(4).toboolean());
        assertTrue(result.get(5).toboolean());
        assertEquals("LuaProxy(" + JavaBridgeOverloaded.class.getName() + ")", result.get(6).tojstring());
    }

    @Test
    void javaBridgeProxyCleanupInvalidatesLuaCallbacksButPreservesObjectIdentityMethods() {
        MLuaScript createProxy = script("mlua", ScriptType.SERVER, "proxy_lifecycle.server.lua", "proxy_lifecycle", """
                local Java = Minecraft:GetService("Java")
                proxy = Java:Proxy(
                    Java:LoadClass("dev.mlua.core.MLuaRuntimeIntegrationTest$JavaBridgeOverloaded"),
                    {
                        ["format(Ljava/lang/String;)Ljava/lang/String;"] = function(value) return "ok:" .. value end,
                        ["format(Ljava/lang/Integer;)Ljava/lang/String;"] = function(value) return "number:" .. value end,
                    })
                return proxy:format("before")
                """);
        MLuaScript useProxy = script("mlua", ScriptType.SERVER, "proxy_lifecycle_check.server.lua", "proxy_lifecycle_check", """
                local called, message = pcall(function() return proxy:format("after") end)
                return { called, tostring(message), proxy:toString(), proxy:equals(proxy) }
                """);
        MLuaRuntime runtime = new MLuaRuntime();
        runtime.registerClass("Object", null, "Root class for test fixtures.");
        runtime.registerInstanceBaseClass("Instance", "Object", "Root instance class for test fixtures.");
        runtime.registerServiceBaseClass("Service", "Instance", "Root service class for test fixtures.");
        MLuaModule.ServiceEntry javaService = new JavaModule().services().get(0);
        runtime.registerService("Java", Side.BOTH, javaService.factory(), javaService.description(),
            javaService.functionDescriptions());
        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(createProxy, useProxy));

        assertEquals("ok:before", ((LuaValue)engine.execute(createProxy,
            context(runtime, createProxy, Side.SERVER)).raw()).tojstring());
        new JavaModule().clearOwnedState(runtime, runtime.session(), Side.SERVER, "mlua");

        LuaValue result = (LuaValue)engine.execute(useProxy, context(runtime, useProxy, Side.SERVER)).raw();
        assertFalse(result.get(1).toboolean());
        assertTrue(result.get(2).tojstring().contains("invalidated when its script runtime unloaded"));
        assertEquals("LuaProxy(" + JavaBridgeOverloaded.class.getName() + ")", result.get(3).tojstring());
        assertTrue(result.get(4).toboolean());
    }

    @Test
    void javaBridgeCastTest() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "cast_test.server.lua", "cast_test", """
                local Java = Minecraft:GetService("Java")
                local String = Java:LoadClass("java.lang.String")
                local ok1, v1 = pcall(function() return Java:Cast("hello", String) end)
                local obj = Java:New("java.lang.Object")
                local ok2, v2 = pcall(function() return Java:Cast(obj, String) end)
                return { ok1, v1, ok2, tostring(v2) }
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, contextWithJava(test)).raw();

        assertTrue(result.get(1).toboolean());
        assertEquals("hello", result.get(2).tojstring());
        assertEquals(false, result.get(3).toboolean());
        assertTrue(result.get(4).tojstring().contains("Cannot cast"));
    }

    @Test
    void javaBridgePackageTest() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "package_test.server.lua", "package_test", """
                local Java = Minecraft:GetService("Java")
                local util = Java:Package("java.util")
                local list = util.ArrayList:new()
                list:add("pkg-test")
                return list[1]
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaValue result = (LuaValue) engine.execute(test, contextWithJava(test)).raw();

        assertEquals("pkg-test", result.tojstring());
    }

    @Test
    void javaBridgeNonExistentClass() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "missing_class.server.lua", "missing_class", """
                local Java = Minecraft:GetService("Java")
                return Java:LoadClass("does.not.Exist")
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaError error = assertThrows(LuaError.class, () -> engine.execute(test, contextWithJava(test)));
        assertTrue(error.getMessage().contains("Could not resolve Java class"));
    }

    @Test
    void javaBridgeWrongConstructorArgs() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "ctor_error.server.lua", "ctor_error", """
                local Java = Minecraft:GetService("Java")
                local Fixture = Java:LoadClass("dev.mlua.core.MLuaRuntimeIntegrationTest$JavaBridgeFixture")
                return Fixture:new()
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaError error = assertThrows(LuaError.class, () -> engine.execute(test, contextWithJava(test)));
        assertTrue(error.getMessage().contains("No matching public constructor"));
    }

    @Test
    void javaBridgeGetEnumConstantsOnNonEnum() {
        MLuaScript test = script("mlua", ScriptType.SERVER, "enum_error.server.lua", "enum_error", """
                local Java = Minecraft:GetService("Java")
                local Fixture = Java:LoadClass("dev.mlua.core.MLuaRuntimeIntegrationTest$JavaBridgeFixture")
                return Java:GetEnumConstants(Fixture)
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(test));
        LuaError error = assertThrows(LuaError.class, () -> engine.execute(test, contextWithJava(test)));
        assertTrue(error.getMessage().contains("is not an enum type"));
    }

    @Test
    void schedulerDeferExecutesAfterOneTick() {
        MLuaScript main = script("mlua", ScriptType.SERVER, "main.server.lua", "main", """
                local value = 0
                task.defer(function()
                    value = 42
                end)
                return function()
                    return value
                end
                """);
        MLuaTaskScheduler scheduler = new MLuaTaskScheduler();
        MLuaContext context = context(main, scheduler);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(main));
        LuaValue probe = (LuaValue) engine.execute(main, context).raw();

        assertEquals(0, probe.call().toint());
        scheduler.tick();
        assertEquals(42, probe.call().toint());
    }

    @Test
    void circularRequireDetected() {
        MLuaScript main = script("mlua", ScriptType.SERVER, "main.server.lua", "main", """
                return Require("shared.A")
                """);
        MLuaScript moduleA = script("mlua", ScriptType.MODULE, "shared/A.lua", "shared.A", """
                return Require("shared.B")
                """);
        MLuaScript moduleB = script("mlua", ScriptType.MODULE, "shared/B.lua", "shared.B", """
                return Require("shared.A")
                """);

        LuaJEngineBackend engine = new LuaJEngineBackend(List.of(main, moduleA, moduleB));
        RuntimeException error = assertThrows(RuntimeException.class, () -> engine.execute(main, context(main)));
        assertTrue(error.getMessage().contains("Circular Require detected"));
    }

    private MLuaContext context(MLuaScript script) {
        return context(script, new MLuaTaskScheduler());
    }

    private MLuaContext context(MLuaScript script, MLuaTaskScheduler scheduler) {
        return new MLuaContext(
                script,
                Side.SERVER,
                RegistrationPhase.RUNTIME,
                new MLuaServiceRegistry(),
                scheduler,
                new MLuaModuleCache(),
                null,
                temp.resolve("data")
        );
    }

    private MLuaContext contextWithJava(MLuaScript script) {
        MLuaRuntime runtime = new MLuaRuntime();
        runtime.registerClass("Object", null, "Root class for test fixtures.");
        runtime.registerInstanceBaseClass("Instance", "Object", "Base class for live test instances.");
        runtime.registerServiceBaseClass("Service", "Instance", "Base class for test services.");
        MLuaModule.ServiceEntry javaService = new JavaModule().services().get(0);
        runtime.registerService("Java", Side.BOTH, javaService.factory(), javaService.description(),
            javaService.functionDescriptions());
        return context(runtime, script, Side.SERVER);
    }

    private MLuaScript script(String owner, ScriptType type, String relativePath, String moduleId, String source) {
        return new MLuaScript(owner, type, temp, temp.resolve(relativePath), moduleId, source);
    }

    public enum JavaBridgeEnum {
        FIRST,
        SECOND
    }

    public interface JavaBridgeOverloaded {
        String format(String value);
        String format(Integer value);

        default String formatInBrackets(String value) {
            return "[" + format(value) + "]";
        }
    }

    private static final class BaseProbeService implements MLuaService, LuaBackedService {
        private BaseProbeService(MLuaService.Context context) {
        }

        @Override public String name() { return "BaseProbe"; }

        @Override public LuaTable toLua() {
            LuaTable table = new LuaTable();
            table.set("Value", "registered");
            table.set("GetValue", new org.luaj.vm2.lib.VarArgFunction() {
                @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                    return LuaValue.valueOf("registered");
                }
            });
            return table;
        }
    }

    private record NamedService(String value) implements MLuaService {
        @Override public String name() { return "SideService"; }
        @Override public LuaTable toLua() {
            LuaTable table = new LuaTable();
            table.set("Value", value);
            table.set("Probe", new org.luaj.vm2.lib.VarArgFunction() {
                @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                    return LuaValue.valueOf(value);
                }
            });
            return table;
        }
    }

    public static final class JavaBridgeFixture {
        public final String ctorUsed;
        public String label = "fixture";

        public JavaBridgeFixture(String value) {
            this.ctorUsed = "string";
            this.label = value;
        }

        public JavaBridgeFixture(Object value) {
            this.ctorUsed = "object";
            this.label = String.valueOf(value);
        }

        public static String choose(String value) {
            return "string";
        }

        public static String choose(Object value) {
            return "object";
        }

        public String pick(String value) {
            return "string";
        }

        public String pick(Double value) {
            return "number";
        }

        public List<String> values() {
            ArrayList<String> values = new ArrayList<>();
            values.add("one");
            values.add("two");
            return values;
        }

        public java.math.BigInteger largeInteger() {
            return new java.math.BigInteger("9007199254740993");
        }

        public java.math.BigDecimal preciseDecimal() {
            return new java.math.BigDecimal("0.100000000000000000000000000000000000001");
        }

        public JavaBridgeAdaptedValue echoAdaptedValue(JavaBridgeAdaptedValue value) {
            return value;
        }

        public List<JavaBridgeAdaptedValue> adaptedValues() {
            return List.of(new JavaBridgeAdaptedValue("nested"));
        }

        public String[] makeArray() {
            return new String[]{"alpha", "beta", "gamma"};
        }

        public Object[] makeNullableArray() {
            return new Object[]{"array-value", null};
        }

        public String join(String prefix, String... values) {
            return prefix + ":" + String.join(",", values);
        }

        public String summarize(String[] values) {
            return values.length + ":" + String.join(",", values);
        }

        public String nullableVarargs(String... values) {
            if (values == null) return "null-array";
            if (values.length == 0) return "0";
            return values.length + (values[0] == null ? ":null-element" : ":value");
        }

        public String acceptObject(Object value) {
            if (value == null) return "null";
            if (value instanceof String s) return "string:" + s;
            return "obj:" + value.getClass().getSimpleName();
        }
    }

    public static final class JavaBridgeAdaptedValue {
        private final String value;
        public JavaBridgeAdaptedValue(String value) { this.value = value; }
        public String value() { return value; }
    }
}
