package dev.mlua.modules.javabridge.service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;

/** Tracks Lua-backed Java interface proxies by runtime and addon owner. */
final class MLuaJavaProxyRegistry {
   static final int MAX_TOTAL = 4096;
   static final int MAX_PER_OWNER = 512;

   interface Handle {
      void close();
   }

   private final ReferenceQueue<Object> collectedSessions = new ReferenceQueue<>();
   private final Map<SessionKey, Map<Owner, List<WeakReference<Handle>>>> sessions = new LinkedHashMap<>();

   void register(MLuaContext context, Handle handle) {
      Object session = runtimeToken(context);
      if (session == null) return;
      Owner owner = new Owner(context.ownerModId(), context.side());
      synchronized (this.sessions) {
         int total = this.pruneAndCount();
         if (total >= MAX_TOTAL) {
            throw new LuaError("[MLua] Java-backed proxy limit reached (" + MAX_TOTAL + ")");
         }
         Map<Owner, List<WeakReference<Handle>>> byOwner = this.session(session, true);
         if (countOwned(byOwner, owner.ownerModId()) >= MAX_PER_OWNER) {
            throw new LuaError("[MLua] Java-backed proxy limit reached for addon '" + owner.ownerModId()
               + "' (" + MAX_PER_OWNER + ")");
         }
         byOwner.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(new WeakReference<>(handle));
      }
   }

   LuaTable limits(MLuaContext context) {
      int total;
      int owned;
      synchronized (this.sessions) {
         total = this.pruneAndCount();
         Map<Owner, List<WeakReference<Handle>>> byOwner = this.session(runtimeToken(context), false);
         owned = byOwner == null ? 0 : countOwned(byOwner, context.ownerModId());
      }
      LuaTable result = new LuaTable();
      result.set("Active", total);
      result.set("ActiveOwned", owned);
      result.set("MaxTotal", MAX_TOTAL);
      result.set("MaxPerOwner", MAX_PER_OWNER);
      return result;
   }

   void clearRuntime(Object runtimeSession, Side side) {
      this.clear(runtimeSession, side, null);
   }

   void clearOwner(Object runtimeSession, Side side, String ownerModId) {
      if (ownerModId == null || ownerModId.isBlank()) return;
      this.clear(runtimeSession, side, ownerModId);
   }

   private void clear(Object runtimeSession, Side side, String ownerModId) {
      if (runtimeSession == null) return;
      List<Handle> handles = new ArrayList<>();
      synchronized (this.sessions) {
         Map<Owner, List<WeakReference<Handle>>> byOwner = this.session(runtimeSession, false);
         if (byOwner == null) return;
         byOwner.entrySet().removeIf(entry -> {
            Owner owner = entry.getKey();
            boolean ownerMatches = ownerModId == null || owner.ownerModId().equals(ownerModId);
            boolean sideMatches = side == Side.BOTH || owner.side() == Side.BOTH || owner.side() == side;
            if (!ownerMatches || !sideMatches) return false;
            for (WeakReference<Handle> reference : entry.getValue()) {
               Handle handle = reference.get();
               if (handle != null) handles.add(handle);
            }
            return true;
         });
         if (byOwner.isEmpty()) this.sessions.remove(new SessionKey(runtimeSession));
      }
      handles.forEach(Handle::close);
   }

   /** Must be called while holding sessions. */
   private int pruneAndCount() {
      this.expungeCollectedSessions();
      int total = 0;
      for (Map<Owner, List<WeakReference<Handle>>> byOwner : this.sessions.values()) {
         byOwner.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(reference -> reference.get() == null);
            return entry.getValue().isEmpty();
         });
         for (List<WeakReference<Handle>> handles : byOwner.values()) total += handles.size();
      }
      this.sessions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
      return total;
   }

   /** Must be called while holding sessions. */
   private Map<Owner, List<WeakReference<Handle>>> session(Object token, boolean create) {
      if (token == null) return null;
      this.expungeCollectedSessions();
      SessionKey lookup = new SessionKey(token);
      Map<Owner, List<WeakReference<Handle>>> byOwner = this.sessions.get(lookup);
      if (byOwner == null && create) {
         byOwner = new LinkedHashMap<>();
         this.sessions.put(new SessionKey(token, this.collectedSessions), byOwner);
      }
      return byOwner;
   }

   private void expungeCollectedSessions() {
      SessionKey key;
      while ((key = (SessionKey)this.collectedSessions.poll()) != null) this.sessions.remove(key);
   }

   private static int countOwned(Map<Owner, List<WeakReference<Handle>>> byOwner, String ownerModId) {
      int count = 0;
      for (Map.Entry<Owner, List<WeakReference<Handle>>> entry : byOwner.entrySet()) {
         if (entry.getKey().ownerModId().equals(ownerModId)) count += entry.getValue().size();
      }
      return count;
   }

   private static Object runtimeToken(MLuaContext context) {
      return context.session() == null ? context.registration() : context.session();
   }

   private record Owner(String ownerModId, Side side) { }

   /** Runtime tokens use identity, never user-defined equals/hashCode. */
   private static final class SessionKey extends WeakReference<Object> {
      private final int hash;

      private SessionKey(Object token) {
         super(token);
         this.hash = System.identityHashCode(token);
      }

      private SessionKey(Object token, ReferenceQueue<Object> queue) {
         super(token, queue);
         this.hash = System.identityHashCode(token);
      }

      @Override public int hashCode() { return this.hash; }

      @Override public boolean equals(Object other) {
         if (this == other) return true;
         if (!(other instanceof SessionKey key)) return false;
         Object token = this.get();
         return token != null && token == key.get();
      }
   }
}
