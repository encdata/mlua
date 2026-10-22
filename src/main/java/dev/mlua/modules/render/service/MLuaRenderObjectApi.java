package dev.mlua.modules.render.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.signal.MLuaConnection;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.entity.MLuaEntityScopeResolver;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.MLuaRenderOwnershipRegistry;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;


/** Shared public Lua-facing base for MLua render-object services. */
public abstract class MLuaRenderObjectApi extends MLuaReflectiveService {
    static final String OBJECT_OPERATIONS = "__mlua_render_object_operations";
    public static final String OBJECT_ID = "__mlua_render_object_id";
    private final MLuaRenderObjectHandleCache objectHandleCache = new MLuaRenderObjectHandleCache();
    private final MLuaRenderObjectCreation objectCreation;
    private final MLuaRenderObjectOperations.Access objectOperationsAccess;

    MLuaContext renderOwnerContext() {
        MLuaContext context = MLuaLuaBindings.boundContext(this.context.mlua().globals());
        if (context == null) throw new LuaError("[MLua] Render callbacks can only be registered while a Lua addon is executing.");
        return context;
    }
    protected MLuaRenderObjectApi(MLuaService.Context context) {
        super(context);
        this.objectCreation = new MLuaRenderObjectCreation(this);
        this.objectOperationsAccess = new MLuaRenderObjectOperations.Access() {
            @Override public MLuaRegistration registration() { return MLuaRenderObjectApi.this.context.mlua().registration(); }
            @Override public MLuaClientRenderManager.RenderObject requireObject(int id) { return MLuaRenderObjectApi.this.requireObject(id); }
            @Override public MLuaClientRenderManager.RenderObject requireMeshObject(int id) { return MLuaRenderObjectApi.this.requireMeshObject(id); }
            @Override public MLuaClientRenderManager.RenderObject requireModelObject(int id) { return MLuaRenderObjectApi.this.requireModelObject(id); }
            @Override public MLuaContext renderOwnerContext() { return MLuaRenderObjectApi.this.renderOwnerContext(); }
            @Override public MLuaEntityScope resolveOptionalEntityScope(LuaValue value) { return MLuaRenderObjectApi.this.resolveOptionalEntityScope(value); }
            @Override public void forgetHandle(int id) { MLuaRenderObjectApi.this.objectHandleCache.remove(id); }
        };
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
        double[] startPosition = MLuaRenderValues.worldVector(start);
        double[] endPosition = MLuaRenderValues.worldVector(end);
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
        double[] startPosition = MLuaRenderValues.worldVector(start);
        double[] endPosition = MLuaRenderValues.worldVector(end);
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
        double[] centerPosition = MLuaRenderValues.worldVector(center);
        double[] dimensions = MLuaRenderValues.worldVector(size);
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
        objectHandleCache.clear();
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

    private static LuaTable optionalTable(Optional<LuaValue> value) {
        return value.orElse(LuaValue.NIL).opttable(new LuaTable());
    }

    protected final LuaTable connection(MLuaConnection connection) {
        return MLuaLuaBindings.connection(connection, this.context.registration());
    }

    private LuaValue createObject(MLuaClientRenderManager.RenderType type, String resource, LuaTable props) {
        return this.objectCreation.create(type, resource, props);
    }

    private LuaValue createMesh(LuaTable definition, LuaTable props) {
        return this.objectCreation.createMesh(definition, props);
    }

    private LuaValue loadBBModel(String path, LuaTable props) {
        return this.objectCreation.loadBBModel(path, props);
    }

    private LuaValue createWorldObject(MLuaClientRenderManager.RenderType type, String resource, LuaValue worldPosition, LuaTable props) {
        return this.objectCreation.createWorldObject(type, resource, worldPosition, props);
    }

    /** Roll back a partially configured handle so validation failures never consume quota or retain textures. */
    LuaValue configureObject(int id, LuaTable props, Consumer<MLuaClientRenderManager.RenderObject> initialize) {
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

    synchronized LuaTable objectTable(int id) {
        LuaTable cached = objectHandleCache.get(id);
        if (cached != null) return cached;
        MLuaRenderOwnershipRegistry.Owner renderOwner = owner();
        MLuaClientRenderManager.RenderObject renderObject = requireObject(id);
        MLuaClientRenderManager.RenderType primitive = Objects.requireNonNull(renderObject.type(), "Render object " + id + " has no render primitive");
        String className = renderObject.luaClassName();
        LuaValue renderTypeValue = LuaValue.valueOf(primitive.name().toLowerCase(Locale.ROOT));
        LuaTable operations = new MLuaRenderObjectOperations(
            id, renderOwner, primitive, className, renderTypeValue, objectOperationsAccess).create();
        LuaTable instance = new LuaTable();
        instance.rawset("__mluaOwnerModId", LuaValue.valueOf(renderOwner.ownerModId()));
        instance.rawset("__mluaRenderOwner", LuaValue.userdataOf(renderOwner));
        instance.rawset(OBJECT_ID, LuaValue.valueOf(id));
        instance.rawset(OBJECT_OPERATIONS, operations);
        LuaTable handle = context.mlua().registration().attachClassInstance(instance, requireObject(id).luaClassName(), null);
        handle.rawset("GetRenderType", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return renderTypeValue; }
        });
        handle.rawset("__mluaRenderType", renderTypeValue);
        objectHandleCache.put(id, handle);
        return handle;
    }



    MLuaClientRenderManager.RenderObject requireModelObject(int id) {
        MLuaClientRenderManager.RenderObject object = requireObject(id);
        if (object.type() != MLuaClientRenderManager.RenderType.BBMODEL || object.bbmodel() == null) {
            throw new LuaError("[MLua] RenderObject model parts are available only on a loaded Blockbench model.");
        }
        return object;
    }

    MLuaClientRenderManager.RenderObject requireMeshObject(int id) {
        MLuaClientRenderManager.RenderObject object = requireObject(id);
        if (object.type() != MLuaClientRenderManager.RenderType.MESH
                && object.type() != MLuaClientRenderManager.RenderType.BBMODEL) {
            throw new LuaError("[MLua] RenderObject ModelTransform is available only on Mesh and BBModel objects.");
        }
        return object;
    }

    void applyProps(MLuaClientRenderManager.RenderObject object, LuaTable props, String... extraProperties) {
        try {
            String className = MLuaRenderObjectProperties.className(props);
            if (context.mlua().registration().classTable(className) == null) {
                throw new LuaError("[MLua] Unknown RenderObject class: " + className);
            }
            if (!context.mlua().registration().isSubclassOf(className, "RenderObject")) {
                throw new LuaError("[MLua] RenderObject ClassName must name a registered subclass of RenderObject.");
            }
            LuaTable descriptors = MLuaRenderObjectProperties.descriptors(context.mlua().registration(), className);
            MLuaRenderObjectProperties.Plan plan = MLuaRenderObjectProperties.validate(props, descriptors, extraProperties);
            object.setLuaClassName(className);
            MLuaRenderObjectProperties.apply(objectTable(object.id()), plan);
        } catch (RuntimeException | Error failure) {
            MLuaClientRenderManager.remove(object.id(), owner());
            objectHandleCache.remove(object.id());
            throw failure;
        }
    }

    MLuaClientRenderManager.RenderObject requireObject(int id) {
        var object = MLuaClientRenderManager.get(id, owner());
        if (object == null) {
            throw new LuaError("[MLua] Render object no longer exists");
        }
        return object;
    }

    void requireClientApi() {
        this.requireClientSide(this.name());
    }

    void removeObject(int id) {
        MLuaClientRenderManager.remove(id, owner());
        objectHandleCache.remove(id);
    }

    MLuaRenderOwnershipRegistry.Owner owner() {
        Object runtimeOwner = context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
        return new MLuaRenderOwnershipRegistry.Owner(runtimeOwner, context.mlua().ownerModId());
    }

    MLuaEntityScope resolveOptionalEntityScope(LuaValue value) {
        return MLuaEntityScopeResolver.resolveOptionalClientTarget(value,
            this.context.mlua().registration(), "FollowEntity must reference an entity in the current client world");
    }

    private MLuaEntityScope resolveEntityScope(LuaValue value) {
        return MLuaEntityScopeResolver.resolveClientTarget(value,
            this.context.mlua().registration(), "FollowEntity must reference an entity in the current client world");
    }

}
