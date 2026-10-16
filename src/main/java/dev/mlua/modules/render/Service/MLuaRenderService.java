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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

@MLuaDescription("Client-side HUD and projected world-space renderables.")
public final class MLuaRenderService extends MLuaReflectiveService {
    private static final String OBJECT_OPERATIONS = "__render_object_operations";
    private static final String OBJECT_ID = "__render_object_id";
    private record ObjectProperty(String name, String type, String description, String getter, String setter) { }
    private static final List<ObjectProperty> OBJECT_PROPERTIES = List.of(
        new ObjectProperty("Id", "number", "The render object's runtime identifier.", "", null),
        new ObjectProperty("Type", "string", "The render object's type.", "GetType", null),
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
        new ObjectProperty("Target", "string", "Render stage: Hud, WorldOverlay, WorldModel, or EntityModel. GetTarget returns the same canonical spelling accepted by SetTarget.", "GetTarget", "SetTarget"),
        new ObjectProperty("RenderStage", "string", "The cross-loader world render stage used by WorldModel objects. Defaults to AFTER_ENTITIES.", "GetRenderStage", "SetRenderStage"),
        new ObjectProperty("ScaleWithDistance", "boolean", "Whether world-space size changes with camera distance.", "GetScaleWithDistance", "SetScaleWithDistance"),
        new ObjectProperty("ModelScale", "number", "The finite uniform scale applied to this object or model; at least 0.001.", "GetModelScale", "SetModelScale"),
        new ObjectProperty("FollowEntity", "Entity|number|nil", "The entity followed by this object. Assign an entity instance or id, or nil to clear it.", "GetFollowEntity", "SetFollowEntity"),
        new ObjectProperty("FollowBone", "string|nil", "A registered model adapter bone to attach this 3D object to on the followed entity.", "GetFollowBone", "SetFollowBone"),
        new ObjectProperty("FollowOffset", "CFrame|function", "A local transform after the followed model bone. A function returning CFrame is sampled once per client tick.", "GetFollowOffset", "SetFollowOffset"),
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
        for (ObjectProperty property : OBJECT_PROPERTIES) {
            registerObjectProperty(registration, property.name(), property.type(), property.description(),
                property.getter(), property.setter());
        }
        String[][] methods = {
            {"GetId", "Returns the object's runtime identifier."}, {"GetType", "Returns the object's render type."},
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
            {"GetTarget", "Returns Hud, WorldOverlay, WorldModel, or EntityModel using the canonical spelling accepted by SetTarget."}, {"SetTarget", "Selects Hud, WorldOverlay, WorldModel, or EntityModel; matching is case-insensitive."},
            {"GetRenderStage", "Returns the selected world render stage."}, {"SetRenderStage", "Selects a supported world render stage for WorldModel rendering."},
            {"GetScaleWithDistance", "Returns whether world-space size follows camera distance."}, {"SetScaleWithDistance", "Changes distance-based scaling."},
            {"GetModelScale", "Returns the uniform model scale."}, {"SetModelScale", "Sets a finite uniform model scale of at least 0.001."},
            {"GetFollowEntity", "Returns the followed entity id, or nil."}, {"SetFollowEntity", "Attaches the object to an entity or clears the attachment with nil."},
            {"ClearFollowEntity", "Stops the object from following an entity."},
            {"GetFollowBone", "Returns the registered model bone used by this attachment, or nil."},
            {"SetFollowBone", "Attaches a 3D object to a model bone registered by ModelService."},
            {"GetFollowOffset", "Returns the local CFrame or callback used after the followed model bone."},
            {"SetFollowOffset", "Sets a local CFrame or a function returning CFrame, sampled once per client tick."},
            {"GetResource", "Returns the resource identifier for an image, item, or block object; otherwise nil."},
            {"SetResource", "Changes the resource identifier on image, item, or block objects."},
            {"GetText", "Returns displayed text on text objects; otherwise nil."},
            {"SetText", "Changes displayed text on text objects."},
            {"GetAnimations", "Returns animation names available on a Blockbench model."},
            {"PlayAnimation", "Plays a named Blockbench model animation."},
            {"StopAnimation", "Stops the current Blockbench model animation."},
            {"GetAnimationTime", "Returns elapsed time for the current model animation."},
            {"Destroy", "Removes this render object."}
        };
        for (String[] method : methods) registerObjectMethod(registration, method[0], method[1]);
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
        LuaValue get = new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                return name.equals("Id") ? self.checktable().rawget(OBJECT_ID) : invokeObjectOperation(self, getter).arg1();
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
        registration.registerClassFunction("RenderObject", name, new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return invokeObjectOperation(args.arg1(), name, args.subargs(2)); }
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

    @MLuaDescription("Creates a mesh render object from vertices and indices.")
    public LuaValue CreateMesh(LuaTable definition, Optional<LuaValue> properties) {
        return createMesh(definition, optionalTable(properties));
    }

    @MLuaDescription("Loads a Blockbench model as a render object.")
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
        ensureClient();
        double[] startPosition = worldVector(start);
        double[] endPosition = worldVector(end);
        int id = MLuaClientRenderManager.create(MLuaClientRenderManager.RenderType.LINE, null, owner());
        var object = requireObject(id);
        object.setWorldSpace(true);
        object.setWorldPosition(startPosition[0], startPosition[1], startPosition[2]);
        object.setEndWorldPosition(endPosition[0], endPosition[1], endPosition[2]);
        applyProps(object, optionalTable(properties));
        return objectTable(id);
    }

    @MLuaDescription("Creates a beam between two world positions.")
    public LuaValue CreateWorldBeam(LuaValue start, LuaValue end, Optional<LuaValue> properties) {
        ensureClient();
        double[] startPosition = worldVector(start);
        double[] endPosition = worldVector(end);
        int id = MLuaClientRenderManager.create(MLuaClientRenderManager.RenderType.BEAM, null, owner());
        var object = requireObject(id);
        object.setWorldSpace(true);
        object.setWorldPosition(startPosition[0], startPosition[1], startPosition[2]);
        object.setEndWorldPosition(endPosition[0], endPosition[1], endPosition[2]);
        applyProps(object, optionalTable(properties));
        return objectTable(id);
    }

    @MLuaDescription("Creates a box render object at a world position.")
    public LuaValue CreateWorldBox(LuaValue center, LuaValue size, Optional<LuaValue> properties) {
        ensureClient();
        double[] centerPosition = worldVector(center);
        double[] dimensions = worldVector(size);
        if (dimensions[0] < 0.0 || dimensions[1] < 0.0 || dimensions[2] < 0.0) {
            throw new LuaError("[MLua] World box size components must be non-negative.");
        }
        int id = MLuaClientRenderManager.create(MLuaClientRenderManager.RenderType.BOX, null, owner());
        var object = requireObject(id);
        object.setWorldSpace(true);
        object.setWorldPosition(centerPosition[0], centerPosition[1], centerPosition[2]);
        object.setSize(dimensions[0], dimensions[1]);
        object.setDepth(dimensions[2]);
        applyProps(object, optionalTable(properties));
        return objectTable(id);
    }

    @MLuaDescription("Removes render objects owned by the calling mod.")
    public void Clear() {
        ensureClient();
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
        ensureClient();
        dev.mlua.runtime.client.MLuaRenderCallbacks.register(entityType, callback, renderOwnerContext());
    }

    @MLuaDescription("Removes this addon's callback from an entity type's living entity renderer.")
    public boolean UnregisterEntityRenderer(String entityType) {
        ensureClient();
        return dev.mlua.runtime.client.MLuaRenderCallbacks.unregister(entityType, renderOwnerContext());
    }

    @MLuaDescription("Registers an addon-owned callback at the native world render point after Minecraft renders world entities and geometry.")
    public void RegisterWorldRenderer(LuaValue callback) {
        ensureClient();
        dev.mlua.runtime.client.MLuaRenderCallbacks.registerWorld(callback, renderOwnerContext());
    }

    @MLuaDescription("Registers a callback at a cross-loader world stage: BEFORE_ENTITIES, AFTER_ENTITIES, AFTER_TRANSLUCENT, or AFTER_LEVEL. MLua maps the stage to the nearest native Fabric, Forge, or NeoForge render event; the callback receives that native event/context in RenderContext.")
    public void RegisterWorldRendererAt(String stage, LuaValue callback) {
        ensureClient();
        dev.mlua.runtime.client.MLuaRenderCallbacks.registerWorld(stage, callback, renderOwnerContext());
    }

    @MLuaDescription("Removes this addon's callback from the native world render point.")
    public boolean UnregisterWorldRenderer() {
        ensureClient();
        return dev.mlua.runtime.client.MLuaRenderCallbacks.unregisterWorld(renderOwnerContext());
    }

    @MLuaDescription("Removes this addon's callback from the selected world render stage.")
    public boolean UnregisterWorldRendererAt(String stage) {
        ensureClient();
        return dev.mlua.runtime.client.MLuaRenderCallbacks.unregisterWorld(stage, renderOwnerContext());
    }

    @MLuaDescription("Returns the cross-loader world render stage names supported by RenderService.")
    public LuaTable GetWorldRenderStages() {
        LuaTable stages = new LuaTable();
        java.util.List<String> supported = dev.mlua.loaderapi.ClientBridge.WORLD_RENDER_STAGES;
        for (int index = 0; index < supported.size(); index++) stages.set(index + 1, supported.get(index));
        return stages;
    }

    @MLuaDescription("Registers an addon-owned callback in the HUD render layer, before MLua-managed RenderObjects are drawn.")
    public void RegisterHudRenderer(LuaValue callback) {
        ensureClient();
        dev.mlua.runtime.client.MLuaRenderCallbacks.registerHud(callback, renderOwnerContext());
    }

    @MLuaDescription("Removes this addon's callback from the HUD render layer.")
    public boolean UnregisterHudRenderer() {
        ensureClient();
        return dev.mlua.runtime.client.MLuaRenderCallbacks.unregisterHud(renderOwnerContext());
    }

    private static LuaTable optionalTable(Optional<LuaValue> value) {
        return value.orElse(LuaValue.NIL).opttable(new LuaTable());
    }

    private LuaValue createObject(MLuaClientRenderManager.RenderType type, String resource, LuaTable props) {
        ensureClient();
        int id = MLuaClientRenderManager.create(type, resource, owner());
        var object = requireObject(id);
        applyProps(object, props);
        return objectTable(id);
    }

    private LuaValue createMesh(LuaTable definition, LuaTable props) {
        ensureClient();
        var mesh = MLuaMeshLuaParser.parse(definition);
        int id = MLuaClientRenderManager.createMesh(mesh, owner());
        applyProps(requireObject(id), props);
        return objectTable(id);
    }

    private LuaValue loadBBModel(String path, LuaTable props) {
        ensureClient();
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
            operations.set("PlayAnimation", fn(args -> {
                String name = args.checkjstring(2);
                MLuaAnimationOptions options = MLuaAnimationOptions.fromLua(args.arg(3), "RenderObject:PlayAnimation");
                object.animationPlayer().play(name, asset, options.loop(), options.speed());
                return LuaValue.NONE;
            }));
            operations.set("StopAnimation", fn(args -> { object.animationPlayer().stop(); return LuaValue.NONE; }));
            operations.set("GetAnimationTime", fn(args -> LuaValue.valueOf(object.animationPlayer().time())));
            return table;
        } catch (LuaError error) {
            throw error;
        } catch (IOException | RuntimeException exception) {
            throw new LuaError("[MLua] Failed to load BBModel '" + path + "': " + exception.getMessage());
        }
    }

    private LuaValue createWorldObject(MLuaClientRenderManager.RenderType type, String resource, LuaValue worldPosition, LuaTable props) {
        ensureClient();
        double[] position = worldVector(worldPosition);
        int id = MLuaClientRenderManager.create(type, resource, owner());
        var object = requireObject(id);
        object.setWorldSpace(true);
        object.setWorldPosition(position[0], position[1], position[2]);
        applyProps(object, props);
        return objectTable(id);
    }

    private synchronized LuaTable objectTable(int id) {
        drainCollectedObjectHandles();
        ObjectHandleReference reference = objectHandles.get(id);
        LuaTable cached = reference == null ? null : reference.get();
        if (cached != null) return cached;
        if (reference != null) objectHandles.remove(id, reference);

        MLuaClientRenderManager.RenderObject renderObject = requireObject(id);
        String renderType = renderObject.type().name().toLowerCase(java.util.Locale.ROOT);
        LuaTable operations = new LuaTable();
        operations.set("GetId", fn(args -> LuaValue.valueOf(id)));
        // Type is immutable identity, so keep it readable on a destroyed handle.
        operations.set("GetType", fn(args -> LuaValue.valueOf(renderType)));
        operations.set("GetIsDestroyed", fn(args -> LuaValue.valueOf(MLuaClientRenderManager.get(id, owner()) == null)));
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
                throw new LuaError("[MLua] Invalid render target '" + name + "'. Use Hud, WorldOverlay, WorldModel, or EntityModel.");
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
                if (value.isfunction()) requireObject(id).setFollowOffsetCallback(value);
                else requireObject(id).setFollowOffset(MLuaCFrame.fromLua(value));
            } catch (IllegalArgumentException | LuaError invalid) {
                throw new LuaError("[MLua] FollowOffset must be a CFrame or a function returning CFrame: " + invalid.getMessage());
            }
            return LuaValue.NONE;
        }));
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
            MLuaClientRenderManager.remove(id, owner());
            objectHandles.remove(id);
            return LuaValue.NONE;
        }));
        LuaTable instance = new LuaTable();
        instance.rawset("__mluaOwnerModId", LuaValue.valueOf(owner().ownerModId()));
        instance.rawset(OBJECT_ID, LuaValue.valueOf(id));
        instance.rawset(OBJECT_OPERATIONS, operations);
        LuaTable handle = context.mlua().registration().attachClassInstance(instance, requireObject(id).luaClassName(), null);
        objectHandles.put(id, new ObjectHandleReference(id, handle, collectedObjectHandles));
        return handle;
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
            if (!key.isstring()) {
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
        object.setExecutionLock(context.mlua().executionLock());
        return object;
    }

    private MLuaRenderOwnershipRegistry.Owner owner() {
        Object runtimeOwner = context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
        return new MLuaRenderOwnershipRegistry.Owner(runtimeOwner, context.mlua().ownerModId());
    }

    private void ensureClient() {
        if (context.mlua().side() != Side.CLIENT) {
            throw new LuaError("[MLua] RenderService is client-side only.");
        }
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
