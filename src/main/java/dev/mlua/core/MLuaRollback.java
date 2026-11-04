package dev.mlua.core;

/** Runs every cleanup action while preserving failures for the caller. */
final class MLuaRollback {
   private MLuaRollback() { }

   static Throwable run(Throwable failure, Runnable action) {
      try {
         action.run();
      } catch (RuntimeException | Error cleanupFailure) {
         if (failure == null) return cleanupFailure;
         if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
      }
      return failure;
   }

   static void rethrow(Throwable failure) {
      if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
      if (failure instanceof Error error) throw error;
   }
}
