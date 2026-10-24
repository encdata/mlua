package dev.mlua.core;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/** Owns world-entrypoint dispatch state and deferred client lifecycle transitions. */
final class MLuaRuntimeWorldLifecycle {
   private final Set<Level> dispatchedWorlds = Collections.newSetFromMap(new IdentityHashMap<>());
   private final ConcurrentLinkedQueue<Transition> pendingClientTransitions = new ConcurrentLinkedQueue<>();

   synchronized boolean markDispatched(Level level, boolean loaded) {
      return loaded ? this.dispatchedWorlds.add(level) : this.dispatchedWorlds.remove(level);
   }

   synchronized void removeServerWorlds(MinecraftServer server) {
      this.dispatchedWorlds.removeIf(level -> !level.isClientSide() && level.getServer() == server);
   }

   synchronized void forget(Level level) {
      this.dispatchedWorlds.remove(level);
   }

   void queueClientTransition(Level level, boolean loaded) {
      this.pendingClientTransitions.add(new Transition(level, loaded));
   }

   void clearPendingClientTransitions() {
      this.pendingClientTransitions.clear();
   }

   void drainClientTransitions(Consumer<Transition> dispatcher) {
      Transition transition;
      while ((transition = this.pendingClientTransitions.poll()) != null) dispatcher.accept(transition);
   }

   record Transition(Level level, boolean loaded) { }
}
