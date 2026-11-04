package dev.mlua.modules.render.service;

import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.core.MLuaContext;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.MLuaRenderOwnershipRegistry;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import java.util.Locale;
import java.util.Objects;

final class MLuaRenderObjectOperations {
    interface Access {
        MLuaRegistration registration();
        MLuaClientRenderManager.RenderObject requireObject(int id);
        MLuaClientRenderManager.RenderObject requireMeshObject(int id);
        MLuaClientRenderManager.RenderObject requireModelObject(int id);
        MLuaContext renderOwnerContext();
        MLuaEntityScope resolveOptionalEntityScope(LuaValue value);
        void forgetHandle(int id);
    }
    private final Access access;

    private final int id;
    private final LuaTable operations = new LuaTable();
    private final MLuaRenderOwnershipRegistry.Owner renderOwner;
    private final MLuaClientRenderManager.RenderType primitive;
    private final String className;
    private final LuaValue renderTypeValue;

    MLuaRenderObjectOperations(int id, MLuaRenderOwnershipRegistry.Owner renderOwner,
            MLuaClientRenderManager.RenderType primitive, String className, LuaValue renderTypeValue, Access access) {
        this.id = id;
        this.access = access;
        this.renderOwner = renderOwner;
        this.primitive = primitive;
        this.className = className;
        this.renderTypeValue = renderTypeValue;
    }

    LuaTable create() {
        registerIdentityOperations();
        registerPositionOperations();
        registerAppearanceOperations();
        registerWorldSpaceOperations();
        registerTargetOperations();
        registerModelOperations();
        registerContentOperations();
        registerLifecycleOperations();
        MLuaRenderObjectClassBinding.validateObjectOperations(operations);
        return operations;
    }

    private void registerIdentityOperations() {
        operations.set("GetId", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(id)));
        // The render primitive is immutable, so keep it readable on a destroyed handle.
        operations.set("GetRenderType", MLuaLuaErrors.operation("Render service call", args -> renderTypeValue));
        operations.set("Equals", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue other = args.arg(2);
                if (!other.istable() || !access.registration().isInstanceOf(other, "RenderObject")) {
                    return LuaValue.FALSE;
                }
                LuaTable otherHandle = other.checktable();
                LuaValue ownerValue = otherHandle.rawget("__mluaRenderOwner");
                return LuaValue.valueOf(id == otherHandle.rawget(MLuaRenderService.OBJECT_ID).optint(-1)
                    && ownerValue.isuserdata(MLuaRenderOwnershipRegistry.Owner.class)
                    && renderOwner.equals(ownerValue.touserdata(MLuaRenderOwnershipRegistry.Owner.class)));
            }
        });
        operations.set("GetHashCode", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(
            Objects.hash(renderOwner, id))));
        operations.set("ToString", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf((className == null ? "RenderObject" : className) + "["
                    + primitive.name().toLowerCase(Locale.ROOT) + ", " + renderOwner.ownerModId() + ":" + id + "]");
            }
        });
        operations.set("GetIsDestroyed", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(MLuaClientRenderManager.get(id, renderOwner) == null)));
    }

    private void registerPositionOperations() {
        operations.set("GetPosition", MLuaLuaErrors.operation("Render service call", args -> {
            var object = access.requireObject(id);
            return MLuaVector.vector2(object.x(), object.y());
        }));
        operations.set("SetPosition", MLuaLuaErrors.operation("Render service call", args -> {
            var object = access.requireObject(id);
            double[] value = MLuaRenderValues.screenVector(args.arg(2));
            object.setPosition(value[0], value[1]);
            return LuaValue.NONE;
        }));
        operations.set("GetEndPosition", MLuaLuaErrors.operation("Render service call", args -> {
            var object = access.requireObject(id);
            return MLuaVector.vector2(object.endX(), object.endY());
        }));
        operations.set("SetEndPosition", MLuaLuaErrors.operation("Render service call", args -> {
            double[] value = MLuaRenderValues.screenVector(args.arg(2));
            access.requireObject(id).setEndPosition(value[0], value[1]);
            return LuaValue.NONE;
        }));
        operations.set("GetWorldPosition", MLuaLuaErrors.operation("Render service call", args -> {
            var object = access.requireObject(id);
            return MLuaVector.vector3(object.worldX(), object.worldY(), object.worldZ());
        }));
        operations.set("SetWorldPosition", MLuaLuaErrors.operation("Render service call", args -> {
            var object = access.requireObject(id);
            double[] value = MLuaRenderValues.worldVector(args.arg(2));
            object.setWorldPosition(value[0], value[1], value[2]);
            object.setWorldSpace(true);
            return LuaValue.NONE;
        }));
        operations.set("GetEndWorldPosition", MLuaLuaErrors.operation("Render service call", args -> {
            var object = access.requireObject(id);
            return MLuaVector.vector3(object.endWorldX(), object.endWorldY(), object.endWorldZ());
        }));
        operations.set("SetEndWorldPosition", MLuaLuaErrors.operation("Render service call", args -> {
            var object = access.requireObject(id);
            double[] value = MLuaRenderValues.worldVector(args.arg(2));
            object.setEndWorldPosition(value[0], value[1], value[2]);
            return LuaValue.NONE;
        }));
        operations.set("GetDepth", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(access.requireObject(id).depth())));
        operations.set("SetDepth", MLuaLuaErrors.operation("Render service call", args -> {
            access.requireObject(id).setDepth(MLuaRenderValues.nonNegative(args.arg(2), "Depth"));
            return LuaValue.NONE;
        }));
        operations.set("GetSize", MLuaLuaErrors.operation("Render service call", args -> {
            var object = access.requireObject(id);
            return MLuaVector.vector2(object.width(), object.height());
        }));
        operations.set("SetSize", MLuaLuaErrors.operation("Render service call", args -> {
            var object = access.requireObject(id);
            double[] value = MLuaRenderValues.screenSize(args.arg(2));
            object.setSize(value[0], value[1]);
            return LuaValue.NONE;
        }));
        operations.set("GetRotation", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(access.requireObject(id).rotation())));
        operations.set("SetRotation", MLuaLuaErrors.operation("Render service call", args -> {
            access.requireObject(id).setRotation(MLuaRenderValues.finiteNumber(args.arg(2), "Rotation"));
            return LuaValue.NONE;
        }));
    }

    private void registerAppearanceOperations() {
        operations.set("GetVisible", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(access.requireObject(id).visible())));
        operations.set("SetVisible", MLuaLuaErrors.operation("Render service call", args -> {
            access.requireObject(id).setVisible(args.checkboolean(2));
            return LuaValue.NONE;
        }));
        operations.set("GetTransparency", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(access.requireObject(id).transparency())));
        operations.set("SetTransparency", MLuaLuaErrors.operation("Render service call", args -> {
            access.requireObject(id).setTransparency(MLuaRenderValues.opacity(args.arg(2), "Transparency"));
            return LuaValue.NONE;
        }));
        operations.set("GetAlpha", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(1.0 - access.requireObject(id).transparency())));
        operations.set("SetAlpha", MLuaLuaErrors.operation("Render service call", args -> {
            access.requireObject(id).setTransparency(1.0 - MLuaRenderValues.opacity(args.arg(2), "Alpha"));
            return LuaValue.NONE;
        }));
        operations.set("GetColor", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(access.requireObject(id).color())));
        operations.set("SetColor", MLuaLuaErrors.operation("Render service call", args -> {
            access.requireObject(id).setColor(MLuaRenderValues.packedColor(args.arg(2)));
            return LuaValue.NONE;
        }));
        operations.set("GetThickness", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(access.requireObject(id).thickness())));
        operations.set("SetThickness", MLuaLuaErrors.operation("Render service call", args -> {
            access.requireObject(id).setThickness(MLuaRenderValues.atLeast(args.arg(2), "Thickness", 1.0));
            return LuaValue.NONE;
        }));
        operations.set("GetZIndex", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(access.requireObject(id).zIndex())));
        operations.set("SetZIndex", MLuaLuaErrors.operation("Render service call", args -> {
            access.requireObject(id).setZIndex(MLuaRenderValues.wholeInt(args.arg(2), "ZIndex"));
            return LuaValue.NONE;
        }));
    }

    private void registerWorldSpaceOperations() {
        operations.set("GetWorldSpace", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(access.requireObject(id).worldSpace())));
        operations.set("SetWorldSpace", MLuaLuaErrors.operation("Render service call", args -> {
            access.requireObject(id).setWorldSpace(args.checkboolean(2));
            return LuaValue.NONE;
        }));
    }

    private void registerTargetOperations() {
        operations.set("GetTarget", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(MLuaRenderValues.renderTargetName(access.requireObject(id).target()))));
        operations.set("SetTarget", MLuaLuaErrors.operation("Render service call", args -> {
            String name = args.checkjstring(2).trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            try {
                access.requireObject(id).setTarget(MLuaClientRenderManager.RenderTarget.valueOf(name));
            } catch (IllegalArgumentException invalid) {
                throw new LuaError("[MLua] Invalid render target '" + name + "'. Use Manual, Hud, WorldOverlay, WorldModel, EntityModel, FirstPersonItem, or ThirdPersonItem.");
            }
            return LuaValue.NONE;
        }));
        operations.set("GetItemId", MLuaLuaErrors.operation("Render service call", args -> {
            String itemId = access.requireObject(id).heldItemId();
            return itemId == null ? LuaValue.NIL : LuaValue.valueOf(itemId);
        }));
        operations.set("SetItemId", MLuaLuaErrors.operation("Render service call", args -> {
            try {
                access.requireObject(id).setHeldItemId(args.arg(2).isnil() ? null : args.checkjstring(2));
            } catch (IllegalArgumentException invalid) {
                throw new LuaError("[MLua] " + invalid.getMessage());
            }
            return LuaValue.NONE;
        }));
        operations.set("GetRenderStage", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(access.requireObject(id).renderStage())));
        operations.set("SetRenderStage", MLuaLuaErrors.operation("Render service call", args -> {
            try {
                access.requireObject(id).setRenderStage(args.checkjstring(2));
            } catch (IllegalArgumentException invalid) {
                throw new LuaError("[MLua] " + invalid.getMessage());
            }
            return LuaValue.NONE;
        }));
        operations.set("GetRenderDistance", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(access.requireObject(id).worldRenderDistance())));
        operations.set("SetRenderDistance", MLuaLuaErrors.operation("Render service call", args -> {
            double distance = MLuaRenderValues.finiteNumber(args.arg(2), "RenderDistance");
            if (distance < 1.0D || distance > MLuaClientRenderManager.MAX_WORLD_RENDER_DISTANCE) {
                throw new LuaError("[MLua] RenderObject RenderDistance must be between 1 and "
                    + (int)MLuaClientRenderManager.MAX_WORLD_RENDER_DISTANCE + " blocks.");
            }
            access.requireObject(id).setWorldRenderDistance(distance);
            return LuaValue.NONE;
        }));
        operations.set("GetScaleWithDistance", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(access.requireObject(id).scaleWithDistance())));
        operations.set("SetScaleWithDistance", MLuaLuaErrors.operation("Render service call", args -> {
            access.requireObject(id).setScaleWithDistance(args.checkboolean(2));
            return LuaValue.NONE;
        }));
    }

    private void registerModelOperations() {
        operations.set("GetModelScale", MLuaLuaErrors.operation("Render service call", args -> LuaValue.valueOf(access.requireObject(id).modelScale())));
        operations.set("SetModelScale", MLuaLuaErrors.operation("Render service call", args -> {
            access.requireObject(id).setModelScale(MLuaRenderValues.modelScale(args.arg(2)));
            return LuaValue.NONE;
        }));
        operations.set("GetModelTransform", MLuaLuaErrors.operation("Render service call", args -> access.requireMeshObject(id).modelTransform().toLua()));
        operations.set("SetModelTransform", MLuaLuaErrors.operation("Render service call", args -> {
            if (!MLuaCFrame.isCFrame(args.arg(2))) {
                throw new LuaError("[MLua] RenderObject ModelTransform must be a CFrame.");
            }
            try {
                access.requireMeshObject(id).setModelTransform(MLuaCFrame.fromLua(args.arg(2)));
            } catch (IllegalArgumentException invalid) {
                throw new LuaError("[MLua] RenderObject ModelTransform " + invalid.getMessage());
            }
            return LuaValue.NONE;
        }));
        operations.set("GetFollowEntity", MLuaLuaErrors.operation("Render service call", args -> {
            MLuaEntityScope scope = access.requireObject(id).followEntityScope();
            return scope != null ? LuaValue.valueOf(scope.entityId()) : LuaValue.NIL;
        }));
        operations.set("SetFollowEntity", MLuaLuaErrors.operation("Render service call", args -> {
            access.requireObject(id).setFollowEntityScope(access.resolveOptionalEntityScope(args.arg(2)));
            return LuaValue.NONE;
        }));
        operations.set("ClearFollowEntity", MLuaLuaErrors.operation("Render service call", args -> {
            access.requireObject(id).setFollowEntityScope(null);
            return LuaValue.NONE;
        }));
        operations.set("GetFollowBone", MLuaLuaErrors.operation("Render service call", args -> {
            String bone = access.requireObject(id).followBoneName();
            return bone == null ? LuaValue.NIL : LuaValue.valueOf(bone);
        }));
        operations.set("SetFollowBone", MLuaLuaErrors.operation("Render service call", args -> {
            LuaValue value = args.arg(2);
            try {
                access.requireObject(id).setFollowBoneName(value.isnil() ? null : value.checkjstring());
            } catch (IllegalArgumentException invalid) {
                throw new LuaError("[MLua] " + invalid.getMessage());
            }
            return LuaValue.NONE;
        }));
        operations.set("GetFollowOffset", MLuaLuaErrors.operation("Render service call", args -> access.requireObject(id).followOffsetValue()));
        operations.set("SetFollowOffset", MLuaLuaErrors.operation("Render service call", args -> {
            LuaValue value = args.arg(2);
            try {
                if (value.isfunction()) access.requireObject(id).setFollowOffsetCallback(value, access.renderOwnerContext());
                else access.requireObject(id).setFollowOffset(MLuaCFrame.fromLua(value));
            } catch (IllegalArgumentException | LuaError invalid) {
                throw new LuaError("[MLua] FollowOffset must be a CFrame or a function returning CFrame: " + invalid.getMessage());
            }
            return LuaValue.NONE;
        }));
        operations.set("GetModelParts", MLuaLuaErrors.operation("Render service call", args -> MLuaRenderPartBinding.all(
            access.registration(), id, renderOwner, access.requireModelObject(id).bbmodel())));
        operations.set("GetModelPart", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return MLuaRenderPartBinding.find(access.registration(), id, renderOwner,
                    access.requireModelObject(id).bbmodel(), args.arg(2).checkjstring());
            }
        });
        operations.set("GetModelPartById", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return MLuaRenderPartBinding.findById(access.registration(), id, renderOwner,
                    access.requireModelObject(id).bbmodel(), args.arg(2).checkjstring());
            }
        });
    }

    private void registerContentOperations() {
        operations.set("GetResource", MLuaLuaErrors.operation("Render service call", args -> {
            var object = access.requireObject(id);
            if (!MLuaRenderValues.supportsResource(object.type())) return LuaValue.NIL;
            String resource = object.resource();
            return resource != null ? LuaValue.valueOf(resource) : LuaValue.NIL;
        }));
        operations.set("SetResource", MLuaLuaErrors.operation("Render service call", args -> {
            var object = access.requireObject(id);
            MLuaRenderValues.requireResourceType(object.type(), "SetResource");
            object.setResource(args.arg(2).isnil() ? null : args.checkjstring(2));
            return LuaValue.NONE;
        }));
        operations.set("GetText", MLuaLuaErrors.operation("Render service call", args -> {
            var object = access.requireObject(id);
            if (object.type() != MLuaClientRenderManager.RenderType.TEXT) return LuaValue.NIL;
            String resource = object.resource();
            return resource != null ? LuaValue.valueOf(resource) : LuaValue.NIL;
        }));
        operations.set("SetText", MLuaLuaErrors.operation("Render service call", args -> {
            var object = access.requireObject(id);
            if (object.type() != MLuaClientRenderManager.RenderType.TEXT) {
                throw new LuaError("[MLua] RenderObject:SetText is only available on text render objects.");
            }
            object.setResource(args.arg(2).isnil() ? null : args.checkjstring(2));
            return LuaValue.NONE;
        }));
    }

    private void registerLifecycleOperations() {
        operations.set("Destroy", MLuaLuaErrors.operation("Render service call", args -> {
            MLuaClientRenderManager.remove(id, renderOwner);
            access.forgetHandle(id);
            return LuaValue.NONE;
        }));
    }
}
