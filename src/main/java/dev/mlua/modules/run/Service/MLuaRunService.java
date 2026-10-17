package dev.mlua.modules.run.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.loaderapi.FrameClock;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.modules.events.Service.MLuaEventsService;
import net.minecraft.SharedConstants;
import org.luaj.vm2.LuaTable;

@MLuaDescription("Provides runtime frame signals and environment information.")
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

   public static void setDeltaTime(Object runtimeSession, Side side, double value) {
      if (!(runtimeSession instanceof MLuaRuntimeSession session)) return;
      double bounded = FrameClock.boundedSeconds(value);
      if (side == Side.BOTH) {
         frameState(session, Side.SERVER).simulationDeltaTime = bounded;
         frameState(session, Side.CLIENT).simulationDeltaTime = bounded;
      } else if (side == Side.SERVER || side == Side.CLIENT) {
         frameState(session, side).simulationDeltaTime = bounded;
      }
   }

   public static void setRenderDeltaTime(Object runtimeSession, double value) {
      if (!(runtimeSession instanceof MLuaRuntimeSession session)) return;
      double bounded = FrameClock.boundedSeconds(value);
      FrameState state = frameState(session, Side.CLIENT);
      state.renderFrame = bounded > 0.0D
         ? new RenderFrameSample(bounded, true)
         : RenderFrameSample.EMPTY;
   }

   @MLuaDescription("Returns the latest simulation frame delta time for this runtime side.")
   public double GetDeltaTime() {
      return frameState().simulationDeltaTime;
   }

   @MLuaDescription("Returns the latest usable client render-frame duration in seconds, capped at one second. Returns zero when the latest sample has no complete interval and on the server.")
   public double GetRenderDeltaTime() {
      return frameState().renderFrame.deltaTime();
   }

   @MLuaDescription("Returns whether the latest client render-frame sample is a complete positive interval for this runtime session.")
   public boolean HasRenderDeltaTime() {
      return frameState().renderFrame.available();
   }

   @MLuaDescription("Returns whether this script is running on the server.")
   public boolean IsServer() { return this.context.isServer(); }

   @MLuaDescription("Returns whether this script is running on a client.")
   public boolean IsClient() { return this.context.isClient(); }

   @MLuaDescription("Returns the runtime side for this script.")
   public String GetSide() { return this.context.side().name(); }

   @MLuaDescription("Returns whether MLua is running on Forge.")
   public boolean IsForge() { return "forge".equalsIgnoreCase(LoaderApi.platform().loaderName()); }

   @MLuaDescription("Returns whether MLua is running on Fabric.")
   public boolean IsFabric() { return "fabric".equalsIgnoreCase(LoaderApi.platform().loaderName()); }

   @MLuaDescription("Returns whether MLua is running on NeoForge.")
   public boolean IsNeoForge() { return "neoforge".equalsIgnoreCase(LoaderApi.platform().loaderName()); }

   @MLuaDescription("Returns the Minecraft version string.")
   public String GetVersion() { return SharedConstants.getCurrentVersion().getName(); }

   @MLuaDescription("Returns the MLua mod version string.")
   public String GetMluaVersion() {
      String version = MLuaRunService.class.getPackage().getImplementationVersion();
      return version == null || version.isBlank() ? "0.0.5" : version;
   }

   @MLuaDescription("Returns the active mod loader name.")
   public String GetLoader() { return LoaderApi.platform().loaderName(); }

   @MLuaDescription("Returns whether the runtime is in development mode.")
   public boolean IsDevelopmentMode() { return !LoaderApi.platform().isProduction(); }

   private FrameState frameState() {
      return this.sharedState("frame", FrameState.class, FrameState::new);
   }

   private static FrameState frameState(MLuaRuntimeSession session, Side side) {
      return session.sharedServiceState(side, "RunService", "frame", FrameState.class, FrameState::new);
   }

   private static final class FrameState {
      private volatile double simulationDeltaTime = 0.05D;
      private volatile RenderFrameSample renderFrame = RenderFrameSample.EMPTY;
   }

   private record RenderFrameSample(double deltaTime, boolean available) {
      private static final RenderFrameSample EMPTY = new RenderFrameSample(0.0D, false);
   }
}
