package dev.mlua.core.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Builds HTML references from raw LuaJ tables without invoking Lua code. */
public final class MLuaLuaApiCrawler {
    private static final int MAX_DEPTH = 12;
    private static final int MAX_ENTRIES = 20_000;
    private static final LuaValue TYPE = LuaValue.valueOf("__type");
    private static final LuaValue DOC_TYPE = LuaValue.valueOf("__mlua_doc_type");
    private static final LuaValue DOC_PROPERTIES = LuaValue.valueOf("__mlua_doc_properties");
    private static final LuaValue PROPERTIES = LuaValue.valueOf("__mlua_properties");

    private MLuaLuaApiCrawler() { }

    public static String crawlHtml(LuaValue globals, Map<String, LuaTable> services) {
        CrawlData data = collect(globals, services);
        return renderInventory(data.entries(), data.categories());
    }

    public static CrawlResult crawl(LuaValue globals, Map<String, LuaTable> services) {
        CrawlData data = collect(globals, services);
        return new CrawlResult(renderInventory(data.entries(), data.categories()));
    }

    private static CrawlData collect(LuaValue globals, Map<String, LuaTable> services) {
        List<Entry> entries = new ArrayList<>();
        Map<String, String> categories = new LinkedHashMap<>();
        if (globals.istable()) {
            IdentitySet seen = new IdentitySet();
            seen.add(globals);
            IdentitySet categoryTables = new IdentitySet();
            int[] remainingCategories = new int[]{MAX_ENTRIES};
            collectCategories(globals, "", categories, categoryTables, remainingCategories, 0);
            LuaTable serviceRoot = new LuaTable();
            services.forEach((name, service) -> serviceRoot.set(name, service));
            if (!services.isEmpty()) {
                categories.put("Services", "Services");
                collectCategories(serviceRoot, "Services", categories, new IdentitySet(), new int[]{MAX_ENTRIES}, 0);
            }
            addChildren((LuaTable) globals, "", 0, entries, seen);
            if (!services.isEmpty()) visit(serviceRoot, "Services", 0, entries, seen);
        }
        entries.sort(Comparator.comparing(Entry::path).thenComparing(Entry::kind));
        return new CrawlData(entries, categories);
    }

    private static void collectCategories(LuaValue value, String path, Map<String, String> categories,
                                          IdentitySet seen, int[] remaining, int depth) {
        if (!value.istable() || depth >= MAX_DEPTH || remaining[0] <= 0 || !seen.add(value)) return;
        remaining[0]--;
        LuaTable table = value.checktable();
        LuaValue category = table.rawget("__mlua_doc_category");
        if (category.isstring() && !category.tojstring().isBlank()) categories.put(path, category.tojstring());
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next;
            try { next = table.next(key); } catch (RuntimeException ignored) { return; }
            key = next.arg1();
            if (key.isnil() || remaining[0] <= 0) return;
            if (key.isstring() && key.tojstring().startsWith("__")) continue;
            String child = childPath(path, key);
            if (child != null) collectCategories(next.arg(2), child, categories, seen, remaining, depth + 1);
        }
    }

    private static String renderInventory(List<Entry> entries, Map<String, String> categories) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"lua-api-inventory\"><p>This reference follows the Lua tables registered for this runtime. Class, service, and property descriptions come from their registrations. The scan reads tables and metadata without calling API functions or property accessors.</p>")
            .append("<p>Showing ").append(entries.size()).append(" entries. Search by API path, kind, or value.</p>")
            .append("<label>Search API <input class=\"lua-api-filter\" type=\"search\" placeholder=\"Search API paths, kinds, and values\"></label><script>(function(){var script=document.currentScript,root=script.parentNode,input=root.querySelector('.lua-api-filter');if(!input)return;input.addEventListener('input',function(){var q=input.value.toLowerCase();root.querySelectorAll('.lua-api-row').forEach(function(row){row.hidden=row.textContent.toLowerCase().indexOf(q)===-1;});});})();</script>");
        Map<String, List<Entry>> groups = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Entry entry : entries) groups.computeIfAbsent(categoryForPath(entry.path, categories), ignored -> new ArrayList<>()).add(entry);
        groups.forEach((category, members) -> html.append("<section class=\"lua-api-category\"><h2>")
            .append(escape(category)).append("</h2>").append(memberRows(members)).append("</section>"));
        return html.append("</div>").toString();
    }

    private static String memberRows(List<Entry> entries) {
        Map<String, String> descriptions = new java.util.HashMap<>();
        for (Entry entry : entries) {
            if (entry.value != null && (entry.kind.equals("description")
                || entry.kind.equals("function description")
                || entry.kind.equals("inherited function description"))) {
                descriptions.putIfAbsent(entry.path, entry.value);
            }
        }
        StringBuilder html = new StringBuilder();
        for (Entry entry : entries) {
            if (entry.kind.equals("description") || entry.kind.equals("function description")
                || entry.kind.equals("inherited function description")) continue;
            String description = descriptions.get(entry.path);
            if (description == null && entry.kind.startsWith("property ")) description = entry.value;
            html.append("<article class=\"member lua-api-row\"><div class=\"member-header\"><div class=\"member-name\"><code>")
                .append(escape(entry.path)).append("</code></div><div class=\"member-flags\"><span class=\"badge shared\">")
                .append(escape(entry.kind)).append("</span></div></div>");
            if (description != null && !description.isBlank()) {
                html.append("<p class=\"member-desc\">").append(escape(description)).append("</p>");
            }
            if (entry.value != null && !entry.kind.startsWith("property ")) {
                html.append("<p class=\"member-desc\">Value: <code>").append(escape(entry.value)).append("</code></p>");
            }
            html.append("</article>");
        }
        return html.toString();
    }

    private static String categoryForPath(String path, Map<String, String> categories) {
        String current = path;
        while (true) {
            String category = categories.get(current);
            if (category != null) return category;
            int separator = Math.max(current.lastIndexOf('.'), current.lastIndexOf('['));
            if (separator < 0) break;
            current = current.substring(0, separator);
        }
        return "Globals";
    }

    private static void visit(LuaValue value, String path, int depth, List<Entry> entries, IdentitySet seen) {
        if (entries.size() >= MAX_ENTRIES || value == null) return;
        String kind = kind(value);
        if (value.istable()) {
            LuaValue type = value.rawget(TYPE);
            if (!type.isstring()) type = value.rawget(DOC_TYPE);
            if (type.isstring()) kind = "table: " + type.tojstring();
            if (hasRaw(value, "Connect") && hasRaw(value, "Fire")) kind = "signal";
            else if (hasRaw(value, "Disconnect")) kind = "connection";
        }
        entries.add(new Entry(path, kind, scalar(value)));
        if (!value.istable() || depth >= MAX_DEPTH || !seen.add(value)) return;
        LuaTable table = (LuaTable) value;
        addDocumentation(table, path, entries);
        addChildren(table, path, depth + 1, entries, seen);
        LuaValue properties = table.rawget(PROPERTIES);
        LuaValue propertyDocs = table.rawget(DOC_PROPERTIES);
        addProperties(properties.istable() ? properties : propertyDocs, propertyDocs, path, entries);
        LuaValue metatable = table.getmetatable();
        if (metatable != null && metatable.istable()) {
            LuaTable meta = (LuaTable) metatable;
            LuaValue metaProperties = meta.rawget(PROPERTIES);
            LuaValue metaPropertyDocs = meta.rawget(DOC_PROPERTIES);
            addProperties(metaProperties.istable() ? metaProperties : metaPropertyDocs,
                metaPropertyDocs, path, entries);
            LuaValue index = meta.rawget(LuaValue.valueOf("__index"));
            if (index.istable()) addIndexedMembers((LuaTable) index, path, depth + 1, entries, seen);
        }
    }

    private static void addDocumentation(LuaTable table, String path, List<Entry> entries) {
        LuaValue description = table.rawget("__mlua_doc_description");
        if (description.isstring() && !description.tojstring().isBlank()) {
            entries.add(new Entry(path, "description", description.tojstring()));
        }
        LuaValue parents = table.rawget("__mlua_doc_parents");
        if (parents.istable()) {
            List<String> names = new ArrayList<>();
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs next = parents.checktable().next(key);
                key = next.arg1();
                if (key.isnil()) break;
                if (next.arg(2).isstring()) names.add(next.arg(2).tojstring());
            }
            if (!names.isEmpty()) entries.add(new Entry(path, "inherits", String.join(", ", names)));
        }
        addMethodDescriptions(table.rawget("__mlua_doc_methods"), path, "function description", entries);
        if (table.rawget("__class").toboolean()) {
            addMethodDescriptions(table.rawget("__mlua_doc_inherited_methods"), path,
                "inherited function description", entries);
        }
    }

    private static void addMethodDescriptions(LuaValue methodsValue, String path,
                                              String kind, List<Entry> entries) {
        if (!methodsValue.istable()) return;
        LuaValue key = LuaValue.NIL;
        while (entries.size() < MAX_ENTRIES) {
            Varargs next = methodsValue.checktable().next(key);
            key = next.arg1();
            if (key.isnil()) return;
            if (!key.isstring() || !next.arg(2).isstring() || next.arg(2).tojstring().isBlank()) continue;
            String methodPath = childPath(path, key);
            if (methodPath != null) entries.add(new Entry(methodPath, kind, next.arg(2).tojstring()));
        }
    }

    private static void addIndexedMembers(LuaTable table, String path, int depth, List<Entry> entries, IdentitySet seen) {
        if (depth >= MAX_DEPTH || !seen.add(table)) return;
        addChildren(table, path, depth, entries, seen);
        addProperties(table.rawget(PROPERTIES), table.rawget(DOC_PROPERTIES), path, entries);
        LuaValue metatable = table.getmetatable();
        if (metatable != null && metatable.istable()) {
            LuaTable meta = (LuaTable) metatable;
            addProperties(meta.rawget(PROPERTIES), meta.rawget(DOC_PROPERTIES), path, entries);
            LuaValue parent = meta.rawget(LuaValue.valueOf("__index"));
            if (parent.istable()) addIndexedMembers((LuaTable) parent, path, depth + 1, entries, seen);
        }
    }

    private static void addChildren(LuaTable table, String path, int depth, List<Entry> entries, IdentitySet seen) {
        LuaValue key = LuaValue.NIL;
        while (entries.size() < MAX_ENTRIES) {
            Varargs next;
            try { next = table.next(key); } catch (RuntimeException ignored) { return; }
            key = next.arg1();
            if (key.isnil()) return;
            if (key.isstring() && key.tojstring().startsWith("__")) continue;
            String child = childPath(path, key);
            if (child != null) visit(next.arg(2), child, depth, entries, seen);
        }
    }

    private static void addProperties(LuaValue propertyMetadata, LuaValue descriptions,
                                      String path, List<Entry> entries) {
        if (!propertyMetadata.istable()) return;
        LuaValue key = LuaValue.NIL;
        while (entries.size() < MAX_ENTRIES) {
            Varargs next = propertyMetadata.checktable().next(key);
            key = next.arg1();
            if (key.isnil()) return;
            if (!key.isstring() || !next.arg(2).istable()) continue;
            LuaTable descriptor = (LuaTable) next.arg(2);
            boolean readable = descriptor.rawget(LuaValue.valueOf("get")).isfunction()
                || descriptor.rawget(LuaValue.valueOf("readable")).toboolean();
            boolean writable = descriptor.rawget(LuaValue.valueOf("set")).isfunction()
                || descriptor.rawget(LuaValue.valueOf("writable")).toboolean();
            boolean signal = descriptor.rawget(LuaValue.valueOf("signal")).toboolean();
            if (!readable && !writable && !signal) continue;
            String access = readable && writable ? "read/write" : readable ? "read" : "write";
            LuaValue type = descriptor.rawget(LuaValue.valueOf("type"));
            LuaValue side = descriptor.rawget(LuaValue.valueOf("side"));
            String metadata = type.isstring() ? " · " + type.tojstring() : "";
            if (side.isstring()) metadata += " · " + side.tojstring();
            String propertyPath = childPath(path, key);
            if (propertyPath != null) {
                LuaValue description = descriptions.istable() ? descriptions.checktable().rawget(key) : LuaValue.NIL;
                if (!description.isstring()) description = descriptor.rawget(LuaValue.valueOf("description"));
                entries.add(new Entry(propertyPath, signal ? "signal" : "property " + access + metadata,
                    description.isstring() ? description.tojstring() : null));
            }
        }
    }

    private static boolean hasRaw(LuaValue table, String key) {
        return !table.rawget(LuaValue.valueOf(key)).isnil();
    }

    private static String childPath(String parent, LuaValue key) {
        String name = key.isstring() || key.isnumber() ? key.tojstring() : null;
        if (name == null || name.isBlank()) return null;
        boolean identifier = name.matches("[A-Za-z_][A-Za-z0-9_]*");
        if (parent.isEmpty()) return identifier ? name : "[" + name + "]";
        return identifier ? parent + "." + name : parent + "[" + name + "]";
    }

    private static String kind(LuaValue value) {
        if (value.istable()) return "table";
        if (value.isfunction()) return "function";
        if (value.isnil()) return "nil";
        if (value.isboolean()) return "boolean";
        if (value.isnumber()) return "number";
        if (value.isstring()) return "string";
        if (value.isuserdata()) return "userdata";
        return value.typename();
    }

    private static String scalar(LuaValue value) {
        return value.isnil() || value.isboolean() || value.isnumber() || value.isstring() ? value.tojstring() : null;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public record CrawlResult(String inventoryHtml) { }

    private record CrawlData(List<Entry> entries, Map<String, String> categories) { }
    private record Entry(String path, String kind, String value) { }

    /** Identity-based set without importing a general-purpose mutable collection type into the crawler. */
    private static final class IdentitySet {
        private final java.util.IdentityHashMap<LuaValue, Boolean> values = new java.util.IdentityHashMap<>();
        boolean add(LuaValue value) { return this.values.put(value, Boolean.TRUE) == null; }
    }
}
