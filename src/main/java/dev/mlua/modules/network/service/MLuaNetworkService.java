package dev.mlua.modules.network.service;

import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.api.MLuaDescription;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaContextView;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.service.MLuaService;
import dev.mlua.minecraft.network.MLuaNetworkManager;
import dev.mlua.minecraft.network.MLuaRemoteEvent;
import dev.mlua.module.MLuaRegistration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpClient.Redirect;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import net.minecraft.server.level.ServerPlayer;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

@MLuaDescription("Provides client-server remote events, HTTP requests, and JSON conversion.")
public final class MLuaNetworkService extends MLuaReflectiveService {
   private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(10);
   private static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(30);
   private static final Semaphore HTTP_ASYNC_REQUESTS = new Semaphore(64);
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
         return MLuaRemoteEventLuaBinding.create(remote, this.context.mlua(),
            value -> this.resolvePlayer(this.context.mlua(), value));
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
      this.httpAsync("GET", url, null, callback);
   }

   @MLuaDescription("Performs an HTTP POST request and invokes a callback with the result.")
   public void HttpPostAsync(String url, String body, LuaValue callback) {
      this.httpAsync("POST", url, body, callback);
   }

   @MLuaDescription("Encodes nil, JSONNull, booleans, finite numbers, strings, dense arrays, or string-keyed tables as JSON. Empty tables encode as arrays; cycles, sparse arrays, and other Lua or Java values are rejected.")
   public String JSONEncode(LuaValue value) {
      return MLuaNetworkJsonCodec.encode(value);
   }

   @MLuaDescription("Decodes JSON into Lua values. JSON null is returned as the JSONNull singleton so array slots and object fields are preserved.")
   public LuaValue JSONDecode(String text) {
      return MLuaNetworkJsonCodec.decode(text);
   }

   private void httpAsync(String method, String url, String body, LuaValue callback) {
      if (callback == null || !callback.isfunction()) {
         throw new LuaError("[MLua] Network async HTTP requests expect a callback function");
      }
      long expectedEpoch = this.lifecycleEpoch();
      MLuaContext callbackContext = MLuaLuaBindings.currentContext();
      if (!HTTP_ASYNC_REQUESTS.tryAcquire()) {
         this.dispatchAsync(callback, LuaValue.FALSE, LuaValue.valueOf("too many asynchronous HTTP requests are in flight"),
            expectedEpoch, callbackContext);
         return;
      }
      try {
         HTTP.sendAsync(this.httpRequest(method, url, body), BodyHandlers.ofString())
            .whenComplete((response, failure) -> {
               try {
                  if (failure == null) {
                     this.dispatchAsync(callback, LuaValue.TRUE, LuaValue.valueOf(response.body()), expectedEpoch,
                        callbackContext);
                  } else {
                     this.dispatchAsync(callback, LuaValue.FALSE, LuaValue.valueOf(httpFailureMessage(failure)),
                        expectedEpoch, callbackContext);
                  }
               } finally {
                  HTTP_ASYNC_REQUESTS.release();
               }
            });
      } catch (RuntimeException failure) {
         HTTP_ASYNC_REQUESTS.release();
         this.dispatchAsync(callback, LuaValue.FALSE, LuaValue.valueOf(httpFailureMessage(failure)),
            expectedEpoch, callbackContext);
      }
   }

   private static String httpFailureMessage(Throwable failure) {
      if (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
      String message = failure.getMessage();
      return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
   }

   private long lifecycleEpoch() {
      return this.context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.lifecycleEpoch() : -1L;
   }

   private void dispatchAsync(LuaValue callback, LuaValue success, LuaValue value, long expectedEpoch,
                              MLuaContext callbackContext) {
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
                  ? MLuaLuaBindings.withContext(callbackContext, () -> callback.invoke(args))
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
         return HTTP.send(this.httpRequest(method, url, body), BodyHandlers.ofString()).body();
      } catch (LuaError var7) {
         throw var7;
      } catch (Exception var8) {
         throw MLuaLuaErrors.javaFailure("Network HTTP request", var8);
      }
   }

   private HttpRequest httpRequest(String method, String url, String body) {
      URI uri = URI.create(url);
      if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
         throw new LuaError("[MLua] Network HTTP URL must use http or https");
      }
      HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
         .timeout(HTTP_REQUEST_TIMEOUT)
         .header("User-Agent", "MLua/0.1");
      return "POST".equals(method)
         ? builder.POST(BodyPublishers.ofString(body == null ? "" : body)).build()
         : builder.GET().build();
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
      HTTP = HttpClient.newBuilder()
         .connectTimeout(HTTP_CONNECT_TIMEOUT)
         .followRedirects(Redirect.NORMAL)
         .build();
   }
}
