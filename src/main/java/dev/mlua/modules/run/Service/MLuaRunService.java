package dev.mlua.modules.run.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.modules.events.Service.MLuaEventsService;
import net.minecraft.SharedConstants;
import org.luaj.vm2.LuaTable;

/** Runtime signals and environment information exposed through the shared Java service base. */
public final class MLuaRunService extends MLuaReflectiveService {
   public MLuaRunService(Context context) {
      super(context);
   }

   @Override
   public String name() {
      return "RunService";
   }

   @Override
   public LuaTable toLua() {
      LuaTable table = super.toLua();
      table.set("__side", "Both");
      table.set("Heartbeat", MLuaLuaBindings.signalInstance(MLuaEventsService.SERVER_TICK,
         this.context.mlua().globals(), this.context.scheduler(), this.context.ownerModId(), this.context.side()));
      table.set("Stepped", MLuaLuaBindings.signalInstance(MLuaEventsService.SERVER_TICK,
         this.context.mlua().globals(), this.context.scheduler(), this.context.ownerModId(), this.context.side()));
      table.set("RenderStepped", MLuaLuaBindings.signalInstance(MLuaEventsService.RENDER_STEPPED,
         this.context.mlua().globals(), this.context.scheduler(), this.context.ownerModId(), this.context.side()));
      table.set("PreRender", MLuaLuaBindings.signalInstance(MLuaEventsService.PRE_RENDER,
         this.context.mlua().globals(), this.context.scheduler(), this.context.ownerModId(), this.context.side()));
      table.set("PostRender", MLuaLuaBindings.signalInstance(MLuaEventsService.POST_RENDER,
         this.context.mlua().globals(), this.context.scheduler(), this.context.ownerModId(), this.context.side()));
      return table;
   }

   /** Updates the live per-session frame state used by GetDeltaTime. */
   public static void setDeltaTime(Object runtimeSession, Side side, double value) {
      if (!(runtimeSession instanceof MLuaRuntimeSession session)) return;
      double bounded = Math.max(0.0D, Math.min(1.0D, value));
      if (side == Side.BOTH) {
         frameState(session, Side.SERVER).deltaTime = bounded;
         frameState(session, Side.CLIENT).deltaTime = bounded;
      } else if (side == Side.SERVER || side == Side.CLIENT) {
         frameState(session, side).deltaTime = bounded;
      }
   }

   public double GetDeltaTime() {
      return frameState().deltaTime;
   }

   public double GetRenderDeltaTime() {
      return frameState().deltaTime;
   }

   public boolean IsServer() { return this.context.isServer(); }

   public boolean IsClient() { return this.context.isClient(); }

   public String GetSide() { return this.context.side().name(); }

   public boolean IsForge() { return "forge".equalsIgnoreCase(LoaderApi.platform().loaderName()); }

   public boolean IsFabric() { return "fabric".equalsIgnoreCase(LoaderApi.platform().loaderName()); }

   public boolean IsNeoForge() { return "neoforge".equalsIgnoreCase(LoaderApi.platform().loaderName()); }

   public String GetVersion() { return SharedConstants.getCurrentVersion().getName(); }

   public String GetMluaVersion() {
      String version = MLuaRunService.class.getPackage().getImplementationVersion();
      return version == null || version.isBlank() ? "0.0.5" : version;
   }

   public String GetLoader() { return LoaderApi.platform().loaderName(); }

   public boolean IsDevelopmentMode() { return !LoaderApi.platform().isProduction(); }

   private FrameState frameState() {
      return this.sharedState("frame", FrameState.class, FrameState::new);
   }

   private static FrameState frameState(MLuaRuntimeSession session, Side side) {
      return session.sharedServiceState(side, "RunService", "frame", FrameState.class, FrameState::new);
   }

   private static final class FrameState {
      private volatile double deltaTime = 0.05D;
   }
}
