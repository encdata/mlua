package dev.mlua.modules.tween.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.common.enums.Side;
import dev.mlua.module.MLuaRegistration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

@MLuaDescription("Animates writable Lua instance properties on client render frames with typed interpolation and easing.")
public final class MLuaTweenService extends MLuaReflectiveService {
    private static final int MAX_PROPERTIES_PER_TWEEN = 32;
    public static final int MAX_ACTIVE_TWEENS = 1_024;
    public static final int MAX_ACTIVE_TWEENS_PER_RUNTIME = 64;
    private static final Map<Object, ActiveTweens> ACTIVE = new ConcurrentHashMap<>();
    private static final Object ACTIVE_LOCK = new Object();
    private static int activeCount;

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
        if (info == null || goals == null) throw new LuaError("[MLua] TweenService:Create expects TweenInfo and a goals table.");
        if (!"TweenInfo".equals(info.get("__type").optjstring(""))) {
            throw new LuaError("[MLua] TweenService:Create expects a TweenInfo.new value.");
        }
        if (!targetValue.istable()) throw new LuaError("[MLua] TweenService:Create expects an MLua instance or service table.");
        LuaTable target = targetValue.checktable();
        MLuaRegistration registration = context.mlua().registration();
        boolean classInstance = registration.classNameOf(targetValue) != null;
        LuaValue metatable = target.getmetatable();
        boolean serviceInstance = metatable != null && metatable.istable()
            && metatable.checktable().rawget("__mlua_service_name").isstring();
        if (!classInstance && !serviceInstance) {
            throw new LuaError("[MLua] TweenService:Create only accepts registered MLua instances and services.");
        }

        double duration = info.get("Duration").checkdouble();
        if (!Double.isFinite(duration) || duration < 0.0 || duration > 86_400.0) {
            throw new LuaError("[MLua] TweenInfo.Duration must be finite and between 0 and 86400 seconds.");
        }
        String style = info.get("EasingStyle").optjstring("Linear");
        String direction = info.get("EasingDirection").optjstring("Out");
        if (!Set.of("Linear", "Sine", "Quad", "Cubic", "Quart", "Quint", "Expo", "Circ", "Back", "Elastic", "Bounce").contains(style)) {
            throw new LuaError("[MLua] Unknown TweenInfo.EasingStyle: " + style);
        }
        if (!Set.of("In", "Out", "InOut").contains(direction)) {
            throw new LuaError("[MLua] Unknown TweenInfo.EasingDirection: " + direction);
        }

        List<MLuaTween.Property> properties = new ArrayList<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = goals.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (!key.isstring() || key.tojstring().isBlank()) {
                throw new LuaError("[MLua] Tween goal keys must be property names.");
            }
            if (properties.size() >= MAX_PROPERTIES_PER_TWEEN) {
                throw new LuaError("[MLua] A tween can change at most " + MAX_PROPERTIES_PER_TWEEN + " properties.");
            }
            String property = key.tojstring();
            LuaValue getter = target.get("Get" + property);
            LuaValue setter = target.get("Set" + property);
            if (!getter.isfunction() || !setter.isfunction()) {
                throw new LuaError("[MLua] Tween target has no Get" + property + "/Set" + property + " property pair.");
            }
            LuaValue goal = entry.arg(2);
            if (goal.isnumber() && !Double.isFinite(goal.todouble())) {
                throw new LuaError("[MLua] Tween numeric goals must be finite.");
            }
            if (!goal.isnumber() && !dev.mlua.math.MLuaVector.isVector2(goal)
                    && !dev.mlua.math.MLuaVector.isVector3(goal)
                    && !dev.mlua.math.MLuaCFrame.isCFrame(goal)
                    && !dev.mlua.core.engine.MLuaLuauTypes.isColor3(goal)) {
                throw new LuaError("[MLua] Tween goals support numbers and native Vector2, Vector3, Color3, or CFrame values.");
            }
            properties.add(new MLuaTween.Property(property, getter, setter, goal));
        }
        if (properties.isEmpty()) throw new LuaError("[MLua] TweenService:Create needs at least one goal property.");

        MLuaContext owner = MLuaLuaBindings.boundContext(context.mlua().globals());
        if (owner == null) throw new LuaError("[MLua] TweenService:Create must be called while a Lua addon is executing.");
        MLuaTween tween = new MLuaTween(target, properties, owner, duration, style, direction);
        return tweenHandle(tween, registration);
    }

    @MLuaDescription("Returns the number of active tweens owned by the calling runtime.")
    public int GetActiveCount() {
        this.requireClientSide(this.name());
        MLuaContext activeContext = MLuaLuaBindings.boundContext(context.mlua().globals());
        if (activeContext == null) return 0;
        ActiveTweens active = ACTIVE.get(activeContext.session());
        if (active == null) return 0;
        int count = 0;
        for (MLuaTween tween : active.snapshot()) {
            if (tween.ownerModId().equals(activeContext.ownerModId())) count++;
        }
        return count;
    }

    @MLuaDescription("Returns active tween usage and capacity limits.")
    public LuaTable GetLimits() {
        this.requireClientSide(this.name());
        LuaTable limits = new LuaTable();
        synchronized (ACTIVE_LOCK) { limits.set("Active", activeCount); }
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
        handle.set("Completed", tween.completedSignal());
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

    static void activate(MLuaTween tween) {
        synchronized (ACTIVE_LOCK) {
            ActiveTweens active = ACTIVE.computeIfAbsent(tweenOwner(tween), ignored -> new ActiveTweens());
            if (active.contains(tween)) return;
            if (active.size() >= MAX_ACTIVE_TWEENS_PER_RUNTIME || activeCount >= MAX_ACTIVE_TWEENS) {
                throw new LuaError("[MLua] Tween capacity reached (" + MAX_ACTIVE_TWEENS_PER_RUNTIME
                    + " per runtime, " + MAX_ACTIVE_TWEENS + " total).");
            }
            active.add(tween);
            activeCount++;
        }
    }

    static void deactivate(MLuaTween tween) {
        synchronized (ACTIVE_LOCK) {
            Object owner = tweenOwner(tween);
            ActiveTweens active = ACTIVE.get(owner);
            if (active != null && active.remove(tween)) {
                activeCount--;
                if (active.isEmpty()) ACTIVE.remove(owner, active);
            }
        }
    }

    private static Object tweenOwner(MLuaTween tween) { return tween.ownerSession(); }

    public static void onRenderFrame(Object runtimeSession) {
        ActiveTweens active = ACTIVE.get(runtimeSession);
        if (active == null) return;
        long now = System.nanoTime();
        for (MLuaTween tween : active.snapshot()) {
            if (!tween.sample(now)) {
                deactivate(tween);
                tween.firePendingCompletion();
            }
        }
    }

    public static void clearRuntime(Object runtimeSession, Side side) {
        if (runtimeSession == null || (side != Side.CLIENT && side != Side.BOTH)) return;
        MLuaTween[] removed;
        synchronized (ACTIVE_LOCK) {
            ActiveTweens active = ACTIVE.remove(runtimeSession);
            if (active == null) return;
            removed = active.snapshot();
            activeCount -= removed.length;
        }
        for (MLuaTween tween : removed) tween.dispose();
    }

    public static void clearOwned(Object runtimeSession, String ownerModId, Side side) {
        if (side != Side.CLIENT && side != Side.BOTH) return;
        if (runtimeSession == null) return;
        ActiveTweens active = ACTIVE.get(runtimeSession);
        if (active == null) return;
        for (MLuaTween tween : active.snapshot()) {
            if (tween.ownerModId().equals(ownerModId)) tween.dispose();
        }
    }

    /**
     * The render loop reads an immutable snapshot without allocating. Membership changes
     * happen on play, completion, cancellation, or addon unload and publish a new snapshot.
     * All mutations are guarded by ACTIVE_LOCK.
     */
    private static final class ActiveTweens {
        private final Set<MLuaTween> members = new HashSet<>();
        private volatile MLuaTween[] snapshot = new MLuaTween[0];

        boolean contains(MLuaTween tween) { return members.contains(tween); }
        int size() { return members.size(); }
        boolean isEmpty() { return members.isEmpty(); }
        MLuaTween[] snapshot() { return snapshot; }

        boolean add(MLuaTween tween) {
            if (!members.add(tween)) return false;
            publish();
            return true;
        }

        boolean remove(MLuaTween tween) {
            if (!members.remove(tween)) return false;
            publish();
            return true;
        }

        private void publish() { snapshot = members.toArray(MLuaTween[]::new); }
    }
}
