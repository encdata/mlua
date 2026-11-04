package dev.mlua.minecraft.binding;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.ui.element.GuiBase;
import dev.mlua.minecraft.ui.element.MLuaTableRegistry;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

/** Owns the Lua-facing lifecycle and hierarchy methods shared by every GuiBase instance. */
final class MLuaUIInstanceBindings {
    private MLuaUIInstanceBindings() { }

    static void register(MLuaRegistration registration) {
        registration.registerClassFunction("GuiBase", "GetIsDestroyed", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                return LuaValue.valueOf(isDestroyedGui(self));
            }
        }, "Returns whether this UI instance has been destroyed.");
        dev.mlua.core.classes.MLuaClassMethods.registerInstanceProperty(registration, "GuiBase", "IsDestroyed",
            "GetIsDestroyed", null, "boolean", "client:Client",
            "Whether this UI instance has been destroyed.");
        registerGuiBaseMethod(registration, "AddChild", "Parents a live UI instance to this instance and returns the child.",
            new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    GuiBase parent = requireGuiElement(args.arg1(), "AddChild");
                    LuaValue childValue = args.arg(2);
                    GuiBase child = childValue.istable()
                        ? MLuaTableRegistry.getElement(childValue.checktable()) : null;
                    if (child == null) throw new LuaError("[MLua] AddChild expects a live UI instance.");
                    try {
                        parent.addChild(child);
                    } catch (IllegalArgumentException failure) {
                        throw new LuaError("[MLua] " + failure.getMessage());
                    }
                    return child.getSelfTable();
                }
            });
        registerGuiBaseMethod(registration, "RemoveChild", "Detaches a direct child from this instance without destroying it.",
            new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    GuiBase parent = requireGuiElement(args.arg1(), "RemoveChild");
                    LuaValue childValue = args.arg(2);
                    GuiBase child = childValue.istable()
                        ? MLuaTableRegistry.getElement(childValue.checktable()) : null;
                    if (child == null || child.getParent() != parent) return LuaValue.FALSE;
                    parent.removeChild(child);
                    return LuaValue.TRUE;
                }
            });
        registerGuiBaseMethod(registration, "Destroy", "Removes this GUI instance and its descendants from their parent.",
            new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    LuaValue self = args.arg1();
                    if (self.istable() && self.checktable().rawget("__mlua_destroyed").toboolean()) {
                        return LuaValue.NONE;
                    }
                    requireGuiElement(self, "Destroy").destroy();
                    return LuaValue.NONE;
                }
            });
        registerGuiBaseMethod(registration, "GetChildren", "Returns this GUI instance's direct children.",
            new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    if (isDestroyedGui(args.arg1())) return new LuaTable();
                    LuaTable children = new LuaTable();
                    int index = 1;
                    for (GuiBase child : requireGuiElement(args.arg1(), "GetChildren").getChildren()) {
                        children.set(index++, child.getSelfTable());
                    }
                    return children;
                }
            });
        registerGuiBaseMethod(registration, "FindFirstChild",
            "Returns a child with the given name, or nil. Pass true for the recursive argument to search descendants.",
            new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    if (isDestroyedGui(args.arg1())) return LuaValue.NIL;
                    return findFirstChild(requireGuiElement(args.arg1(), "FindFirstChild"),
                        args.arg(2).checkjstring(), args.arg(3).optboolean(false));
                }
            });
        registerGuiBaseMethod(registration, "GetDescendants", "Returns every descendant UI instance in parent-first order.",
            new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    if (isDestroyedGui(args.arg1())) return new LuaTable();
                    LuaTable descendants = new LuaTable();
                    appendDescendants(requireGuiElement(args.arg1(), "GetDescendants"), descendants, 1);
                    return descendants;
                }
            });
        registerGuiBaseMethod(registration, "GetFullName", "Returns this instance's dot-separated path from its root UI instance.",
            new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    if (isDestroyedGui(args.arg1())) {
                        LuaValue self = args.arg1();
                        return LuaValue.valueOf(self.istable() ? self.checktable().get("Name").optjstring("") : "");
                    }
                    GuiBase element = requireGuiElement(args.arg1(), "GetFullName");
                    java.util.List<String> names = new java.util.ArrayList<>();
                    for (GuiBase current = element; current != null; current = current.getParent()) {
                        names.add(current.getName());
                    }
                    java.util.Collections.reverse(names);
                    return LuaValue.valueOf(String.join(".", names));
                }
            });
    }

    private static void registerGuiBaseMethod(MLuaRegistration registration, String name,
                                              String description, LuaValue implementation) {
        registration.registerClassFunction("GuiBase", name, implementation, description);
    }

    static GuiBase requireGuiElement(LuaValue instance, String method) {
        GuiBase element = instance.istable() ? MLuaTableRegistry.getElement(instance.checktable()) : null;
        if (element == null) throw new LuaError("[MLua] " + method + " requires a live UI instance.");
        return element;
    }


    private static LuaValue findFirstChild(GuiBase parent, String name, boolean recursive) {
        for (GuiBase child : parent.getChildren()) {
            if (name.equals(child.getName())) return child.getSelfTable();
            if (recursive) {
                LuaValue nested = findFirstChild(child, name, true);
                if (!nested.isnil()) return nested;
            }
        }
        return LuaValue.NIL;
    }

    private static int appendDescendants(GuiBase parent, LuaTable results, int index) {
        for (GuiBase child : parent.getChildren()) {
            results.set(index++, child.getSelfTable());
            index = appendDescendants(child, results, index);
        }
        return index;
    }

    private static boolean isDestroyedGui(LuaValue instance) {
        return instance.istable() && instance.checktable().rawget("__mlua_destroyed").toboolean();
    }

}
