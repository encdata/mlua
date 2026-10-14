package dev.mlua.modules.animation;

import dev.mlua.minecraft.animation.MLuaAnimationController;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.binding.MLuaAnimationBindings;
import dev.mlua.loaderapi.EventBridge;
import dev.mlua.common.enums.Side;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.MLuaContextView;
import dev.mlua.modules.animation.Service.MLuaAnimationService;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.luaj.vm2.Globals;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public class AnimationModule implements MLuaModule {
    @Override
    public String id() { return "animation"; }

    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        MLuaAnimationController.clearRuntime(runtime, side);
    }

    @Override
    public int priority() { return 15; }

    @Override
    public List<String> dependencies() { return List.of("registry"); }

    @Override
    public Side side() { return Side.CLIENT; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder animation = ServiceEntry.builder("Animation", MLuaAnimationService::new)
            .reflective(MLuaAnimationService.class);
        return List.of(animation.build());
    }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        MLuaAnimationBindings.registerClasses(registration);
    }

    @Override
    public void contributeGlobals(Globals globals, MLuaContextView context) {
        MLuaAnimationBindings.ensureRegisteredClasses(context.registration());
        globals.set("Animation", MLuaAnimationBindings.createAnimationTable(context.registration()));
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.signature("Animation", "Animation:Create(keyframes: KeyframeDefinition[])", null);
        docs.signature("Animation", "Animation:RegisterAnimation(id: string, animation: Animation)", null);
        docs.signature("Animation", "Animation:GetAnimation(id: string)", null);
        docs.signature("Animation", "Animation:SetPose(entity: Entity, bone: string, cframe: CFrame)", null);
        docs.signature("Animation", "Animation:GetPose(entity: Entity)", null);
        docs.signature("Animation", "Animation:ClearPose(entity: Entity)", null);

        docs.service("Animation", "MLuaAnimationService",
                "Service for creating and managing animations.",
                "services/animation.html");
        docs.clazz("Animation", "MLuaAnimationBindings",
                "A reusable sequence of keyframes that can be played on a living entity.",
                "classes/animation.html", "Instance");
        docs.clazz("AnimationTrack", "MLuaAnimationBindings",
                "A live animation playback instance returned by an entity animation controller.",
                "classes/animation-track.html", "Instance");
        docs.type("KeyframeDefinition", null, "One time and pose value in an animation keyframe sequence.");
        docs.field("KeyframeDefinition", "time", "number", "Time position of the keyframe in seconds.");
        docs.field("KeyframeDefinition", "value", "any", "Pose values to apply at this keyframe.");
        docs.field("KeyframeDefinition", "easing?", "string", "Optional easing function used between this and the next keyframe.");
    }

    @Override
    public void registerEvents(EventBridge events) {
        events.onClientTickEnd(() -> {
            Minecraft mc = Minecraft.getInstance();
            Level level = mc.level;
            if (level == null) return;
            tickTrackedEntities(level, true);
        });

        events.onServerLevelTickEnd(levelObj -> {
            Level level = (Level) levelObj;
            tickTrackedEntities(level, false);
        });

        events.onClientDisconnect(MLuaAnimationController::clearAll);
    }

    private static void tickTrackedEntities(Level level, boolean clientSide) {
        String dimension = level.dimension().location().toString();
        for (MLuaEntityScope scope : MLuaAnimationController.getTrackedEntities()) {
            if (scope.side() != (clientSide ? dev.mlua.common.enums.Side.CLIENT : dev.mlua.common.enums.Side.SERVER)
                || !scope.dimension().equals(dimension)) continue;
            net.minecraft.world.entity.Entity entity = level.getEntity(scope.entityId());
            if (entity != null && entity.getUUID().equals(scope.uuid())) {
                MLuaAnimationController.tickEntity(entity, 0.05f);
            }
        }
    }
}
