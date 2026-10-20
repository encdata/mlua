package dev.mlua.core.engine;

import dev.mlua.core.MLuaContext;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaRegistration;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.TwoArgFunction;

/** Owns the Lua Docs registry table and validates addon documentation contributions. */
final class MLuaDocumentationBindings {
    private MLuaDocumentationBindings() { }

    static LuaTable table(MLuaContext context) {
        LuaTable docs = new LuaTable();
        docs.rawset("__type", LuaValue.valueOf("DocumentationRegistry"));
        docs.rawset("__mlua_doc_description", LuaValue.valueOf(
            "Register addon guides, tutorial steps, examples, and reusable script templates."));
        LuaTable methodDocs = new LuaTable();
        methodDocs.rawset("Register", LuaValue.valueOf(
            "Adds an addon authored guide, Getting Started steps, examples, or reusable script templates during registry startup."));
        methodDocs.rawset("Unregister", LuaValue.valueOf(
            "Removes a guide registered by this addon. Accepts the addon's short id or its owner-qualified id."));
        docs.rawset("__mlua_doc_methods", methodDocs);
        docs.set("Register", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue value) {
                MLuaRegistration registration = MLuaLuaBindings.requireMutableRegistration(context);
                if (!value.istable()) throw new LuaError("[MLua] Docs:Register expects a table.");
                LuaTable input = value.checktable();
                String owner = context.ownerModId();
                String id = requiredString(input, "id");
                if (!id.contains(":")) id = owner + ":" + id;
                else if (!id.startsWith(owner + ":")) {
                    throw new LuaError("[MLua] Documentation id must use this addon's namespace ('" + owner + "').");
                }
                String section = requiredString(input, "section");
                String title = requiredString(input, "title");
                String description = requiredString(input, "description");
                int order = input.rawget("order").isnumber() ? input.rawget("order").toint() : 0;
                List<MLuaDocBuilder.GuideStep> steps = new ArrayList<>();
                readArray(input.rawget("steps"), step -> steps.add(new MLuaDocBuilder.GuideStep(
                    "Docs", requiredString(step, "title"), requiredString(step, "description"),
                    requiredString(step, "example"))));
                List<MLuaDocBuilder.Example> examples = new ArrayList<>();
                readArray(input.rawget("examples"), example -> examples.add(new MLuaDocBuilder.Example(
                    requiredString(example, "title"), requiredString(example, "description"),
                    requiredString(example, "code"))));
                List<MLuaDocBuilder.Template> templates = new ArrayList<>();
                readArray(input.rawget("templates"), template -> templates.add(new MLuaDocBuilder.Template(
                    requiredString(template, "title"), requiredString(template, "description"),
                    requiredString(template, "filename"), requiredString(template, "code"))));
                try {
                    registration.registerDocumentation(new MLuaDocBuilder.Contribution(
                        id, section, title, description, owner, order, steps, examples, templates));
                } catch (RuntimeException failure) {
                    throw new LuaError("[MLua] Could not register documentation: " + failure.getMessage());
                }
                return LuaValue.valueOf(id);
            }
        });
        docs.set("Unregister", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue value) {
                MLuaRegistration registration = MLuaLuaBindings.requireMutableRegistration(context);
                if (!value.isstring() || value.tojstring().isBlank()) {
                    throw new LuaError("[MLua] Docs:Unregister expects a non-empty documentation id.");
                }
                String owner = context.ownerModId();
                String id = value.tojstring();
                if (!id.contains(":")) id = owner + ":" + id;
                else if (!id.startsWith(owner + ":")) {
                    throw new LuaError("[MLua] Documentation id must use this addon's namespace ('" + owner + "').");
                }
                return LuaValue.valueOf(registration.unregisterDocumentation(id, owner));
            }
        });
        return docs;
    }

    private static String requiredString(LuaTable table, String key) {
        LuaValue value = table.rawget(key);
        if (!value.isstring() || value.tojstring().isBlank()) {
            throw new LuaError("[MLua] Documentation field '" + key + "' must be a non-empty string.");
        }
        return value.tojstring();
    }

    private static void readArray(LuaValue value, java.util.function.Consumer<LuaTable> consumer) {
        if (value.isnil()) return;
        if (!value.istable()) throw new LuaError("[MLua] Documentation steps, examples, and templates must be arrays.");
        LuaTable array = value.checktable();
        int entryCount = 0;
        int maximumIndex = 0;
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = array.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (!key.isnumber()) {
                throw new LuaError("[MLua] Documentation steps, examples, and templates must use dense array tables.");
            }
            double numericIndex = key.todouble();
            int index = key.toint();
            if (index < 1 || numericIndex != index) {
                throw new LuaError("[MLua] Documentation steps, examples, and templates must use dense array tables.");
            }
            entryCount++;
            maximumIndex = Math.max(maximumIndex, index);
        }
        if (maximumIndex != entryCount) {
            throw new LuaError("[MLua] Documentation steps, examples, and templates must use dense array tables.");
        }
        for (int i = 1; i <= entryCount; i++) {
            LuaValue item = array.rawget(i);
            if (!item.istable()) {
                throw new LuaError("[MLua] Each documentation step, example, or template must be a table.");
            }
            consumer.accept(item.checktable());
        }
    }
}
