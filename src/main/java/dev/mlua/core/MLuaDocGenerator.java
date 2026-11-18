package dev.mlua.core;

import dev.mlua.module.MLuaModuleLoader;
import dev.mlua.core.engine.MLuaLuaApiDocSnapshot;
import dev.mlua.loaderapi.ClientBridge;
import dev.mlua.loaderapi.ConfigBridge;
import dev.mlua.loaderapi.EventBridge;
import dev.mlua.loaderapi.LifecycleBridge;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.loaderapi.NetworkBridge;
import dev.mlua.loaderapi.PlatformBridge;
import dev.mlua.loaderapi.RegistryBridge;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class MLuaDocGenerator {
   private MLuaDocGenerator() {
   }

   public static void main(String[] args) {
      installDocumentationLoader();
      MLuaModuleLoader.get().loadForDocumentation();
      Path gameDir = Paths.get("run");
      MLuaDocumentation.generate(gameDir);
      MLuaLuaApiDocSnapshot.DocumentationSnapshot snapshot = MLuaLuaApiDocSnapshot.generate();
      MLuaDocumentation.writeGeneratedLuaApi(gameDir, snapshot);
   }

   private static void installDocumentationLoader() {
      Path gameDir = Paths.get(".").toAbsolutePath().normalize();
      LoaderApi.install(new LoaderApi.LoaderProvider() {
         private IllegalStateException unavailable(String bridge) {
            return new IllegalStateException(bridge + " is unavailable during offline documentation generation");
         }

         @Override public PlatformBridge platform() {
            return new PlatformBridge() {
               @Override public Path gameDir() { return gameDir; }
               @Override public Path configDir() { return gameDir.resolve("config"); }
               @Override public boolean hasMod(String modId) { return false; }
               @Override public boolean isClient() { return true; }
               @Override public boolean isProduction() { return false; }
               @Override public String loaderName() {
                  return System.getProperty("mlua.documentation.loader", "unknown");
               }
               @Override public java.util.Collection<dev.mlua.loaderapi.ScriptRootRef> bundledScriptRoots() {
                  return List.of();
               }
            };
         }

         @Override public EventBridge events() { throw unavailable("loader events"); }
         @Override public RegistryBridge registry() { throw unavailable("loader registries"); }
         @Override public NetworkBridge network() { throw unavailable("loader networking"); }
         @Override public ConfigBridge config() { throw unavailable("loader configuration"); }
         @Override public LifecycleBridge lifecycle() { throw unavailable("loader lifecycle"); }
         @Override public ClientBridge client() { return ClientBridge.unavailable(); }
      });
   }
}
