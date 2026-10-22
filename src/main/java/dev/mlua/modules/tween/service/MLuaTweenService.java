package dev.mlua.modules.tween.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.module.MLuaRegistration;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

@MLuaDescription("Animates writable Lua instance properties on client render frames with typed interpolation and easing.")
public final class MLuaTweenService extends MLuaReflectiveService {
    private static final int MAX_PROPERTIES_PER_TWEEN = MLuaTweenProperties.MAX_PROPERTIES_PER_TWEEN;
    public static final int MAX_ACTIVE_TWEENS = MLuaTweenRegistry.MAX_ACTIVE_TWEENS;
    public static final int MAX_ACTIVE_TWEENS_PER_RUNTIME = MLuaTweenRegistry.MAX_ACTIVE_TWEENS_PER_RUNTIME;

    public MLuaTweenService(Context context) { super(context); }

    @Override public String name() { return "TweenService"; }

    @Override public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Client");
        return table;
    }

    @MLuaDescription("Creates a tween for an MLua instance or service. Goal keys are property names with matching Get<Property> and Set<Property> methods; values may be numbers, Vector2, Vector3, Color3, or CFrame. Play captures and validates the target's current values, then updates it once per render frame.")
    public LuaTable Create(LuaValue targetValue, LuaTable info, LuaTable goals) {
        this.requireClientSide(this.name());
        if (goals == null) throw new LuaError("[MLua] TweenService:Create expects a goals table.");
        MLuaTweenInfo tweenInfo = MLuaTweenInfo.fromTable(info);
        if (targetValue == null || !targetValue.istable()) {
            throw new LuaError("[MLua] TweenService:Create expects an MLua instance or service table.");
        }
        LuaTable target = targetValue.checktable();
        MLuaRegistration registration = context.mlua().registration();
        List<MLuaTween.Property> properties = MLuaTweenProperties.resolve(targetValue, target, goals, registration);

        MLuaContext owner = MLuaLuaBindings.boundContext(context.mlua().globals());
        if (owner == null) throw new LuaError("[MLua] TweenService:Create must be called while a Lua addon is executing.");
        MLuaTween tween = new MLuaTween(target, properties, owner, tweenInfo.duration(),
            tweenInfo.easingStyle(), tweenInfo.easingDirection());
        return tweenHandle(tween, registration);
    }

    @MLuaDescription("Returns the number of active tweens owned by the calling addon in the current runtime.")
    public int GetActiveCount() {
        this.requireClientSide(this.name());
        MLuaContext activeContext = MLuaLuaBindings.boundContext(context.mlua().globals());
        if (activeContext == null) return 0;
        return MLuaTweenRegistry.countOwned(activeContext.session(), activeContext.ownerModId());
    }

    @MLuaDescription("Returns active tween usage and capacity limits.")
    public LuaTable GetLimits() {
        this.requireClientSide(this.name());
        LuaTable limits = new LuaTable();
        limits.set("Active", MLuaTweenRegistry.activeCount());
        limits.set("MaxActive", MAX_ACTIVE_TWEENS);
        limits.set("MaxPerRuntime", MAX_ACTIVE_TWEENS_PER_RUNTIME);
        limits.set("MaxPropertiesPerTween", MAX_PROPERTIES_PER_TWEEN);
        return limits;
    }

    private static LuaTable tweenHandle(MLuaTween tween, MLuaRegistration registration) {
        LuaTable handle = new LuaTable();
        handle.set("Play", method(handle, ignored -> { tween.play(); return LuaValue.NIL; }));
        handle.set("Pause", method(handle, ignored -> { tween.pause(); return LuaValue.NIL; }));
        handle.set("Resume", method(handle, ignored -> { tween.resume(); return LuaValue.NIL; }));
        handle.set("Cancel", method(handle, ignored -> { tween.cancel(); return LuaValue.NIL; }));
        handle.set("GetPlaybackState", method(handle, ignored -> LuaValue.valueOf(tween.playbackState())));
        handle.set("GetError", method(handle, ignored -> tween.error() == null ? LuaValue.NIL : LuaValue.valueOf(tween.error())));
        handle.set("GetCompleted", method(handle, ignored -> handle.rawget("__tween_completed")));
        handle.set("Destroy", method(handle, ignored -> { tween.dispose(); return LuaValue.NIL; }));
        handle.rawset("__tween_completed", tween.completedSignal());
        return registration.attachClassInstance(handle, "Tween", null);
    }

    @FunctionalInterface private interface Call { LuaValue run(Varargs args); }

    private static VarArgFunction method(LuaTable self, Call call) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return call.run(args.arg1() == self ? args.subargs(2) : args);
            }
        };
    }

    static void activate(MLuaTween tween) { MLuaTweenRegistry.activate(tween); }
    static void deactivate(MLuaTween tween) { MLuaTweenRegistry.deactivate(tween); }

    public static void onRenderFrame(Object runtimeSession) {
        MLuaTweenRegistry.onRenderFrame(runtimeSession);
    }

    public static void clearRuntime(Object runtimeSession, Side side) {
        MLuaTweenRegistry.clearRuntime(runtimeSession, side);
    }

    public static void clearOwned(Object runtimeSession, String ownerModId, Side side) {
        MLuaTweenRegistry.clearOwned(runtimeSession, ownerModId, side);
    }
}
