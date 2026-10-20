package dev.mlua.modules.render.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.signal.MLuaConnection;
import dev.mlua.loaderapi.ClientBridge;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.entity.MLuaEntityScopeResolver;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.MLuaRenderCallbacks;
import dev.mlua.runtime.client.MLuaRenderOwnershipRegistry;
import java.util.List;
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

@MLuaDescription("Client-side HUD, world, all-entity draw, living model, and held-item rendering with addon-owned, frame-scoped callbacks.")
public final class MLuaRenderService extends MLuaReflectiveService {
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
    public MLuaRenderService(MLuaService.Context context) {
        super(context);
        this.objectCreation = new MLuaRenderObjectCreation(this);
        this.objectOperationsAccess = new MLuaRenderObjectOperations.Access() {
            @Override public MLuaRegistration registration() { return MLuaRenderService.this.context.mlua().registration(); }
            @Override public MLuaClientRenderManager.RenderObject requireObject(int id) { return MLuaRenderService.this.requireObject(id); }
            @Override public MLuaClientRenderManager.RenderObject requireMeshObject(int id) { return MLuaRenderService.this.requireMeshObject(id); }
            @Override public MLuaClientRenderManager.RenderObject requireModelObject(int id) { return MLuaRenderService.this.requireModelObject(id); }
            @Override public MLuaContext renderOwnerContext() { return MLuaRenderService.this.renderOwnerContext(); }
            @Override public MLuaEntityScope resolveOptionalEntityScope(LuaValue value) { return MLuaRenderService.this.resolveOptionalEntityScope(value); }
            @Override public void forgetHandle(int id) { MLuaRenderService.this.objectHandleCache.remove(id); }
        };
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

    @MLuaDescription("Registers a Lua callback at the living entity renderer for this entity type. The callback receives the live Entity and a RenderContext containing Minecraft's PoseStack, BufferSource, model, partial tick, light, and overlay values.")
    public LuaTable RegisterEntityRenderer(String entityType, LuaValue callback) {
        this.requireClientSide(this.name());
        return connection(MLuaRenderCallbacks.register(entityType, callback, renderOwnerContext()));
    }

    @MLuaDescription("Registers an independently owned callback at the living renderer for an entity type. Passes at the same render point run in registration order. Re-registering a pass gives it a new position at the end of that order; use a resource id so passes can coexist and be removed separately.")
    public LuaTable RegisterEntityRenderPass(String entityType, String id, LuaValue callback) {
        this.requireClientSide(this.name());
        return connection(MLuaRenderCallbacks.register(entityType, id, callback, renderOwnerContext()));
    }

    @MLuaDescription("Removes this addon's callback from an entity type's living entity renderer.")
    public boolean UnregisterEntityRenderer(String entityType) {
        this.requireClientSide(this.name());
        return MLuaRenderCallbacks.unregister(entityType, renderOwnerContext());
    }

    @MLuaDescription("Removes only the named callback registered for this entity type.")
    public boolean UnregisterEntityRenderPass(String entityType, String id) {
        this.requireClientSide(this.name());
        return MLuaRenderCallbacks.unregister(entityType, id, renderOwnerContext());
    }

    @MLuaDescription("Registers an entity-level render pass before Minecraft draws an entity renderer. Passes at the same render point run in registration order. It works for living and non-living entities, including projectiles and dropped items, and receives the live Entity plus the frame-scoped render pose, source, renderer, light, and native render state when the loader provides one.")
    public LuaTable RegisterEntityDrawPass(String entityType, String id, LuaValue callback) {
        this.requireClientSide(this.name());
        return connection(MLuaRenderCallbacks.registerEntityDraw(entityType, id, callback, renderOwnerContext()));
    }

    @MLuaDescription("Removes only the named entity-level render pass registered for this entity type.")
    public boolean UnregisterEntityDrawPass(String entityType, String id) {
        this.requireClientSide(this.name());
        return MLuaRenderCallbacks.unregisterEntityDraw(entityType, id, renderOwnerContext());
    }

    @MLuaDescription("Registers an addon-owned render pass after Minecraft's native renderer returns for an entity. Passes at the same render point run in registration order. It receives the entity and a frame-scoped RenderContext with the renderer, current pose, render source, partial tick, light, and native render state when available. Model contains the native model when a ModelService adapter resolves one for this renderer. MLua entity attachments are submitted after this callback.")
    public LuaTable RegisterEntityPostRenderPass(String entityType, String id, LuaValue callback) {
        this.requireClientSide(this.name());
        return connection(MLuaRenderCallbacks.registerEntityPostDraw(
            entityType, id, callback, renderOwnerContext()));
    }

    @MLuaDescription("Removes only the named post-render callback registered for this entity type.")
    public boolean UnregisterEntityPostRenderPass(String entityType, String id) {
        this.requireClientSide(this.name());
        return MLuaRenderCallbacks.unregisterEntityPostDraw(
            entityType, id, renderOwnerContext());
    }

    @MLuaDescription("Registers an owned callback while a matching item is rendered in the first-person hand. The callback receives the player and a frame-scoped RenderContext with the hand, item stack, pose stack, buffer source, and light value.")
    public LuaTable RegisterFirstPersonItemRenderer(String itemId, LuaValue callback) {
        this.requireClientSide(this.name());
        return connection(MLuaRenderCallbacks.registerFirstPersonItem(itemId, callback, renderOwnerContext()));
    }

    @MLuaDescription("Registers an independently owned first-person item render pass. Passes for the same item run in registration order. Use a resource id so multiple addons or effects can coexist for the same item.")
    public LuaTable RegisterFirstPersonItemRenderPass(String itemId, String id, LuaValue callback) {
        this.requireClientSide(this.name());
        return connection(MLuaRenderCallbacks.registerFirstPersonItem(itemId, id, callback, renderOwnerContext()));
    }

    @MLuaDescription("Removes this addon's first-person item render callback for the given item.")
    public boolean UnregisterFirstPersonItemRenderer(String itemId) {
        this.requireClientSide(this.name());
        return MLuaRenderCallbacks.unregisterFirstPersonItem(itemId, renderOwnerContext());
    }

    @MLuaDescription("Removes only the named first-person item render pass for the given item.")
    public boolean UnregisterFirstPersonItemRenderPass(String itemId, String id) {
        this.requireClientSide(this.name());
        return MLuaRenderCallbacks.unregisterFirstPersonItem(itemId, id, renderOwnerContext());
    }

    @MLuaDescription("Registers a callback while a matching item is rendered in a living entity's third-person hand. It receives the living entity and a frame-scoped RenderContext with the translated item pose, item stack, arm, hand, display context, render source, and light. MLua reads PartialTick from the render state when available, then falls back to Minecraft's client tick tracker; HasPartialTick is false only when neither source supplies a finite value.")
    public LuaTable RegisterThirdPersonItemRenderer(String itemId, LuaValue callback) {
        this.requireClientSide(this.name());
        return connection(MLuaRenderCallbacks.registerThirdPersonItem(itemId, callback, renderOwnerContext()));
    }

    @MLuaDescription("Registers an independently owned third-person held-item render pass for an item. Passes for the same item run in registration order. A resource id lets addons coexist and unregister one pass without removing others.")
    public LuaTable RegisterThirdPersonItemRenderPass(String itemId, String id, LuaValue callback) {
        this.requireClientSide(this.name());
        return connection(MLuaRenderCallbacks.registerThirdPersonItem(itemId, id, callback, renderOwnerContext()));
    }

    @MLuaDescription("Removes this addon's third-person held-item callback for the given item.")
    public boolean UnregisterThirdPersonItemRenderer(String itemId) {
        this.requireClientSide(this.name());
        return MLuaRenderCallbacks.unregisterThirdPersonItem(itemId, renderOwnerContext());
    }

    @MLuaDescription("Removes only the named third-person held-item render pass for the given item.")
    public boolean UnregisterThirdPersonItemRenderPass(String itemId, String id) {
        this.requireClientSide(this.name());
        return MLuaRenderCallbacks.unregisterThirdPersonItem(itemId, id, renderOwnerContext());
    }

    @MLuaDescription("Registers an addon-owned callback at the native world render point after Minecraft renders world entities and geometry.")
    public LuaTable RegisterWorldRenderer(LuaValue callback) {
        this.requireClientSide(this.name());
        return connection(MLuaRenderCallbacks.registerWorld(callback, renderOwnerContext()));
    }

    @MLuaDescription("Registers the addon's default callback at a cross-loader world stage: BEFORE_ENTITIES, AFTER_ENTITIES, AFTER_TRANSLUCENT, or AFTER_LEVEL. Registering it at another stage moves the same callback instead of leaving a duplicate at the old stage. MLua maps the stage to the nearest native Fabric, Forge, or NeoForge render event; the callback receives that native event/context in RenderContext.")
    public LuaTable RegisterWorldRendererAt(String stage, LuaValue callback) {
        this.requireClientSide(this.name());
        return connection(MLuaRenderCallbacks.registerWorld(stage, callback, renderOwnerContext()));
    }

    @MLuaDescription("Registers an independently owned world render pass by resource id. Passes at the same stage run in registration order. Re-registering that id at another stage moves the pass and assigns a new order there. The id can be removed without affecting other passes from this addon.")
    public LuaTable RegisterWorldRenderPass(String id, String stage, LuaValue callback) {
        this.requireClientSide(this.name());
        return connection(MLuaRenderCallbacks.registerWorld(id, stage, callback, renderOwnerContext()));
    }

    @MLuaDescription("Registers a callback at the loader's custom-geometry submission point, or AFTER_ENTITIES where the loader has no separate geometry event. Check RenderContext.HasDrawObjectSource before calling DrawObject; this accounts for MultiBufferSource and deferred SubmitNodeCollector hooks.")
    public LuaTable RegisterWorldGeometryRenderer(LuaValue callback) {
        return this.RegisterWorldGeometryPass("mlua:default", callback);
    }

    @MLuaDescription("Registers an independently owned callback at the loader's custom-geometry submission point, or AFTER_ENTITIES where the loader has no separate geometry event. Passes run in registration order. Check RenderContext.HasDrawObjectSource before calling DrawObject.")
    public LuaTable RegisterWorldGeometryPass(String id, LuaValue callback) {
        this.requireClientSide(this.name());
        return connection(MLuaRenderCallbacks.registerWorldGeometry(id, callback, renderOwnerContext()));
    }

    @MLuaDescription("Removes this addon's callback from the native world render point.")
    public boolean UnregisterWorldRenderer() {
        this.requireClientSide(this.name());
        return MLuaRenderCallbacks.unregisterWorld(renderOwnerContext());
    }

    @MLuaDescription("Removes this addon's callback from the selected world render stage.")
    public boolean UnregisterWorldRendererAt(String stage) {
        this.requireClientSide(this.name());
        return MLuaRenderCallbacks.unregisterWorld(stage, renderOwnerContext());
    }

    @MLuaDescription("Removes this addon's world render pass with the given resource id, regardless of its registered stage.")
    public boolean UnregisterWorldRenderPass(String id) {
        this.requireClientSide(this.name());
        return MLuaRenderCallbacks.unregisterWorldPass(id, renderOwnerContext());
    }

    @MLuaDescription("Removes this addon's callback registered at the custom-geometry submission point.")
    public boolean UnregisterWorldGeometryRenderer() {
        return this.UnregisterWorldGeometryPass("mlua:default");
    }

    @MLuaDescription("Removes this addon's custom-geometry callback with the given resource id.")
    public boolean UnregisterWorldGeometryPass(String id) {
        this.requireClientSide(this.name());
        return MLuaRenderCallbacks.unregisterWorldGeometry(id, renderOwnerContext());
    }

    @MLuaDescription("Returns the cross-loader world render stage names supported by RenderService.")
    public LuaTable GetWorldRenderStages() {
        this.requireClientSide(this.name());
        LuaTable stages = new LuaTable();
        List<String> supported = ClientBridge.WORLD_RENDER_STAGES;
        for (int index = 0; index < supported.size(); index++) stages.set(index + 1, supported.get(index));
        return stages;
    }

    @MLuaDescription("Returns the world render stages supported by managed Mesh and BBModel objects on this loader and Minecraft version.")
    public LuaTable GetWorldModelRenderStages() {
        this.requireClientSide(this.name());
        LuaTable stages = new LuaTable();
        List<String> supported = LoaderApi.client().worldModelRenderStages();
        for (int index = 0; index < supported.size(); index++) stages.set(index + 1, supported.get(index));
        return stages;
    }

    @MLuaDescription("Returns render callback capacity and usage. Active is the global count; ActiveOwned is the count registered by the calling addon runtime. MaxTotal is shared by all addons, MaxPerOwner limits one addon runtime, and MaxPerTarget applies to each entity type, item, HUD layer, or world stage.")
    public LuaTable GetRenderCallbackLimits() {
        LuaTable limits = new LuaTable();
        limits.set("Active", MLuaRenderCallbacks.registeredCallbackCount());
        limits.set("ActiveOwned", MLuaRenderCallbacks.registeredCallbackCount(renderOwnerContext()));
        limits.set("MaxTotal", MLuaRenderCallbacks.MAX_CALLBACKS);
        limits.set("MaxPerOwner", MLuaRenderCallbacks.MAX_CALLBACKS_PER_OWNER);
        limits.set("MaxPerTarget", MLuaRenderCallbacks.MAX_CALLBACKS_PER_TARGET);
        limits.set("MaxInstructionsPerCallback", MLuaRenderCallbacks.MAX_LUA_INSTRUCTIONS_PER_CALLBACK);
        return limits;
    }

    @MLuaDescription("Registers an addon-owned callback in the HUD render layer, before MLua-managed RenderObjects are drawn.")
    public LuaTable RegisterHudRenderer(LuaValue callback) {
        this.requireClientSide(this.name());
        return connection(MLuaRenderCallbacks.registerHud(callback, renderOwnerContext()));
    }

    @MLuaDescription("Registers an independently owned HUD render pass by resource id, before MLua-managed RenderObjects are drawn. Passes run in registration order.")
    public LuaTable RegisterHudRenderPass(String id, LuaValue callback) {
        this.requireClientSide(this.name());
        return connection(MLuaRenderCallbacks.registerHud(id, callback, renderOwnerContext()));
    }

    @MLuaDescription("Removes this addon's callback from the HUD render layer.")
    public boolean UnregisterHudRenderer() {
        this.requireClientSide(this.name());
        return MLuaRenderCallbacks.unregisterHud(renderOwnerContext());
    }

    @MLuaDescription("Removes only the HUD render pass registered with this id.")
    public boolean UnregisterHudRenderPass(String id) {
        this.requireClientSide(this.name());
        return MLuaRenderCallbacks.unregisterHudPass(id, renderOwnerContext());
    }

    private static LuaTable optionalTable(Optional<LuaValue> value) {
        return value.orElse(LuaValue.NIL).opttable(new LuaTable());
    }

    private LuaTable connection(MLuaConnection connection) {
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

    static VarArgFunction fn(Function<Varargs, LuaValue> body) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    return body.apply(args);
                } catch (LuaError e) {
                    throw e;
                } catch (Exception e) {
                    throw MLuaLuaErrors.javaFailure("Render service call", e);
                }
            }
        };
    }
}
