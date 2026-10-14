package dev.mlua.modules.region;

import dev.mlua.loaderapi.EventBridge;
import dev.mlua.modules.region.Service.MLuaRegionService;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.common.enums.Side;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import java.util.List;

public class RegionModule implements MLuaModule {
   @Override
   public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
      if (!(runtimeSession instanceof MLuaRuntimeSession session)) return;
      if (side == Side.SERVER || side == Side.BOTH) {
         MLuaRegionService.clearForScheduler(session.serverScheduler(), side);
      }
      if (side == Side.CLIENT || side == Side.BOTH) {
         MLuaRegionService.clearForScheduler(session.clientScheduler(), side);
      }
   }

   @Override
   public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
      if (!(runtimeSession instanceof MLuaRuntimeSession session)) return;
      if (side == Side.SERVER || side == Side.BOTH) {
         MLuaRegionService.destroyOwned(session.serverScheduler(), side, ownerModId);
      }
      if (side == Side.CLIENT || side == Side.BOTH) {
         MLuaRegionService.destroyOwned(session.clientScheduler(), side, ownerModId);
      }
   }

   @Override
   public String id() { return "region"; }

   @Override
   public int priority() { return 10; }

   @Override public List<String> dependencies() { return List.of("core-classes"); }

   @Override public void registerClasses(dev.mlua.module.MLuaRegistration registration) {
      MLuaRegionService.registerRegionClass(registration);
   }

   @Override
   public List<ServiceEntry> services() {
      return List.of(ServiceEntry.builder("Region", MLuaRegionService::new)
         .reflective(MLuaRegionService.class)
         .build());
   }

   @Override
   public void registerEvents(EventBridge events) {
      events.onServerTickEnd(server -> MLuaRegionService.tick((net.minecraft.server.MinecraftServer) server));
      if (LoaderApi.platform().isClient()) {
         events.onClientTickEnd(() -> MLuaRegionService.tickClient(net.minecraft.client.Minecraft.getInstance()));
      }
   }

   @Override
   public void contributeDocs(MLuaDocBuilder docs) {
      docs.service("Region", "MLuaRegionService",
              "Spatial trigger system for defining regions and detecting entity enter/exit/within events.",
              "services/region.html");
      docs.clazz("Region", "MLuaRegionService",
         "A live spatial region that tracks matching entities and exposes enter, exit, and within signals.",
         "classes/region.html", "Instance");
      docs.signature("Region", "Region:GetRegion(name: string)", "Region?");
      docs.signature("Region", "Region:CreateRegion(name: string, x: number, y: number, z: number, radius: number)", "Region");
      docs.signature("Region", "Region:CreateBoxRegion(name: string, minX: number, minY: number, minZ: number, maxX: number, maxY: number, maxZ: number)", "Region");
      docs.signature("Region", "Region:CreateSphere(name: string, center: Vector3|CFrame, radius: number)", "Region");
      docs.signature("Region", "Region:CreateBox(name: string, minimum: Vector3|CFrame, maximum: Vector3|CFrame)", "Region");
      docs.method("Region", "Region:Remove()", "Removes this region and clears its tracked entities and signals.", null, "void", "shared:Shared", null);
      docs.method("Region", "Region:GetEntities()", "Returns the live entity instances currently inside this region.", null, "Entity[]", "shared:Shared", null);
      docs.method("Region", "Region:OnEnter()", "Returns the signal fired for entities entering a region.", null, "Signal", "shared:Shared", null);
      docs.method("Region", "Region:OnExit()", "Returns the signal fired for entities leaving a region.", null, "Signal", "shared:Shared", null);
      docs.method("Region", "Region:OnWithin()", "Returns the signal fired for entities inside a region.", null, "Signal", "shared:Shared", null);
      docs.guideStep("Region", "Create and listen to a live region",
         "Region creation returns a Region instance. Use its inherited Instance methods, live properties, and signals to manage the same registered region.",
         "local RegionService = Minecraft:GetService(\"Region\")\nlocal region = RegionService:CreateSphere(\"safe_room\", Vector3.new(0, 64, 0), 8)\n\nregion:OnEnter():Connect(function(event)\n    print(event.Entity.Name .. \" entered \" .. region.Name)\nend)\n\nprint(region:IsA(\"Region\"), region.EntityCount)\nregion:Remove()");
   }
}
