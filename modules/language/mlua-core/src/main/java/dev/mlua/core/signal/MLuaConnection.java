package dev.mlua.core.signal;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Objects;
import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class MLuaConnection {
   private volatile Runnable disconnect;
   private volatile BooleanSupplier active;
   private final AtomicBoolean connected = new AtomicBoolean(true);

   MLuaConnection(Runnable disconnect) {
      this(disconnect, () -> true);
   }

   private MLuaConnection(Runnable disconnect, BooleanSupplier active) {
      this.disconnect = Objects.requireNonNull(disconnect, "disconnect");
      this.active = Objects.requireNonNull(active, "active");
   }

   /** Creates a connection whose owner can also invalidate it when the resource is replaced or removed. */
   private static MLuaConnection owned(Runnable disconnect, BooleanSupplier active) {
      return new MLuaConnection(disconnect, active);
   }

   /** Creates an owner-bound handle without making the handle retain the callback resource. */
   public static <T> MLuaConnection owned(T resource, Consumer<? super T> disconnect,
                                           Predicate<? super T> active) {
      Objects.requireNonNull(resource, "resource");
      Objects.requireNonNull(disconnect, "disconnect");
      Objects.requireNonNull(active, "active");
      WeakReference<T> reference = new WeakReference<>(resource);
      return owned(() -> {
         T current = reference.get();
         if (current != null) disconnect.accept(current);
      }, () -> {
         T current = reference.get();
         return current != null && active.test(current);
      });
   }

   public boolean connected() {
      BooleanSupplier current = this.active;
      return this.connected.get() && current != null && current.getAsBoolean();
   }

   public void disconnect() {
      if (this.connected.compareAndSet(true, false)) {
         Runnable action = this.disconnect;
         this.clearCallbacks();
         if (action != null) action.run();
      }
   }

   void markDisconnected() {
      this.connected.set(false);
      this.clearCallbacks();
   }

   private void clearCallbacks() {
      this.disconnect = null;
      this.active = null;
   }
}
