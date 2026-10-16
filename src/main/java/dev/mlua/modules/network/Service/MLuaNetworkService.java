package dev.mlua.modules.network.Service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContextView;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.service.MLuaService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.minecraft.network.MLuaNetworkManager;
import dev.mlua.minecraft.network.MLuaRemoteEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerPlayer;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

@MLuaDescription("Provides client-server remote events, HTTP requests, and JSON conversion.")
public final class MLuaNetworkService extends MLuaReflectiveService {
   private static final HttpClient HTTP;
   private final Map<String, LuaTable> remoteTables = new HashMap();

   public MLuaNetworkService(MLuaService.Context context) {
      super(context);
   }

   public String name() {
      return "Network";
   }

   public LuaTable toLua() {
      LuaTable table = super.toLua();
      table.set("__side", "Both");
      return table;
   }

   @MLuaDescription("Gets or creates a named remote event.")
   public LuaTable GetRemote(String name) {
      MLuaNetworkManager.get().validateRemoteName(name);
      return this.remoteTables.computeIfAbsent(name, key -> {
         MLuaRemoteEvent remote = MLuaNetworkManager.get().getRemote(key);
         if (remote == null) throw new LuaError("[MLua] Remote '" + key + "' not found");
         return this.remoteTable(remote, this.context.mlua());
      });
   }

   @MLuaDescription("Returns whether a server is available to this script.")
   public boolean IsServerAvailable() {
      return MLuaNetworkManager.get().hasServerMLua();
   }

   @MLuaDescription("Returns whether a client is available to this script.")
   public boolean IsClientAvailable(LuaValue playerValue) {
      MLuaRemoteEvent.requireSide(this.context.mlua().side(), Side.SERVER, "IsClientAvailable");
      return MLuaNetworkManager.get().hasClientMLua(this.resolvePlayer(this.context.mlua(), playerValue));
   }

   @MLuaDescription("Performs an HTTP GET request and returns its result.")
   public String HttpGet(String url) {
      return this.http("GET", url, null);
   }

   @MLuaDescription("Performs an HTTP POST request and returns its result.")
   public String HttpPost(String url, Optional<String> body) {
      return this.http("POST", url, body.orElse(""));
   }

   @MLuaDescription("Performs an HTTP GET request and invokes a callback with the result.")
   public void HttpGetAsync(String url, LuaValue callback) {
      long expectedEpoch = this.lifecycleEpoch();
      CompletableFuture.runAsync(() -> {
         try {
            String body = this.http("GET", url, null);
            this.dispatchAsync(callback, LuaValue.TRUE, LuaValue.valueOf(body), expectedEpoch);
         } catch (Exception failure) {
            this.dispatchAsync(callback, LuaValue.FALSE, LuaValue.valueOf(failure.getMessage()), expectedEpoch);
         }
      });
   }

   @MLuaDescription("Performs an HTTP POST request and invokes a callback with the result.")
   public void HttpPostAsync(String url, String body, LuaValue callback) {
      long expectedEpoch = this.lifecycleEpoch();
      CompletableFuture.runAsync(() -> {
         try {
            String result = this.http("POST", url, body);
            this.dispatchAsync(callback, LuaValue.TRUE, LuaValue.valueOf(result), expectedEpoch);
         } catch (Exception failure) {
            this.dispatchAsync(callback, LuaValue.FALSE, LuaValue.valueOf(failure.getMessage()), expectedEpoch);
         }
      });
   }

   @MLuaDescription("Encodes a Lua value as JSON.")
   public String JSONEncode(LuaValue value) {
      return new Gson().toJson(MLuaLuaBindings.javaValue(value));
   }

   @MLuaDescription("Decodes a JSON string into Lua values.")
   public LuaValue JSONDecode(String text) {
      try {
         return this.jsonToLua(com.google.gson.JsonParser.parseString(text));
      } catch (JsonParseException | IllegalStateException failure) {
         throw new LuaError("[MLua] Network JSONDecode received invalid JSON: " + failure.getMessage());
      }
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

   private LuaValue jsonToLua(JsonElement value) {
      return jsonToLua(value, 0);
   }

   private LuaValue jsonToLua(JsonElement value, int depth) {
      if (depth > 64) throw new LuaError("[MLua] Network JSONDecode table nesting exceeds 64 levels");
      if (value == null || value.isJsonNull()) return LuaValue.NIL;
      if (value.isJsonPrimitive()) {
         var primitive = value.getAsJsonPrimitive();
         if (primitive.isBoolean()) return LuaValue.valueOf(primitive.getAsBoolean());
         if (primitive.isNumber()) return LuaValue.valueOf(primitive.getAsDouble());
         return LuaValue.valueOf(primitive.getAsString());
      }
      if (value.isJsonArray()) {
         JsonArray array = value.getAsJsonArray();
         LuaTable result = new LuaTable(array.size(), 0);
         for (int index = 0; index < array.size(); index++) {
            result.set(index + 1, jsonToLua(array.get(index), depth + 1));
         }
         return result;
      }
      JsonObject object = value.getAsJsonObject();
      LuaTable result = new LuaTable(0, object.size());
      for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
         result.set(entry.getKey(), jsonToLua(entry.getValue(), depth + 1));
      }
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
