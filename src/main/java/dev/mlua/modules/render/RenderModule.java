package dev.mlua.modules.render;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.render.Service.MLuaRenderService;
import dev.mlua.common.enums.Side;
import java.util.List;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public final class RenderModule implements MLuaModule {
    private static final String[][] CONTEXT_PROPERTIES = {
        {"Target", "string", "Render callback target: EntityModel, Entity, FirstPersonItem, ThirdPersonItem, World, or Hud."},
        {"PoseStack", "Java object|nil", "The live pose stack when exposed by this render point."},
        {"BufferSource", "Java object|nil", "Minecraft's live buffer source or render collector when exposed."},
        {"RenderCollector", "Java object|nil", "Minecraft's SubmitNodeCollector on render-pipeline versions that use one; nil on older versions."},
        {"Model", "Java object|nil", "The native entity model after Minecraft has applied its animation pose."},
        {"PartialTick", "number", "Frame interpolation value; check HasPartialTick before relying on it."},
        {"HasPartialTick", "boolean", "Whether this render point supplied a real frame interpolation value. If false, PartialTick is zero."},
        {"DeltaTime", "number", "Duration of the latest client render frame in seconds, capped at one second; zero until HasDeltaTime is true. This is separate from PartialTick."},
        {"HasDeltaTime", "boolean", "Whether the latest client render-frame sample contains a complete positive interval; false means DeltaTime is zero."},
        {"PackedLight", "number", "Minecraft packed light value when supplied."},
        {"PackedOverlay", "number", "Minecraft packed overlay value when supplied."},
        {"PassId", "string", "Namespaced id of the render pass receiving this context."},
        {"Stage", "WorldRenderStage|nil", "Canonical world render stage for world callbacks."},
        {"NativeContext", "Java object|nil", "The loader's native render event or context."},
        {"Renderer", "Java object|nil", "The native entity renderer for entity draw passes when supplied."},
        {"Camera", "Java object|nil", "The native camera for world render callbacks."},
        {"Player", "Player|nil", "The player whose first-person item is being rendered."},
        {"ItemStack", "ItemStack|nil", "The live item stack for a first-person item callback."},
        {"Hand", "string|nil", "The hand being rendered: MAIN_HAND or OFF_HAND."},
        {"Arm", "string|nil", "The rendered model arm: LEFT or RIGHT, when this render point supplies one."},
        {"DisplayContext", "string|nil", "The native item display context for third-person held-item passes."},
        {"Pitch", "number|nil", "Player pitch supplied to a first-person item callback."},
        {"SwingProgress", "number|nil", "Current hand swing progress."},
        {"EquipProgress", "number|nil", "Current hand equip progress."},
        {"Graphics", "Java object|nil", "The live GuiGraphics for HUD callbacks."},
        {"Width", "number|nil", "HUD width in GUI-scaled pixels."},
        {"Height", "number|nil", "HUD height in GUI-scaled pixels."}
    };

    @Override
    public String id() {
        return "render";
    }

    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        dev.mlua.runtime.client.MLuaClientRenderManager.clearRuntime(runtimeSession);
        dev.mlua.runtime.client.MLuaRenderCallbacks.clearRuntime(runtimeSession);
    }

    @Override
    public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        dev.mlua.runtime.client.MLuaClientRenderManager.clearOwned(
            new dev.mlua.runtime.client.MLuaRenderOwnershipRegistry.Owner(runtimeSession, ownerModId));
        dev.mlua.runtime.client.MLuaRenderCallbacks.clearOwned(runtimeSession, ownerModId);
    }

    @Override
    public Side side() { return Side.CLIENT; }

    @Override public List<String> dependencies() { return List.of("core-classes"); }

    @Override public void registerClasses(MLuaRegistration registration) {
        MLuaRenderService.registerRenderObjectClass(registration);
        registration.registerClass("RenderContext", registration.requireInstanceBaseClassName(),
            "Frame-scoped values supplied to an entity, world, or HUD render callback.");
        for (String[] property : CONTEXT_PROPERTIES) {
            String getter = "Get" + property[0];
            registration.registerClassFunction("RenderContext", getter, new org.luaj.vm2.lib.OneArgFunction() {
                @Override public org.luaj.vm2.LuaValue call(org.luaj.vm2.LuaValue self) {
                    return self.checktable().get(property[0]);
                }
            }, "Returns the " + property[0] + " value for this render callback.");
            registration.registerClassProperty("RenderContext", property[0], new org.luaj.vm2.lib.OneArgFunction() {
                @Override public org.luaj.vm2.LuaValue call(org.luaj.vm2.LuaValue self) {
                    return self.checktable().get(property[0]);
                }
            }, org.luaj.vm2.LuaValue.NIL, property[1], "client:Client", property[2]);
        }
        registration.registerClassFunction("RenderContext", "IsActive", new org.luaj.vm2.lib.OneArgFunction() {
            @Override public org.luaj.vm2.LuaValue call(org.luaj.vm2.LuaValue self) {
                org.luaj.vm2.LuaValue scope = self.checktable().rawget("__mluaRenderFrameScope");
                return org.luaj.vm2.LuaValue.valueOf(scope.isuserdata(
                    dev.mlua.runtime.client.MLuaRenderFrameScope.View.class)
                    && ((dev.mlua.runtime.client.MLuaRenderFrameScope.View)scope.touserdata(
                        dev.mlua.runtime.client.MLuaRenderFrameScope.View.class)).isActive());
            }
        }, "Returns whether this RenderContext is still active on the render thread that created it.");
        registration.registerClassFunction("RenderContext", "DrawObject", new org.luaj.vm2.lib.VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                return dev.mlua.runtime.client.MLuaRenderCallbacks.drawObject(
                    registration, args.arg(1), args.arg(2), args.arg(3));
            }
        }, "Draws an addon-owned Mesh or BBModel RenderObject in this callback's current frame pose.");
    }

    @Override
    public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("RenderService", MLuaRenderService::new)
            .reflective(MLuaRenderService.class)
            .build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("RenderService", "MLuaRenderService",
            "Client-side HUD and world renderables, entity model attachments, and first-person item passes. Each addon runtime may own up to 512 render objects; the client caps all live render objects at 8192. Meshes are limited to 16384 vertices and 49152 indices. Blockbench files are limited to 8 MiB, 8192 cubes, 2048 bones, 256 textures and animations, and 32768 keyframes per animation.",
            "services/render.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("RenderObject", "MLuaRenderService",
            "A live client render instance with mutable screen-space or world-space properties and rendering methods.",
            "classes/render-object.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.clazz("RenderPart", "MLuaRenderService",
            "A live handle to a bone in a Blockbench RenderObject. Handles follow the model hierarchy and become stale when their owning render object is destroyed.",
            "classes/render-part.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateRect(properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateText(text: string, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateImage(resource: string, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateItem(itemId: string, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateBlock(blockId: string, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateMesh(definition: MeshDefinition, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:LoadBBModel(path: string, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderObject", "RenderObject:GetModelParts()", "RenderPart[]"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderObject", "RenderObject:GetModelPart(nameOrId: string)", "RenderPart|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderObject", "RenderObject:GetModelPartById(uuid: string)", "RenderPart|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldRect(position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldText(text: string, position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldImage(resource: string, position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldItem(itemId: string, position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldBlock(blockId: string, position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldBillboard(resource: string, position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldLine(start: Vector3|CFrame, finish: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldBeam(start: Vector3|CFrame, finish: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldBox(center: Vector3|CFrame, size: Vector3, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:Clear()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:GetAll()", "RenderObject[]"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:RegisterEntityRenderer(entityType: string, callback: function(Entity, RenderContext))", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:RegisterFirstPersonItemRenderer(itemId: string, callback: function(Player, RenderContext))", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:RegisterFirstPersonItemRenderPass(itemId: string, id: string, callback: function(Player, RenderContext))", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:UnregisterEntityRenderer(entityType: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:RegisterEntityRenderPass(entityType: string, id: string, callback: function(Entity, RenderContext))", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:RegisterEntityDrawPass(entityType: string, id: string, callback: function(Entity, RenderContext))", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:UnregisterFirstPersonItemRenderer(itemId: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:UnregisterFirstPersonItemRenderPass(itemId: string, id: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:UnregisterEntityRenderPass(entityType: string, id: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:UnregisterEntityDrawPass(entityType: string, id: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:RegisterWorldRenderer(callback: function(RenderContext))", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:UnregisterWorldRenderer()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:RegisterWorldRendererAt(stage: string, callback: function(RenderContext))", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:UnregisterWorldRendererAt(stage: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:RegisterWorldRenderPass(id: string, stage: WorldRenderStage, callback: function(RenderContext))", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:UnregisterWorldRenderPass(id: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:GetWorldRenderStages()", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:GetWorldModelRenderStages()", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:GetRenderCallbackLimits()", "RenderCallbackLimits"));
        docs.register(MLuaDocBuilder.Registration.type("RenderCallbackLimits", null, "Current active render callbacks and capacity limits for the client, calling addon runtime, and individual render targets."));
        docs.register(MLuaDocBuilder.Registration.field("RenderCallbackLimits", "Active", "number", "Number of active callbacks across all render targets and addons."));
        docs.register(MLuaDocBuilder.Registration.field("RenderCallbackLimits", "ActiveOwned", "number", "Number of active callbacks owned by the calling addon runtime."));
        docs.register(MLuaDocBuilder.Registration.field("RenderCallbackLimits", "MaxTotal", "number", "Maximum active callbacks across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("RenderCallbackLimits", "MaxPerOwner", "number", "Maximum active callbacks owned by one addon runtime."));
        docs.register(MLuaDocBuilder.Registration.field("RenderCallbackLimits", "MaxPerTarget", "number", "Maximum callbacks for one entity type, item id, HUD layer, or world stage."));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:RegisterHudRenderer(callback: function(RenderContext))", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:UnregisterHudRenderer()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:RegisterHudRenderPass(id: string, callback: function(RenderContext))", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:UnregisterHudRenderPass(id: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.clazz("RenderContext", "RenderService", "Values supplied at HUD, world, living entity, or first-person item render points. Native Java references are valid only during the callback; Java bridge calls reject them after the callback returns or from another thread. DrawObject submits a Mesh or BBModel using this frame's pose and render source. Plain values such as stage names and dimensions can be retained.", "classes/render-context.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.method("RenderContext", "RenderContext:IsActive()", "Returns whether this RenderContext is still active on the render thread that created it.", null, "boolean", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderContext", "RenderContext:DrawObject(renderObject: RenderObject, transform?: CFrame)", "Submits a Mesh or BBModel RenderObject at this callback's current Minecraft pose using its render source, light, and overlay. The optional CFrame is local to that pose. The object must be owned by the same addon runtime. Returns false when the object is hidden. HUD callbacks do not provide a 3D pose or render source; use HUD screen-space drawing there.", "Call inside a world, entity, or held-item render callback; the render context expires when the callback returns.", "boolean", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.type("WorldRenderStage", null, "A shared stage name mapped to the closest supported native world-render event for the active loader and Minecraft version."));
        docs.register(MLuaDocBuilder.Registration.field("WorldRenderStage", "BEFORE_ENTITIES", "string", "After opaque terrain and before entity rendering."));
        docs.register(MLuaDocBuilder.Registration.field("WorldRenderStage", "AFTER_ENTITIES", "string", "After opaque entity and block-entity features."));
        docs.register(MLuaDocBuilder.Registration.field("WorldRenderStage", "AFTER_TRANSLUCENT", "string", "After translucent geometry, at the nearest matching native stage."));
        docs.register(MLuaDocBuilder.Registration.field("WorldRenderStage", "AFTER_LEVEL", "string", "At the end of the main level-render pass, before late effects where the loader separates them."));
        for (String[] property : CONTEXT_PROPERTIES) {
            docs.register(MLuaDocBuilder.Registration.field("RenderContext", property[0], property[1], property[2]));
            docs.register(MLuaDocBuilder.Registration.method("RenderContext", "RenderContext:Get" + property[0] + "()",
                "Returns the " + property[0] + " value for this render callback.", null, property[1], "client:Client", null));
        }
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:SetWorldPosition(position: Vector3|CFrame)", "Sets this object's world position.", "Native Vector3 or CFrame", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:GetWorldPosition()", "Returns this object's world position as a native Vector3.", null, "Vector3", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:SetEndWorldPosition(position: Vector3|CFrame)", "Sets the endpoint used by world lines and beams.", "Native Vector3 or CFrame", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:GetEndWorldPosition()", "Returns the world endpoint as a native Vector3.", null, "Vector3", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:GetTarget()", "Returns the canonical render target name.", null, "string", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:SetTarget(target: string)", "Selects where this object is drawn. EntityModel draws relative to the followed entity render origin, or to FollowBone when set for a living entity. Manual disables automatic drawing so it can be submitted from a RenderContext.", "Manual, Hud, WorldOverlay, WorldModel, or EntityModel; matching is case-insensitive", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:PlayAnimation(name: string, options: ModelAnimationOptions?)", "Plays a named Blockbench model animation.", "Animation name and optional playback settings", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:GetAnimations()", "Lists animation names on a loaded Blockbench model.", null, "string[]", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:GetAnimationName()", "Returns the selected animation name, or nil after StopAnimation clears the pose.", null, "string|nil", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:IsAnimationPlaying()", "Returns whether the selected animation is advancing. A held final pose can remain selected after playback ends.", null, "boolean", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:GetModelParts()", "Returns every bone as a RenderPart in the model's original order. Use Parent and Children to traverse the hierarchy.", null, "RenderPart[]", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:GetModelPart(nameOrId: string)", "Looks up a bone by its exact name or UUID. Returns nil when absent; duplicate names raise an error so callers can use the UUID.", "Exact case-sensitive bone name or UUID", "RenderPart|nil", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:GetModelPartById(uuid: string)", "Looks up a bone by its stable Blockbench UUID. Returns nil when absent.", "Blockbench bone UUID", "RenderPart|nil", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:StopAnimation()", "Stops playback and restores the Blockbench model's base pose. An animation that ends naturally with hold behavior keeps its final pose until stopped or replaced.", null, "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:GetAnimationTime()", "Returns elapsed playback time for the current Blockbench model animation.", null, "number", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:GetFollowEntity()", "Returns the followed entity's runtime id, or nil when unattached.", null, "number|nil", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:SetFollowEntity(entity?: Entity|number)", "Attaches this render object to an entity or clears the attachment with nil.", "Optional live Entity instance or runtime id", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:ClearFollowEntity()", "Removes this object's entity attachment.", null, "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:SetFollowBone(bone?: string)", "Attaches this object to a named bone on the followed entity's registered model adapter. Clear the bone to attach at the root of a living entity model without registering an adapter.", "Registered bone name, or nil to clear", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:SetFollowOffset(offset: CFrame|function)", "Sets a local transform after the followed entity root or bone. A callback is sampled once per client tick and must return a CFrame. If it fails, MLua disables it and keeps the last valid transform.", "CFrame value or callback returning CFrame", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:Destroy()", "Removes this render object and releases its owned render state.", null, "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderPart", "RenderPart:GetName()", "Returns this bone's name from the loaded Blockbench model.", null, "string", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderPart", "RenderPart:GetId()", "Returns this bone's stable UUID. Use it to look up bones when names are duplicated.", null, "string", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderPart", "RenderPart:GetParent()", "Returns the direct parent part, or nil when this is a root bone.", null, "RenderPart|nil", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderPart", "RenderPart:GetChildren()", "Returns direct child parts in model order.", null, "RenderPart[]", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderPart", "RenderPart:GetTransform()", "Returns this part's local CFrame override, or the identity CFrame when no override is set. The override is applied after the Blockbench base pose and current animation pose.", null, "CFrame", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderPart", "RenderPart:SetTransform(transform: CFrame)", "Sets a local transform override applied after this bone's base and animated transforms. CFrame position uses Minecraft block units; rotation uses radians.", "Finite local CFrame", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderPart", "RenderPart:ResetTransform()", "Removes the local override and returns this bone to its Blockbench and animated pose.", null, "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderPart", "RenderPart:Equals(other: RenderPart)", "Returns true when both handles refer to the same bone on the same owned render object.", "Another RenderPart handle", "boolean", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.property("RenderPart", "RenderPart.Name", "string", "The Blockbench bone name.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("RenderPart", "RenderPart.Id", "string", "The stable Blockbench bone UUID.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("RenderPart", "RenderPart.Parent", "RenderPart|nil", "The direct parent part, or nil for a root bone.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("RenderPart", "RenderPart.Children", "RenderPart[]", "The direct child parts in model order.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("RenderPart", "RenderPart.Transform", "CFrame", "The part's local CFrame override, composed with its base and animation pose.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.type("ModelAnimationOptions", null, "Playback options shared by entity-attached and standalone Blockbench models."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAnimationOptions", "Loop", "boolean", "When true, forces repetition. When false, playback follows the Blockbench animation setting: loop repeats, while hold and once stop on the final keyframe. Defaults to true."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAnimationOptions", "Speed", "number", "Finite, non-negative playback speed. Defaults to one."));
        docs.register(MLuaDocBuilder.Registration.type("MeshDefinition", null, "Vertex data, zero-based triangle indices, and optional texture for RenderService:CreateMesh. A mesh may contain at most 16384 vertices and 49152 indices."));
        docs.register(MLuaDocBuilder.Registration.field("MeshDefinition", "Vertices", "MeshVertex[]", "At least three vertices used by the mesh."));
        docs.register(MLuaDocBuilder.Registration.field("MeshDefinition", "Indices", "number[]", "One or more complete triangles. Each zero-based index must refer to an entry in Vertices."));
        docs.register(MLuaDocBuilder.Registration.field("MeshDefinition", "Texture?", "string", "Optional texture resource identifier. Unqualified paths resolve in the owning addon's namespace; use namespace:path to select another namespace."));
        docs.register(MLuaDocBuilder.Registration.type("MeshVertex", null, "One mesh vertex with a world position, optional texture coordinate, and optional packed color."));
        docs.register(MLuaDocBuilder.Registration.field("MeshVertex", "Position", "Vector3|CFrame", "Finite position in mesh-local space, relative to RenderObject.WorldPosition."));
        docs.register(MLuaDocBuilder.Registration.field("MeshVertex", "UV?", "Vector2", "Optional texture coordinate."));
        docs.register(MLuaDocBuilder.Registration.field("MeshVertex", "Color?", "number", "Optional packed 32-bit vertex color. Defaults to opaque white."));
        docs.register(MLuaDocBuilder.Registration.type("RenderObjectProperties", null, "Initial properties accepted when a render object is created. Use the RenderObject property names in PascalCase or lower camel case."));
        MLuaRenderService.contributeRenderObjectPropertyDocs(docs);
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "ClassName", "string", "Registered RenderObject subclass to attach to this instance. The class and its inherited properties are preserved by RenderService:GetAll()."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "AnimationPath", "string", "Animation file to load with a Blockbench model. LoadBBModel also accepts the alias AnimationsPath."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "AnimationsPath", "string", "Alias for AnimationPath when loading a Blockbench model; provide only one."));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderService", "Create and update a render instance",
            "Render constructors return an owned RenderObject. HUD, projected-world, and 3D world-model draws use separate targets under the same object lifecycle. World-space positions accept native Vector3 or CFrame values; screen positions use Vector2. GuiService keeps its layout tree and screen lifecycle.",
            "local Render = Minecraft:GetService(\"RenderService\")\nlocal label = Render:CreateText(\"The door is locked\", { Position = Vector2.new(20, 20), Target = \"Hud\" })\nlocal marker = Render:CreateWorldText(\"Exit\", CFrame.new(12, 65, -4), { Target = \"WorldOverlay\" })\nlocal model = Render:LoadBBModel(\"myaddon:models/marker.bbmodel\", { Target = \"WorldModel\", RenderStage = \"AFTER_TRANSLUCENT\" })\nmodel.WorldPosition = CFrame.new(12, 65, -4)\n\nlabel.Position = Vector2.new(24, 32)\nlabel.Visible = true\nmarker.WorldPosition = CFrame.new(14, 65, -4)\nprint(label.Target, marker.Target, model.RenderStage)\nlabel:Destroy()\nmarker:Destroy()\nmodel:Destroy()"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderObject", "Extend the render object class",
            "Register a subclass under RenderObject and pass its className in constructor properties. The handle keeps that class through GetAll, so addon methods and inherited properties remain available for the owned render object's lifetime.",
            "Minecraft:RegisterClass(\"WarningLabel\", \"RenderObject\", \"A warning label with a critical style.\")\nMinecraft:RegisterClassFunction(\"WarningLabel\", \"SetCriticalStyle\", function(self)\n    self:SetColor(0xFF3030)\n    self:SetZIndex(20)\nend, \"Applies the critical warning color and draw order.\")\n\nlocal Render = Minecraft:GetService(\"RenderService\")\nlocal label = Render:CreateText(\"Low health\", { ClassName = \"WarningLabel\", Position = Vector2.new(12, 12) })\nlabel:SetCriticalStyle()\nfor _, object in ipairs(Render:GetAll()) do\n    if object:IsA(\"WarningLabel\") then\n        print(object.ClassName, object.Text)\n    end\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderService", "Attach a 3D model to an entity part",
            "EntityModel draws a mesh or Blockbench model from a registered bone path after Minecraft has posed that entity. FollowOffset accepts a CFrame or a function returning a CFrame. MLua samples the function once per client tick and applies its returned position and rotation after the bone pose; if it fails, MLua disables the callback and keeps the last valid transform.",
            "local Models = Minecraft:GetService(\"ModelService\")\nlocal Render = Minecraft:GetService(\"RenderService\")\nlocal Players = Minecraft:GetService(\"Players\")\n\nModels:RegisterAdapter(\"net.minecraft.client.model.PlayerModel\", {\n    head = \"head\",\n})\n\nlocal hat = Render:LoadBBModel(\"myaddon:models/hat.bbmodel\")\nhat.Target = \"EntityModel\"\nhat.FollowEntity = Players.LocalPlayer\nhat.FollowBone = \"head\"\nlocal phase = 0\nhat.FollowOffset = function()\n    phase = phase + 0.1\n    return CFrame.new(0, 0.35 + math.sin(phase) * 0.02, 0)\n        * CFrame.Angles(0, math.sin(phase) * 0.08, 0)\nend\nhat.ModelScale = 1\nhat.Visible = true"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderService", "Attach a model to an entity root",
            "Choose EntityModel and set FollowEntity. With FollowBone unset, MLua draws at the living model root or at a non-living entity's render origin. Root attachments do not require a ModelService adapter. Add FollowBone when a living model should track a named part.",
            "local Render = Minecraft:GetService(\"RenderService\")\nlocal Players = Minecraft:GetService(\"Players\")\n\nlocal marker = Render:LoadBBModel(\"myaddon:models/aura.bbmodel\")\nmarker.Target = \"EntityModel\"\nmarker.FollowEntity = Players.LocalPlayer\nmarker.FollowBone = nil\nmarker.FollowOffset = CFrame.new(0, 1.5, 0)\nmarker.Visible = true"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderPart", "Pose a Blockbench bone from Lua",
            "GetModelParts returns handles for the model's bone tree. A part's Transform is a local CFrame override applied after its Blockbench pose and animation, so the same model animation can keep running while Lua adds a small pose adjustment. Use UUID lookup when bone names repeat. The handles are owned by the RenderObject and become stale when it is destroyed.",
            "local model = Minecraft:GetService(\"RenderService\"):LoadBBModel(\"myaddon:models/creature.bbmodel\")\nlocal jaw = model:GetModelPart(\"jaw\")\nif jaw then\n    jaw.Transform = CFrame.Angles(math.rad(12), 0, 0)\n    print(jaw.Name, jaw.Id, jaw.Parent and jaw.Parent.Name)\nend\n\nfor _, part in ipairs(model:GetModelParts()) do\n    print(part.Name, #part.Children)\nend\n\n-- Restore the Blockbench and animation pose.\nif jaw then jaw:ResetTransform() end"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderService", "Hook a native entity render point",
            "RegisterEntityRenderer adds an addon-owned callback after Minecraft has posed a living entity's model and before its model is drawn. It follows the same render-point pattern as a native entity renderer, while the callback receives the real entity, PoseStack, buffer source, model, partial tick, light, and overlay. Use Java:Invoke or Java object proxies for native rendering calls. The render context is frame-scoped: do not save it, yield from the callback, or use it after the callback returns. A callback error disables that callback for the rest of the session.",
            "local Render = Minecraft:GetService(\"RenderService\")\nlocal Java = Minecraft:GetService(\"Java\")\n\nRender:RegisterEntityRenderPass(\"myaddon:stalker\", \"myaddon:glow\", function(entity, context)\n    local pose = context.PoseStack\n    Java:Invoke(pose, \"pushPose\", \"()V\")\n    -- Use the live pose, model, and buffer source for a native render pass.\n    Java:Invoke(pose, \"popPose\", \"()V\")\nend)\n\n-- Remove just this pass; other passes for the same entity remain registered.\nRender:UnregisterEntityRenderPass(\"myaddon:stalker\", \"myaddon:glow\")"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderService", "Render a non-living entity",
            "RegisterEntityDrawPass runs in the entity dispatcher for any registered EntityType, including projectiles and dropped items. Its pose is translated to the entity render origin before that entity's renderer runs. NativeContext contains the render state on 26.1 and is nil on older versions. Use RegisterEntityRenderPass when you need the prepared model and bone mappings of a living entity.",
            "local Render = Minecraft:GetService(\"RenderService\")\nlocal Java = Minecraft:GetService(\"Java\")\n\nRender:RegisterEntityDrawPass(\"minecraft:arrow\", \"myaddon:arrow_glow\", function(entity, context)\n    local pose = context.PoseStack\n    local renderer = context.Renderer\n    Java:Invoke(pose, \"pushPose\", \"()V\")\n    -- Draw an effect at the arrow's world position before its renderer runs.\n    Java:Invoke(pose, \"popPose\", \"()V\")\nend)\n\nRender:UnregisterEntityDrawPass(\"minecraft:arrow\", \"myaddon:arrow_glow\")"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderService", "Add a first-person held-item render pass",
            "Register a callback for one item id. MLua invokes it from the first-person item render hook and supplies the live player, item stack, hand, pose stack, buffer source or collector, swing and equip progress, and light. The callback and its frame-scoped values follow the same ownership and cleanup rules as other RenderService passes.",
            "local Render = Minecraft:GetService(\"RenderService\")\nlocal Java = Minecraft:GetService(\"Java\")\n\nRender:RegisterFirstPersonItemRenderPass(\"myaddon:ritual_blade\", \"myaddon:glint\", function(player, context)\n    local pose = context.PoseStack\n    local stack = context.ItemStack\n    local renderSource = context.RenderCollector or context.BufferSource\n    if context.Hand == \"MAIN_HAND\" then\n        -- Use this frame's pose and render source for native drawing calls.\n        Java:Invoke(pose, \"pushPose\", \"()V\")\n        Java:Invoke(pose, \"popPose\", \"()V\")\n    end\nend)\n\nRender:UnregisterFirstPersonItemRenderPass(\"myaddon:ritual_blade\", \"myaddon:glint\")"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderService", "Add a third-person held-item render pass",
            "MLua invokes this callback after the entity arm and vanilla item transforms are applied, immediately before the item is submitted. It works for player and mob equipment. MLua reads partial tick from the render state when available, then falls back to Minecraft's client frame tracker; check HasPartialTick before using it. The pose and other Minecraft render objects are valid only until the callback returns.",
            "local Render = Minecraft:GetService(\"RenderService\")\nlocal Java = Minecraft:GetService(\"Java\")\n\nRender:RegisterThirdPersonItemRenderPass(\"myaddon:ritual_blade\", \"myaddon:glow\", function(entity, context)\n    local pose = context.PoseStack\n    if context.Hand == \"MAIN_HAND\" then\n        Java:Invoke(pose, \"pushPose\", \"()V\")\n        -- Add a render effect at the item's current hand transform.\n        Java:Invoke(pose, \"popPose\", \"()V\")\n    end\nend)\n\nRender:UnregisterThirdPersonItemRenderPass(\"myaddon:ritual_blade\", \"myaddon:glow\")"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderService", "Draw an animated model on a held item",
            "Load a Mesh or Blockbench RenderObject once, then submit it from first- and third-person item callbacks. Set Target to Manual so it is used only by DrawObject and is not also auto-drawn. DrawObject uses the callback's current Minecraft hand pose, light, overlay, and version-specific render source, then applies its optional local CFrame and the model's animation and scale. Keep the render object in the same addon runtime as the callbacks. The render context and native buffers expire when each callback returns.",
            "local Render = Minecraft:GetService(\"RenderService\")\nlocal blade = Render:LoadBBModel(\"myaddon:models/ritual_blade.bbmodel\", { Target = \"Manual\" })\nblade:PlayAnimation(\"idle\", { Loop = true })\n\nlocal function drawBlade(entity, context)\n    if context.Hand ~= \"MAIN_HAND\" then return end\n    context:DrawObject(blade, CFrame.new(0, 0.05, 0) * CFrame.Angles(0, math.rad(90), 0))\nend\n\nRender:RegisterFirstPersonItemRenderPass(\"myaddon:ritual_blade\", \"myaddon:model\", drawBlade)\nRender:RegisterThirdPersonItemRenderPass(\"myaddon:ritual_blade\", \"myaddon:model\", drawBlade)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderContext", "Use frame timing in an effect",
            "DeltaTime is the latest usable client render-frame interval in seconds. It is zero and HasDeltaTime is false until a complete interval is sampled. PartialTick is Minecraft's interpolation fraction from zero to one. Use DeltaTime for time-based motion and PartialTick for interpolating entity state.",
            "local Render = Minecraft:GetService(\"RenderService\")\nlocal phase = 0\n\nRender:RegisterHudRenderPass(\"myaddon:pulse\", function(context)\n    if context.HasDeltaTime then\n        phase = phase + context.DeltaTime * 2\n    end\n    local pulse = (math.sin(phase) + 1) / 2\n    -- Use pulse to update a render object or a Color3 value.\nend)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderService", "Attach a world or HUD render callback",
            "World callbacks run at one of the cross-loader stages exposed by GetWorldRenderStages(); MLua maps each name to that loader's nearest native render event. HUD callbacks run in MLua's registered GUI layer before MLua RenderObjects. Both callbacks receive a frame-scoped RenderContext. Use Java:Invoke or Java:Proxy to call Minecraft rendering APIs, and never retain frame objects or yield from a callback.",
            "local Render = Minecraft:GetService(\"RenderService\")\nlocal Java = Minecraft:GetService(\"Java\")\n\nRender:RegisterHudRenderPass(\"myaddon:status\", function(context)\n    local graphics = context.Graphics\n    -- Call GuiGraphics methods here through Java:Invoke.\nend)\n\n-- World passes can run at a selected shared stage.\nRender:RegisterWorldRenderPass(\"myaddon:glow\", \"AFTER_TRANSLUCENT\", function(context)\n    local nativeEvent = context.NativeContext\n    local poseStack = context.PoseStack\n    local buffers = context.BufferSource\n    -- Use Java:Invoke for operations exposed by that loader/version.\nend)\n\nRender:UnregisterHudRenderPass(\"myaddon:status\")\nRender:UnregisterWorldRenderPass(\"myaddon:glow\")"));
    }
}
