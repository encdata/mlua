package dev.mlua.modules.coregui.service;

import dev.mlua.minecraft.binding.MLuaNativeControl;
import dev.mlua.minecraft.ui.element.GuiObject;
import dev.mlua.minecraft.ui.element.MLuaTableRegistry;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns native widget layout property validation and transactional updates. */
final class MLuaGuiNativeControlProperties {
    private MLuaGuiNativeControlProperties() {}

    static void setProperty(MLuaNativeControl.Backing backing, String name, LuaValue value) {
        if (value == null) throw new LuaError("[MLua] NativeControl property values cannot be Java null; use Lua nil where supported");
        if ("Visible".equals(name) && !value.isboolean()) {
            throw new LuaError("[MLua] NativeControl.Visible expects a boolean");
        }
        if ("Text".equals(name) && value.type() != LuaValue.TSTRING) {
            throw new LuaError("[MLua] NativeControl.Text expects a Lua string");
        }
        if (value.isnil() && !isObjectProperty(name)) {
            throw new LuaError("[MLua] NativeControl property '" + name + "' does not accept nil");
        }
        if (value.isnil() && isObjectProperty(name)) {
            LuaTable update = new LuaTable();
            update.set("__Clear" + name, LuaValue.TRUE);
            setProperties(backing, update);
            updateObjectReference(backing, name, value);
            return;
        }
        LuaTable properties = new LuaTable();
        properties.set(name, value);
        setProperties(backing, properties);
        updateObjectReference(backing, name, value);
    }

    static void setProperties(MLuaNativeControl.Backing backing, LuaTable updates) {
        backing.requireCurrent();
        MLuaGuiService service = MLuaGuiService.getInstance();
        if (service == null) throw new LuaError("[MLua] Gui service is not active on the client");
        synchronized (service.state) {
            MLuaNativeLayout layout = service.findNativeLayout(backing.screenName, backing.index, backing.ownerModId());
            boolean createdLayout = false;
            LuaTable previousProperties = null;
            boolean clearsObjects = updates.rawget("__ClearReplacement").toboolean()
                || updates.rawget("__ClearBefore").toboolean() || updates.rawget("__ClearAfter").toboolean();
            if (layout == null && clearsObjects) return;
            if (layout == null) {
                LuaTable selector = new LuaTable();
                selector.set("Index", backing.index);
                layout = service.addNativeLayout(backing.screenName, selector, copyProperties(updates));
                createdLayout = true;
            } else {
                previousProperties = copyProperties(layout.properties());
                boolean clearReplacement = updates.rawget("__ClearReplacement").toboolean();
                boolean clearBefore = updates.rawget("__ClearBefore").toboolean();
                boolean clearAfter = updates.rawget("__ClearAfter").toboolean();
                LuaTable nextProperties = copyProperties(layout.properties());
                validateObjectUpdate(updates, "Replacement");
                validateObjectUpdate(updates, "Before");
                validateObjectUpdate(updates, "After");
                for (LuaValue key : updates.keys()) {
                    if (key.type() == LuaValue.TSTRING && !key.tojstring().startsWith("__Clear")) {
                        nextProperties.set(key, updates.get(key));
                    }
                }
                if (clearReplacement) nextProperties.set("Replacement", LuaValue.NIL);
                if (clearBefore) nextProperties.set("Before", LuaValue.NIL);
                if (clearAfter) nextProperties.set("After", LuaValue.NIL);
                validateLayoutObjects(nextProperties);
                replaceProperties(layout.properties(), nextProperties);
                if (clearReplacement || clearBefore || clearAfter || !updates.get("Replacement").isnil()
                    || !updates.get("Before").isnil() || !updates.get("After").isnil()) {
                    service.syncLayoutObjects(layout);
                }
            }
            try {
                service.recomposeLayouts(backing.screen(), layout);
            } catch (RuntimeException | Error failure) {
                if (createdLayout) service.removeNativeLayout(layout.id());
                else if (previousProperties != null) {
                    try {
                        replaceProperties(layout.properties(), previousProperties);
                        service.syncLayoutObjects(layout);
                        service.recomposeLayouts(backing.screen(), layout);
                    } catch (RuntimeException | Error rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                throw failure;
            }
        }
    }

    static GuiObject guiObjectProperty(LuaTable properties, String name) {
        LuaValue value = properties.get(name);
        return value.isnil() ? null : requireGuiObject(value, name);
    }

    static void validateLayoutObjects(LuaTable properties) {
        GuiObject replacement = guiObjectProperty(properties, "Replacement");
        GuiObject before = guiObjectProperty(properties, "Before");
        GuiObject after = guiObjectProperty(properties, "After");
        if ((replacement != null && (replacement == before || replacement == after)) || (before != null && before == after)) {
            throw new LuaError("[MLua] NativeControl Replacement, Before, and After must use separate GuiObject instances");
        }
    }

    private static void updateObjectReference(MLuaNativeControl.Backing backing, String name, LuaValue value) {
        switch (name) {
            case "Replacement" -> backing.setReplacement(value);
            case "Before" -> backing.setBefore(value);
            case "After" -> backing.setAfter(value);
            default -> { }
        }
    }

    private static boolean isObjectProperty(String name) {
        return "Replacement".equals(name) || "Before".equals(name) || "After".equals(name);
    }

    private static void validateObjectUpdate(LuaTable updates, String property) {
        LuaValue value = updates.get(property);
        if (!value.isnil()) requireGuiObject(value, property);
    }

    private static GuiObject requireGuiObject(LuaValue value, String name) {
        if (!value.istable() || !(MLuaTableRegistry.getElement(value.checktable()) instanceof GuiObject object)) {
            throw new LuaError("[MLua] NativeControl." + name + " expects a live GuiObject instance or nil");
        }
        return object;
    }

    private static LuaTable copyProperties(LuaTable source) {
        LuaTable copy = new LuaTable();
        for (LuaValue key : source.keys()) copy.set(key, source.get(key));
        return copy;
    }

    private static void replaceProperties(LuaTable target, LuaTable source) {
        for (LuaValue key : target.keys()) target.set(key, LuaValue.NIL);
        for (LuaValue key : source.keys()) target.set(key, source.get(key));
    }
}
