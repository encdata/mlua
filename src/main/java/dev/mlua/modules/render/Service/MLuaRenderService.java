package dev.mlua.modules.render.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.MLuaRenderOwnershipRegistry;
import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import dev.mlua.runtime.client.model.MLuaBBModelLoader;
import dev.mlua.runtime.client.model.MLuaMeshDefinition;
import dev.mlua.runtime.client.model.MLuaMeshVertex;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

public final class MLuaRenderService extends MLuaServiceBase {
    private static final String OBJECT_OPERATIONS = "__render_object_operations";
    private static final String OBJECT_ID = "__render_object_id";
    public static void registerRenderObjectClass(MLuaRegistration registration) {
        String description = "A live client render instance with mutable screen-space or world-space properties and rendering methods.";
        registration.registerClass("RenderObject", registration.requireInstanceBaseClassName(), description);
        registerObjectProperty(registration, "Id", "number", "The render object's runtime identifier.", "", null);
        registerObjectProperty(registration, "Type", "string", "The render object's type.", "GetType", null);
        registerObjectProperty(registration, "IsDestroyed", "boolean", "Whether this render object has been removed.", "GetIsDestroyed", null);
        registerObjectProperty(registration, "Position", "Vector2", "The render object's screen position.", "GetPosition", "SetPosition");
        registerObjectProperty(registration, "EndPosition", "Vector2", "The second screen position used by line and beam objects.", "GetEndPosition", "SetEndPosition");
        registerObjectProperty(registration, "WorldPosition", "Vector3", "The render object's world position.", "GetWorldPosition", "SetWorldPosition");
        registerObjectProperty(registration, "EndWorldPosition", "Vector3", "The second world position used by line and beam objects.", "GetEndWorldPosition", "SetEndWorldPosition");
        registerObjectProperty(registration, "Depth", "number", "The object's depth offset.", "GetDepth", "SetDepth");
        registerObjectProperty(registration, "Size", "Vector2", "The render object's width and height.", "GetSize", "SetSize");
        registerObjectProperty(registration, "Rotation", "number", "The object's rotation in degrees.", "GetRotation", "SetRotation");
        registerObjectProperty(registration, "Visible", "boolean", "Whether this render object is visible.", "GetVisible", "SetVisible");
        registerObjectProperty(registration, "Transparency", "number", "The object's transparency from zero to one.", "GetTransparency", "SetTransparency");
        registerObjectProperty(registration, "Alpha", "number", "The object's opacity from zero to one.", "GetAlpha", "SetAlpha");
        registerObjectProperty(registration, "Color", "number", "The object's packed color value.", "GetColor", "SetColor");
        registerObjectProperty(registration, "Thickness", "number", "The line or outline thickness.", "GetThickness", "SetThickness");
        registerObjectProperty(registration, "ZIndex", "number", "The screen draw order.", "GetZIndex", "SetZIndex");
        registerObjectProperty(registration, "WorldSpace", "boolean", "Whether the object is positioned in the world.", "GetWorldSpace", "SetWorldSpace");
        registerObjectProperty(registration, "ScaleWithDistance", "boolean", "Whether world-space size changes with camera distance.", "GetScaleWithDistance", "SetScaleWithDistance");
        registerObjectProperty(registration, "FollowEntity", "Entity|number|nil", "The entity followed by this object. Assign an entity instance or id, or nil to clear it.", "GetFollowEntity", "SetFollowEntity");
        registerObjectProperty(registration, "Resource", "string|nil", "The image or model resource used by this object.", "GetResource", "SetResource");
        registerObjectProperty(registration, "Text", "string|nil", "The text displayed by a text object.", "GetText", "SetText");
        String[][] methods = {
            {"GetId", "Returns the object's runtime identifier."}, {"GetType", "Returns the object's render type."},
            {"GetIsDestroyed", "Returns whether this object has been removed."},
            {"GetPosition", "Returns the object's screen position."}, {"SetPosition", "Changes the object's screen position."},
            {"GetEndPosition", "Returns the second screen position."}, {"SetEndPosition", "Changes the second screen position."},
            {"GetWorldPosition", "Returns the object's world position."}, {"SetWorldPosition", "Changes the object's world position."},
            {"GetEndWorldPosition", "Returns the second world position."}, {"SetEndWorldPosition", "Changes the second world position."},
            {"GetDepth", "Returns the depth offset."}, {"SetDepth", "Changes the depth offset."},
            {"GetSize", "Returns the object's width and height."}, {"SetSize", "Changes the object's width and height."},
            {"GetRotation", "Returns the rotation in degrees."}, {"SetRotation", "Changes the rotation in degrees."},
            {"GetVisible", "Returns whether the object is visible."}, {"SetVisible", "Shows or hides the object."},
            {"GetTransparency", "Returns the object's transparency."}, {"SetTransparency", "Changes the object's transparency."},
            {"GetAlpha", "Returns the object's opacity."}, {"SetAlpha", "Changes the object's opacity."},
            {"GetColor", "Returns the packed color value."}, {"SetColor", "Changes the packed color value."},
            {"GetThickness", "Returns the line or outline thickness."}, {"SetThickness", "Changes the line or outline thickness."},
            {"GetZIndex", "Returns the screen draw order."}, {"SetZIndex", "Changes the screen draw order."},
            {"GetWorldSpace", "Returns whether the object is positioned in the world."}, {"SetWorldSpace", "Changes whether the object is positioned in the world."},
            {"GetScaleWithDistance", "Returns whether world-space size follows camera distance."}, {"SetScaleWithDistance", "Changes distance-based scaling."},
            {"GetFollowEntity", "Returns the followed entity id, or nil."}, {"SetFollowEntity", "Attaches the object to an entity or clears the attachment with nil."},
            {"ClearFollowEntity", "Stops the object from following an entity."},
            {"GetResource", "Returns the image or model resource."}, {"SetResource", "Changes the image or model resource."},
            {"GetText", "Returns the displayed text."}, {"SetText", "Changes the displayed text."},
            {"GetAnimations", "Returns animation names available on a Blockbench model."},
            {"PlayAnimation", "Plays a named Blockbench model animation."},
            {"StopAnimation", "Stops the current Blockbench model animation."},
            {"GetAnimationTime", "Returns elapsed time for the current model animation."},
            {"Destroy", "Removes this render object."}
        };
        for (String[] method : methods) registerObjectMethod(registration, method[0], method[1]);
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
        LuaTable table = new LuaTable();
        table.set("__side", "Client");
        table.set("CreateRect", fn(args -> createObject(MLuaClientRenderManager.RenderType.RECT, null, args.arg(2).opttable(new LuaTable()))));
        table.set("CreateText", fn(args -> createObject(MLuaClientRenderManager.RenderType.TEXT, args.checkjstring(2), args.arg(3).opttable(new LuaTable()))));
        table.set("CreateImage", fn(args -> createObject(MLuaClientRenderManager.RenderType.IMAGE, args.checkjstring(2), args.arg(3).opttable(new LuaTable()))));
        table.set("CreateItem", fn(args -> createObject(MLuaClientRenderManager.RenderType.ITEM, args.checkjstring(2), args.arg(3).opttable(new LuaTable()))));
        table.set("CreateBlock", fn(args -> createObject(MLuaClientRenderManager.RenderType.BLOCK, args.checkjstring(2), args.arg(3).opttable(new LuaTable()))));
        table.set("CreateMesh", fn(args -> createMesh(args.checktable(2), args.arg(3).opttable(new LuaTable()))));
        table.set("LoadBBModel", fn(args -> loadBBModel(args.checkjstring(2), args.arg(3).opttable(new LuaTable()))));
        table.set("CreateWorldRect", fn(args -> createWorldObject(MLuaClientRenderManager.RenderType.RECT, null, args.checktable(2), args.arg(3).opttable(new LuaTable()))));
        table.set("CreateWorldText", fn(args -> createWorldObject(MLuaClientRenderManager.RenderType.TEXT, args.checkjstring(2), args.checktable(3), args.arg(4).opttable(new LuaTable()))));
        table.set("CreateWorldImage", fn(args -> createWorldObject(MLuaClientRenderManager.RenderType.IMAGE, args.checkjstring(2), args.checktable(3), args.arg(4).opttable(new LuaTable()))));
        table.set("CreateWorldItem", fn(args -> createWorldObject(MLuaClientRenderManager.RenderType.ITEM, args.checkjstring(2), args.checktable(3), args.arg(4).opttable(new LuaTable()))));
        table.set("CreateWorldBlock", fn(args -> createWorldObject(MLuaClientRenderManager.RenderType.BLOCK, args.checkjstring(2), args.checktable(3), args.arg(4).opttable(new LuaTable()))));
        table.set("CreateWorldBillboard", fn(args -> createWorldObject(MLuaClientRenderManager.RenderType.IMAGE, args.checkjstring(2), args.checktable(3), args.arg(4).opttable(new LuaTable()))));
        table.set("CreateWorldLine", fn(args -> {
            ensureClient();
            int id = MLuaClientRenderManager.create(MLuaClientRenderManager.RenderType.LINE, null, owner());
            var object = requireObject(id);
            LuaTable start = args.checktable(2);
            LuaTable end = args.checktable(3);
            object.setWorldSpace(true);
            object.setWorldPosition(component(start, "X"), component(start, "Y"), component(start, "Z"));
            object.setEndWorldPosition(component(end, "X"), component(end, "Y"), component(end, "Z"));
            applyProps(object, args.arg(4).opttable(new LuaTable()));
            return objectTable(id);
        }));
        table.set("CreateWorldBeam", fn(args -> {
            ensureClient();
            int id = MLuaClientRenderManager.create(MLuaClientRenderManager.RenderType.BEAM, null, owner());
            var object = requireObject(id);
            LuaTable start = args.checktable(2);
            LuaTable end = args.checktable(3);
            object.setWorldSpace(true);
            object.setWorldPosition(component(start, "X"), component(start, "Y"), component(start, "Z"));
            object.setEndWorldPosition(component(end, "X"), component(end, "Y"), component(end, "Z"));
            applyProps(object, args.arg(4).opttable(new LuaTable()));
            return objectTable(id);
        }));
        table.set("CreateWorldBox", fn(args -> {
            ensureClient();
            int id = MLuaClientRenderManager.create(MLuaClientRenderManager.RenderType.BOX, null, owner());
            var object = requireObject(id);
            LuaTable center = args.checktable(2);
            LuaTable size = args.checktable(3);
            object.setWorldSpace(true);
            object.setWorldPosition(component(center, "X"), component(center, "Y"), component(center, "Z"));
            object.setSize(component(size, "X"), component(size, "Y"));
            object.setDepth(component(size, "Z"));
            applyProps(object, args.arg(4).opttable(new LuaTable()));
            return objectTable(id);
        }));
        table.set("Clear", fn(args -> {
            ensureClient();
            MLuaClientRenderManager.clearOwned(owner());
            return LuaValue.NONE;
        }));
        table.set("GetAll", fn(args -> {
            LuaTable objects = new LuaTable();
            int index = 1;
            for (Integer id : MLuaClientRenderManager.ids(owner())) {
                if (MLuaClientRenderManager.get(id, owner()) != null) {
                    objects.set(index++, objectTable(id));
                }
            }
            return objects;
        }));
        return table;
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
        List<MLuaMeshVertex> vertices = new ArrayList<>();
        LuaTable rawVertices = definition.get("Vertices").checktable();
        for (int i = 1; !rawVertices.get(i).isnil(); i++) {
            LuaTable vertex = rawVertices.get(i).checktable();
            LuaTable position = vertex.get("Position").checktable();
            LuaTable uv = vertex.get("UV").opttable(new LuaTable());
            int color = vertex.get("Color").isnil() ? 0xFFFFFFFF : vertex.get("Color").toint();
            vertices.add(new MLuaMeshVertex((float)component(position, "X"), (float)component(position, "Y"),
                (float)component(position, "Z"), (float)component(uv, "X"), (float)component(uv, "Y"), color));
        }
        List<Integer> indices = new ArrayList<>();
        LuaTable rawIndices = definition.get("Indices").checktable();
        for (int i = 1; !rawIndices.get(i).isnil(); i++) indices.add(rawIndices.get(i).toint());
        String texture = definition.get("Texture").isnil() ? null : definition.get("Texture").tojstring();
        int id = MLuaClientRenderManager.createMesh(new MLuaMeshDefinition(vertices, indices, texture), owner());
        applyProps(requireObject(id), props);
        return objectTable(id);
    }

    private LuaValue loadBBModel(String path, LuaTable props) {
        ensureClient();
        try {
            MLuaBBModelAsset loadedAsset = MLuaBBModelLoader.load(path);
            LuaValue animationPath = props.get("AnimationPath").isnil() ? props.get("AnimationsPath") : props.get("AnimationPath");
            if (animationPath.isstring() && !animationPath.tojstring().isBlank()) {
                loadedAsset = loadedAsset.withAnimations(MLuaBBModelLoader.loadAnimations(animationPath.tojstring()));
            }
            final MLuaBBModelAsset asset = loadedAsset;
            int id = MLuaClientRenderManager.createBBModel(asset, owner());
            var object = requireObject(id);
            applyProps(object, props);
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
                object.animationPlayer().play(name, asset, args.arg(3).opttable(new LuaTable()).get("Loop").optboolean(true), (float)args.arg(3).opttable(new LuaTable()).get("Speed").optdouble(1.0));
                return LuaValue.NONE;
            }));
            operations.set("StopAnimation", fn(args -> { object.animationPlayer().stop(); return LuaValue.NONE; }));
            operations.set("GetAnimationTime", fn(args -> LuaValue.valueOf(object.animationPlayer().time())));
            return table;
        } catch (IOException | RuntimeException exception) {
            throw new LuaError("[MLua] Failed to load BBModel '" + path + "': " + exception.getMessage());
        }
    }

    private LuaValue createWorldObject(MLuaClientRenderManager.RenderType type, String resource, LuaTable worldPosition, LuaTable props) {
        ensureClient();
        int id = MLuaClientRenderManager.create(type, resource, owner());
        var object = requireObject(id);
        object.setWorldSpace(true);
        object.setWorldPosition(component(worldPosition, "X"), component(worldPosition, "Y"), component(worldPosition, "Z"));
        applyProps(object, props);
        return objectTable(id);
    }

    private LuaTable objectTable(int id) {
        LuaTable operations = new LuaTable();
        operations.set("GetId", fn(args -> LuaValue.valueOf(id)));
        operations.set("GetType", fn(args -> {
            var object = requireObject(id);
            return LuaValue.valueOf(object.type().name().toLowerCase());
        }));
        operations.set("GetIsDestroyed", fn(args -> LuaValue.valueOf(MLuaClientRenderManager.get(id, owner()) == null)));
        operations.set("GetPosition", fn(args -> {
            var object = requireObject(id);
            return MLuaVector.vector2(object.x(), object.y());
        }));
        operations.set("SetPosition", fn(args -> {
            var object = requireObject(id);
            LuaTable value = args.checktable(2);
            object.setPosition(component(value, "X"), component(value, "Y"));
            return LuaValue.NONE;
        }));
        operations.set("GetEndPosition", fn(args -> {
            var object = requireObject(id);
            return MLuaVector.vector2(object.endX(), object.endY());
        }));
        operations.set("SetEndPosition", fn(args -> {
            LuaTable value = args.checktable(2);
            requireObject(id).setEndPosition(component(value, "X"), component(value, "Y"));
            return LuaValue.NONE;
        }));
        operations.set("GetWorldPosition", fn(args -> {
            var object = requireObject(id);
            return MLuaVector.vector3(object.worldX(), object.worldY(), object.worldZ());
        }));
        operations.set("SetWorldPosition", fn(args -> {
            var object = requireObject(id);
            LuaTable value = args.checktable(2);
            object.setWorldPosition(component(value, "X"), component(value, "Y"), component(value, "Z"));
            object.setWorldSpace(true);
            return LuaValue.NONE;
        }));
        operations.set("GetEndWorldPosition", fn(args -> {
            var object = requireObject(id);
            return MLuaVector.vector3(object.endWorldX(), object.endWorldY(), object.endWorldZ());
        }));
        operations.set("SetEndWorldPosition", fn(args -> {
            var object = requireObject(id);
            LuaTable value = args.checktable(2);
            object.setEndWorldPosition(component(value, "X"), component(value, "Y"), component(value, "Z"));
            return LuaValue.NONE;
        }));
        operations.set("GetDepth", fn(args -> LuaValue.valueOf(requireObject(id).depth())));
        operations.set("SetDepth", fn(args -> {
            requireObject(id).setDepth(args.checkdouble(2));
            return LuaValue.NONE;
        }));
        operations.set("GetSize", fn(args -> {
            var object = requireObject(id);
            return MLuaVector.vector2(object.width(), object.height());
        }));
        operations.set("SetSize", fn(args -> {
            var object = requireObject(id);
            LuaTable value = args.checktable(2);
            object.setSize(component(value, "X"), component(value, "Y"));
            return LuaValue.NONE;
        }));
        operations.set("GetRotation", fn(args -> LuaValue.valueOf(requireObject(id).rotation())));
        operations.set("SetRotation", fn(args -> {
            requireObject(id).setRotation(args.checkdouble(2));
            return LuaValue.NONE;
        }));
        operations.set("GetVisible", fn(args -> LuaValue.valueOf(requireObject(id).visible())));
        operations.set("SetVisible", fn(args -> {
            requireObject(id).setVisible(args.checkboolean(2));
            return LuaValue.NONE;
        }));
        operations.set("GetTransparency", fn(args -> LuaValue.valueOf(requireObject(id).transparency())));
        operations.set("SetTransparency", fn(args -> {
            requireObject(id).setTransparency(args.checkdouble(2));
            return LuaValue.NONE;
        }));
        operations.set("GetAlpha", fn(args -> LuaValue.valueOf(1.0 - requireObject(id).transparency())));
        operations.set("SetAlpha", fn(args -> {
            requireObject(id).setTransparency(1.0 - Math.max(0.0, Math.min(1.0, args.checkdouble(2))));
            return LuaValue.NONE;
        }));
        operations.set("GetColor", fn(args -> LuaValue.valueOf(requireObject(id).color())));
        operations.set("SetColor", fn(args -> {
            requireObject(id).setColor(args.checkint(2));
            return LuaValue.NONE;
        }));
        operations.set("GetThickness", fn(args -> LuaValue.valueOf(requireObject(id).thickness())));
        operations.set("SetThickness", fn(args -> {
            requireObject(id).setThickness(args.checkdouble(2));
            return LuaValue.NONE;
        }));
        operations.set("GetZIndex", fn(args -> LuaValue.valueOf(requireObject(id).zIndex())));
        operations.set("SetZIndex", fn(args -> {
            requireObject(id).setZIndex(args.checkint(2));
            return LuaValue.NONE;
        }));
        operations.set("GetWorldSpace", fn(args -> LuaValue.valueOf(requireObject(id).worldSpace())));
        operations.set("SetWorldSpace", fn(args -> {
            requireObject(id).setWorldSpace(args.checkboolean(2));
            return LuaValue.NONE;
        }));
        operations.set("GetScaleWithDistance", fn(args -> LuaValue.valueOf(requireObject(id).scaleWithDistance())));
        operations.set("SetScaleWithDistance", fn(args -> {
            requireObject(id).setScaleWithDistance(args.checkboolean(2));
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
        operations.set("GetResource", fn(args -> {
            String resource = requireObject(id).resource();
            return resource != null ? LuaValue.valueOf(resource) : LuaValue.NIL;
        }));
        operations.set("SetResource", fn(args -> {
            requireObject(id).setResource(args.arg(2).isnil() ? null : args.checkjstring(2));
            return LuaValue.NONE;
        }));
        operations.set("GetText", fn(args -> {
            String resource = requireObject(id).resource();
            return resource != null ? LuaValue.valueOf(resource) : LuaValue.NIL;
        }));
        operations.set("SetText", fn(args -> {
            requireObject(id).setResource(args.arg(2).isnil() ? null : args.checkjstring(2));
            return LuaValue.NONE;
        }));
        operations.set("Destroy", fn(args -> {
            MLuaClientRenderManager.remove(id, owner());
            return LuaValue.NONE;
        }));
        LuaTable instance = new LuaTable();
        instance.rawset("__mluaOwnerModId", LuaValue.valueOf(owner().ownerModId()));
        instance.rawset(OBJECT_ID, LuaValue.valueOf(id));
        instance.rawset(OBJECT_OPERATIONS, operations);
        return context.mlua().registration().attachClassInstance(instance, "RenderObject", null);
    }

    private void applyProps(MLuaClientRenderManager.RenderObject object, LuaTable props) {
        LuaValue position = firstNonNil(props.get("Position"), props.get("position"));
        if (position.istable()) {
            object.setPosition(component(position.checktable(), "X"), component(position.checktable(), "Y"));
        }
        LuaValue size = firstNonNil(props.get("Size"), props.get("size"));
        if (size.istable()) {
            object.setSize(component(size.checktable(), "X"), component(size.checktable(), "Y"));
        }
        if (!props.get("Rotation").isnil()) {
            object.setRotation(props.get("Rotation").todouble());
        } else if (!props.get("rotation").isnil()) {
            object.setRotation(props.get("rotation").todouble());
        }
        if (!props.get("Visible").isnil()) {
            object.setVisible(props.get("Visible").toboolean());
        } else if (!props.get("visible").isnil()) {
            object.setVisible(props.get("visible").toboolean());
        }
        if (!props.get("Transparency").isnil()) {
            object.setTransparency(props.get("Transparency").todouble());
        } else if (!props.get("transparency").isnil()) {
            object.setTransparency(props.get("transparency").todouble());
        }
        if (!props.get("Color").isnil()) {
            object.setColor(props.get("Color").toint());
        } else if (!props.get("color").isnil()) {
            object.setColor(props.get("color").toint());
        }
        if (!props.get("ZIndex").isnil()) {
            object.setZIndex(props.get("ZIndex").toint());
        } else if (!props.get("zIndex").isnil()) {
            object.setZIndex(props.get("zIndex").toint());
        }
        LuaValue worldPosition = firstNonNil(props.get("WorldPosition"), props.get("worldPosition"));
        if (worldPosition.istable()) {
            object.setWorldSpace(true);
            object.setWorldPosition(component(worldPosition.checktable(), "X"), component(worldPosition.checktable(), "Y"), component(worldPosition.checktable(), "Z"));
        }
        LuaValue endWorldPosition = firstNonNil(props.get("EndWorldPosition"), props.get("endWorldPosition"));
        if (endWorldPosition.istable()) {
            object.setEndWorldPosition(component(endWorldPosition.checktable(), "X"), component(endWorldPosition.checktable(), "Y"), component(endWorldPosition.checktable(), "Z"));
        }
        if (!props.get("ScaleWithDistance").isnil()) {
            object.setScaleWithDistance(props.get("ScaleWithDistance").toboolean());
        } else if (!props.get("scaleWithDistance").isnil()) {
            object.setScaleWithDistance(props.get("scaleWithDistance").toboolean());
        }
        LuaValue modelScale = firstNonNil(props.get("ModelScale"), props.get("modelScale"));
        if (!modelScale.isnil()) object.setModelScale((float) modelScale.todouble());
        if (!props.get("Thickness").isnil()) {
            object.setThickness(props.get("Thickness").todouble());
        } else if (!props.get("thickness").isnil()) {
            object.setThickness(props.get("thickness").todouble());
        }
        LuaValue followEntity = firstNonNil(props.get("FollowEntity"), props.get("followEntity"));
        if (!followEntity.isnil()) {
            object.setFollowEntityScope(resolveOptionalEntityScope(followEntity));
        }
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

    private void ensureClient() {
        if (context.mlua().side() != Side.CLIENT) {
            throw new LuaError("[MLua] RenderService is client-side only.");
        }
    }

    private static LuaValue firstNonNil(LuaValue a, LuaValue b) {
        return !a.isnil() ? a : b;
    }

    private static double component(LuaTable table, String key) {
        LuaValue value = table.get(key);
        if (value.isnil()) {
            value = table.get(key.toLowerCase());
        }
        return value.optdouble(0.0);
    }

    private MLuaEntityScope resolveOptionalEntityScope(LuaValue value) {
        return value.isnil() ? null : resolveEntityScope(value);
    }

    private MLuaEntityScope resolveEntityScope(LuaValue value) {
        if (value.isint()) {
            return this.resolveEntityScope(value.toint());
        }
        if (value.istable()) {
            LuaValue entity = value.get("__entity");
            if (entity.isuserdata(net.minecraft.world.entity.Entity.class)) {
                return MLuaEntityScope.of((net.minecraft.world.entity.Entity) entity.checkuserdata(net.minecraft.world.entity.Entity.class));
            }
            LuaValue id = value.get("Id");
            if (id.isint()) return this.resolveEntityScope(id.toint());
        }
        throw new LuaError("[MLua] Expected entity table or entity id");
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
