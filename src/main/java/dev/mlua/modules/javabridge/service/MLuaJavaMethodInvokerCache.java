package dev.mlua.modules.javabridge.service;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Supplier;
import org.luaj.vm2.LuaValue;

/** Caches per-proxy Java method wrappers and missing lookups with a small access-ordered bound. */
final class MLuaJavaMethodInvokerCache {
    static final int MAX_ENTRIES = 256;
    private final LinkedHashMap<String, LuaValue> values = new LinkedHashMap<>(16, 0.75f, true);

    synchronized LuaValue get(String name, Supplier<LuaValue> factory) {
        LuaValue cached = this.values.get(name);
        if (cached != null) return cached;

        LuaValue created = Objects.requireNonNull(factory.get(), "method invoker factory returned null");
        if (this.values.size() >= MAX_ENTRIES) {
            var oldest = this.values.keySet().iterator();
            if (oldest.hasNext()) this.values.remove(oldest.next());
        }
        this.values.put(name, created);
        return created;
    }
}
