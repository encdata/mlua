package dev.mlua.modules.render.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.math.MLuaVector;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.animation.MLuaAnimationOptions;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.MLuaRenderOwnershipRegistry;
import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import dev.mlua.runtime.client.model.MLuaBBModelLoader;
import dev.mlua.runtime.client.model.MLuaMeshLuaParser;
import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

@MLuaDescription("Client-side HUD, world, all-entity draw, living model, and held-item rendering with addon-owned, frame-scoped callbacks.")
public final class MLuaRenderService extends MLuaReflectiveService {
    private static final String OBJECT_OPERATIONS = "__mlua_render_object_operations";
    private static final String MODEL_PART_HANDLE = "__mlua_render_model_part";
    public static final String OBJECT_ID = "__mlua_render_object_id";
    private record ModelPartHandle(int objectId, MLuaRenderOwnershipRegistry.Owner owner, String partId,
                                  MLuaRegistration registration) { }
    private record ResolvedModelPart(ModelPartHandle handle, MLuaClientRenderManager.RenderObject object,
                                     MLuaBBModelAsset.Bone bone) { }
    private static final Map<String, LuaValue> MODEL_PART_METHODS = modelPartMethods();
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
        new ObjectProperty("Target", "string", "Automatic destination: Manual, Hud, WorldOverlay, WorldModel, or EntityModel. EntityModel follows an entity root or a living model bone. Manual objects render only when submitted explicitly through a RenderContext.", "GetTarget", "SetTarget"),
        new ObjectProperty("RenderStage", "string", "The cross-loader world render stage used by WorldModel objects. Defaults to AFTER_ENTITIES; NeoForge 26.1 currently submits WorldModel objects at AFTER_ENTITIES only.", "GetRenderStage", "SetRenderStage"),
        new ObjectProperty("ScaleWithDistance", "boolean", "Whether world-space size changes with camera distance.", "GetScaleWithDistance", "SetScaleWithDistance"),
        new ObjectProperty("ModelScale", "number", "The finite uniform scale applied to this object or model; at least 0.001.", "GetModelScale", "SetModelScale"),
        new ObjectProperty("FollowEntity", "Entity|number|nil", "The entity followed by this object. Assign an entity instance or id, or nil to clear it.", "GetFollowEntity", "SetFollowEntity"),
        new ObjectProperty("FollowBone", "string|nil", "A registered model adapter bone on the followed entity. When unset, EntityModel attaches at the living entity model root.", "GetFollowBone", "SetFollowBone"),
        new ObjectProperty("FollowOffset", "CFrame|function", "A local transform after the followed entity root or model bone. A function returning CFrame is sampled once per client tick.", "GetFollowOffset", "SetFollowOffset"),
        new ObjectProperty("Resource", "string|nil", "The resource identifier for image, item, or block objects; nil for other render types.", "GetResource", "SetResource"),
        new ObjectProperty("Text", "string|nil", "The displayed text; available only on text objects.", "GetText", "SetText")
    );
    private final ReferenceQueue<LuaTable> collectedObjectHandles = new ReferenceQueue<>();
    private final Map<Integer, ObjectHandleReference> objectHandles = new ConcurrentHashMap<>();

    private dev.mlua.core.MLuaContext renderOwnerContext() {
        dev.mlua.core.MLuaContext context = MLuaLuaBindings.boundContext(this.context.mlua().globals());
        if (context == null) throw new LuaError("[MLua] Render callbacks can only be registered while a Lua addon is executing.");
        return context;
    }
    public static void registerRenderObjectClass(MLuaRegistration registration) {
        String description = "A live client render instance with mutable screen-space or world-space properties and rendering methods.";
        registration.registerClass("RenderObject", registration.requireInstanceBaseClassName(), description);
        registerModelPartClass(registration);
        for (ObjectProperty property : OBJECT_PROPERTIES) {
            registerObjectProperty(registration, property.name(), property.type(), property.description(),
                property.getter(), property.setter());
        }
        String[][] methods = {
            {"GetId", "Returns the object's runtime identifier."}, {"GetRenderType", "Returns the Minecraft render primitive used by this object."},
            {"Equals", "Compares this render handle with another handle for the same owned render object."},
            {"GetHashCode", "Returns a hash derived from this handle's owner and runtime identifier."},
            {"ToString", "Returns the runtime class, render primitive, owner, and identifier."},
            {"GetIsDestroyed", "Returns whether this object has been removed."},
            {"GetPosition", "Returns the object's screen position."}, {"SetPosition", "Changes the object's screen position."},
            {"GetEndPosition", "Returns the second screen position."}, {"SetEndPosition", "Changes the second screen position."},
            {"GetWorldPosition", "Returns the object's world position."}, {"SetWorldPosition", "Changes the object's world position."},
            {"GetEndWorldPosition", "Returns the second world position."}, {"SetEndWorldPosition", "Changes the second world position."},
            {"GetDepth", "Returns the depth offset."}, {"SetDepth", "Sets a finite, non-negative depth offset."},
            {"GetSize", "Returns the object's width and height."}, {"SetSize", "Sets finite, non-negative width and height values."},
            {"GetRotation", "Returns the rotation in degrees."}, {"SetRotation", "Changes the rotation in degrees."},
            {"GetVisible", "Returns whether the object is visible."}, {"SetVisible", "Shows or hides the object."},
            {"GetTransparency", "Returns the object's transparency."}, {"SetTransparency", "Changes the object's transparency."},
            {"GetAlpha", "Returns the object's opacity."}, {"SetAlpha", "Changes the object's opacity."},
            {"GetColor", "Returns the packed color value."}, {"SetColor", "Changes the packed color value."},
            {"GetThickness", "Returns the line or outline thickness."}, {"SetThickness", "Sets a finite thickness of at least one pixel."},
            {"GetZIndex", "Returns the screen draw order."}, {"SetZIndex", "Changes the screen draw order."},
            {"GetWorldSpace", "Returns whether the object is positioned in the world."}, {"SetWorldSpace", "Changes whether the object is positioned in the world."},
            {"GetTarget", "Returns Manual, Hud, WorldOverlay, WorldModel, or EntityModel using the canonical spelling accepted by SetTarget."}, {"SetTarget", "Selects Manual, Hud, WorldOverlay, WorldModel, or EntityModel; matching is case-insensitive."},
            {"GetRenderStage", "Returns the selected world render stage."}, {"SetRenderStage", "Selects a supported world render stage for WorldModel rendering."},
            {"GetScaleWithDistance", "Returns whether world-space size follows camera distance."}, {"SetScaleWithDistance", "Changes distance-based scaling."},
            {"GetModelScale", "Returns the uniform model scale."}, {"SetModelScale", "Sets a finite uniform model scale of at least 0.001."},
            {"GetFollowEntity", "Returns the followed entity id, or nil."}, {"SetFollowEntity", "Attaches the object to an entity or clears the attachment with nil."},
            {"ClearFollowEntity", "Stops the object from following an entity."},
            {"GetFollowBone", "Returns the registered model bone used by this attachment, or nil."},
            {"SetFollowBone", "Attaches a 3D object to a model bone registered by ModelService."},
            {"GetFollowOffset", "Returns the local CFrame or callback used after the followed model bone."},
            {"SetFollowOffset", "Sets a local CFrame or a function returning CFrame, sampled once per client tick. A failing function is disabled and the last valid transform is kept."},
            {"GetResource", "Returns the resource identifier for an image, item, or block object; otherwise nil."},
            {"SetResource", "Changes the resource identifier on image, item, or block objects."},
            {"GetText", "Returns displayed text on text objects; otherwise nil."},
            {"SetText", "Changes displayed text on text objects."},
            {"GetAnimations", "Returns animation names available on a Blockbench model."},
            {"GetAnimationName", "Returns the selected Blockbench animation name, or nil when no pose is selected."},
            {"IsAnimationPlaying", "Returns whether the selected Blockbench animation is advancing."},
            {"GetModelParts", "Returns live RenderPart instances for a Blockbench model's bone hierarchy."},
            {"GetModelPart", "Returns a model part by its unique name or UUID, or nil when it does not exist."},
            {"GetModelPartById", "Returns a model part by its stable Blockbench UUID, or nil when it does not exist."},
            {"PlayAnimation", "Plays a named Blockbench model animation."},
            {"StopAnimation", "Stops the current Blockbench model animation and restores the base pose."},
            {"GetAnimationTime", "Returns elapsed time for the current model animation."},
            {"Destroy", "Removes this render object."}
        };
        for (String[] method : methods) registerObjectMethod(registration, method[0], method[1]);
    }

    private static void registerModelPartClass(MLuaRegistration registration) {
        registration.registerClass("RenderPart", registration.requireInstanceBaseClassName(),
            "A live handle to one bone in an owned Blockbench RenderObject. Part transforms are local to the bone and compose with its animation and parent transforms.");
        registration.registerClassFunction("RenderPart", "new", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                throw new LuaError("[MLua] RenderPart instances come from RenderObject:GetModelPart, GetModelPartById, or GetModelParts; they cannot be constructed directly.");
            }
        }, "RenderPart handles are created by RenderObject model-part lookup methods.");
        for (Map.Entry<String, LuaValue> method : MODEL_PART_METHODS.entrySet()) {
            registration.registerClassFunction("RenderPart", method.getKey(), method.getValue(),
                modelPartMethodDescription(method.getKey()));
        }
        registration.registerClassProperty("RenderPart", "Name", MODEL_PART_METHODS.get("GetName"), LuaValue.NIL,
            "string", "client:Client", "The Blockbench bone name.");
        registration.registerClassProperty("RenderPart", "Id", MODEL_PART_METHODS.get("GetId"), LuaValue.NIL,
            "string", "client:Client", "The stable Blockbench bone UUID; use it when names are duplicated.");
        registration.registerClassProperty("RenderPart", "IsValid", MODEL_PART_METHODS.get("IsValid"), LuaValue.NIL,
            "boolean", "client:Client", "Whether this handle still refers to a bone on a live Blockbench RenderObject.");
        registration.registerClassProperty("RenderPart", "Parent", MODEL_PART_METHODS.get("GetParent"), LuaValue.NIL,
            "RenderPart|nil", "client:Client", "The direct parent part, or nil for a root bone.");
        registration.registerClassProperty("RenderPart", "Children", MODEL_PART_METHODS.get("GetChildren"), LuaValue.NIL,
            "RenderPart[]", "client:Client", "The direct child parts in model order.");
        registration.registerClassProperty("RenderPart", "Transform", MODEL_PART_METHODS.get("GetTransform"),
            MODEL_PART_METHODS.get("SetTransform"), "CFrame", "client:Client",
            "A local transform override composed with the part's base and animation transforms.");
    }

    private static Map<String, LuaValue> modelPartMethods() {
        Map<String, LuaValue> methods = new LinkedHashMap<>();
        methods.put("GetName", modelPartMethod("GetName"));
        methods.put("GetId", modelPartMethod("GetId"));
        methods.put("IsValid", modelPartMethod("IsValid"));
        methods.put("GetParent", modelPartMethod("GetParent"));
        methods.put("GetChildren", modelPartMethod("GetChildren"));
        methods.put("GetTransform", modelPartMethod("GetTransform"));
        methods.put("SetTransform", modelPartMethod("SetTransform"));
        methods.put("ResetTransform", modelPartMethod("ResetTransform"));
        methods.put("Equals", modelPartMethod("Equals"));
        return Collections.unmodifiableMap(methods);
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
                if ("Id".equals(propertyName)) return self.checktable().rawget(OBJECT_ID);
                if ("Type".equals(propertyName)) return self.checktable().rawget("__mluaRenderType");
                return invokeObjectOperation(self, getter).arg1();
            }
        };
        LuaValue set = setter == null ? LuaValue.NIL : new TwoArgFunction() {
            @Override public LuaValue call(LuaValue self, LuaValue value) {
                invokeObjectOperation(self, setter, value);
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

    private static Varargs invokeObjectOperation(LuaValue self, String name, LuaValue... values) {
        LuaValue operations = self.checktable().rawget(OBJECT_OPERATIONS);
        LuaValue operation = operations.istable() ? operations.checktable().rawget(name) : LuaValue.NIL;
        if (!operation.isfunction()) throw new LuaError("[MLua] RenderObject." + name + " is unavailable on this instance.");
        LuaValue[] args = new LuaValue[values.length + 1];
        args[0] = self;
        System.arraycopy(values, 0, args, 1, values.length);
        return operation.invoke(LuaValue.varargsOf(args));
    }

    private static Varargs invokeObjectOperation(LuaValue self, String name, Varargs values) {
        LuaValue[] args = new LuaValue[values.narg() + 1];
        args[0] = self;
        for (int i = 1; i < args.length; i++) args[i] = values.arg(i);
        LuaValue operations = self.checktable().rawget(OBJECT_OPERATIONS);
        LuaValue operation = operations.istable() ? operations.checktable().rawget(name) : LuaValue.NIL;
        if (!operation.isfunction()) throw new LuaError("[MLua] RenderObject." + name + " is unavailable on this instance.");
        return operation.invoke(LuaValue.varargsOf(args));
    }

    public MLuaRenderService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "RenderService";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Client");
        return table;
    }

    @MLuaDescription("Creates a screen-space rectangle render object.")
    public LuaValue CreateRect(Optional<LuaValue> properties) {
        return createObject(MLuaClientRenderManager.RenderType.RECT, null, optionalTable(properties));
    }

    @MLuaDescription("Creates screen-space text from a string.")
    public LuaValue CreateText(String text, Optional<LuaValue> properties) {
        return createObject(MLuaClientRenderManager.RenderType.TEXT, text, optionalTable(properties));
    }

    @MLuaDescription("Creates a screen-space image from a resource identifier.")
    public LuaValue CreateImage(String resource, Optional<LuaValue> properties) {
        return createObject(MLuaClientRenderManager.RenderType.IMAGE, resource, optionalTable(properties));
    }

    @MLuaDescription("Creates a screen-space item render object.")
    public LuaValue CreateItem(String itemId, Optional<LuaValue> properties) {
        return createObject(MLuaClientRenderManager.RenderType.ITEM, itemId, optionalTable(properties));
    }

    @MLuaDescription("Creates a screen-space block render object.")
    public LuaValue CreateBlock(String blockId, Optional<LuaValue> properties) {
        return createObject(MLuaClientRenderManager.RenderType.BLOCK, blockId, optionalTable(properties));
    }

    @MLuaDescription("Creates a mesh render object from vertices and indices. Meshes are limited to 16384 vertices and 49152 indices.")
    public LuaValue CreateMesh(LuaTable definition, Optional<LuaValue> properties) {
        return createMesh(definition, optionalTable(properties));
    }

    @MLuaDescription("Loads a Blockbench model as a render object. Source files are limited to 8 MiB, with limits of 8192 cubes, 2048 bones, 256 textures and animations, and 32768 keyframes per animation.")
    public LuaValue LoadBBModel(String path, Optional<LuaValue> properties) {
        return loadBBModel(path, optionalTable(properties));
    }

    @MLuaDescription("Creates a rectangle at a world position.")
    public LuaValue CreateWorldRect(LuaValue position, Optional<LuaValue> properties) {
        return createWorldObject(MLuaClientRenderManager.RenderType.RECT, null, position, optionalTable(properties));
    }

    @MLuaDescription("Creates text at a world position.")
    public LuaValue CreateWorldText(String text, LuaValue position, Optional<LuaValue> properties) {
        return createWorldObject(MLuaClientRenderManager.RenderType.TEXT, text, position, optionalTable(properties));
    }

    @MLuaDescription("Creates an image at a world position.")
    public LuaValue CreateWorldImage(String resource, LuaValue position, Optional<LuaValue> properties) {
        return createWorldObject(MLuaClientRenderManager.RenderType.IMAGE, resource, position, optionalTable(properties));
    }

    @MLuaDescription("Creates an item render object at a world position.")
    public LuaValue CreateWorldItem(String itemId, LuaValue position, Optional<LuaValue> properties) {
        return createWorldObject(MLuaClientRenderManager.RenderType.ITEM, itemId, position, optionalTable(properties));
    }

    @MLuaDescription("Creates a block render object at a world position.")
    public LuaValue CreateWorldBlock(String blockId, LuaValue position, Optional<LuaValue> properties) {
        return createWorldObject(MLuaClientRenderManager.RenderType.BLOCK, blockId, position, optionalTable(properties));
    }

    @MLuaDescription("Creates a camera-facing image at a world position.")
    public LuaValue CreateWorldBillboard(String resource, LuaValue position, Optional<LuaValue> properties) {
        return createWorldObject(MLuaClientRenderManager.RenderType.IMAGE, resource, position, optionalTable(properties));
    }

    @MLuaDescription("Creates a line between two world positions.")
    public LuaValue CreateWorldLine(LuaValue start, LuaValue end, Optional<LuaValue> properties) {
        this.requireClientSide(this.name());
        double[] startPosition = worldVector(start);
        double[] endPosition = worldVector(end);
        int id = MLuaClientRenderManager.create(MLuaClientRenderManager.RenderType.LINE, null, owner());
        return configureObject(id, optionalTable(properties), object -> {
            object.setWorldSpace(true);
            object.setWorldPosition(startPosition[0], startPosition[1], startPosition[2]);
            object.setEndWorldPosition(endPosition[0], endPosition[1], endPosition[2]);
        });
    }

    @MLuaDescription("Creates a beam between two world positions.")
    public LuaValue CreateWorldBeam(LuaValue start, LuaValue end, Optional<LuaValue> properties) {
        this.requireClientSide(this.name());
        double[] startPosition = worldVector(start);
        double[] endPosition = worldVector(end);
        int id = MLuaClientRenderManager.create(MLuaClientRenderManager.RenderType.BEAM, null, owner());
        return configureObject(id, optionalTable(properties), object -> {
            object.setWorldSpace(true);
            object.setWorldPosition(startPosition[0], startPosition[1], startPosition[2]);
            object.setEndWorldPosition(endPosition[0], endPosition[1], endPosition[2]);
        });
    }

    @MLuaDescription("Creates a box render object at a world position.")
    public LuaValue CreateWorldBox(LuaValue center, LuaValue size, Optional<LuaValue> properties) {
        this.requireClientSide(this.name());
        double[] centerPosition = worldVector(center);
        double[] dimensions = worldVector(size);
        if (dimensions[0] < 0.0 || dimensions[1] < 0.0 || dimensions[2] < 0.0) {
            throw new LuaError("[MLua] World box size components must be non-negative.");
        }
        int id = MLuaClientRenderManager.create(MLuaClientRenderManager.RenderType.BOX, null, owner());
        return configureObject(id, optionalTable(properties), object -> {
            object.setWorldSpace(true);
            object.setWorldPosition(centerPosition[0], centerPosition[1], centerPosition[2]);
            object.setSize(dimensions[0], dimensions[1]);
            object.setDepth(dimensions[2]);
        });
    }

    @MLuaDescription("Removes render objects owned by the calling mod.")
    public void Clear() {
        this.requireClientSide(this.name());
        MLuaClientRenderManager.clearOwned(owner());
        objectHandles.clear();
    }

    @MLuaDescription("Returns live render object handles owned by the calling mod.")
    public LuaTable GetAll() {
        LuaTable objects = new LuaTable();
        int index = 1;
        for (Integer id : MLuaClientRenderManager.ids(owner())) {
            if (MLuaClientRenderManager.get(id, owner()) != null) objects.set(index++, objectTable(id));
        }
        return objects;
    }

    @MLuaDescription("Registers a Lua callback at the living entity renderer for this entity type. The callback receives the live Entity and a RenderContext containing Minecraft's PoseStack, BufferSource, model, partial tick, light, and overlay values.")
    public void RegisterEntityRenderer(String entityType, LuaValue callback) {
        this.requireClientSide(this.name());
        dev.mlua.runtime.client.MLuaRenderCallbacks.register(entityType, callback, renderOwnerContext());
    }

    @MLuaDescription("Registers an independently owned callback at the living renderer for an entity type. Use a resource id so multiple passes can coexist and be removed separately.")
    public void RegisterEntityRenderPass(String entityType, String id, LuaValue callback) {
        this.requireClientSide(this.name());
        dev.mlua.runtime.client.MLuaRenderCallbacks.register(entityType, id, callback, renderOwnerContext());
    }

    @MLuaDescription("Removes this addon's callback from an entity type's living entity renderer.")
    public boolean UnregisterEntityRenderer(String entityType) {
        this.requireClientSide(this.name());
        return dev.mlua.runtime.client.MLuaRenderCallbacks.unregister(entityType, renderOwnerContext());
    }

    @MLuaDescription("Removes only the named callback registered for this entity type.")
    public boolean UnregisterEntityRenderPass(String entityType, String id) {
        this.requireClientSide(this.name());
        return dev.mlua.runtime.client.MLuaRenderCallbacks.unregister(entityType, id, renderOwnerContext());
    }

    @MLuaDescription("Registers an entity-level render pass before Minecraft draws an entity renderer. It works for living and non-living entities, including projectiles and dropped items, and receives the live Entity plus the frame-scoped render pose, source, renderer, light, and native render state when the loader provides one.")
    public void RegisterEntityDrawPass(String entityType, String id, LuaValue callback) {
        this.requireClientSide(this.name());
        dev.mlua.runtime.client.MLuaRenderCallbacks.registerEntityDraw(entityType, id, callback, renderOwnerContext());
    }

    @MLuaDescription("Removes only the named entity-level render pass registered for this entity type.")
    public boolean UnregisterEntityDrawPass(String entityType, String id) {
        this.requireClientSide(this.name());
        return dev.mlua.runtime.client.MLuaRenderCallbacks.unregisterEntityDraw(entityType, id, renderOwnerContext());
    }

    @MLuaDescription("Registers an owned callback while a matching item is rendered in the first-person hand. The callback receives the player and a frame-scoped RenderContext with the hand, item stack, pose stack, buffer source, and light value.")
    public void RegisterFirstPersonItemRenderer(String itemId, LuaValue callback) {
        this.requireClientSide(this.name());
        dev.mlua.runtime.client.MLuaRenderCallbacks.registerFirstPersonItem(itemId, callback, renderOwnerContext());
    }

    @MLuaDescription("Registers an independently owned first-person item render pass. Use a resource id so multiple addons or effects can coexist for the same item.")
    public void RegisterFirstPersonItemRenderPass(String itemId, String id, LuaValue callback) {
        this.requireClientSide(this.name());
        dev.mlua.runtime.client.MLuaRenderCallbacks.registerFirstPersonItem(itemId, id, callback, renderOwnerContext());
    }

    @MLuaDescription("Removes this addon's first-person item render callback for the given item.")
    public boolean UnregisterFirstPersonItemRenderer(String itemId) {
        this.requireClientSide(this.name());
        return dev.mlua.runtime.client.MLuaRenderCallbacks.unregisterFirstPersonItem(itemId, renderOwnerContext());
    }

    @MLuaDescription("Removes only the named first-person item render pass for the given item.")
    public boolean UnregisterFirstPersonItemRenderPass(String itemId, String id) {
        this.requireClientSide(this.name());
        return dev.mlua.runtime.client.MLuaRenderCallbacks.unregisterFirstPersonItem(itemId, id, renderOwnerContext());
    }

    @MLuaDescription("Registers a callback while a matching item is rendered in a living entity's third-person hand. It receives the living entity and a frame-scoped RenderContext with the translated item pose, item stack, arm, hand, display context, render source, and light. MLua reads PartialTick from the render state when available, then falls back to Minecraft's client tick tracker; HasPartialTick is false only when neither source supplies a finite value.")
    public void RegisterThirdPersonItemRenderer(String itemId, LuaValue callback) {
        this.requireClientSide(this.name());
        dev.mlua.runtime.client.MLuaRenderCallbacks.registerThirdPersonItem(itemId, callback, renderOwnerContext());
    }

    @MLuaDescription("Registers an independently owned third-person held-item render pass for an item. A resource id lets addons coexist and unregister one pass without removing others.")
    public void RegisterThirdPersonItemRenderPass(String itemId, String id, LuaValue callback) {
        this.requireClientSide(this.name());
        dev.mlua.runtime.client.MLuaRenderCallbacks.registerThirdPersonItem(itemId, id, callback, renderOwnerContext());
    }

    @MLuaDescription("Removes this addon's third-person held-item callback for the given item.")
    public boolean UnregisterThirdPersonItemRenderer(String itemId) {
        this.requireClientSide(this.name());
        return dev.mlua.runtime.client.MLuaRenderCallbacks.unregisterThirdPersonItem(itemId, renderOwnerContext());
    }

    @MLuaDescription("Removes only the named third-person held-item render pass for the given item.")
    public boolean UnregisterThirdPersonItemRenderPass(String itemId, String id) {
        this.requireClientSide(this.name());
        return dev.mlua.runtime.client.MLuaRenderCallbacks.unregisterThirdPersonItem(itemId, id, renderOwnerContext());
    }

    @MLuaDescription("Registers an addon-owned callback at the native world render point after Minecraft renders world entities and geometry.")
    public void RegisterWorldRenderer(LuaValue callback) {
        this.requireClientSide(this.name());
        dev.mlua.runtime.client.MLuaRenderCallbacks.registerWorld(callback, renderOwnerContext());
    }

    @MLuaDescription("Registers the addon's default callback at a cross-loader world stage: BEFORE_ENTITIES, AFTER_ENTITIES, AFTER_TRANSLUCENT, or AFTER_LEVEL. Registering it at another stage moves the same callback instead of leaving a duplicate at the old stage. MLua maps the stage to the nearest native Fabric, Forge, or NeoForge render event; the callback receives that native event/context in RenderContext.")
    public void RegisterWorldRendererAt(String stage, LuaValue callback) {
        this.requireClientSide(this.name());
        dev.mlua.runtime.client.MLuaRenderCallbacks.registerWorld(stage, callback, renderOwnerContext());
    }

    @MLuaDescription("Registers an independently owned world render pass by resource id. Re-registering that id at another stage moves the pass. The id can be removed without affecting other passes from this addon.")
    public void RegisterWorldRenderPass(String id, String stage, LuaValue callback) {
        this.requireClientSide(this.name());
        dev.mlua.runtime.client.MLuaRenderCallbacks.registerWorld(id, stage, callback, renderOwnerContext());
    }

    @MLuaDescription("Removes this addon's callback from the native world render point.")
    public boolean UnregisterWorldRenderer() {
        this.requireClientSide(this.name());
        return dev.mlua.runtime.client.MLuaRenderCallbacks.unregisterWorld(renderOwnerContext());
    }

    @MLuaDescription("Removes this addon's callback from the selected world render stage.")
    public boolean UnregisterWorldRendererAt(String stage) {
        this.requireClientSide(this.name());
        return dev.mlua.runtime.client.MLuaRenderCallbacks.unregisterWorld(stage, renderOwnerContext());
    }

    @MLuaDescription("Removes this addon's world render pass with the given resource id, regardless of its registered stage.")
    public boolean UnregisterWorldRenderPass(String id) {
        this.requireClientSide(this.name());
        return dev.mlua.runtime.client.MLuaRenderCallbacks.unregisterWorldPass(id, renderOwnerContext());
    }

    @MLuaDescription("Returns the cross-loader world render stage names supported by RenderService.")
    public LuaTable GetWorldRenderStages() {
        this.requireClientSide(this.name());
        LuaTable stages = new LuaTable();
        java.util.List<String> supported = dev.mlua.loaderapi.ClientBridge.WORLD_RENDER_STAGES;
        for (int index = 0; index < supported.size(); index++) stages.set(index + 1, supported.get(index));
        return stages;
    }

    @MLuaDescription("Returns the world render stages supported by managed Mesh and BBModel objects on this loader and Minecraft version.")
    public LuaTable GetWorldModelRenderStages() {
        this.requireClientSide(this.name());
        LuaTable stages = new LuaTable();
        java.util.List<String> supported = dev.mlua.loaderapi.LoaderApi.client().worldModelRenderStages();
        for (int index = 0; index < supported.size(); index++) stages.set(index + 1, supported.get(index));
        return stages;
    }

    @MLuaDescription("Returns render callback capacity and usage. Active is the global count; ActiveOwned is the count registered by the calling addon runtime. MaxTotal is shared by all addons, MaxPerOwner limits one addon runtime, and MaxPerTarget applies to each entity type, item, HUD layer, or world stage.")
    public LuaTable GetRenderCallbackLimits() {
        LuaTable limits = new LuaTable();
        limits.set("Active", dev.mlua.runtime.client.MLuaRenderCallbacks.registeredCallbackCount());
        limits.set("ActiveOwned", dev.mlua.runtime.client.MLuaRenderCallbacks.registeredCallbackCount(renderOwnerContext()));
        limits.set("MaxTotal", dev.mlua.runtime.client.MLuaRenderCallbacks.MAX_CALLBACKS);
        limits.set("MaxPerOwner", dev.mlua.runtime.client.MLuaRenderCallbacks.MAX_CALLBACKS_PER_OWNER);
        limits.set("MaxPerTarget", dev.mlua.runtime.client.MLuaRenderCallbacks.MAX_CALLBACKS_PER_TARGET);
        return limits;
    }

    @MLuaDescription("Registers an addon-owned callback in the HUD render layer, before MLua-managed RenderObjects are drawn.")
    public void RegisterHudRenderer(LuaValue callback) {
        this.requireClientSide(this.name());
        dev.mlua.runtime.client.MLuaRenderCallbacks.registerHud(callback, renderOwnerContext());
    }

    @MLuaDescription("Registers an independently owned HUD render pass by resource id, before MLua-managed RenderObjects are drawn.")
    public void RegisterHudRenderPass(String id, LuaValue callback) {
        this.requireClientSide(this.name());
        dev.mlua.runtime.client.MLuaRenderCallbacks.registerHud(id, callback, renderOwnerContext());
    }

    @MLuaDescription("Removes this addon's callback from the HUD render layer.")
    public boolean UnregisterHudRenderer() {
        this.requireClientSide(this.name());
        return dev.mlua.runtime.client.MLuaRenderCallbacks.unregisterHud(renderOwnerContext());
    }

    @MLuaDescription("Removes only the HUD render pass registered with this id.")
    public boolean UnregisterHudRenderPass(String id) {
        this.requireClientSide(this.name());
        return dev.mlua.runtime.client.MLuaRenderCallbacks.unregisterHudPass(id, renderOwnerContext());
    }

    private static LuaTable optionalTable(Optional<LuaValue> value) {
        return value.orElse(LuaValue.NIL).opttable(new LuaTable());
    }

    private LuaValue createObject(MLuaClientRenderManager.RenderType type, String resource, LuaTable props) {
        this.requireClientSide(this.name());
        int id = MLuaClientRenderManager.create(type, resource, owner());
        return configureObject(id, props, null);
    }

    private LuaValue createMesh(LuaTable definition, LuaTable props) {
        this.requireClientSide(this.name());
        var mesh = MLuaMeshLuaParser.parse(definition);
        int id = MLuaClientRenderManager.createMesh(mesh, owner());
        return configureObject(id, props, null);
    }

    private LuaValue loadBBModel(String path, LuaTable props) {
        this.requireClientSide(this.name());
        try {
            if (!property(props, "AnimationPath").isnil() && !property(props, "AnimationsPath").isnil()) {
                throw new LuaError("[MLua] RenderObject properties AnimationPath and AnimationsPath are aliases; provide only one.");
            }
            MLuaBBModelAsset loadedAsset = MLuaBBModelLoader.load(path);
            LuaValue animationPath = firstNonNil(property(props, "AnimationPath"), property(props, "AnimationsPath"));
            if (animationPath.isstring() && !animationPath.tojstring().isBlank()) {
                loadedAsset = loadedAsset.withAnimations(MLuaBBModelLoader.loadAnimations(animationPath.tojstring()));
            }
            final MLuaBBModelAsset asset = loadedAsset;
            int id = MLuaClientRenderManager.createBBModel(asset, owner());
            try {
                var object = requireObject(id);
                applyProps(object, props, "AnimationPath", "AnimationsPath");
                LuaTable table = objectTable(id);
                table.set("__bbmodel", LuaValue.TRUE);
                table.set("FormatVersion", asset.formatVersion());
                table.set("ModelFormat", asset.modelFormat());
                LuaTable operations = table.rawget(OBJECT_OPERATIONS).checktable();
                operations.set("GetAnimations", fn(args -> {
                    LuaTable animations = new LuaTable();
                    int index = 1;
                    for (String name : asset.animations().keySet()) animations.set(index++, name);
                    return animations;
                }));
                operations.set("GetAnimationName", fn(args -> {
                    synchronized (object.executionLock()) {
                        String name = object.animationPlayer().active();
                        return name == null ? LuaValue.NIL : LuaValue.valueOf(name);
                    }
                }));
                operations.set("IsAnimationPlaying", fn(args -> {
                    synchronized (object.executionLock()) {
                        return LuaValue.valueOf(object.animationPlayer().playing());
                    }
                }));
                operations.set("PlayAnimation", fn(args -> {
                    String name = args.checkjstring(2);
                    MLuaAnimationOptions options = MLuaAnimationOptions.fromLua(args.arg(3), "RenderObject:PlayAnimation");
                    synchronized (object.executionLock()) {
                        if (object.destroyed()) throw new LuaError("[MLua] Render object no longer exists");
                        object.animationPlayer().play(name, asset, options.loop(), options.speed());
                    }
                    return LuaValue.NONE;
                }));
                operations.set("StopAnimation", fn(args -> {
                    synchronized (object.executionLock()) { object.animationPlayer().stop(); }
                    return LuaValue.NONE;
                }));
                operations.set("GetAnimationTime", fn(args -> {
                    synchronized (object.executionLock()) { return LuaValue.valueOf(object.animationPlayer().time()); }
                }));
                return table;
            } catch (RuntimeException failure) {
                MLuaClientRenderManager.remove(id, owner());
                throw failure;
            }
        } catch (LuaError error) {
            throw error;
        } catch (IOException | RuntimeException exception) {
            throw new LuaError("[MLua] Failed to load BBModel '" + path + "': " + exception.getMessage());
        }
    }

    private LuaValue createWorldObject(MLuaClientRenderManager.RenderType type, String resource, LuaValue worldPosition, LuaTable props) {
        this.requireClientSide(this.name());
        double[] position = worldVector(worldPosition);
        int id = MLuaClientRenderManager.create(type, resource, owner());
        return configureObject(id, props, object -> {
            object.setWorldSpace(true);
            object.setWorldPosition(position[0], position[1], position[2]);
        });
    }

    /** Roll back a partially configured handle so validation failures never consume quota or retain textures. */
    private LuaValue configureObject(int id, LuaTable props, Consumer<MLuaClientRenderManager.RenderObject> initialize) {
        try {
            MLuaClientRenderManager.RenderObject object = requireObject(id);
            if (initialize != null) initialize.accept(object);
            applyProps(object, props);
            return objectTable(id);
        } catch (RuntimeException failure) {
            MLuaClientRenderManager.remove(id, owner());
            throw failure;
        }
    }

    private synchronized LuaTable objectTable(int id) {
        drainCollectedObjectHandles();
        ObjectHandleReference reference = objectHandles.get(id);
        LuaTable cached = reference == null ? null : reference.get();
        if (cached != null) return cached;
        if (reference != null) objectHandles.remove(id, reference);

        MLuaRenderOwnershipRegistry.Owner renderOwner = owner();
        MLuaClientRenderManager.RenderObject renderObject = requireObject(id);
        MLuaClientRenderManager.RenderType primitive = java.util.Objects.requireNonNull(
            renderObject.type(), "Render object " + id + " has no render primitive");
        String className = renderObject.luaClassName();
        LuaValue renderTypeValue = LuaValue.valueOf(primitive.name().toLowerCase(java.util.Locale.ROOT));
        LuaTable operations = new LuaTable();
        operations.set("GetId", fn(args -> LuaValue.valueOf(id)));
        // The render primitive is immutable, so keep it readable on a destroyed handle.
        operations.set("GetRenderType", fn(args -> renderTypeValue));
        operations.set("Equals", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue other = args.arg(2);
                if (!other.istable() || !context.mlua().registration().isInstanceOf(other, "RenderObject")) {
                    return LuaValue.FALSE;
                }
                LuaTable otherHandle = other.checktable();
                return LuaValue.valueOf(id == otherHandle.rawget(OBJECT_ID).optint(-1)
                    && renderOwner.ownerModId().equals(otherHandle.rawget("__mluaOwnerModId").optjstring("")));
            }
        });
        operations.set("GetHashCode", fn(args -> LuaValue.valueOf(
            java.util.Objects.hash(renderOwner.ownerModId(), id))));
        operations.set("ToString", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf((className == null ? "RenderObject" : className) + "["
                    + primitive.name().toLowerCase(java.util.Locale.ROOT) + ", " + renderOwner.ownerModId() + ":" + id + "]");
            }
        });
        operations.set("GetIsDestroyed", fn(args -> LuaValue.valueOf(MLuaClientRenderManager.get(id, renderOwner) == null)));
        operations.set("GetPosition", fn(args -> {
            var object = requireObject(id);
            return MLuaVector.vector2(object.x(), object.y());
        }));
        operations.set("SetPosition", fn(args -> {
            var object = requireObject(id);
            double[] value = screenVector(args.arg(2));
            object.setPosition(value[0], value[1]);
            return LuaValue.NONE;
        }));
        operations.set("GetEndPosition", fn(args -> {
            var object = requireObject(id);
            return MLuaVector.vector2(object.endX(), object.endY());
        }));
        operations.set("SetEndPosition", fn(args -> {
            double[] value = screenVector(args.arg(2));
            requireObject(id).setEndPosition(value[0], value[1]);
            return LuaValue.NONE;
        }));
        operations.set("GetWorldPosition", fn(args -> {
            var object = requireObject(id);
            return MLuaVector.vector3(object.worldX(), object.worldY(), object.worldZ());
        }));
        operations.set("SetWorldPosition", fn(args -> {
            var object = requireObject(id);
            double[] value = worldVector(args.arg(2));
            object.setWorldPosition(value[0], value[1], value[2]);
            object.setWorldSpace(true);
            return LuaValue.NONE;
        }));
        operations.set("GetEndWorldPosition", fn(args -> {
            var object = requireObject(id);
            return MLuaVector.vector3(object.endWorldX(), object.endWorldY(), object.endWorldZ());
        }));
        operations.set("SetEndWorldPosition", fn(args -> {
            var object = requireObject(id);
            double[] value = worldVector(args.arg(2));
            object.setEndWorldPosition(value[0], value[1], value[2]);
            return LuaValue.NONE;
        }));
        operations.set("GetDepth", fn(args -> LuaValue.valueOf(requireObject(id).depth())));
        operations.set("SetDepth", fn(args -> {
            requireObject(id).setDepth(nonNegative(args.arg(2), "Depth"));
            return LuaValue.NONE;
        }));
        operations.set("GetSize", fn(args -> {
            var object = requireObject(id);
            return MLuaVector.vector2(object.width(), object.height());
        }));
        operations.set("SetSize", fn(args -> {
            var object = requireObject(id);
            double[] value = screenSize(args.arg(2));
            object.setSize(value[0], value[1]);
            return LuaValue.NONE;
        }));
        operations.set("GetRotation", fn(args -> LuaValue.valueOf(requireObject(id).rotation())));
        operations.set("SetRotation", fn(args -> {
            requireObject(id).setRotation(finiteNumber(args.arg(2), "Rotation"));
            return LuaValue.NONE;
        }));
        operations.set("GetVisible", fn(args -> LuaValue.valueOf(requireObject(id).visible())));
        operations.set("SetVisible", fn(args -> {
            requireObject(id).setVisible(args.checkboolean(2));
            return LuaValue.NONE;
        }));
        operations.set("GetTransparency", fn(args -> LuaValue.valueOf(requireObject(id).transparency())));
        operations.set("SetTransparency", fn(args -> {
            requireObject(id).setTransparency(opacity(args.arg(2), "Transparency"));
            return LuaValue.NONE;
        }));
        operations.set("GetAlpha", fn(args -> LuaValue.valueOf(1.0 - requireObject(id).transparency())));
        operations.set("SetAlpha", fn(args -> {
            requireObject(id).setTransparency(1.0 - opacity(args.arg(2), "Alpha"));
            return LuaValue.NONE;
        }));
        operations.set("GetColor", fn(args -> LuaValue.valueOf(requireObject(id).color())));
        operations.set("SetColor", fn(args -> {
            requireObject(id).setColor(packedColor(args.arg(2)));
            return LuaValue.NONE;
        }));
        operations.set("GetThickness", fn(args -> LuaValue.valueOf(requireObject(id).thickness())));
        operations.set("SetThickness", fn(args -> {
            requireObject(id).setThickness(atLeast(args.arg(2), "Thickness", 1.0));
            return LuaValue.NONE;
        }));
        operations.set("GetZIndex", fn(args -> LuaValue.valueOf(requireObject(id).zIndex())));
        operations.set("SetZIndex", fn(args -> {
            requireObject(id).setZIndex(wholeInt(args.arg(2), "ZIndex"));
            return LuaValue.NONE;
        }));
        operations.set("GetWorldSpace", fn(args -> LuaValue.valueOf(requireObject(id).worldSpace())));
        operations.set("SetWorldSpace", fn(args -> {
            requireObject(id).setWorldSpace(args.checkboolean(2));
            return LuaValue.NONE;
        }));
        operations.set("GetTarget", fn(args -> LuaValue.valueOf(renderTargetName(requireObject(id).target()))));
        operations.set("SetTarget", fn(args -> {
            String name = args.checkjstring(2).trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
            try {
                requireObject(id).setTarget(MLuaClientRenderManager.RenderTarget.valueOf(name));
            } catch (IllegalArgumentException invalid) {
                throw new LuaError("[MLua] Invalid render target '" + name + "'. Use Manual, Hud, WorldOverlay, WorldModel, or EntityModel.");
            }
            return LuaValue.NONE;
        }));
        operations.set("GetRenderStage", fn(args -> LuaValue.valueOf(requireObject(id).renderStage())));
        operations.set("SetRenderStage", fn(args -> {
            try {
                requireObject(id).setRenderStage(args.checkjstring(2));
            } catch (IllegalArgumentException invalid) {
                throw new LuaError("[MLua] " + invalid.getMessage());
            }
            return LuaValue.NONE;
        }));
        operations.set("GetScaleWithDistance", fn(args -> LuaValue.valueOf(requireObject(id).scaleWithDistance())));
        operations.set("SetScaleWithDistance", fn(args -> {
            requireObject(id).setScaleWithDistance(args.checkboolean(2));
            return LuaValue.NONE;
        }));
        operations.set("GetModelScale", fn(args -> LuaValue.valueOf(requireObject(id).modelScale())));
        operations.set("SetModelScale", fn(args -> {
            requireObject(id).setModelScale(modelScale(args.arg(2)));
            return LuaValue.NONE;
        }));
        operations.set("GetFollowEntity", fn(args -> {
            MLuaEntityScope scope = requireObject(id).followEntityScope();
            return scope != null ? LuaValue.valueOf(scope.entityId()) : LuaValue.NIL;
        }));
        operations.set("SetFollowEntity", fn(args -> {
            requireObject(id).setFollowEntityScope(resolveOptionalEntityScope(args.arg(2)));
            return LuaValue.NONE;
        }));
        operations.set("ClearFollowEntity", fn(args -> {
            requireObject(id).setFollowEntityScope(null);
            return LuaValue.NONE;
        }));
        operations.set("GetFollowBone", fn(args -> {
            String bone = requireObject(id).followBoneName();
            return bone == null ? LuaValue.NIL : LuaValue.valueOf(bone);
        }));
        operations.set("SetFollowBone", fn(args -> {
            LuaValue value = args.arg(2);
            try {
                requireObject(id).setFollowBoneName(value.isnil() ? null : value.checkjstring());
            } catch (IllegalArgumentException invalid) {
                throw new LuaError("[MLua] " + invalid.getMessage());
            }
            return LuaValue.NONE;
        }));
        operations.set("GetFollowOffset", fn(args -> requireObject(id).followOffsetValue()));
        operations.set("SetFollowOffset", fn(args -> {
            LuaValue value = args.arg(2);
            try {
                if (value.isfunction()) requireObject(id).setFollowOffsetCallback(value, renderOwnerContext());
                else requireObject(id).setFollowOffset(MLuaCFrame.fromLua(value));
            } catch (IllegalArgumentException | LuaError invalid) {
                throw new LuaError("[MLua] FollowOffset must be a CFrame or a function returning CFrame: " + invalid.getMessage());
            }
            return LuaValue.NONE;
        }));
        operations.set("GetModelParts", fn(args -> {
            MLuaClientRenderManager.RenderObject object = requireModelObject(id);
            LuaTable parts = new LuaTable();
            int index = 1;
            for (MLuaBBModelAsset.Bone bone : object.bbmodel().bones()) {
                parts.set(index++, modelPartInstance(context.mlua().registration(), id, renderOwner, bone));
            }
            return parts;
        }));
        operations.set("GetModelPart", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                MLuaClientRenderManager.RenderObject object = requireModelObject(id);
                MLuaBBModelAsset.Bone bone = findModelPart(object.bbmodel(), args.arg(2).checkjstring());
                return bone == null ? LuaValue.NIL
                    : modelPartInstance(context.mlua().registration(), id, renderOwner, bone);
            }
        });
        operations.set("GetModelPartById", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                MLuaClientRenderManager.RenderObject object = requireModelObject(id);
                String partId = args.arg(2).checkjstring();
                MLuaBBModelAsset.Bone bone = object.bbmodel().boneById(partId);
                return bone == null ? LuaValue.NIL
                    : modelPartInstance(context.mlua().registration(), id, renderOwner, bone);
            }
        });
        operations.set("GetResource", fn(args -> {
            var object = requireObject(id);
            if (!supportsResource(object.type())) return LuaValue.NIL;
            String resource = object.resource();
            return resource != null ? LuaValue.valueOf(resource) : LuaValue.NIL;
        }));
        operations.set("SetResource", fn(args -> {
            var object = requireObject(id);
            requireResourceType(object.type(), "SetResource");
            object.setResource(args.arg(2).isnil() ? null : args.checkjstring(2));
            return LuaValue.NONE;
        }));
        operations.set("GetText", fn(args -> {
            var object = requireObject(id);
            if (object.type() != MLuaClientRenderManager.RenderType.TEXT) return LuaValue.NIL;
            String resource = object.resource();
            return resource != null ? LuaValue.valueOf(resource) : LuaValue.NIL;
        }));
        operations.set("SetText", fn(args -> {
            var object = requireObject(id);
            if (object.type() != MLuaClientRenderManager.RenderType.TEXT) {
                throw new LuaError("[MLua] RenderObject:SetText is only available on text render objects.");
            }
            object.setResource(args.arg(2).isnil() ? null : args.checkjstring(2));
            return LuaValue.NONE;
        }));
        operations.set("Destroy", fn(args -> {
            MLuaClientRenderManager.remove(id, renderOwner);
            objectHandles.remove(id);
            return LuaValue.NONE;
        }));
        LuaTable instance = new LuaTable();
        instance.rawset("__mluaOwnerModId", LuaValue.valueOf(renderOwner.ownerModId()));
        instance.rawset(OBJECT_ID, LuaValue.valueOf(id));
        instance.rawset(OBJECT_OPERATIONS, operations);
        LuaTable handle = context.mlua().registration().attachClassInstance(instance, requireObject(id).luaClassName(), null);
        // Keep the immutable primitive query directly on the instance. It remains usable after
        // destruction and avoids resolving the value through a mutable render manager handle.
        handle.rawset("GetRenderType", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return renderTypeValue; }
        });
        handle.rawset("__mluaRenderType", renderTypeValue);
        objectHandles.put(id, new ObjectHandleReference(id, handle, collectedObjectHandles));
        return handle;
    }

    private MLuaClientRenderManager.RenderObject requireModelObject(int id) {
        MLuaClientRenderManager.RenderObject object = requireObject(id);
        if (object.type() != MLuaClientRenderManager.RenderType.BBMODEL || object.bbmodel() == null) {
            throw new LuaError("[MLua] RenderObject model parts are available only on a loaded Blockbench model.");
        }
        return object;
    }

    private static LuaTable modelPartInstance(MLuaRegistration registration, int objectId,
            MLuaRenderOwnershipRegistry.Owner modelOwner, MLuaBBModelAsset.Bone bone) {
        LuaTable fields = new LuaTable();
        LuaTable part = registration.createClassInstance("RenderPart", fields);
        ModelPartHandle handle = new ModelPartHandle(objectId, modelOwner, bone.uuid(), registration);
        part.rawset(MODEL_PART_HANDLE, LuaValue.userdataOf(handle));
        return part;
    }

    private static String modelPartMethodDescription(String name) {
        return switch (name) {
            case "GetName" -> "Returns the part's Blockbench bone name.";
            case "GetId" -> "Returns the part's stable Blockbench bone UUID.";
            case "IsValid" -> "Returns whether this handle still refers to a bone on a live Blockbench RenderObject.";
            case "GetParent" -> "Returns the parent RenderPart, or nil for a root bone.";
            case "GetChildren" -> "Returns the direct child RenderPart instances in model order.";
            case "GetTransform" -> "Returns the local CFrame override composed after the part's base and animated transforms.";
            case "SetTransform" -> "Sets a local CFrame override composed after the part's base and animated transforms.";
            case "ResetTransform" -> "Removes this part's local CFrame override.";
            case "Equals" -> "Compares handles for the same part on the same owned model.";
            default -> throw new IllegalArgumentException("Unknown RenderPart method: " + name);
        };
    }

    private static LuaValue modelPartMethod(String name) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return invokeModelPartMethod(name, args); }
        };
    }

    private static Varargs invokeModelPartMethod(String name, Varargs args) {
        LuaValue self = args.arg1();
        return switch (name) {
            case "GetName" -> LuaValue.valueOf(resolveModelPart(self).bone().name());
            case "GetId" -> LuaValue.valueOf(resolveModelPart(self).bone().uuid());
            case "IsValid" -> LuaValue.valueOf(isValidModelPart(self));
            case "GetParent" -> {
                ResolvedModelPart resolved = resolveModelPart(self);
                String parentId = resolved.bone().parent();
                MLuaBBModelAsset.Bone parent = parentId == null ? null : resolved.object().bbmodel().boneById(parentId);
                yield parent == null ? LuaValue.NIL : modelPartInstance(resolved.handle().registration(),
                    resolved.handle().objectId(), resolved.handle().owner(), parent);
            }
            case "GetChildren" -> {
                ResolvedModelPart resolved = resolveModelPart(self);
                LuaTable children = new LuaTable();
                int index = 1;
                for (MLuaBBModelAsset.Bone child : resolved.object().bbmodel().childrenOf(resolved.bone().uuid())) {
                    children.set(index++, modelPartInstance(resolved.handle().registration(),
                        resolved.handle().objectId(), resolved.handle().owner(), child));
                }
                yield children;
            }
            case "GetTransform" -> {
                ResolvedModelPart resolved = resolveModelPart(self);
                yield resolved.object().modelPartTransform(resolved.handle().partId()).toLua();
            }
            case "SetTransform" -> {
                ResolvedModelPart resolved = resolveModelPart(self);
                try {
                    resolved.object().setModelPartTransform(resolved.handle().partId(), MLuaCFrame.fromLua(args.arg(2)));
                } catch (IllegalArgumentException invalid) {
                    throw new LuaError("[MLua] RenderPart:SetTransform expects a valid CFrame: " + invalid.getMessage());
                }
                yield LuaValue.NONE;
            }
            case "ResetTransform" -> {
                ResolvedModelPart resolved = resolveModelPart(self);
                resolved.object().resetModelPartTransform(resolved.handle().partId());
                yield LuaValue.NONE;
            }
            case "Equals" -> {
                LuaValue other = args.arg(2);
                if (!other.istable()) yield LuaValue.FALSE;
                LuaValue otherHandle = other.checktable().rawget(MODEL_PART_HANDLE);
                if (!otherHandle.isuserdata(ModelPartHandle.class)) yield LuaValue.FALSE;
                yield LuaValue.valueOf(modelPartHandle(self).equals(
                    (ModelPartHandle) otherHandle.touserdata(ModelPartHandle.class)));
            }
            default -> throw new LuaError("[MLua] Unknown RenderPart method: " + name);
        };
    }

    private static ModelPartHandle modelPartHandle(LuaValue value) {
        LuaValue handle = value.istable() ? value.checktable().rawget(MODEL_PART_HANDLE) : LuaValue.NIL;
        if (!handle.isuserdata(ModelPartHandle.class)) {
            throw new LuaError("[MLua] Expected a live RenderPart instance.");
        }
        return (ModelPartHandle) handle.touserdata(ModelPartHandle.class);
    }

    private static boolean isValidModelPart(LuaValue value) {
        if (!value.istable()) return false;
        LuaValue handleValue = value.checktable().rawget(MODEL_PART_HANDLE);
        if (!handleValue.isuserdata(ModelPartHandle.class)) return false;
        ModelPartHandle handle = (ModelPartHandle) handleValue.touserdata(ModelPartHandle.class);
        MLuaClientRenderManager.RenderObject object = MLuaClientRenderManager.get(handle.objectId(), handle.owner());
        return object != null && object.bbmodel() != null && object.bbmodel().boneById(handle.partId()) != null;
    }

    private static ResolvedModelPart resolveModelPart(LuaValue value) {
        ModelPartHandle handle = modelPartHandle(value);
        MLuaClientRenderManager.RenderObject object = MLuaClientRenderManager.get(handle.objectId(), handle.owner());
        if (object == null || object.bbmodel() == null) {
            throw new LuaError("[MLua] This RenderPart is stale because its Blockbench RenderObject was destroyed.");
        }
        MLuaBBModelAsset.Bone bone = object.bbmodel().boneById(handle.partId());
        if (bone == null)
            throw new LuaError("[MLua] This RenderPart is stale because its Blockbench model no longer contains that bone.");
        return new ResolvedModelPart(handle, object, bone);
    }

    private static MLuaBBModelAsset.Bone findModelPart(MLuaBBModelAsset asset, String nameOrId) {
        String selector = nameOrId == null ? "" : nameOrId;
        if (selector.isBlank()) throw new LuaError("[MLua] RenderObject:GetModelPart expects a non-empty bone name or UUID.");
        MLuaBBModelAsset.Bone byId = asset.boneById(selector);
        if (byId != null) return byId;
        List<MLuaBBModelAsset.Bone> matches = asset.bonesNamed(selector);
        if (matches.size() > 1) throw new LuaError("[MLua] Model part name '" + selector
            + "' is ambiguous; use RenderObject:GetModelPartById(uuid).");
        return matches.isEmpty() ? null : matches.get(0);
    }

    private void drainCollectedObjectHandles() {
        ObjectHandleReference reference;
        while ((reference = (ObjectHandleReference)collectedObjectHandles.poll()) != null) {
            objectHandles.remove(reference.id, reference);
        }
    }

    private static final class ObjectHandleReference extends WeakReference<LuaTable> {
        private final int id;

        private ObjectHandleReference(int id, LuaTable handle, ReferenceQueue<LuaTable> queue) {
            super(handle, queue);
            this.id = id;
        }
    }

    private void applyProps(MLuaClientRenderManager.RenderObject object, LuaTable props, String... extraProperties) {
        try {
            String className = renderClassName(props);
            if (context.mlua().registration().classTable(className) == null) {
                throw new LuaError("[MLua] Unknown RenderObject class: " + className);
            }
            if (!context.mlua().registration().isSubclassOf(className, "RenderObject")) {
                throw new LuaError("[MLua] RenderObject ClassName must name a registered subclass of RenderObject.");
            }
            object.setLuaClassName(className);
            validateProperties(props, className, extraProperties);
            applyPropertyValues(object, props, className, extraProperties);
        } catch (RuntimeException | Error failure) {
            MLuaClientRenderManager.remove(object.id(), owner());
            objectHandles.remove(object.id());
            throw failure;
        }
    }

    private String renderClassName(LuaTable props) {
        LuaValue value = property(props, "ClassName");
        if (value.isnil()) return "RenderObject";
        String className = value.checkjstring();
        if (className.isBlank()) throw new LuaError("[MLua] RenderObject ClassName cannot be empty.");
        return className;
    }

    private LuaTable propertyDescriptors(String className) {
        var registration = context.mlua().registration();
        List<String> hierarchy = new ArrayList<>();
        String current = className;
        while (current != null) {
            hierarchy.add(current);
            current = registration.superclassName(current);
        }
        java.util.Collections.reverse(hierarchy);
        LuaTable merged = new LuaTable();
        for (String typeName : hierarchy) {
            LuaTable type = registration.classTable(typeName);
            LuaValue descriptors = type == null ? LuaValue.NIL : type.rawget("__mlua_properties");
            if (!descriptors.istable()) continue;
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs entry = descriptors.checktable().next(key);
                key = entry.arg1();
                if (key.isnil()) break;
                merged.set(key, entry.arg(2));
            }
        }
        return merged;
    }

    private void validateProperties(LuaTable props, String className, String... extraProperties) {
        LuaTable descriptors = propertyDescriptors(className);
        java.util.Set<String> extras = java.util.Set.of(extraProperties);
        java.util.Set<String> provided = new java.util.HashSet<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = props.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (key.type() != LuaValue.TSTRING) {
                throw new LuaError("[MLua] RenderObject properties must use registered string names.");
            }
            String suppliedName = key.tojstring();
            if (upperCamel(suppliedName).equals("ClassName")) continue;
            String propertyName = registeredPropertyName(descriptors, suppliedName);
            String canonicalName = upperCamel(suppliedName);
            if (extras.contains(canonicalName)) continue;
            if (propertyName == null) {
                throw new LuaError("[MLua] Unknown RenderObject property '" + suppliedName
                    + "'. Use a registered writable RenderObject property.");
            }
            if (!provided.add(propertyName)) {
                throw new LuaError("[MLua] RenderObject property '" + propertyName
                    + "' was supplied more than once through aliases.");
            }
            LuaValue descriptor = descriptors.rawget(propertyName);
            if (!descriptor.istable()) {
                throw new LuaError("[MLua] Unknown RenderObject property '" + suppliedName
                    + "'. Use a registered writable RenderObject property.");
            }
            if (!descriptor.checktable().rawget("set").isfunction()) {
                throw new LuaError("[MLua] RenderObject property '" + propertyName + "' is read-only.");
            }
        }
        if (provided.contains("Target") && provided.contains("WorldSpace")) {
            throw new LuaError("[MLua] Set Target or WorldSpace in one RenderObject definition, not both.");
        }
    }

    private void applyPropertyValues(MLuaClientRenderManager.RenderObject object, LuaTable props,
                                     String className, String... extraProperties) {
        LuaTable descriptors = propertyDescriptors(className);
        java.util.Set<String> extras = java.util.Set.of(extraProperties);
        java.util.Map<String, LuaValue> values = new java.util.TreeMap<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = props.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            String suppliedName = key.checkjstring();
            if (upperCamel(suppliedName).equals("ClassName")) continue;
            String canonicalName = upperCamel(suppliedName);
            if (extras.contains(canonicalName)) continue;
            String propertyName = registeredPropertyName(descriptors, suppliedName);
            if (propertyName == null) continue;
            values.put(propertyName, entry.arg(2));
        }

        if (values.containsKey("Transparency") && values.containsKey("Alpha")) {
            throw new LuaError("[MLua] RenderObject properties Alpha and Transparency describe the same value; provide only one.");
        }
        if (values.containsKey("Resource") && values.containsKey("Text")) {
            throw new LuaError("[MLua] RenderObject properties Resource and Text refer to the same content value; provide only one.");
        }

        LuaTable instance = objectTable(object.id());
        for (java.util.Map.Entry<String, LuaValue> entry : values.entrySet()) {
            LuaValue descriptor = descriptors.rawget(entry.getKey());
            LuaValue setter = descriptor.istable() ? descriptor.checktable().rawget("set") : LuaValue.NIL;
            if (!setter.isfunction()) {
                throw new LuaError("[MLua] RenderObject property '" + entry.getKey() + "' has no registered setter.");
            }
            setter.invoke(LuaValue.varargsOf(new LuaValue[]{instance, entry.getValue()}));
        }
        if ((values.containsKey("WorldPosition") || values.containsKey("EndWorldPosition"))
            && !values.containsKey("WorldSpace") && !values.containsKey("Target")) {
            LuaValue descriptor = descriptors.rawget("WorldSpace");
            LuaValue setter = descriptor.istable() ? descriptor.checktable().rawget("set") : LuaValue.NIL;
            if (setter.isfunction()) {
                setter.invoke(LuaValue.varargsOf(new LuaValue[]{instance, LuaValue.TRUE}));
            }
        }
    }

    private static String registeredPropertyName(LuaTable descriptors, String suppliedName) {
        if (descriptors.rawget(suppliedName).istable()) return suppliedName;
        String upperCamel = upperCamel(suppliedName);
        if (descriptors.rawget(upperCamel).istable()) return upperCamel;
        String lowerCamel = suppliedName.isEmpty() ? suppliedName
            : Character.toLowerCase(suppliedName.charAt(0)) + suppliedName.substring(1);
        return descriptors.rawget(lowerCamel).istable() ? lowerCamel : null;
    }

    private static String upperCamel(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static LuaValue property(LuaTable props, String name) {
        LuaValue value = props.get(name);
        if (name.isEmpty()) return value;
        String lowerCamel = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        LuaValue alias = props.get(lowerCamel);
        if (!value.isnil() && !alias.isnil()) {
            throw new LuaError("[MLua] RenderObject properties '" + name + "' and '" + lowerCamel
                + "' are aliases; provide only one.");
        }
        return value.isnil() ? alias : value;
    }

    private static double opacity(LuaValue value, String propertyName) {
        double opacity = finiteNumber(value, propertyName);
        if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
            throw new LuaError("[MLua] RenderObject " + propertyName + " must be between 0 and 1.");
        }
        return opacity;
    }

    private MLuaClientRenderManager.RenderObject requireObject(int id) {
        var object = MLuaClientRenderManager.get(id, owner());
        if (object == null) {
            throw new LuaError("[MLua] Render object no longer exists");
        }
        return object;
    }

    private MLuaRenderOwnershipRegistry.Owner owner() {
        Object runtimeOwner = context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
        return new MLuaRenderOwnershipRegistry.Owner(runtimeOwner, context.mlua().ownerModId());
    }

    private static boolean supportsResource(MLuaClientRenderManager.RenderType type) {
        return type == MLuaClientRenderManager.RenderType.IMAGE
            || type == MLuaClientRenderManager.RenderType.ITEM
            || type == MLuaClientRenderManager.RenderType.BLOCK;
    }

    private static void requireResourceType(MLuaClientRenderManager.RenderType type, String operation) {
        if (type == MLuaClientRenderManager.RenderType.TEXT) {
            throw new LuaError("[MLua] RenderObject:" + operation + " is not available on text objects; use SetText.");
        }
        if (!supportsResource(type)) {
            throw new LuaError("[MLua] RenderObject:" + operation + " is only available on image, item, or block objects.");
        }
    }

    private static LuaValue firstNonNil(LuaValue a, LuaValue b) {
        return !a.isnil() ? a : b;
    }

    private static double[] worldVector(LuaValue value) {
        double[] vector = MLuaCFrame.vector(value);
        for (double component : vector) finiteNumber(component, "WorldPosition");
        return vector;
    }

    private static double[] screenVector(LuaValue value) {
        if (!MLuaVector.isVector2(value)) throw new LuaError("[MLua] Render screen coordinates require Vector2 values");
        LuaTable vector = value.checktable();
        return new double[]{finiteNumber(vector.get("X"), "Position"), finiteNumber(vector.get("Y"), "Position")};
    }

    private static double[] screenSize(LuaValue value) {
        double[] size = screenVector(value);
        if (size[0] < 0.0 || size[1] < 0.0) {
            throw new LuaError("[MLua] RenderObject Size components must be non-negative.");
        }
        return size;
    }

    private static double nonNegative(LuaValue value, String name) {
        return atLeast(value, name, 0.0);
    }

    private static float modelScale(LuaValue value) {
        double scale = atLeast(value, "ModelScale", 0.001);
        if (scale > Float.MAX_VALUE) {
            throw new LuaError("[MLua] RenderObject ModelScale must fit in a finite float.");
        }
        return (float)scale;
    }

    private static double atLeast(LuaValue value, String name, double minimum) {
        double number = finiteNumber(value, name);
        if (number < minimum) {
            throw new LuaError("[MLua] RenderObject " + name + " must be at least " + minimum + ".");
        }
        return number;
    }

    private static double finiteNumber(LuaValue value, String name) {
        return finiteNumber(value.checkdouble(), name);
    }

    private static double finiteNumber(double number, String name) {
        if (!Double.isFinite(number)) throw new LuaError("[MLua] RenderObject " + name + " must be finite.");
        return number;
    }

    private static int wholeInt(LuaValue value, String name) {
        double number = finiteNumber(value, name);
        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new LuaError("[MLua] RenderObject " + name + " must be a whole number within the 32-bit integer range.");
        }
        return (int)number;
    }

    private static String renderTargetName(MLuaClientRenderManager.RenderTarget target) {
        return switch (target) {
            case MANUAL -> "Manual";
            case HUD -> "Hud";
            case WORLD_OVERLAY -> "WorldOverlay";
            case WORLD_MODEL -> "WorldModel";
            case ENTITY_MODEL -> "EntityModel";
        };
    }

    private static int packedColor(LuaValue value) {
        double number = finiteNumber(value, "Color");
        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > 0xffffffffL) {
            throw new LuaError("[MLua] RenderObject Color must be a whole 32-bit packed color value.");
        }
        return (int)(long)number;
    }

    private MLuaEntityScope resolveOptionalEntityScope(LuaValue value) {
        return value.isnil() ? null : resolveEntityScope(value);
    }

    private MLuaEntityScope resolveEntityScope(LuaValue value) {
        if (value.isint()) {
            return this.resolveEntityScope(value.toint());
        }
        if (value.istable()) {
            net.minecraft.world.entity.Entity entity = MLuaEntityWrapper.fromLuaInstance(value, this.context.mlua().registration());
            if (entity != null) return MLuaEntityScope.of(entity);
        }
        throw new LuaError("[MLua] Expected an Entity instance or entity ID");
    }

    private MLuaEntityScope resolveEntityScope(int entityId) {
        net.minecraft.world.level.Level level = MLuaMinecraftClient.instance().level;
        net.minecraft.world.entity.Entity entity = level == null ? null : level.getEntity(entityId);
        if (entity == null) throw new LuaError("[MLua] FollowEntity must reference an entity in the current client world");
        return MLuaEntityScope.of(entity);
    }

    private static VarArgFunction fn(Function<Varargs, LuaValue> body) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    return body.apply(args);
                } catch (LuaError e) {
                    throw e;
                } catch (Exception e) {
                    throw new LuaError("[MLua] " + e.getMessage());
                }
            }
        };
    }
}
