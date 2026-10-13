package dev.mlua.core.signal;

public final class MLuaConnection {
   private final Runnable disconnect;
   private boolean connected = true;

   MLuaConnection(Runnable disconnect) {
      this.disconnect = disconnect;
   }

   public boolean connected() {
      return this.connected;
   }

   public void disconnect() {
      if (this.connected) {
         this.connected = false;
         this.disconnect.run();
      }

   }
}
