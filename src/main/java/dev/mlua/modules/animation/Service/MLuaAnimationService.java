package dev.mlua.modules.animation.Service;

import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaAnimation;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.common.enums.Side;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.minecraft.entity.MLuaEntityLookup;
import dev.mlua.minecraft.binding.MLuaAnimationBindings;
import dev.mlua.modules.enums.MLuaEnumValue;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import java.util.Optional;

@MLuaDescription("Service for creating and managing animations.")
public final class MLuaAnimationService extends MLuaReflectiveService {
    private final Map<String, MLuaAnimation> registeredAnimations = new ConcurrentHashMap<>();

    public MLuaAnimationService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() { return "Animation"; }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Client");
        return table;
    }

    @MLuaDescription("Creates an animation from an ordered keyframe table.")
    public LuaValue Create(LuaTable keyframes) {
        MLuaAnimation animation = MLuaAnimation.fromLuaTable(keyframes);
        return MLuaAnimationBindings.wrapAnimation(animation, this.context.mlua().registration());
    }

    @MLuaDescription("Stores an animation under an identifier for later retrieval.")
    public void RegisterAnimation(String id, LuaValue animationValue) {
        MLuaAnimation animation;
        if (this.context.mlua().registration().isInstanceOf(animationValue, "Animation")) {
            LuaValue getKeyframes = animationValue.get("GetKeyframes");
            if (!getKeyframes.isfunction()) throw new LuaError("[MLua] Animation instance does not provide GetKeyframes.");
            LuaValue keyframes = getKeyframes.call(animationValue);
            if (!keyframes.istable()) throw new LuaError("[MLua] Animation:GetKeyframes() must return a table.");
            animation = MLuaAnimation.fromLuaTable(keyframes.checktable());
        } else {
            if (animationValue.istable() && this.context.mlua().registration().classNameOf(animationValue) != null) {
                throw new LuaError("[MLua] RegisterAnimation expects an Animation instance or a keyframe table.");
            }
            animation = MLuaAnimation.fromLuaTable(animationValue.checktable());
        }
        this.registeredAnimations.put(id, animation);
    }

    @MLuaDescription("Returns a registered animation by identifier, or nil when missing.")
    public LuaValue GetAnimation(String id) {
        MLuaAnimation animation = this.registeredAnimations.get(id);
        return animation == null ? LuaValue.NIL
            : MLuaAnimationBindings.wrapAnimation(animation, this.context.mlua().registration());
    }

    @MLuaDescription("Returns all animations registered in this service instance.")
    public LuaTable GetRegisteredAnimations() {
        LuaTable result = new LuaTable();
        for (Map.Entry<String, MLuaAnimation> entry : this.registeredAnimations.entrySet()) {
            result.set(entry.getKey(), MLuaAnimationBindings.wrapAnimation(entry.getValue(), this.context.mlua().registration()));
        }
        return result;
    }

    @MLuaDescription("Returns the current custom pose for an entity.")
    public LuaTable GetPose(LuaValue entity) {
        MLuaEntityScope scope = resolveEntityScope(entity);
        Map<String, MLuaCFrame> pose = MLuaBonePose.getPose(scope, this.context.mlua().registration());
        LuaTable result = new LuaTable();
        for (Map.Entry<String, MLuaCFrame> entry : pose.entrySet()) {
            result.set(entry.getKey(), entry.getValue().toLua());
        }
        return result;
    }

    @MLuaDescription("Sets a custom transform for one entity bone.")
    public void SetPose(LuaValue entity, String bone, LuaValue cframe) {
        MLuaBonePose.setPose(resolveEntityScope(entity), bone, MLuaCFrame.fromLua(cframe), this.context.mlua().registration());
    }

    @MLuaDescription("Clears custom pose overrides for an entity.")
    public void ClearPose(Optional<LuaValue> entity) {
        if (entity.isEmpty() || entity.get().isnil()) {
            throw new LuaError("[MLua] AnimationService:ClearPose expects an Entity instance or entity ID.");
        }
        MLuaBonePose.clearPose(resolveEntityScope(entity.get()), this.context.mlua().registration());
    }

    @MLuaDescription("Returns the available animation priorities.")
    public LuaTable GetAnimationPriority() {
        return MLuaEnumValue.enumConstants(AnimationPriority.class, null);
    }

    private MLuaEntityScope resolveEntityScope(LuaValue value) {
        if (value.istable()) {
            if (!this.context.mlua().registration().isInstanceOf(value, "Entity")) {
                throw new LuaError("[MLua] Expected an Entity instance or entity ID");
            }
            LuaTable table = value.checktable();
            LuaValue entityObj = table.get("__entity");
            if (entityObj.isuserdata(net.minecraft.world.entity.Entity.class)) {
                return MLuaEntityScope.of((net.minecraft.world.entity.Entity) entityObj.checkuserdata(net.minecraft.world.entity.Entity.class));
            }
        }
        if (value.isint()) return this.resolveEntityScope(value.toint());
        throw new LuaError("[MLua] Expected Entity or entity ID");
    }

    private MLuaEntityScope resolveEntityScope(int entityId) {
        if (this.context.mlua().side() == Side.CLIENT) {
            net.minecraft.world.level.Level level = MLuaMinecraftClient.instance().level;
            net.minecraft.world.entity.Entity entity = level == null ? null : level.getEntity(entityId);
            return entity == null ? MLuaEntityScope.legacy(entityId) : MLuaEntityScope.of(entity);
        }
        if (this.context.mlua().server() != null) {
            MLuaEntityLookup.LookupResult<net.minecraft.world.entity.Entity> lookup =
                    MLuaEntityLookup.findById(this.context.mlua().server(), entityId);
            if (lookup.ambiguous()) {
                throw new LuaError("[MLua] Entity id " + entityId + " exists in multiple dimensions; pass an entity object");
            }
            if (lookup.found()) return MLuaEntityScope.of(lookup.value());
        }
        return MLuaEntityScope.legacy(entityId);
    }

}
