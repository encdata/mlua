package dev.mlua.modules.render.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.signal.MLuaConnection;
import dev.mlua.loaderapi.ClientBridge;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.runtime.client.MLuaRenderCallbacks;
import java.util.List;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Client-side HUD, world, all-entity draw, living model, and held-item rendering with addon-owned, frame-scoped callbacks.")
public final class MLuaRenderService extends MLuaRenderObjectApi {
    static final String OBJECT_OPERATIONS = MLuaRenderObjectApi.OBJECT_OPERATIONS;
    public static final String OBJECT_ID = MLuaRenderObjectApi.OBJECT_ID;

    public MLuaRenderService(MLuaService.Context context) { super(context); }

    @Override public String name() { return "RenderService"; }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Client");
        return table;
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

}
