package dev.mlua.modules.registry.service;

import java.util.List;

import dev.mlua.api.MLuaLuaErrors;
import com.google.gson.JsonObject;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Installs addon generated data, resource assets, and world generation registrations. */
final class MLuaRegistryGeneratedResourceApi {
   private MLuaRegistryGeneratedResourceApi() { }

   static void install(MLuaRegistryService service, LuaTable dataPack, LuaTable assets, LuaTable worldGeneration) {
      dataPack.set("Register", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String resourceType = args.checkjstring(2);
            String id = args.checkjstring(3);
            String owner = service.apiContext().mlua().ownerModId();
            LuaTable definition = args.checktable(4);
            JsonObject json = MLuaLuaJson.objectArg(definition, "data pack resource");
            try {
               service.registerGeneratedDataResource(owner, resourceType, id, json);
            } catch (IllegalArgumentException | IllegalStateException error) {
               throw MLuaLuaErrors.javaFailure("Registry data pack registration", error);
            }
            return LuaValue.valueOf(service.generatedAssetId(owner, id));
         }
      });
      assets.set("register", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String path = args.checkjstring(2);
            JsonObject json = MLuaLuaJson.objectArg(args.arg(3), "asset resource");
            try {
               service.registerGeneratedAssetResource(owner, path, json);
            } catch (IllegalArgumentException | IllegalStateException error) {
               throw MLuaLuaErrors.javaFailure("Registry asset registration", error);
            }
            return LuaValue.valueOf(service.generatedAssetId(owner, path));
         }
      });
      assets.rawset("__mlua_doc_description", LuaValue.valueOf(
         "Generates JSON files under the addon's assets namespace, including shared manifests such as sounds.json."));
      worldGeneration.set("register", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            String resourceType = args.checkjstring(2);
            if (!resourceType.equals("dimension")
               && !resourceType.equals("dimension_type") && !resourceType.startsWith("worldgen/")) {
               throw new LuaError("[MLua] WorldGeneration.register accepts dimension, dimension_type, or worldgen/* resources such as worldgen/biome");
            }
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String id = args.checkjstring(3);
            LuaTable definition = args.checktable(4);
            service.registerWorldGenerationResource(owner, resourceType, id, definition);
            return LuaValue.valueOf(service.generatedAssetId(owner, id));
         }
      });
      worldGeneration.set("Register", worldGeneration.get("register"));
      service.addWorldGenerationRegistration(worldGeneration, "Biome", "worldgen/biome");
      service.addWorldGenerationRegistration(worldGeneration, "DimensionType", "dimension_type");
      service.addWorldGenerationRegistration(worldGeneration, "Dimension", "dimension");
      service.addWorldGenerationRegistration(worldGeneration, "NoiseSettings", "worldgen/noise_settings");
      service.addWorldGenerationRegistration(worldGeneration, "DensityFunction", "worldgen/density_function");
      service.addWorldGenerationRegistration(worldGeneration, "ConfiguredFeature", "worldgen/configured_feature");
      service.addWorldGenerationRegistration(worldGeneration, "PlacedFeature", "worldgen/placed_feature");
      service.addWorldGenerationRegistration(worldGeneration, "ConfiguredCarver", "worldgen/configured_carver");
      service.addWorldGenerationRegistration(worldGeneration, "Structure", "worldgen/structure");
      service.addWorldGenerationRegistration(worldGeneration, "StructureSet", "worldgen/structure_set");
      service.addWorldGenerationRegistration(worldGeneration, "TemplatePool", "worldgen/template_pool");
      service.installGeneratedDataLookupApi(dataPack);
      service.installGeneratedDataLookupApi(worldGeneration);
      worldGeneration.set("addFeature", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String name = args.checkjstring(2);
            LuaTable definition = args.checktable(3);
            String feature = MLuaRegistryPropertyReaders.stringProperty(definition, "feature", "Feature", "placedFeature", "PlacedFeature");
            String step = MLuaRegistryWorldGenerationValues.decorationStep(definition);
            if (feature == null || feature.isBlank()) throw new LuaError("[MLua] WorldGeneration.addFeature requires a placed feature id");
            if (step == null || step.isBlank()) throw new LuaError("[MLua] WorldGeneration.addFeature requires a generation step");
            try {
               step = dev.mlua.loaderapi.WorldGenerationBridge.normalizeStep(step);
            } catch (IllegalArgumentException error) {
               throw MLuaLuaErrors.javaFailure("World generation step validation", error);
            }
            LuaValue biomes = definition.get("biomes");
            if (biomes.isnil()) biomes = definition.get("Biomes");
            String featureId = MLuaRegistryWorldGenerationValues.qualifiedId(owner, feature, false);
            List<String> biomeIds = MLuaRegistryPropertyReaders.stringValues(biomes, "biomes").stream()
               .map(value -> MLuaRegistryWorldGenerationValues.qualifiedId(owner,
                  value.startsWith("#") ? value.substring(1) : value, value.startsWith("#")))
               .toList();
            try {
               dev.mlua.loaderapi.WorldGenerationBridge bridge = dev.mlua.loaderapi.LoaderApi.worldGeneration();
               String registeredStep = step;
               bridge.addPlacedFeature(owner, name, featureId, biomeIds, registeredStep);
               service.apiContext().mlua().registration().trackRollback(() ->
                  bridge.removePlacedFeature(owner, name, featureId, biomeIds, registeredStep));
            } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException error) {
               throw MLuaLuaErrors.javaFailure("World generation feature registration", error);
            }
            return LuaValue.valueOf(service.generatedAssetId(owner, name));
         }
      });
      worldGeneration.set("AddFeature", worldGeneration.get("addFeature"));
      MLuaRegistryOreVeinApi.install(service, worldGeneration);
      assets.set("Register", assets.get("register"));
   }
}
