package dev.mlua.core.require;

import dev.mlua.core.engine.MLuaValue;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class MLuaModuleCache {
   private final Map<String, MLuaValue> values = new HashMap();
   private final Deque<String> loading = new ArrayDeque();

   public boolean contains(String id) {
      return this.values.containsKey(id);
   }

   public MLuaValue get(String id) {
      return (MLuaValue)this.values.get(id);
   }

   public void put(String id, MLuaValue value) {
      this.values.put(id, value);
   }

   public void enter(String id) {
      if (!this.loading.contains(id)) {
         this.loading.addLast(id);
      } else {
         StringBuilder chain = new StringBuilder("[MLua] Circular Require detected:");
         Iterator var3 = this.loading.iterator();

         while(var3.hasNext()) {
            String item = (String)var3.next();
            chain.append("\n  ").append(item);
         }

         chain.append("\n  ").append(id);
         throw new IllegalStateException(chain.toString());
      }
   }

    public void Destroy() {
       this.values.clear();
       this.loading.clear();
    }

    public void exit(String id) {
      if (!this.loading.isEmpty() && ((String)this.loading.peekLast()).equals(id)) {
         this.loading.removeLast();
      }

   }

   public List<String> chain() {
      return List.copyOf(this.loading);
   }
}
