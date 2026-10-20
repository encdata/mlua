package dev.mlua.modules.render.service;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaRegistration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

/** Registers RenderObject properties, method dispatch, and property documentation. */
public final class MLuaRenderObjectClassBinding {
    private MLuaRenderObjectClassBinding() { }

    private record ObjectProperty(String name, String type, String description, String getter, String setter) { }
    private static final List<ObjectProperty> OBJECT_PROPERTIES = List.of(
        new ObjectProperty("Id", "number", "The render object's runtime identifier.", "", null),
        new ObjectProperty("Type", "string", "The Minecraft render primitive used by this object.", "GetRenderType", null),
        new ObjectProperty("IsDestroyed", "boolean", "Whether this render object has been removed.", "GetIsDestroyed", null),
        new ObjectProperty("Position", "Vector2", "The render object's screen position.", "GetPosition", "SetPosition"),
        new ObjectProperty("EndPosition", "Vector2", "The second screen position used by line and beam objects.", "GetEndPosition", "SetEndPosition"),
        new ObjectProperty("WorldPosition", "Vector3|CFrame", "The render object's world position.", "GetWorldPosition", "SetWorldPosition"),
        new ObjectProperty("EndWorldPosition", "Vector3|CFrame", "The second world position used by line and beam objects.", "GetEndWorldPosition", "SetEndWorldPosition"),
        new ObjectProperty("Depth", "number", "The object's non-negative depth offset.", "GetDepth", "SetDepth"),
        new ObjectProperty("Size", "Vector2", "The render object's non-negative width and height.", "GetSize", "SetSize"),
        new ObjectProperty("Rotation", "number", "The object's rotation in degrees.", "GetRotation", "SetRotation"),
        new ObjectProperty("Visible", "boolean", "Whether this render object is visible.", "GetVisible", "SetVisible"),
        new ObjectProperty("Transparency", "number", "The object's transparency from zero to one. Provide Alpha or Transparency, not both.", "GetTransparency", "SetTransparency"),
        new ObjectProperty("Alpha", "number", "The object's opacity from zero to one; the inverse of Transparency. Provide Alpha or Transparency, not both.", "GetAlpha", "SetAlpha"),
        new ObjectProperty("Color", "number", "The object's packed color value.", "GetColor", "SetColor"),
        new ObjectProperty("Thickness", "number", "The line or outline thickness, at least one pixel.", "GetThickness", "SetThickness"),
        new ObjectProperty("ZIndex", "number", "The screen draw order.", "GetZIndex", "SetZIndex"),
        new ObjectProperty("WorldSpace", "boolean", "Whether the object is positioned in the world.", "GetWorldSpace", "SetWorldSpace"),
        new ObjectProperty("Target", "string", "Automatic destination: Manual, Hud, WorldOverlay, WorldModel, EntityModel, FirstPersonItem, or ThirdPersonItem. EntityModel follows an entity root or registered model bone; held-item targets draw a Mesh or BBModel in the matching item's current hand pose. Manual objects render only when submitted explicitly through a RenderContext.", "GetTarget", "SetTarget"),
        new ObjectProperty("ItemId", "string|nil", "The item identifier matched by FirstPersonItem and ThirdPersonItem targets. Use a namespaced id; an unqualified id means minecraft:<path>.", "GetItemId", "SetItemId"),
        new ObjectProperty("RenderStage", "string", "The managed-model submission point used by WorldModel objects. Choose a value returned by RenderService:GetWorldModelRenderStages() for the active loader and Minecraft version; the default is the first supported value.", "GetRenderStage", "SetRenderStage"),
        new ObjectProperty("RenderDistance", "number", "Maximum distance in blocks for a WorldModel object. Defaults to 256 and accepts values from 1 to 4096.", "GetRenderDistance", "SetRenderDistance"),
        new ObjectProperty("ScaleWithDistance", "boolean", "Whether world-space size changes with camera distance.", "GetScaleWithDistance", "SetScaleWithDistance"),
        new ObjectProperty("ModelScale", "number", "The finite uniform scale applied to this object or model; at least 0.001.", "GetModelScale", "SetModelScale"),
        new ObjectProperty("ModelTransform", "CFrame", "The finite local translation and rotation applied to a Mesh or BBModel after its render target anchor and before ModelScale.", "GetModelTransform", "SetModelTransform"),
        new ObjectProperty("FollowEntity", "Entity|number|nil", "The entity followed by this object. Assign an entity instance or id, or nil to clear it.", "GetFollowEntity", "SetFollowEntity"),
        new ObjectProperty("FollowBone", "string|nil", "A registered model adapter bone on the followed entity. When unset, EntityModel attaches at the entity render origin. Living entity renderers and non-living renderers that expose an adapted model can resolve named bones.", "GetFollowBone", "SetFollowBone"),
        new ObjectProperty("FollowOffset", "CFrame|function", "A local transform after the followed entity root or model bone, or after a held item's hand pose for FirstPersonItem and ThirdPersonItem targets. A function must return a finite CFrame and is sampled once per client tick.", "GetFollowOffset", "SetFollowOffset"),
        new ObjectProperty("Resource", "string|nil", "The resource identifier for image, item, or block objects; nil for other render types.", "GetResource", "SetResource"),
        new ObjectProperty("Text", "string|nil", "The displayed text; available only on text objects.", "GetText", "SetText")
    );
    private record ObjectMethod(String name, String description, boolean blockbenchOnly) {
        private ObjectMethod(String name, String description) { this(name, description, false); }
    }
    private static final List<ObjectMethod> OBJECT_METHODS = List.of(
        new ObjectMethod("GetId", "Returns the object's runtime identifier."),
        new ObjectMethod("GetRenderType", "Returns the Minecraft render primitive used by this object."),
        new ObjectMethod("Equals", "Compares this render handle with another handle for the same owned render object."),
        new ObjectMethod("GetHashCode", "Returns a hash derived from this handle's owner and runtime identifier."),
        new ObjectMethod("ToString", "Returns the runtime class, render primitive, owner, and identifier."),
        new ObjectMethod("GetIsDestroyed", "Returns whether this object has been removed."),
        new ObjectMethod("GetPosition", "Returns the object's screen position."),
        new ObjectMethod("SetPosition", "Changes the object's screen position."),
        new ObjectMethod("GetEndPosition", "Returns the second screen position."),
        new ObjectMethod("SetEndPosition", "Changes the second screen position."),
        new ObjectMethod("GetWorldPosition", "Returns the object's world position."),
        new ObjectMethod("SetWorldPosition", "Changes the object's world position."),
        new ObjectMethod("GetEndWorldPosition", "Returns the second world position."),
        new ObjectMethod("SetEndWorldPosition", "Changes the second world position."),
        new ObjectMethod("GetDepth", "Returns the depth offset."),
        new ObjectMethod("SetDepth", "Sets a finite, non-negative depth offset."),
        new ObjectMethod("GetSize", "Returns the object's width and height."),
        new ObjectMethod("SetSize", "Sets finite, non-negative width and height values."),
        new ObjectMethod("GetRotation", "Returns the rotation in degrees."),
        new ObjectMethod("SetRotation", "Changes the rotation in degrees."),
        new ObjectMethod("GetVisible", "Returns whether the object is visible."),
        new ObjectMethod("SetVisible", "Shows or hides the object."),
        new ObjectMethod("GetTransparency", "Returns the object's transparency."),
        new ObjectMethod("SetTransparency", "Changes the object's transparency."),
        new ObjectMethod("GetAlpha", "Returns the object's opacity."),
        new ObjectMethod("SetAlpha", "Changes the object's opacity."),
        new ObjectMethod("GetColor", "Returns the packed color value."),
        new ObjectMethod("SetColor", "Changes the packed color value."),
        new ObjectMethod("GetThickness", "Returns the line or outline thickness."),
        new ObjectMethod("SetThickness", "Sets a finite thickness of at least one pixel."),
        new ObjectMethod("GetZIndex", "Returns the screen draw order."),
        new ObjectMethod("SetZIndex", "Changes the screen draw order."),
        new ObjectMethod("GetWorldSpace", "Returns whether the object is positioned in the world."),
        new ObjectMethod("SetWorldSpace", "Changes whether the object is positioned in the world."),
        new ObjectMethod("GetTarget", "Returns the canonical render target name accepted by SetTarget."),
        new ObjectMethod("SetTarget", "Selects an automatic render destination; matching is case-insensitive."),
        new ObjectMethod("GetItemId", "Returns the item id matched by held-item model targets, or nil."),
        new ObjectMethod("SetItemId", "Selects the item matched by held-item model targets."),
        new ObjectMethod("GetRenderStage", "Returns the selected managed-model submission point."),
        new ObjectMethod("SetRenderStage", "Selects a value returned by RenderService:GetWorldModelRenderStages() for WorldModel rendering. For other targets, any stage returned by GetWorldRenderStages() is accepted."),
        new ObjectMethod("GetRenderDistance", "Returns the maximum WorldModel render distance in blocks."),
        new ObjectMethod("SetRenderDistance", "Sets a WorldModel distance from 1 to 4096 blocks; the default is 256."),
        new ObjectMethod("GetScaleWithDistance", "Returns whether world-space size follows camera distance."),
        new ObjectMethod("SetScaleWithDistance", "Changes distance-based scaling."),
        new ObjectMethod("GetModelScale", "Returns the uniform model scale."),
        new ObjectMethod("SetModelScale", "Sets a finite uniform model scale of at least 0.001."),
        new ObjectMethod("GetModelTransform", "Returns the model's local CFrame transform."),
        new ObjectMethod("SetModelTransform", "Sets a finite local translation and rotation for a Mesh or BBModel."),
        new ObjectMethod("GetFollowEntity", "Returns the followed entity id, or nil."),
        new ObjectMethod("SetFollowEntity", "Attaches the object to an entity or clears the attachment with nil."),
        new ObjectMethod("ClearFollowEntity", "Stops the object from following an entity."),
        new ObjectMethod("GetFollowBone", "Returns the registered model bone used by this attachment, or nil."),
        new ObjectMethod("SetFollowBone", "Attaches a 3D object to a model bone registered by ModelService."),
        new ObjectMethod("GetFollowOffset", "Returns the local CFrame or callback used after the selected render anchor."),
        new ObjectMethod("SetFollowOffset", "Sets a local CFrame or a function returning a finite CFrame, sampled once per client tick under MLua's callback instruction limit. It is applied after an entity root, model bone, or held-item hand pose. If the callback errors or returns an invalid transform, MLua disables it and keeps the last valid offset."),
        new ObjectMethod("GetResource", "Returns the resource identifier for an image, item, or block object; otherwise nil."),
        new ObjectMethod("SetResource", "Changes the resource identifier on image, item, or block objects."),
        new ObjectMethod("GetText", "Returns displayed text on text objects; otherwise nil."),
        new ObjectMethod("SetText", "Changes displayed text on text objects."),
        new ObjectMethod("GetAnimations", "Returns animation names available on a Blockbench model.", true),
        new ObjectMethod("GetAnimationName", "Returns the selected Blockbench animation name, or nil when no pose is selected.", true),
        new ObjectMethod("IsAnimationPlaying", "Returns whether the selected Blockbench animation is advancing.", true),
        new ObjectMethod("IsAnimationPaused", "Returns whether the selected Blockbench animation is paused.", true),
        new ObjectMethod("GetAnimationSpeed", "Returns the playback speed of the selected Blockbench animation.", true),
        new ObjectMethod("GetModelParts", "Returns live RenderPart instances for a Blockbench model's bone hierarchy."),
        new ObjectMethod("GetModelPart", "Returns a model part by its unique name or UUID, or nil when it does not exist."),
        new ObjectMethod("GetModelPartById", "Returns a model part by its stable Blockbench UUID, or nil when it does not exist."),
        new ObjectMethod("PlayAnimation", "Plays a named Blockbench model animation.", true),
        new ObjectMethod("PauseAnimation", "Pauses the selected Blockbench model animation without clearing its pose.", true),
        new ObjectMethod("ResumeAnimation", "Resumes the selected Blockbench model animation.", true),
        new ObjectMethod("SetAnimationTime", "Seeks the selected Blockbench model animation to a time in seconds.", true),
        new ObjectMethod("SetAnimationSpeed", "Changes the playback speed of the selected Blockbench model animation.", true),
        new ObjectMethod("StopAnimation", "Stops the current Blockbench model animation and restores the base pose.", true),
        new ObjectMethod("GetAnimationTime", "Returns elapsed time for the current model animation.", true),
        new ObjectMethod("Destroy", "Removes this render object.")
    );

    public static void registerRenderObjectClass(MLuaRegistration registration) {
        Set<String> methodNames = new HashSet<>();
        for (ObjectMethod method : OBJECT_METHODS) {
            if (!methodNames.add(method.name())) {
                throw new IllegalStateException("RenderObject method is registered more than once: " + method.name());
            }
        }
        for (ObjectProperty property : OBJECT_PROPERTIES) {
            String getter = property.getter();
            String setter = property.setter();
            if ((!getter.isBlank() && !methodNames.contains(getter))
                    || (setter != null && !methodNames.contains(setter))) {
                throw new IllegalStateException("RenderObject property references an unregistered method: " + property.name());
            }
        }
        String description = "A live client render instance with mutable screen-space or world-space properties and rendering methods.";
        registration.registerClass("RenderObject", registration.requireInstanceBaseClassName(), description);
        MLuaRenderPartBinding.registerClass(registration);
        for (ObjectProperty property : OBJECT_PROPERTIES) {
            registerObjectProperty(registration, property.name(), property.type(), property.description(),
                property.getter(), property.setter());
        }
        for (ObjectMethod method : OBJECT_METHODS) {
            registerObjectMethod(registration, method.name(), method.description());
        }
    }

    static void validateObjectOperations(LuaTable operations) {
        validateObjectOperations(operations, false);
    }

    static void validateBlockbenchObjectOperations(LuaTable operations) {
        validateObjectOperations(operations, true);
    }

    private static void validateObjectOperations(LuaTable operations, boolean blockbenchModel) {
        for (ObjectMethod method : OBJECT_METHODS) {
            if (method.blockbenchOnly() && !blockbenchModel) continue;
            if (!operations.rawget(method.name()).isfunction()) {
                throw new IllegalStateException("RenderObject method has no operation binding: " + method.name());
            }
        }
    }

    public static void contributeRenderObjectPropertyDocs(MLuaDocBuilder docs) {
        for (ObjectProperty property : OBJECT_PROPERTIES) {
            docs.register(MLuaDocBuilder.Registration.property("RenderObject",
                "RenderObject." + property.name(), property.type(), property.description(), "client:Client"));
            String getter = property.getter().isBlank() ? "Get" + property.name() : property.getter();
            if (!hasMethodRegistration(docs, "RenderObject", getter)) {
                docs.register(MLuaDocBuilder.Registration.signature("RenderObject",
                    "RenderObject:" + getter + "()", property.type()));
            }
            if (property.setter() != null) {
                docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", property.name(),
                    property.type(), "Initial value. " + property.description()));
                if (!hasMethodRegistration(docs, "RenderObject", property.setter())) {
                    docs.register(MLuaDocBuilder.Registration.signature("RenderObject",
                        "RenderObject:" + property.setter() + "(value: " + property.type() + ")", "void"));
                }
            }
        }
    }

    private static boolean hasMethodRegistration(MLuaDocBuilder docs, String receiver, String name) {
        return docs.signatures().stream().anyMatch(signature -> signature.receiver().equals(receiver)
                && registeredMethodName(signature.signature()).equals(name))
            || docs.methods().stream().anyMatch(method -> method.receiver().equals(receiver)
                && registeredMethodName(method.signature()).equals(name));
    }

    private static String registeredMethodName(String signature) {
        if (signature == null) return "";
        int open = signature.indexOf('(');
        if (open < 0) return "";
        String prefix = signature.substring(0, open).trim();
        int separator = Math.max(prefix.lastIndexOf(':'), prefix.lastIndexOf('.'));
        return prefix.substring(separator + 1).trim();
    }

    private static void registerObjectProperty(MLuaRegistration registration, String name, String type,
                                               String description, String getter, String setter) {
        String propertyName = name;
        LuaValue get = new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                if ("Id".equals(propertyName)) return self.checktable().rawget(MLuaRenderService.OBJECT_ID);
                if ("Type".equals(propertyName)) return self.checktable().rawget("__mluaRenderType");
                return invokeObjectOperation(self, getter, LuaValue.NONE).arg1();
            }
        };
        LuaValue set = setter == null ? LuaValue.NIL : new TwoArgFunction() {
            @Override public LuaValue call(LuaValue self, LuaValue value) {
                invokeObjectOperation(self, setter, LuaValue.varargsOf(new LuaValue[]{value}));
                return LuaValue.NONE;
            }
        };
        registration.registerClassProperty("RenderObject", name, get, set, type, "client:Client", description);
    }

    private static void registerObjectMethod(MLuaRegistration registration, String name, String description) {
        String methodName = name;
        registration.registerClassFunction("RenderObject", name, new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return invokeObjectOperation(args.arg1(), methodName, args.subargs(2)); }
        }, description);
    }

    private static Varargs invokeObjectOperation(LuaValue self, String name, Varargs values) {
        LuaValue[] args = new LuaValue[values.narg() + 1];
        args[0] = self;
        for (int i = 1; i < args.length; i++) args[i] = values.arg(i);
        LuaValue operations = self.checktable().rawget(MLuaRenderService.OBJECT_OPERATIONS);
        LuaValue operation = operations.istable() ? operations.checktable().rawget(name) : LuaValue.NIL;
        if (!operation.isfunction()) throw new LuaError("[MLua] RenderObject." + name + " is unavailable on this instance.");
        return operation.invoke(LuaValue.varargsOf(args));
    }
}
