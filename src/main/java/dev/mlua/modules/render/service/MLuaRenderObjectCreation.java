package dev.mlua.modules.render.service;

import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import dev.mlua.runtime.client.model.MLuaBBModelLoader;
import dev.mlua.runtime.client.model.MLuaMeshLuaParser;
import java.io.IOException;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns render-object creation for primitives, meshes, models, and world-space objects. */
final class MLuaRenderObjectCreation {
   private final MLuaRenderObjectApi service;

   MLuaRenderObjectCreation(MLuaRenderObjectApi service) {
      this.service = service;
   }

   LuaValue create(MLuaClientRenderManager.RenderType type, String resource, LuaTable properties) {
      this.service.requireClientApi();
      int id = MLuaClientRenderManager.create(type, resource, this.service.owner());
      return this.service.configureObject(id, properties, null);
   }

   LuaValue createMesh(LuaTable definition, LuaTable properties) {
      this.service.requireClientApi();
      var mesh = MLuaMeshLuaParser.parse(definition);
      int id = MLuaClientRenderManager.createMesh(mesh, this.service.owner());
      return this.service.configureObject(id, properties, null);
   }

   LuaValue loadBBModel(String path, LuaTable properties) {
      this.service.requireClientApi();
      try {
         LuaValue animationPathValue = MLuaRenderObjectProperties.read(properties, "AnimationPath");
         LuaValue animationsPathValue = MLuaRenderObjectProperties.read(properties, "AnimationsPath");
         if (!animationPathValue.isnil() && !animationsPathValue.isnil()) {
            throw new LuaError("[MLua] RenderObject properties AnimationPath and AnimationsPath are aliases; provide only one.");
         }
         MLuaBBModelAsset asset = MLuaBBModelLoader.load(path);
         LuaValue animationPath = !animationPathValue.isnil() ? animationPathValue : animationsPathValue;
         if (animationPath.isstring() && !animationPath.tojstring().isBlank()) {
            asset = asset.withAnimations(MLuaBBModelLoader.loadAnimations(animationPath.tojstring()));
         }
         int id = MLuaClientRenderManager.createBBModel(asset, this.service.owner());
         try {
            MLuaClientRenderManager.RenderObject object = this.service.requireObject(id);
            this.service.applyProps(object, properties, "AnimationPath", "AnimationsPath");
            LuaTable handle = this.service.objectTable(id);
            handle.set("__bbmodel", LuaValue.TRUE);
            handle.set("FormatVersion", asset.formatVersion());
            handle.set("ModelFormat", asset.modelFormat());
            LuaTable operations = handle.rawget(MLuaRenderService.OBJECT_OPERATIONS).checktable();
            MLuaRenderAnimationBinding.register(operations, object, asset);
            MLuaRenderObjectClassBinding.validateBlockbenchObjectOperations(operations);
            return handle;
         } catch (RuntimeException failure) {
            this.service.removeObject(id);
            throw failure;
         }
      } catch (LuaError error) {
         throw error;
      } catch (IOException | RuntimeException error) {
         throw new LuaError("[MLua] Failed to load BBModel '" + path + "': " + error.getMessage());
      }
   }

   LuaValue createWorldObject(MLuaClientRenderManager.RenderType type, String resource,
      LuaValue worldPosition, LuaTable properties) {
      this.service.requireClientApi();
      double[] position = MLuaRenderValues.worldVector(worldPosition);
      int id = MLuaClientRenderManager.create(type, resource, this.service.owner());
      return this.service.configureObject(id, properties, object -> {
         object.setWorldSpace(true);
         object.setWorldPosition(position[0], position[1], position[2]);
      });
   }
}
