package dev.mlua.core.engine;

import dev.mlua.api.MLuaApiPolicy;
import dev.mlua.api.MLuaLuaCoercions;
import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.core.signal.MLuaConnection;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.module.MLuaRegistration;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

public final class MLuaLuaBindings {
   private static final String API_VERSION = MLuaApiPolicy.VERSION;
   private static final List<String> CAPABILITIES = MLuaApiPolicy.capabilities().stream().sorted().toList();

   private MLuaLuaBindings() {
   }

    public static LuaTable minecraftTable(final MLuaContext context) {
        LuaTable table = new LuaTable();
        table.set("GetService", new TwoArgFunction() {
            public LuaValue call(LuaValue self, LuaValue name) {
                return MLuaServiceBindings.liveServiceProxy(context, name.checkjstring());
            }
        });
        table.set("GetServiceNames", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return MLuaServiceBindings.serviceNames(context);
            }
        });
        MLuaClassBindings.bindInto(table, context);
        MLuaServiceRegistrationBindings.bindInto(table, context);
        table.set("IsServer", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(context.side() == Side.SERVER);
            }
        });
        table.set("IsClient", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(context.side() == Side.CLIENT);
            }
        });
        table.set("GetSide", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(context.side().name());
            }
        });
        table.set("GetApiVersion", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(API_VERSION);
            }
        });
        table.set("HasCapability", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue name) {
                return LuaValue.valueOf(supportsCapability(name.checkjstring()));
            }
        });
        table.set("ApiVersion", API_VERSION);
        table.set("Capabilities", capabilitiesTable());
        MLuaServiceBindings.addDynamicLookup(table, context);

        return table;
    }

   static MLuaRegistration requireMutableRegistration(MLuaContext context) {
      if (context.phase() != RegistrationPhase.REGISTRY) {
         throw new LuaError("[MLua] Class, mixin, and service registrations must run in a registry script.");
      }
      MLuaRegistration registration = context.registration();
      if (registration == null) {
         throw new LuaError("[MLua] Class registration is unavailable in this script context.");
      }
      return registration;
   }

    private static LuaTable capabilitiesTable() {
        LuaTable table = new LuaTable();
        int i = 1;
        for (String capability : CAPABILITIES) {
            if (!supportsCapability(capability)) continue;
            table.set(i++, capability);
            table.set(capability, LuaValue.TRUE);
        }
        return table;
    }

    private static boolean supportsCapability(String capability) {
        try {
            return MLuaApiPolicy.supports(capability, LoaderApi.platform().loaderName());
        } catch (IllegalStateException unavailable) {
            return false;
        }
    }

    public static LuaTable scriptContextTable(final MLuaContext context) {
        LuaTable table = minecraftTable(context);
        table.set("Minecraft", table);
        table.set("ModId", context.ownerModId());
        table.set("ScriptName", context.script().displayName());
        table.set("Phase", LuaValue.valueOf(context.phase().name()));
        table.set("IsRuntime", LuaValue.valueOf(context.phase() == RegistrationPhase.RUNTIME));

        return table;
    }

   public static LuaTable materializeServiceTable(MLuaContext context, String serviceName) {
      return MLuaServiceBindings.materializeServiceTable(context, serviceName);
   }

   public static void bindContext(Globals globals, MLuaContext context) {
      MLuaContextBindings.bindContext(globals, context);
   }

   public static void configureContextGlobals(Globals globals, MLuaContext context) {
      MLuaContextBindings.configureContextGlobals(globals, context);
   }

   public static <T> T withContext(MLuaContext context, java.util.function.Supplier<T> callback) {
      return MLuaContextBindings.withContext(context, callback);
   }

   public static <T> T withExecutionLock(MLuaContext context, java.util.function.Supplier<T> callback) {
      return MLuaContextBindings.withExecutionLock(context, callback);
   }

   public static MLuaContext activeContext(MLuaRegistration registration) {
      return MLuaContextBindings.activeContext(registration);
   }

   public static MLuaContext currentContext() {
      return MLuaContextBindings.currentContext();
   }

   public static MLuaContext boundContext(Globals globals) {
      return MLuaContextBindings.boundContext(globals);
   }

   public static LuaTable taskTable(final MLuaTaskSchedulerView scheduler, final Globals globals) {
      return MLuaSignalBindings.taskTable(scheduler, globals);
   }

   public static LuaTable signalTable(final Globals globals, final MLuaTaskSchedulerView scheduler) {
      return MLuaSignalBindings.signalTable(globals, scheduler);
   }

   public static LuaTable signalInstance(final MLuaSignal signal, final Globals globals) {
      return MLuaSignalBindings.signalInstance(signal, globals);
   }

   public static LuaTable signalInstance(final MLuaSignal signal, final Globals globals, final MLuaTaskSchedulerView scheduler) {
      return MLuaSignalBindings.signalInstance(signal, globals, scheduler);
   }

   public static LuaTable signalInstance(final MLuaSignal signal, final Globals globals, final MLuaTaskSchedulerView scheduler, final String ownerKey) {
      return MLuaSignalBindings.signalInstance(signal, globals, scheduler, ownerKey);
   }

   public static LuaTable signalInstance(final MLuaSignal signal, final Globals globals, final MLuaTaskSchedulerView scheduler, final String ownerKey, final Side side) {
      return MLuaSignalBindings.signalInstance(signal, globals, scheduler, ownerKey, side);
   }

   public static LuaTable connection(final MLuaConnection connection, MLuaRegistration registration) {
      return MLuaSignalBindings.connection(connection, registration);
   }

   public static Map<String, Object> mapFromLua(LuaTable table) {
      try {
         return MLuaLuaCoercions.mapFromLua(table);
      } catch (IllegalArgumentException failure) {
         throw new LuaError("[MLua] " + failure.getMessage());
      }
   }

   public static Object javaValue(LuaValue value) {
      return MLuaLuaCoercions.javaValue(value);
   }

   public static LuaValue toLuaValue(Object value) {
      return MLuaLuaCoercions.toLuaValue(value);
   }
}
