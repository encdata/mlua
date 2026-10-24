package dev.mlua.modules.animation.service;

import java.util.List;
import java.util.Map;
import dev.mlua.api.MLuaDescription;
import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.service.MLuaService;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaAnimation;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.binding.MLuaAnimationBindings;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.modules.enums.MLuaEnumValue;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.runtime.MLuaGameThreadAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Client-side animation assets and per-entity pose controls.")
public final class MLuaAnimationService extends MLuaReflectiveService {
    public static final int MAX_REGISTERED_ANIMATIONS = MLuaAnimationCatalog.MAX_REGISTERED_ANIMATIONS;
    public static final int MAX_REGISTERED_ANIMATIONS_PER_ADDON = MLuaAnimationCatalog.MAX_REGISTERED_ANIMATIONS_PER_ADDON;
    private final MLuaAnimationCatalog registeredAnimations = this.sharedState(
        "definitions", MLuaAnimationCatalog.class, MLuaAnimationCatalog::new);

    public static void clearRuntimeState(Object runtimeSession, Side side) {
        MLuaAnimationCatalog catalog = catalog(runtimeSession, side);
        if (catalog != null) catalog.clear();
    }

    public static void clearOwnedState(Object runtimeSession, String owner, Side side) {
        if (owner == null || owner.isBlank()) return;
        MLuaAnimationCatalog catalog = catalog(runtimeSession, side);
        if (catalog != null) catalog.clearOwner(owner);
    }

    private static MLuaAnimationCatalog catalog(Object runtimeSession, Side side) {
        if (!(runtimeSession instanceof MLuaRuntimeSession session)) return null;
        Side runtimeSide = side == Side.BOTH ? Side.CLIENT : side;
        if (runtimeSide != Side.CLIENT) return null;
        return session.existingSharedServiceState(runtimeSide, "Animation", "definitions", MLuaAnimationCatalog.class);
    }

    public MLuaAnimationService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() { return "Animation"; }

    @Override
    public LuaTable toLua() {
        this.requireClientSide(this.name());
        LuaTable table = super.toLua();
        table.set("__side", "Client");
        return table;
    }

    @MLuaDescription("Creates an animation from an ordered keyframe table. A single keyframe at time zero is a static pose that remains active on a played track until stopped. Assets are limited to "
        + MLuaAnimation.MAX_KEYFRAMES + " keyframes, " + MLuaAnimation.MAX_BONES_PER_KEYFRAME
        + " bone poses per keyframe, and " + MLuaAnimation.MAX_POSE_KEYS + " total bone poses.")
    public LuaValue Create(LuaTable keyframes) {
        this.requireClientSide(this.name());
        MLuaAnimation animation = MLuaAnimation.fromLuaTable(keyframes);
        return MLuaAnimationBindings.wrapAnimation(animation, this.context.mlua().registration());
    }

    @MLuaDescription("Stores an addon-owned animation under a namespaced id. Unqualified ids use this addon's namespace; an addon cannot replace another owner's id. Definitions are removed when their owner unloads. The runtime holds up to " + MAX_REGISTERED_ANIMATIONS + " animations total, with at most " + MAX_REGISTERED_ANIMATIONS_PER_ADDON + " per addon.")
    public void RegisterAnimation(String id, LuaValue animationValue) {
        this.requireClientSide(this.name());
        String owner = this.context.mlua().ownerModId();
        id = animationId(id, owner, "AnimationService:RegisterAnimation", true);
        MLuaAnimation animation = MLuaAnimationBindings.resolveAnimation(
            animationValue, this.context.mlua().registration(), "AnimationService:RegisterAnimation");
        if (!this.registeredAnimations.register(id, owner, animation)) {
            throw new LuaError("[MLua] Animation id '" + id + "' is already owned by another addon.");
        }
    }

    @MLuaDescription("Returns an animation by id, or nil when missing. Unqualified ids use this addon's namespace; a fully namespaced id can reference another addon's registered animation.")
    public LuaValue GetAnimation(String id) {
        this.requireClientSide(this.name());
        id = animationId(id, this.context.mlua().ownerModId(), "AnimationService:GetAnimation", false);
        MLuaAnimation animation = this.registeredAnimations.get(id);
        return animation == null ? LuaValue.NIL
            : MLuaAnimationBindings.wrapAnimation(animation, this.context.mlua().registration());
    }

    @MLuaDescription("Returns whether an animation id resolves. Unqualified ids use this addon's namespace.")
    public boolean HasAnimation(String id) {
        this.requireClientSide(this.name());
        return this.registeredAnimations.contains(animationId(id, this.context.mlua().ownerModId(),
            "AnimationService:HasAnimation", false));
    }

    @MLuaDescription("Removes one animation owned by this addon and returns whether it existed.")
    public boolean RemoveAnimation(String id) {
        this.requireClientSide(this.name());
        return this.registeredAnimations.remove(animationId(id, this.context.mlua().ownerModId(),
            "AnimationService:RemoveAnimation", false), this.context.mlua().ownerModId());
    }

    @MLuaDescription("Removes this addon's registered animations and returns the number removed.")
    public synchronized int ClearAnimations() {
        this.requireClientSide(this.name());
        return this.registeredAnimations.clearOwner(this.context.mlua().ownerModId());
    }

    @MLuaDescription("Returns this addon's namespaced animation identifiers in lexicographic order.")
    public LuaTable GetAnimationIds() {
        this.requireClientSide(this.name());
        LuaTable result = new LuaTable();
        int index = 1;
        for (String id : this.registeredAnimations.idsForOwner(this.context.mlua().ownerModId())) {
            result.set(index++, id);
        }
        return result;
    }

    @MLuaDescription("Returns how many animations this addon has registered in the current runtime.")
    public int GetAnimationCount() {
        this.requireClientSide(this.name());
        return this.registeredAnimations.countForOwner(this.context.mlua().ownerModId());
    }

    @MLuaDescription("Returns this addon's registered animations keyed by namespaced id.")
    public LuaTable GetRegisteredAnimations() {
        this.requireClientSide(this.name());
        LuaTable result = new LuaTable();
        Map<String, MLuaAnimation> owned = this.registeredAnimations.entriesForOwner(
            this.context.mlua().ownerModId());
        for (Map.Entry<String, MLuaAnimation> entry : owned.entrySet()) {
            result.set(entry.getKey(), MLuaAnimationBindings.wrapAnimation(
                entry.getValue(), this.context.mlua().registration()));
        }
        return result;
    }

    private static String animationId(String id, String owner, String operation, boolean registering) {
        if (id == null || id.isBlank() || !id.equals(id.trim()) || id.chars().anyMatch(Character::isWhitespace)) {
            throw new LuaError("[MLua] " + operation + " expects a non-empty animation id without whitespace.");
        }
        if (owner == null || owner.isBlank()) throw new LuaError("[MLua] " + operation + " requires an addon owner.");
        String canonical = id.indexOf(':') < 0 ? owner + ":" + id : id;
        if (canonical.length() > 256 || !canonical.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new LuaError("[MLua] " + operation + " expects a namespaced animation id such as 'myaddon:walk'.");
        }
        if (registering && !canonical.substring(0, canonical.indexOf(':')).equals(owner)) {
            throw new LuaError("[MLua] An addon may register animations only in its own namespace.");
        }
        return canonical;
    }

    @MLuaDescription("Returns the current custom pose for an entity.")
    public LuaTable GetPose(LuaValue entity) {
        this.requireClientSide(this.name());
        MLuaEntityScope scope = MLuaGameThreadAccess.callUnchecked(
            this.context, () -> resolveEntityScope(entity));
        Map<String, MLuaCFrame> pose = MLuaBonePose.getPose(scope, this.context.mlua().session());
        LuaTable result = new LuaTable();
        for (Map.Entry<String, MLuaCFrame> entry : pose.entrySet()) {
            result.set(entry.getKey(), entry.getValue().toLua());
        }
        return result;
    }

    @MLuaDescription("Sets this addon's manual transform for one entity bone. The override is removed when the addon unloads.")
    public void SetPose(LuaValue entity, String bone, LuaValue cframe) {
        this.requireClientSide(this.name());
        MLuaCFrame transform = MLuaCFrame.fromLua(cframe);
        MLuaEntityScope scope = MLuaGameThreadAccess.callUnchecked(
            this.context, () -> resolveEntityScope(entity));
        MLuaBonePose.setPose(scope, bone, transform, this.context.mlua().session(),
            this.context.mlua().ownerModId());
    }

    @MLuaDescription("Clears this addon's manual pose overrides for an entity without stopping animation tracks.")
    public void ClearPose(LuaValue entity) {
        this.requireClientSide(this.name());
        MLuaEntityScope scope = MLuaGameThreadAccess.callUnchecked(
            this.context, () -> resolveEntityScope(entity));
        MLuaBonePose.clearPose(scope, this.context.mlua().session(), this.context.mlua().ownerModId());
    }

    @MLuaDescription("Clears this addon's manual pose overrides on the current side without stopping animation tracks.")
    public void ClearPoses() {
        this.requireClientSide(this.name());
        MLuaBonePose.clearOwned(this.context.mlua().session(), this.context.mlua().ownerModId(),
            this.context.mlua().side());
    }

    @MLuaDescription("Returns the available animation priorities.")
    public LuaTable GetAnimationPriority() {
        this.requireClientSide(this.name());
        return MLuaEnumValue.enumConstants(AnimationPriority.class, null);
    }

    private MLuaEntityScope resolveEntityScope(LuaValue value) {
        if (value.istable()) {
            if (!this.context.mlua().registration().isInstanceOf(value, "Entity")) {
                throw new LuaError("[MLua] Expected an Entity instance or entity ID");
            }
            LuaTable table = value.checktable();
            LuaValue entityObj = table.get("__entity");
            if (entityObj.isuserdata(Entity.class)) {
                Entity entity =
                    (Entity) entityObj.checkuserdata(Entity.class);
                if (!entity.level().isClientSide()) {
                    throw new LuaError("[MLua] Animation service requires an entity from the client world.");
                }
                return MLuaEntityScope.of(entity);
            }
        }
        if (value.isint()) return this.resolveEntityScope(value.toint());
        throw new LuaError("[MLua] Expected Entity or entity ID");
    }

    private MLuaEntityScope resolveEntityScope(int entityId) {
        if (this.context.mlua().side() != Side.CLIENT) {
            throw new LuaError("[MLua] Animation service is available in client scripts only.");
        }
        Level level = MLuaMinecraftClient.instance().level;
        Entity entity = level == null ? null : level.getEntity(entityId);
        if (entity == null) {
            throw new LuaError("[MLua] Entity id " + entityId
                + " is not loaded in the client world; pass a live Entity instance.");
        }
        return MLuaEntityScope.of(entity);
    }

}
