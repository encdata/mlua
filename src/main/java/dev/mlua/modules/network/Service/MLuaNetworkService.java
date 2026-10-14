package dev.mlua.modules.network.Service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContextView;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.minecraft.network.MLuaNetworkManager;
import dev.mlua.minecraft.network.MLuaRemoteEvent;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerPlayer;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaNetworkService extends MLuaServiceBase {
   private static final HttpClient HTTP;
   private final Map<String, LuaTable> remoteTables = new HashMap();

   public MLuaNetworkService(MLuaService.Context context) {
      super(context);
   }

   public String name() {
      return "Network";
   }

   public LuaTable toLua() {
      LuaTable table = new LuaTable();
      table.set("__side", "Both");
      table.set("GetRemote", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            String name = args.checkjstring(2);
             MLuaNetworkManager.get().validateRemoteName(name);
             return (Varargs)MLuaNetworkService.this.remoteTables.computeIfAbsent(name, (key) -> {
                MLuaRemoteEvent remote = MLuaNetworkManager.get().getRemote(key);
                if (remote == null) {
                   throw new LuaError("[MLua] Remote '" + key + "' not found");
                }
                return MLuaNetworkService.this.remoteTable(remote, MLuaNetworkService.this.context.mlua());
             });
         }
      });
      table.set("IsServerAvailable", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(MLuaNetworkManager.get().hasServerMLua());
         }
      });
      table.set("IsClientAvailable", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            MLuaRemoteEvent.requireSide(MLuaNetworkService.this.context.mlua().side(), Side.SERVER, "IsClientAvailable");
            return LuaValue.valueOf(MLuaNetworkManager.get().hasClientMLua(MLuaNetworkService.this.resolvePlayer(MLuaNetworkService.this.context.mlua(), args.arg(2))));
         }
      });
      table.set("HttpGet", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(MLuaNetworkService.this.http("GET", args.checkjstring(2), (String)null));
         }
      });
      table.set("HttpPost", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(MLuaNetworkService.this.http("POST", args.checkjstring(2), args.optjstring(3, "")));
         }
      });
      table.set("HttpGetAsync", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            String url = args.checkjstring(2);
            LuaValue callback = args.arg(3);
            long lifecycleEpoch = MLuaNetworkService.this.lifecycleEpoch();
            CompletableFuture.runAsync(() -> {
               try {
                  String body = MLuaNetworkService.this.http("GET", url, (String)null);
                  MLuaNetworkService.this.dispatchAsync(callback, LuaValue.TRUE, LuaValue.valueOf(body), lifecycleEpoch);
               } catch (Exception var4) {
                  MLuaNetworkService.this.dispatchAsync(callback, LuaValue.FALSE, LuaValue.valueOf(var4.getMessage()), lifecycleEpoch);
               }

            });
            return LuaValue.NONE;
         }
      });
      table.set("HttpPostAsync", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            String url = args.checkjstring(2);
            String body = args.optjstring(3, "");
            LuaValue callback = args.arg(4);
            long lifecycleEpoch = MLuaNetworkService.this.lifecycleEpoch();
            CompletableFuture.runAsync(() -> {
               try {
                  String result = MLuaNetworkService.this.http("POST", url, body);
                  MLuaNetworkService.this.dispatchAsync(callback, LuaValue.TRUE, LuaValue.valueOf(result), lifecycleEpoch);
               } catch (Exception var5) {
                  MLuaNetworkService.this.dispatchAsync(callback, LuaValue.FALSE, LuaValue.valueOf(var5.getMessage()), lifecycleEpoch);
               }

            });
            return LuaValue.NONE;
         }
      });
      table.set("JSONEncode", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaTable table = args.arg(2).checktable();
            Gson gson = new Gson();
            Map<String, Object> map = MLuaLuaBindings.mapFromLua(table);
            return LuaValue.valueOf(gson.toJson(map));
         }
      });
      table.set("JSONDecode", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            String text = args.checkjstring(2);
            Gson gson = new Gson();
            Type type = (new TypeToken<Map<String, Object>>() {
            }).getType();
            Map<String, Object> map = (Map)gson.fromJson(text, type);
            if (map == null) {
               return LuaValue.NIL;
            } else {
               return MLuaNetworkService.this.jsonToLua(map);
            }
         }
      });
      return table;
   }

   private long lifecycleEpoch() {
      return this.context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.lifecycleEpoch() : -1L;
   }

   private void dispatchAsync(LuaValue callback, LuaValue success, LuaValue value, long expectedEpoch) {
      try {
         MLuaContextView mlua = this.context.mlua();
         var globals = mlua.globals();
         var scheduler = mlua.scheduler();
         if (globals == null || scheduler == null || !callback.isfunction()) {
            return;
         }
         MLuaRegistration registration = mlua.registration();
         MLuaRuntime runtime = registration instanceof MLuaRuntime owner ? owner : null;
         if (runtime != null && runtime.lifecycleEpoch() != expectedEpoch) return;
         LuaValue guardedCallback = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               return runtime == null || runtime.lifecycleEpoch() == expectedEpoch
                  ? callback.invoke(args)
                  : LuaValue.NONE;
            }
         };
         scheduler.defer(globals, guardedCallback, LuaValue.varargsOf(new LuaValue[]{success, value}));
      } catch (RuntimeException exception) {
         dev.mlua.core.MLuaLogger.LOGGER.error("Could not schedule asynchronous HTTP callback", exception);
      }
   }

   private String http(String method, String url, String body) {
      try {
         URI uri = URI.create(url);
         if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new LuaError("[MLua] Network HTTP URL must use http or https");
         } else {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).header("User-Agent", "MLua/0.1");
            HttpRequest request = "POST".equals(method) ? builder.POST(BodyPublishers.ofString(body == null ? "" : body)).build() : builder.GET().build();
            return (String)HTTP.send(request, BodyHandlers.ofString()).body();
         }
      } catch (LuaError var7) {
         throw var7;
      } catch (Exception var8) {
         throw new LuaError("[MLua] Network HTTP request failed: " + var8.getMessage());
      }
   }

   private LuaTable jsonToLua(Map<String, Object> map) {
      LuaTable result = new LuaTable();
      map.forEach((key, value) -> {
         if (value instanceof Map<?, ?> nested) {
            result.set(key, this.jsonToLua((Map<String, Object>) nested));
         } else if (value instanceof Number n) {
            result.set(key, n.doubleValue());
         } else if (value instanceof Boolean b) {
            result.set(key, b ? LuaValue.TRUE : LuaValue.FALSE);
         } else {
            result.set(key, String.valueOf(value));
         }
      });
      return result;
   }

   private LuaTable remoteTable(final MLuaRemoteEvent remote, final MLuaContextView mlua) {
      LuaTable table = new LuaTable();
      LuaTable serverSignal = MLuaLuaBindings.signalInstance(remote.serverEvent(), mlua.globals(), mlua.scheduler(), mlua.ownerModId(), mlua.side());
      LuaTable clientSignal = MLuaLuaBindings.signalInstance(remote.clientEvent(), mlua.globals(), mlua.scheduler(), mlua.ownerModId(), mlua.side());
      LuaTable instance = mlua.registration().attachClassInstance(table, "RemoteEvent", null);
      MLuaClassMethods.setInstanceMethod(instance, "GetName", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(remote.name());
         }
      });
      MLuaClassMethods.setInstanceMethod(instance, "GetOnServerEvent", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            return serverSignal;
         }
      });
      MLuaClassMethods.setInstanceMethod(instance, "GetOnClientEvent", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            return clientSignal;
         }
      });
      MLuaClassMethods.setInstanceMethod(instance, "FireServer", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            MLuaRemoteEvent.requireSide(mlua.side(), Side.CLIENT, "FireServer");
            return LuaValue.valueOf(remote.fireServer(args));
         }
      });
      MLuaClassMethods.setInstanceMethod(instance, "FireServerOptional", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            MLuaRemoteEvent.requireSide(mlua.side(), Side.CLIENT, "FireServerOptional");
            return LuaValue.valueOf(remote.fireServer(args, true));
         }
      });
      MLuaClassMethods.setInstanceMethod(instance, "FireClient", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            MLuaRemoteEvent.requireSide(mlua.side(), Side.SERVER, "FireClient");
            return LuaValue.valueOf(remote.fireClient(MLuaNetworkService.this.resolvePlayer(mlua, args.arg(2)), args));
         }
      });
      MLuaClassMethods.setInstanceMethod(instance, "FireClientOptional", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            MLuaRemoteEvent.requireSide(mlua.side(), Side.SERVER, "FireClientOptional");
            return LuaValue.valueOf(remote.fireClient(MLuaNetworkService.this.resolvePlayer(mlua, args.arg(2)), args, true));
         }
      });
      MLuaClassMethods.setInstanceMethod(instance, "FireAllClients", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            MLuaRemoteEvent.requireSide(mlua.side(), Side.SERVER, "FireAllClients");
            return LuaValue.valueOf(remote.fireAllClients(args, false, mlua.server()));
         }
      });
      MLuaClassMethods.setInstanceMethod(instance, "FireAllClientsOptional", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            MLuaRemoteEvent.requireSide(mlua.side(), Side.SERVER, "FireAllClientsOptional");
            return LuaValue.valueOf(remote.fireAllClients(args, true, mlua.server()));
         }
      });
      return instance;
   }

   private ServerPlayer resolvePlayer(MLuaContextView mlua, LuaValue value) {
      if (!value.istable()) {
         throw new LuaError("[MLua] Network:FireClient expected an MLua player wrapper");
      } else if (mlua.server() == null) {
         throw new LuaError("[MLua] Network:FireClient cannot resolve players before a server is available");
      } else {
         String uuid = value.checktable().get("UUID").checkjstring();
         ServerPlayer player = mlua.server().getPlayerList().getPlayer(UUID.fromString(uuid));
         if (player == null) {
            throw new LuaError("[MLua] Network:FireClient target player is not online: " + uuid);
         } else {
            return player;
         }
      }
   }

   static {
      HTTP = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).build();
   }
}
