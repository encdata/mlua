#!/usr/bin/env python3
"""Check render injection targets and routing across supported loader/version pairs."""

from pathlib import Path
import json
import sys

from mlua_target_sources import TARGET_CONFIG, inherited_loader_source


ROOT = Path(__file__).resolve().parents[2]
HOOKS = {
    "fabric": ROOT / "mlua-loaders/fabric/26.1/impl/dev/mlua/modules/mixin/service/ModelFeatureRendererMixin.java",
    "neoforge": ROOT / "mlua-loaders/neoforge/26.1/impl/dev/mlua/mixin/ModelFeatureRendererMixin.java",
}
DISPATCHERS = {
    "fabric": ROOT / "mlua-loaders/fabric/26.1/impl/dev/mlua/modules/mixin/service/EntityRenderDispatcherMixin.java",
    "neoforge": ROOT / "mlua-loaders/neoforge/26.1/impl/dev/mlua/mixin/EntityRenderDispatcherMixin.java",
}
MIXINS = {
    "fabric": ROOT / "mlua-loaders/fabric/26.1/project/src/main/resources/mlua.fabric.mixins.json",
}
NEOFORGE_CONFIG_GENERATOR = ROOT / "gradle/mlua-neoforge-project.gradle"
NEOFORGE_RUNTIME_TARGET = ROOT / "gradle/mlua-runtime-neoforge-target.gradle"
TARGETS = ROOT / "gradle/mlua-targets.json"
CALLBACKS = ROOT / "src/main/java/dev/mlua/runtime/client/MLuaRenderCallbacks.java"
RENDER_SERVICE = ROOT / "src/main/java/dev/mlua/modules/render/service/MLuaRenderService.java"
RENDER_OBJECT_API = ROOT / "src/main/java/dev/mlua/modules/render/service/MLuaRenderObjectApi.java"
RENDER_OBJECT_OPERATIONS = ROOT / "src/main/java/dev/mlua/modules/render/service/MLuaRenderObjectOperations.java"
OBJECT_CREATION = ROOT / "src/main/java/dev/mlua/modules/render/service/MLuaRenderObjectCreation.java"
MODEL_ADAPTERS = ROOT / "src/main/java/dev/mlua/runtime/client/MLuaModelAdapterRegistry.java"
RENDERER_ADAPTERS = ROOT / "src/main/java/dev/mlua/runtime/client/MLuaRendererAdapterRegistry.java"
MODEL_FIELD_ACCESS = ROOT / "src/main/java/dev/mlua/runtime/client/MLuaModelFieldAccess.java"


def fail(message: str) -> None:
    print(f"Render hook contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


for loader, path in HOOKS.items():
    source = path.read_text(encoding="utf-8")
    if "MLuaRenderStateEntityResolver.findEntity(state)" not in source:
        fail(f"{loader} does not resolve the live entity for a submitted model")
    if "if (entity == null) return;" not in source:
        fail(f"{loader} does not safely handle a render state with no live entity")
    if "MLuaEntityScope scope = MLuaEntityScope.of(entity);" not in source:
        fail(f"{loader} does not scope model animation by the rendered entity")
    if "if (entity instanceof LivingEntity) MLuaModelAdapterRegistry.apply(model, scope);" not in source:
        fail(f"{loader} must reserve direct model mutation for living entities; nonliving models use dispatcher scopes")
    if "if (entity instanceof LivingEntity livingEntity)" not in source:
        fail(f"{loader} must restrict living-only render callbacks without restricting adapters")
    living_block = source.split("if (entity instanceof LivingEntity livingEntity)", 1)[1].split("\n        }", 1)[0]
    if "renderEntityAttachmentsAtModelDraw(submission.model()," not in living_block or "scope, poseStack" not in living_block:
        fail(f"{loader} must render living attachments at model draw and leave nonliving fallback to its dispatcher")
    dispatcher = DISPATCHERS[loader].read_text(encoding="utf-8")
    if "if (living || !MLuaClientRenderManager.hasEntityModel(entity)) return;" not in dispatcher:
        fail(f"{loader} dispatcher must limit its attachment fallback to nonliving entities")
    if "MLuaClientRenderManager.renderEntityAttachments(model, MLuaEntityScope.of(entity), poseStack," not in dispatcher:
        fail(f"{loader} dispatcher no longer submits nonliving entity attachments")
    if "finally {\n            MLuaClientModelManager.restoreOverrides(model);" not in source:
        fail(f"{loader} does not restore shared model state when drawing fails")

    mixin_config = (MIXINS[loader] if loader in MIXINS else NEOFORGE_CONFIG_GENERATOR).read_text(encoding="utf-8")
    if (loader == "fabric" and '"ModelFeatureRendererMixin"' not in mixin_config) or (
        loader == "neoforge" and "(is26Family ? ['ModelFeatureRendererMixin'] : [])" not in mixin_config
    ):
        fail(f"{loader} does not install its model feature render hook")


def filter_versioned_source(source: str, target: str) -> str:
    output = []
    active = True
    for line in source.splitlines():
        trimmed = line.strip()
        if trimmed.startswith("//--version "):
            versions = {version.strip() for version in trimmed[len("//--version "):].split(",")}
            active = target in versions
        elif trimmed == "//--endversion":
            active = True
        elif active:
            output.append(line)
    return "\n".join(output)


root_model_part = (ROOT / "src/main/java/dev/mlua/modules/mixin/service/ModelPartMixin.java").read_text(encoding="utf-8")
legacy_model_part = filter_versioned_source(root_model_part, "1.20.1")
modern_model_part = filter_versioned_source(root_model_part, "1.21.1")
four_arg_render = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"
color_render = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"
if four_arg_render not in legacy_model_part:
    fail("1.20.1 shared ModelPartMixin lost its native four-argument render hook")
if color_render in legacy_model_part:
    fail("1.20.1 shared ModelPartMixin targets the unavailable five-integer color overload")
if color_render not in modern_model_part:
    fail("1.21.1 shared ModelPartMixin does not cover its five-integer color overload")

targets = TARGET_CONFIG["targets"]
supported = {
    (str(target["minecraft"]), str(loader))
    for target in targets
    for loader in target["loaders"]
}
expected = {
    ("1.20.1", "fabric"), ("1.20.1", "forge"),
    ("1.21.1", "fabric"), ("1.21.1", "forge"), ("1.21.1", "neoforge"),
    ("26.1", "fabric"), ("26.1", "neoforge"),
    ("26.1.1", "fabric"), ("26.1.2", "fabric"), ("26.1.2", "neoforge"),
}
if supported != expected:
    fail(f"render audit target list drifted; expected {len(expected)} targets, found {len(supported)}")

def model_part_source(version: str, loader: str) -> Path:
    if version.startswith("26.") and loader == "fabric":
        return ROOT / "mlua-loaders/fabric/26.1/impl/dev/mlua/modules/mixin/service/ModelPartMixin.java"
    if version.startswith("26.") and loader == "neoforge":
        return ROOT / "mlua-loaders/neoforge/26.1/impl/dev/mlua/mixin/ModelPartMixin.java"
    if version == "1.21.1" and loader == "neoforge":
        return ROOT / "mlua-loaders/neoforge/1.21.1/impl/dev/mlua/mixin/ModelPartMixin.java"
    return ROOT / "src/main/java/dev/mlua/modules/mixin/service/ModelPartMixin.java"


for version, loader in sorted(supported):
    source_path = model_part_source(version, loader)
    if not source_path.is_file():
        fail(f"{loader} {version} selects a missing ModelPartMixin source: {source_path}")
    source = source_path.read_text(encoding="utf-8")
    if source_path == ROOT / "src/main/java/dev/mlua/modules/mixin/service/ModelPartMixin.java":
        source = filter_versioned_source(source, version)
    if four_arg_render not in source:
        fail(f"{loader} {version} has no ModelPart four-argument render hook")
    if version == "1.20.1" and color_render in source:
        fail(f"{loader} {version} targets the unavailable five-integer ModelPart overload")
    if version != "1.20.1" and color_render not in source:
        fail(f"{loader} {version} omits the five-integer ModelPart overload")
    if source.count('at = @At("RETURN")') < (1 if version == "1.20.1" else 2):
        fail(f"{loader} {version} does not restore ModelPart state after each render overload")


def item_hook_source(version: str, loader: str, perspective: str) -> Path:
    """Resolve loader hook source by walking the build's inherited source roots."""
    if perspective == "first-person":
        fallback = ROOT / "src/main/java/dev/mlua/mixin/FirstPersonItemRendererMixin.java"
        paths = {
            "fabric": "impl/dev/mlua/mixin/FirstPersonItemRendererMixin.java",
            "forge": "project/src/main/java/dev/mlua/mixin/FirstPersonItemRendererMixin.java",
            "neoforge": "impl/dev/mlua/mixin/FirstPersonItemRendererMixin.java",
        }
    else:
        fallback = ROOT / "src/main/java/dev/mlua/modules/mixin/service/ThirdPersonItemRendererMixin.java"
        paths = {
            "fabric": "impl/dev/mlua/modules/mixin/service/ThirdPersonItemRendererMixin.java",
            "neoforge": "impl/dev/mlua/mixin/ThirdPersonItemRendererMixin.java",
        }
    source_path = paths.get(loader)
    if source_path is None:
        return fallback
    return inherited_loader_source(loader, version, source_path, fallback)

def item_hook_is_installed(version: str, loader: str, perspective: str) -> bool:
    mixin_name = "FirstPersonItemRendererMixin" if perspective == "first-person" else "ThirdPersonItemRendererMixin"
    if loader == "fabric":
        config_version = "26.1" if version.startswith("26.") else version
        config_path = ROOT / f"mlua-loaders/fabric/{config_version}/project/src/main/resources/mlua.fabric.mixins.json"
        config = json.loads(config_path.read_text(encoding="utf-8"))
        return mixin_name in set(config.get("client", [])) | set(config.get("mixins", []))
    if loader == "forge":
        if perspective == "third-person":
            config = json.loads((ROOT / "src/main/resources/mlua.forge.mixins.json").read_text(encoding="utf-8"))
            return mixin_name in set(config.get("client", []))
        config = json.loads((ROOT / "src/main/resources/mlua.firstperson.mixins.json").read_text(encoding="utf-8"))
        return mixin_name in set(config.get("client", []))
    generator = NEOFORGE_CONFIG_GENERATOR.read_text(encoding="utf-8")
    return f"'{mixin_name}'" in generator


for version, loader in sorted(supported):
    for perspective, callback_name in (
        ("first-person", "renderFirstPersonItem"),
        ("third-person", "renderThirdPersonItem"),
    ):
        source_path = item_hook_source(version, loader, perspective)
        if not source_path.is_file():
            fail(f"{loader} {version} selects a missing {perspective} item hook source: {source_path}")
        source = source_path.read_text(encoding="utf-8")
        if callback_name not in source:
            fail(f"{loader} {version} {perspective} item hook does not dispatch {callback_name}")
        if not item_hook_is_installed(version, loader, perspective):
            fail(f"{loader} {version} does not install its {perspective} item render hook")

fabric_mixins = {
    "1.20.1": ROOT / "mlua-loaders/fabric/1.20.1/project/src/main/resources/mlua.fabric.mixins.json",
    "1.21.1": ROOT / "mlua-loaders/fabric/1.21.1/project/src/main/resources/mlua.fabric.mixins.json",
    "26.1": ROOT / "mlua-loaders/fabric/26.1/project/src/main/resources/mlua.fabric.mixins.json",
}
for version in ("1.20.1", "1.21.1", "26.1", "26.1.1", "26.1.2"):
    source_version = "26.1" if version.startswith("26.") else version
    config_path = fabric_mixins[source_version]
    config = json.loads(config_path.read_text(encoding="utf-8"))
    installed = set(config.get("client", []))
    for name in ("LivingEntityRendererMixin", "EntityRenderDispatcherMixin", "ModelPartMixin"):
        if name not in installed:
            fail(f"Fabric {version} does not install {name}")
    if source_version == "26.1" and "ModelFeatureRendererMixin" not in installed:
        fail(f"Fabric {version} does not install its render-state model submission hook")

for version in ("1.20.1", "1.21.1"):
    config = json.loads((ROOT / "src/main/resources/mlua.forge.mixins.json").read_text(encoding="utf-8"))
    for name in ("LivingEntityRendererMixin", "EntityRenderDispatcherMixin", "ModelPartMixin"):
        if name not in config.get("client", []):
            fail(f"Forge {version} does not install {name}")

generator = NEOFORGE_CONFIG_GENERATOR.read_text(encoding="utf-8")
for version in ("1.21.1", "26.1", "26.1.2"):
    for name in ("LivingEntityRendererMixin", "EntityRenderDispatcherMixin", "ModelPartMixin"):
        if f"'{name}'" not in generator:
            fail(f"NeoForge {version} mixin config generation omits {name}")
    if version.startswith("26.") and "(is26Family ? ['ModelFeatureRendererMixin'] : [])" not in generator:
        fail(f"NeoForge {version} does not install its render-state model submission hook")

runtime_target = NEOFORGE_RUNTIME_TARGET.read_text(encoding="utf-8")
if "'dev/mlua/mixin/FirstPersonItemRendererMixin.java'" not in runtime_target:
    fail("NeoForge nested runtime must exclude the outer loader's first-person mixin to avoid duplicate JPMS packages")

callback_source = CALLBACKS.read_text(encoding="utf-8")
frame_context_factory_path = ROOT / "src/main/java/dev/mlua/runtime/client/MLuaRenderFrameContextFactory.java"
if not frame_context_factory_path.is_file():
    fail("render callback state projection and draw submission must have a dedicated owner")
frame_context_factory = frame_context_factory_path.read_text(encoding="utf-8")
frame_context_owner_path = ROOT / "src/main/java/dev/mlua/runtime/client/MLuaRenderFrameContexts.java"
if not frame_context_owner_path.is_file():
    fail("temporary RenderContext-to-frame-scope ownership must have a dedicated component")
frame_context_owner = frame_context_owner_path.read_text(encoding="utf-8")
if "WeakHashMap<LuaTable, MLuaRenderFrameScope> CONTEXTS" not in frame_context_owner \
        or "MLuaRenderFrameContexts.register(instance.checktable(), frameScope)" not in frame_context_factory:
    fail("render frame-context construction must delegate weak scope ownership and cleanup to its component")
if "MLuaRenderFrameContextFactory.create(" not in callback_source \
        or "MLuaRenderFrameContextFactory.drawObject(" not in callback_source \
        or "MLuaRenderFrameContextFactory.withPose(" not in callback_source:
    fail("render callback dispatch must delegate context construction and draw operations to its component")
if "FRAME_SCOPES" in callback_source:
    fail("render callback dispatch must not own the frame-scope identity map")
safe_dispatch = callback_source.split("private static void invokeSafely(Hook hook", 1)
if len(safe_dispatch) != 2:
    fail("render callback setup has no per-hook exception boundary")
dispatch_methods = safe_dispatch[0]
safe_boundary = safe_dispatch[1].split("private static void invoke(Hook hook", 1)[0]
if "invoke(hook," in dispatch_methods:
    fail("a render pass bypasses protected argument/context construction")
if callback_source.count("invokeSafely(hook,") != 8:
    fail("a render target is missing from the protected callback dispatch path")
if "disableHook(hook, failure);" not in safe_boundary or "VirtualMachineError | ThreadDeath" not in safe_boundary:
    fail("render setup failures must disable only the failing hook and rethrow fatal JVM errors")
adapter_source = MODEL_ADAPTERS.read_text(encoding="utf-8")
renderer_adapter_source = RENDERER_ADAPTERS.read_text(encoding="utf-8")
model_field_access = MODEL_FIELD_ACCESS.read_text(encoding="utf-8")
if "recordRenderFailureOnce(key)" not in adapter_source:
    fail("model adapter preparation failures are not deduplicated through the bounded warning cache")
if "RENDERER_ADAPTERS = new ConcurrentHashMap" in adapter_source \
        or "MLuaRendererAdapterRegistry.register(rendererClass, modelFieldPath, owner)" not in adapter_source \
        or "Map<String, Map<Owner, String>> ADAPTERS" not in renderer_adapter_source:
    fail("renderer field-path registrations must have a dedicated registry owner")
if "rendererModelFieldPath(Class<?> rendererClass, long generation" not in model_field_access \
        or "ClassValue<RendererModelPathCache> RENDERER_MODEL_PATHS" not in model_field_access:
    fail("renderer model-field lookups must be cached by the field-access owner and invalidated by registry generation")
if "orderedResolvers(type.getName(), resolvers)" not in adapter_source \
        or "ORDERED_RESOLVERS.clear();" not in adapter_source:
    fail("model resolver registration order must be cached between adapter changes")
failure_cache = adapter_source.split("private static boolean recordRenderFailureOnce", 1)
if len(failure_cache) != 2 or "MAX_RENDERER_WARNING_KEYS" not in failure_cache[1].split("\n    }", 1)[0]:
    fail("model adapter failure deduplication does not honor the bounded warning-key limit")

render_service = (ROOT / "src/main/java/dev/mlua/modules/render/service/MLuaRenderService.java").read_text(encoding="utf-8")
render_object_api = RENDER_OBJECT_API.read_text(encoding="utf-8")
render_object_operations = RENDER_OBJECT_OPERATIONS.read_text(encoding="utf-8")
render_api = render_service + "\n" + render_object_api + "\n" + render_object_operations
lua_errors = (ROOT / "mlua-api/src/main/java/dev/mlua/api/MLuaLuaErrors.java").read_text(encoding="utf-8")
if "extends MLuaRenderObjectApi" not in render_service or "class MLuaRenderObjectApi extends MLuaReflectiveService" not in render_object_api:
    fail("RenderService must keep its documented object API in the dedicated reflective object owner")
reflective_catalog = (ROOT / "mlua-api/src/main/java/dev/mlua/core/service/MLuaReflectiveServiceCatalog.java").read_text(encoding="utf-8")
if ("publicServiceMethods(serviceType)" not in reflective_catalog
        or "current.getDeclaredMethods()" not in reflective_catalog
        or "collectPublicInterfaceMethods(parentInterface, methods" not in reflective_catalog):
    fail("reflective services must scan public methods across their class and interface hierarchy")
if "public static VarArgFunction operation(String operation" not in lua_errors:
    fail("Java-backed Lua callbacks must use the common Lua exception boundary")
if "MLuaLuaErrors.operation(\"Render service call\"" not in render_object_operations or "MLuaRenderService.fn(" in render_api:
    fail("render object bindings must use the common Lua callback error owner")
animation_binding = (ROOT / "src/main/java/dev/mlua/modules/render/service/MLuaRenderAnimationBinding.java").read_text(encoding="utf-8")
if "MLuaLuaErrors.operation(\"Render animation operation\", body)" not in animation_binding:
    fail("render animation operations must use the common Lua callback error boundary")
render_docs = (ROOT / "src/main/java/dev/mlua/modules/render/RenderModule.java").read_text(encoding="utf-8")
connection_methods = (
    "RegisterEntityRenderer", "RegisterEntityRenderPass", "RegisterEntityDrawPass",
    "RegisterEntityPostRenderPass", "RegisterFirstPersonItemRenderer",
    "RegisterFirstPersonItemRenderPass", "RegisterThirdPersonItemRenderer",
    "RegisterThirdPersonItemRenderPass", "RegisterWorldRenderer", "RegisterWorldRendererAt",
    "RegisterWorldRenderPass", "RegisterWorldGeometryRenderer", "RegisterWorldGeometryPass",
    "RegisterHudRenderer", "RegisterHudRenderPass",
)
for name in connection_methods:
    if f"public LuaTable {name}(" not in render_api:
        fail(f"RenderService:{name} must return a Lua Connection instance")
    if f'"RenderService:{name}(' not in render_docs or '"Connection"));' not in render_docs.split(
        f'"RenderService:{name}(', 1
    )[1].split("\n", 1)[0]:
        fail(f"RenderService:{name} documentation must declare its Connection return value")
if "MLuaLuaBindings.connection(connection, this.context.registration())" not in render_api:
    fail("render registration handles are not wrapped as registered Lua Connection instances")

model_service = (ROOT / "src/main/java/dev/mlua/modules/model/service/MLuaModelService.java").read_text(encoding="utf-8")
model_registry = (ROOT / "src/main/java/dev/mlua/runtime/client/MLuaModelAdapterRegistry.java").read_text(encoding="utf-8")
field_access_path = ROOT / "src/main/java/dev/mlua/runtime/client/MLuaModelFieldAccess.java"
if not field_access_path.is_file():
    fail("model reflection and field-path caching must have a dedicated owner")
field_access = field_access_path.read_text(encoding="utf-8")
for operation in ("rendererFields(Class<?> rendererClass)", "resolveFieldPath(Object root, String path)",
                  "readField(Object target, String name)", "pathSegments(String path)"):
    if operation not in field_access:
        fail(f"model field access owner is missing {operation}")
renderer_resolver_path = ROOT / "src/main/java/dev/mlua/runtime/client/MLuaRendererModelResolver.java"
if not renderer_resolver_path.is_file():
    fail("renderer model selection must have a dedicated resolver")
renderer_resolver = renderer_resolver_path.read_text(encoding="utf-8")
if "MLuaRendererModelResolver.findModel(renderer, configuredPath" not in model_registry:
    fail("model adapter registry must delegate renderer model selection")
if "MLuaModelFieldAccess.rendererFields(renderer.getClass())" not in renderer_resolver:
    fail("renderer model resolver must delegate cached field discovery")
if "MLuaModelFieldAccess.resolveFieldPath(renderer, configuredPath)" not in renderer_resolver:
    fail("renderer model resolver must delegate configured field paths")
if "MODEL_FIELDS" in model_registry or "RENDERER_FIELDS" in model_registry:
    fail("model adapter registry still owns reflection field caches")
model_docs = (ROOT / "src/main/java/dev/mlua/modules/model/ModelModule.java").read_text(encoding="utf-8")
connection_source = (ROOT / "mlua-core/src/main/java/dev/mlua/core/signal/MLuaConnection.java").read_text(encoding="utf-8")
for name in ("RegisterAdapterResolver", "RegisterCustomAdapter"):
    if f"public LuaTable {name}(" not in model_service:
        fail(f"ModelService:{name} must return a Lua Connection instance")
    if f'"ModelService:{name}(' not in model_docs or '"Connection"));' not in model_docs.split(
        f'"ModelService:{name}(', 1
    )[1].split("\n", 1)[0]:
        fail(f"ModelService:{name} documentation must declare its Connection return value")
if "MLuaConnection.owned(resolver, MLuaModelAdapterRegistry::unregisterResolver" not in model_registry:
    fail("model resolver connections are not bound to the exact resolver registration")
if "MLuaConnection.owned(replacement, MLuaModelAdapterRegistry::unregisterCustomAdapter" not in model_registry:
    fail("custom model adapter connections are not bound to the exact adapter registration")
if "WeakReference<T> reference = new WeakReference<>(resource)" not in connection_source:
    fail("owned Connection handles must not strongly retain render/model callback resources")
if "this.disconnect = null;" not in connection_source or "this.active = null;" not in connection_source:
    fail("disconnect and owner teardown must release Connection cleanup closures")

handle_cache_path = ROOT / "src/main/java/dev/mlua/modules/render/service/MLuaRenderObjectHandleCache.java"
if not handle_cache_path.is_file():
    fail("render object handle cache must have a dedicated owner")
handle_cache = handle_cache_path.read_text(encoding="utf-8")
for contract in ("ReferenceQueue<LuaTable>", "WeakReference<LuaTable>",
                 "synchronized LuaTable get(int id)", "synchronized void put(int id, LuaTable handle)",
                 "void remove(int id)", "void clear()"):
    if contract not in handle_cache:
        fail(f"render object handle cache is missing {contract}")
if "new MLuaRenderObjectHandleCache()" not in render_api:
    fail("RenderService must delegate weak handle ownership to the cache")
if "ReferenceQueue<" in render_api or "WeakReference<" in render_api:
    fail("RenderService still owns weak handle-cache implementation details")
for operation in ("objectHandleCache.get(id)", "objectHandleCache.put(id, handle)",
                  "objectHandleCache.remove(id)", "objectHandleCache.clear()"):
    if operation not in render_api:
        fail(f"RenderService does not route handle lifecycle through its cache: {operation}")

context_access_path = ROOT / "src/main/java/dev/mlua/runtime/client/MLuaRenderContextAccess.java"
if not context_access_path.is_file():
    fail("loader-specific camera and render-context reflection must have a dedicated owner")
context_access = context_access_path.read_text(encoding="utf-8")
for operation in ("optional(Object target, String... names)", "cameraLook(Object camera)",
                  "cameraPosition(Object camera)", "ClassValue<ConcurrentMap<String, Optional<Method>>> ACCESSORS"):
    if operation not in context_access:
        fail(f"render-context access owner is missing {operation}")
render_manager = (ROOT / "src/main/java/dev/mlua/runtime/client/MLuaClientRenderManager.java").read_text(encoding="utf-8")
world_render_pipeline = (ROOT / "src/main/java/dev/mlua/runtime/client/MLuaWorldRenderPipeline.java").read_text(encoding="utf-8")
render_paths = render_manager + "\n" + world_render_pipeline
if "//--version 1.20.1,1.21.1" not in world_render_pipeline:
    fail("shared world pipeline must be excluded where 26.1 uses loader-specific render managers")
if "package dev.mlua.runtime.client;" in filter_versioned_source(world_render_pipeline, "26.1"):
    fail("26.1 root sources must not compile the legacy ResourceLocation world pipeline")
render_object = (ROOT / "src/main/java/dev/mlua/runtime/client/MLuaRenderObject.java").read_text(encoding="utf-8")
if "public static final class RenderObject extends MLuaRenderObject" not in render_manager:
    fail("render manager must keep its public RenderObject compatibility type")
if "MLuaWorldRenderPipeline.renderWorldContext(stage, context);" not in render_manager:
    fail("render manager must delegate world-stage work to the dedicated pipeline")
if "static void renderWorldContext(String stage, Object context)" not in world_render_pipeline \
        or "submitCustomGeometry(" not in world_render_pipeline:
    fail("world render pipeline must own context routing and deferred geometry submission")
for operation in ("MLuaWorldRenderPipeline.submitMeshGeometry(",
                  "MLuaWorldRenderPipeline.submitBBModelGeometry("):
    if operation not in render_manager:
        fail(f"render manager must share deferred geometry submission across render targets: {operation}")
if "private static void renderWorldModels(" in render_manager \
        or "private static boolean submitWorldMesh(" in render_manager:
    fail("world render implementation is duplicated in the render manager")
for contract in ("class MLuaRenderObject", "executionLock()", "setFollowEntityScope(",
                 "setFollowOffsetCallback(", "copy()"):
    if contract not in render_object:
        fail(f"dedicated render object state owner is missing {contract}")
if "MLuaRenderContextAccess.cameraLook(camera)" not in render_manager:
    fail("render manager must preserve its camera look call while delegating accessor resolution")
if "MLuaRenderContextAccess.cameraPosition(rawCamera)" not in world_render_pipeline:
    fail("world render pipeline must resolve loader camera positions through the context owner")
if "invokeOptional(" in render_manager:
    fail("render manager still owns loader-specific reflective context lookup")

target_index_path = ROOT / "src/main/java/dev/mlua/runtime/client/MLuaRenderTargetIndex.java"
if not target_index_path.is_file():
    fail("entity and held-item render target indexes must have one dedicated owner")
target_index = target_index_path.read_text(encoding="utf-8")
for operation in ("orderedEntityModels(MLuaEntityScope scope)",
                  "orderedHeldItemModels(", "entitiesByObject", "heldItemsByObject",
                  "void removeObject(int id)", "void registerEntityModel(",
                  "void unregisterEntityModel("):
    if operation not in target_index:
        fail(f"render target index is missing {operation}")
if "ConcurrentMap<Integer, MLuaEntityScope> entitiesByObject" not in target_index:
    fail("entity model removal must use a reverse index instead of scanning every entity")
if "ConcurrentMap<Integer, HeldItemKey> heldItemsByObject" not in target_index:
    fail("held item target changes must use a reverse index")
for operation in ("ConcurrentMap<HeldItemKey, OrderedObjects> orderedHeldItems",
                  "ConcurrentMap<MLuaEntityScope, OrderedObjects> orderedEntities",
                  "AtomicLong orderVersion", "List.copyOf(objects)",
                  "void invalidateOrder()"):
    if operation not in target_index:
        fail(f"render target index is missing ordered snapshot caching: {operation}")
if "RENDER_TARGET_INDEX.invalidateOrder();" not in render_manager or "MLuaClientRenderManager.invalidateRenderOrder();" not in render_object:
    fail("render object z-order changes must invalidate cached target ordering through the manager")
for operation in ("RENDER_TARGET_INDEX.orderedEntityModels(scope)",
                  "RENDER_TARGET_INDEX.orderedHeldItemModels(target, itemId)",
                  "RENDER_TARGET_INDEX.removeObject(id)",
                  "RENDER_TARGET_INDEX.registerEntityModel(scope, id)"):
    if operation not in render_manager:
        fail(f"render manager does not delegate target indexing: {operation}")
if "MLuaClientRenderManager.registerEntityModel(followEntityScope, this.id);" not in render_object:
    fail("render object attachment changes must update the manager-owned target index")
for overlay in (
    ROOT / "mlua-loaders/fabric/26.1/project/src/main/java/dev/mlua/runtime/client/MLuaClientRenderManager.java",
    ROOT / "mlua-loaders/neoforge/26.1/project/src/main/java/dev/mlua/runtime/client/MLuaClientRenderManager.java",
):
    if not overlay.is_file():
        fail(f"26.1 render manager overlay is missing: {overlay.relative_to(ROOT)}")
    overlay_source = overlay.read_text(encoding="utf-8")
    if "public static final class RenderObject extends MLuaRenderObject" not in overlay_source:
        fail(f"{overlay.relative_to(ROOT)} must use the common render-object state owner")
    if "static void updateHeldItemModelIndex(MLuaRenderObject object" not in overlay_source:
        fail(f"{overlay.relative_to(ROOT)} must preserve held-item indexing for shared render objects")
    if "static void invalidateRenderOrder()" not in overlay_source:
        fail(f"{overlay.relative_to(ROOT)} must invalidate cached order for shared render objects")
    for operation in ("MLuaRenderVertexEmitter.submitBBFace(",
                      "MLuaRenderVertexEmitter.submitVertex(",
                      "MLuaRenderVertexEmitter.applyTransform("):
        if operation not in overlay_source:
            fail(f"{overlay.relative_to(ROOT)} must delegate shared geometry to the vertex emitter: {operation}")
    if "private static void submitBBFace(" in overlay_source or "private static void submitVertex(" in overlay_source:
        fail(f"{overlay.relative_to(ROOT)} still duplicates common vertex emission")
    for operation in ("MLuaAnimationScratch.current()", "scratch.position", "scratch.rotation", "scratch.scale"):
        if operation not in overlay_source:
            fail(f"{overlay.relative_to(ROOT)} must reuse render-thread animation vectors: {operation}")
    if "new float[3]" in overlay_source:
        fail(f"{overlay.relative_to(ROOT)} must not allocate animation scratch vectors per draw")
vertex_emitter_path = ROOT / "mlua-loaders/common/26.1/project/src/main/java/dev/mlua/runtime/client/MLuaRenderVertexEmitter.java"
if not vertex_emitter_path.is_file():
    fail("26.1 mesh and Blockbench vertex emission must have one common source owner")
vertex_emitter = vertex_emitter_path.read_text(encoding="utf-8")
if "float[][] points" in vertex_emitter or "float[][] corners" in vertex_emitter:
    fail("shared BBModel face emission must not allocate nested coordinate arrays per face")
if "transform.toEulerAnglesXYZ(scratch.eulerAngles)" not in vertex_emitter:
    fail("shared vertex transforms must write into reusable Euler scratch storage")
neoforge_build = NEOFORGE_CONFIG_GENERATOR.read_text(encoding="utf-8")
for operation in ("@Mixin(value = ${arguments}, remap = false)",
                  "package dev.mlua.modules.mixin.service;', 'package dev.mlua.mixin.service;'",
                  "replace(', at = @At', ', remap = false, at = @At')",
                  "@At(value = \"$1\", remap = false)",
                  "@Invoker(value = \"$1\", remap = false)"):
    if operation not in neoforge_build:
        fail(f"NeoForge service mixin preparation must disable foreign obfuscation mappings: {operation}")
if "package: 'dev.mlua.mixin.service'" not in neoforge_build:
    fail("NeoForge service mixins must use a package retained in the loader JAR")
if "ENTITY_MODELS" in render_manager or "HELD_ITEM_MODELS" in render_manager:
    fail("render manager must not own target index storage")

forge_client_bridge_path = ROOT / "mlua-loaders/forge/1.21.1/project/src/main/java/dev/mlua/loader/impl/forge/ForgeClientBridge.java"
forge_client_bridge = forge_client_bridge_path.read_text(encoding="utf-8")
if "IEventBus eventBus = isModBusEvent(eventClass) ? modBus : MinecraftForge.EVENT_BUS;" not in forge_client_bridge:
    fail("Forge overlay hooks must register on the bus that owns each event type")
if "private static boolean isModBusEvent(Class<?> eventClass)" not in forge_client_bridge:
    fail("Forge overlay hooks must identify mod-bus events before listener registration")

model_render_plan_path = ROOT / "src/main/java/dev/mlua/runtime/client/MLuaBBModelRenderPlan.java"
if not model_render_plan_path.is_file():
    fail("deferred BBModel material batching must have one dedicated owner")
model_render_plan = model_render_plan_path.read_text(encoding="utf-8")
for operation in ("record TextureBatch(", "static MLuaBBModelRenderPlan prepare(",
                  "List.copyOf(textureBatches)", "FACE_NAMES"):
    if operation not in model_render_plan:
        fail(f"BBModel render plan is missing immutable prepared batches: {operation}")
for operation in ("MLuaBBModelRenderPlan.prepare(asset, object.modelTextures)",
                  "object.bbModelRenderPlan.textureBatches()", "batch.cubes()"):
    if operation not in render_paths:
        fail(f"render pipeline does not reuse prepared BBModel material batches: {operation}")
if "cubesByTexture" in render_paths:
    fail("deferred BBModel rendering must not rebuild material groups every frame")
animation_scratch_path = ROOT / "src/main/java/dev/mlua/runtime/client/MLuaAnimationScratch.java"
if not animation_scratch_path.is_file():
    fail("BBModel animation vectors must have a render-thread-local scratch owner")
animation_scratch = animation_scratch_path.read_text(encoding="utf-8")
if "ThreadLocal<MLuaAnimationScratch>" not in animation_scratch:
    fail("animation scratch vectors must not be shared across render threads")
if "final double[] eulerAngles = new double[3]" not in animation_scratch:
    fail("render transform angles must reuse a thread-local output vector")
core_cframe_path = ROOT / "mlua-core/src/main/java/dev/mlua/math/MLuaCFrame.java"
core_cframe = core_cframe_path.read_text(encoding="utf-8")
if "void toEulerAnglesXYZ(double[] output)" not in core_cframe:
    fail("CFrame must expose allocation-free XYZ Euler output for render loops")
for operation in ("MLuaAnimationScratch.current()", "scratch.position", "scratch.rotation", "scratch.scale"):
    if operation not in render_paths:
        fail(f"BBModel render loops must reuse thread-local animation vectors: {operation}")

camera_path = ROOT / "src/main/java/dev/mlua/minecraft/client/MLuaCameraController.java"
camera = camera_path.read_text(encoding="utf-8")
if "ThreadLocal<double[]> EULER_SCRATCH" not in camera or "offset.toEulerAnglesXYZ(euler)" not in camera:
    fail("first-person hand transforms must reuse thread-local Euler output storage")
if "offset.toEulerAnglesXYZ()" in camera:
    fail("first-person hand transforms must not allocate Euler arrays per render")

hand_pose_path = ROOT / "src/main/java/dev/mlua/runtime/client/MLuaHandPoseRegistry.java"
hand_pose = hand_pose_path.read_text(encoding="utf-8")
for operation in ("class ComposedPose", "private long poseRevision", "invalidateComposedPoses();",
                  "cached.revision == poseRevision", "cached.partialTickBits == partialTickBits"):
    if operation not in hand_pose:
        fail(f"held-item pose composition must cache immutable frame results: {operation}")

for path in (
    ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaScriptedEntity.java",
    ROOT / "mlua-versions/common/26.1/dev/mlua/minecraft/scripted/MLuaScriptedEntity.java",
):
    scripted_entity = path.read_text(encoding="utf-8")
    if "double[] euler = new double[3];" not in scripted_entity or "transform.toEulerAnglesXYZ(euler);" not in scripted_entity:
        fail(f"{path.relative_to(ROOT)} must reuse one Euler output array per animation sync")
    if "entry.getValue().toEulerAnglesXYZ()" in scripted_entity:
        fail(f"{path.relative_to(ROOT)} must not allocate Euler arrays per synced bone")
if "new float[3]" in render_paths:
    fail("BBModel render loops must not allocate animation scratch vectors per draw")

if not OBJECT_CREATION.is_file():
    fail("render object creation must have one dedicated owner")
object_creation = OBJECT_CREATION.read_text(encoding="utf-8")
for operation in ("MLuaMeshLuaParser.parse(definition)", "MLuaBBModelLoader.load(path)",
                  "createBBModel(asset, this.service.owner())", "createWorldObject("):
    if operation not in object_creation:
        fail(f"render object creation owner is missing {operation}")
for operation in ("this.objectCreation.create(type, resource, props)",
                  "this.objectCreation.createMesh(definition, props)",
                  "this.objectCreation.loadBBModel(path, props)"):
    if operation not in render_api:
        fail(f"RenderService does not delegate object creation: {operation}")

print(f"Render hook contract passed for {len(supported)} supported loader/version targets.")
