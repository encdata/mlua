package dev.mlua.modules.tween;

import dev.mlua.common.enums.Side;
import dev.mlua.common.enums.EasingStyle;
import dev.mlua.common.enums.EasingDirection;
import dev.mlua.core.MLuaContextView;
import dev.mlua.modules.tween.service.MLuaTweenService;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.enums.MLuaEnumValue;
import dev.mlua.loaderapi.LoaderApi;
import java.util.List;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public final class TweenModule implements MLuaModule {
    @Override public String id() { return "tween"; }
    @Override public Side side() { return Side.CLIENT; }
    @Override public List<String> dependencies() { return List.of("enums"); }

    @Override public void registerClasses(MLuaRegistration registration) {
        registration.registerClass("Tween", registration.requireInstanceBaseClassName(),
            "A client-side property tween controlled through TweenService.");
    }

    @Override public void contributeGlobals(Globals globals, MLuaContextView context) {
        LuaTable library = new LuaTable();
        library.set("new", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue receiver = args.arg1() == library ? args.arg1() : LuaValue.NIL;
                int offset = receiver.isnil() ? 0 : 1;
                double duration = args.arg(offset + 1).checkdouble();
                if (!Double.isFinite(duration) || duration < 0.0 || duration > 86_400.0) {
                    throw new LuaError("[MLua] TweenInfo.new duration must be finite and between 0 and 86400 seconds.");
                }
                LuaValue styleValue = args.arg(offset + 2);
                LuaValue directionValue = args.arg(offset + 3);
                EasingStyle easingStyle = MLuaEnumValue.toJava(styleValue, EasingStyle.class);
                EasingDirection easingDirection = MLuaEnumValue.toJava(directionValue, EasingDirection.class);
                String style = easingStyle != null ? easingStyle.name() : styleValue.optjstring("Linear");
                String direction = easingDirection != null ? easingDirection.name() : directionValue.optjstring("Out");
                LuaTable info = new LuaTable();
                info.set("Duration", duration);
                info.set("EasingStyle", style);
                info.set("EasingDirection", direction);
                info.set("__type", "TweenInfo");
                return info;
            }
        });
        globals.set("TweenInfo", library);
    }

    @Override public void onRenderFrame(MLuaRegistration runtime, Object runtimeSession, double deltaTime) {
        MLuaTweenService.onRenderFrame(runtimeSession);
    }

    @Override public void clearRuntimeState(MLuaRegistration runtime, Object runtimeSession, Side side) {
        MLuaTweenService.clearRuntime(runtimeSession, side);
    }

    @Override public void clearOwnedState(MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        MLuaTweenService.clearOwned(runtimeSession, ownerModId, side);
    }

    @Override public List<ServiceEntry> services() {
        if (!LoaderApi.platform().isClient()) return List.of();
        return List.of(ServiceEntry.builder("TweenService", MLuaTweenService::new)
            .reflective(MLuaTweenService.class).build());
    }

    @Override public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.type("Tween", null,
            "An instance that interpolates its target properties on client render frames."));
        docs.register(MLuaDocBuilder.Registration.type("TweenInfo", null,
            "Duration and easing settings used to create a Tween."));
        docs.register(MLuaDocBuilder.Registration.field("TweenInfo", "Duration", "number", "Tween length in seconds, from zero through 86400."));
        docs.register(MLuaDocBuilder.Registration.field("TweenInfo", "EasingStyle", "EasingStyle|string", "Interpolation curve. Defaults to Linear."));
        docs.register(MLuaDocBuilder.Registration.field("TweenInfo", "EasingDirection", "EasingDirection|string", "Curve direction. Defaults to Out."));
        docs.register(MLuaDocBuilder.Registration.service("TweenService", "MLuaTweenService",
            "Animates writable Lua instance properties on client render frames with typed interpolation and easing.",
            "services/tween.html"));
        docs.register(MLuaDocBuilder.Registration.signature("TweenService", "TweenService:Create(target: Instance|Service, info: TweenInfo, goals: table<string, number|Vector2|Vector3|Color3|CFrame>)", "Tween"));
        docs.register(MLuaDocBuilder.Registration.signature("Tween", "Tween:Play()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Tween", "Tween:Pause()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Tween", "Tween:Resume()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Tween", "Tween:Cancel()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Tween", "Tween:GetPlaybackState()", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Tween", "Tween:GetError()", "string|nil"));
        docs.register(MLuaDocBuilder.Registration.property("Tween", "Tween.Completed", "Signal", "Fires with the final playback state when the tween completes or is cancelled.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.signature("TweenInfo", "TweenInfo.new(duration: number, easingStyle?: EasingStyle|string, easingDirection?: EasingDirection|string)", "TweenInfo"));
        docs.register(MLuaDocBuilder.Registration.signature("TweenService", "TweenService:GetActiveCount()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("TweenService", "TweenService:GetLimits()", "table"));
        docs.register(MLuaDocBuilder.Registration.guideStep("TweenService", "Tween a camera transform",
            "Create validates every goal against a Get<Property>/Set<Property> pair before starting. Tween values are sampled and applied on each client render frame, including camera and render-object properties. CFrame uses rotational slerp; numbers and native vector values interpolate component-wise. Cancel keeps the current value, while Play restarts from the target's current values. Put the camera in Scriptable mode before animating its CFrame.",
            """
            local Camera = Minecraft:GetService("Camera")
            local Tweens = Minecraft:GetService("TweenService")
            Camera:SetMode("Scriptable")

            local move = Tweens:Create(Camera, TweenInfo.new(0.6, Enum.EasingStyle.Sine, Enum.EasingDirection.InOut), {
                CFrame = CFrame.lookAt(Vector3.new(0, 4, 8), Vector3.new(0, 1, 0)),
                FOV = 72,
            })
            move.Completed:Connect(function(state)
                print("Camera tween:", state)
            end)
            move:Play()
            """));
    }
}
