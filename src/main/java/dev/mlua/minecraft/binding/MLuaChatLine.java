package dev.mlua.minecraft.binding;

import dev.mlua.module.MLuaRegistration;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

/** Short-lived Lua instance representing a chat component before Minecraft stores it. */
public final class MLuaChatLine {
    private static final String BACKING = "__mlua_chat_line";

    private MLuaChatLine() { }

    public static void registerClass(MLuaRegistration registration) {
        registration.registerClass("ChatLine", registration.requireInstanceBaseClassName(),
            "A live chat message while Minecraft is adding it to the chat HUD.");
        registration.registerClassProperty("ChatLine", "Text", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return LuaValue.valueOf(backing(self.checktable()).text()); }
        }, new TwoArgFunction() {
            @Override public LuaValue call(LuaValue self, LuaValue value) {
                Component component;
                if (value.isstring()) {
                    component = Component.literal(value.checkjstring());
                } else {
                    try {
                        component = (Component) dev.mlua.api.MLuaLuaCoercions.javaValue(value, Component.class);
                    } catch (RuntimeException failure) {
                        throw new LuaError("[MLua] ChatLine.Text expects a string or a Java Component value");
                    }
                }
                if (component == null) throw new LuaError("[MLua] ChatLine.Text cannot be nil");
                backing(self.checktable()).setText(component);
                return LuaValue.NIL;
            }
        }, "string | Java Component", "client:Client", "Gets the rendered text; assigning a string replaces it with literal text, or assign a Java Component to preserve formatting before it is added to the chat HUD.");
        registration.registerClassProperty("ChatLine", "OriginalText", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return LuaValue.valueOf(backing(self.checktable()).originalText()); }
        }, null, "string", "client:Client", "Returns the original message text before any addon changes.");
        registration.registerClassProperty("ChatLine", "Modified", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return LuaValue.valueOf(backing(self.checktable()).isModified()); }
        }, null, "boolean", "client:Client", "Returns whether an addon changed the text, prefix, or suffix.");
        registration.registerClassProperty("ChatLine", "Prefix", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return LuaValue.valueOf(backing(self.checktable()).prefix()); }
        }, new TwoArgFunction() {
            @Override public LuaValue call(LuaValue self, LuaValue value) {
                if (!value.isstring()) throw new LuaError("[MLua] ChatLine.Prefix expects a string");
                backing(self.checktable()).setPrefix(value.checkjstring());
                return LuaValue.NIL;
            }
        }, "string", "client:Client", "Gets or sets literal text inserted before the original message while preserving its formatting.");
        registration.registerClassProperty("ChatLine", "Suffix", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return LuaValue.valueOf(backing(self.checktable()).suffix()); }
        }, new TwoArgFunction() {
            @Override public LuaValue call(LuaValue self, LuaValue value) {
                if (!value.isstring()) throw new LuaError("[MLua] ChatLine.Suffix expects a string");
                backing(self.checktable()).setSuffix(value.checkjstring());
                return LuaValue.NIL;
            }
        }, "string", "client:Client", "Gets or sets literal text appended after the original message while preserving its formatting.");
    }

    public static LuaTable instance(MLuaRegistration registration, Backing backing) {
        LuaTable instance = registration.createClassInstance("ChatLine");
        instance.rawset(BACKING, LuaValue.userdataOf(backing));
        return instance;
    }

    public static Backing backing(LuaTable instance) {
        LuaValue value = instance.rawget(BACKING);
        Object backing = value.isuserdata() ? value.touserdata() : null;
        if (!(backing instanceof Backing chatLine)) throw new LuaError("[MLua] ChatLine is no longer attached to a message");
        return chatLine;
    }

    public static final class Backing {
        private final Component original;
        private Component replacement;
        private String prefix = "";
        private String suffix = "";
        private boolean active = true;

        public Backing(Component original) { this.original = original; }

        public String text() { return this.replacement == null ? this.original.getString() : this.replacement.getString(); }
        public String originalText() { return this.original.getString(); }
        public String prefix() { return this.prefix; }
        public String suffix() { return this.suffix; }
        public boolean isModified() {
            return (this.replacement != null && this.replacement != this.original)
                || !this.prefix.isEmpty() || !this.suffix.isEmpty();
        }

        public void setText(Component text) {
            if (!this.active) throw new LuaError("[MLua] ChatLine.Text can only be changed during Gui.ChatLineAdding");
            this.replacement = text;
        }

        public void setPrefix(String prefix) {
            if (!this.active) throw new LuaError("[MLua] ChatLine.Prefix can only be changed during Gui.ChatLineAdding");
            this.prefix = prefix;
        }

        public void setSuffix(String suffix) {
            if (!this.active) throw new LuaError("[MLua] ChatLine.Suffix can only be changed during Gui.ChatLineAdding");
            this.suffix = suffix;
        }

        public Component result() {
            Component content = this.replacement == null ? this.original : this.replacement;
            if (this.prefix.isEmpty() && this.suffix.isEmpty()) return content;
            return Component.empty().append(Component.literal(this.prefix)).append(content).append(Component.literal(this.suffix));
        }

        public void expire() { this.active = false; }
    }
}
