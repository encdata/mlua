package dev.mlua.modules.coregui.service;

import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.ui.element.GuiBase;
import dev.mlua.minecraft.ui.element.GuiObject;
import dev.mlua.minecraft.ui.element.MLuaTableRegistry;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.TreeSet;

/** Builds owned UI trees from Lua definitions and registered class constructors. */
final class MLuaGuiTreeFactory {
    private static final int MAX_CHILDREN_PER_PARENT = 1_024;
    private static final int MAX_TREE_NODES = 8_192;
    private static final int MAX_TREE_DEPTH = 64;

    private MLuaGuiTreeFactory() { }

    static void populateChildren(LuaTable parentTable, GuiBase parent) {
        populateChildren(parentTable, parent, new TreeBudget(), 0);
    }

    private static void populateChildren(LuaTable parentTable, GuiBase parent, TreeBudget budget, int depth) {
        LuaValue children = parentTable.get("Children");
        if (children.isnil()) return;
        if (!children.istable()) throw new LuaError("[MLua] UI Children must be a dense array of child definition tables.");
        LuaTable childList = children.checktable();
        int childCount = denseArrayLength(childList, "UI Children");
        for (int index = 1; index <= childCount; index++) {
            LuaValue value = childList.rawget(index);
            if (!value.istable()) {
                throw new LuaError("[MLua] UI Children entry " + index + " must be a definition table or live UI instance.");
            }
            if (depth >= MAX_TREE_DEPTH) {
                throw new LuaError("[MLua] UI tree depth exceeds the limit of " + MAX_TREE_DEPTH + ".");
            }
            budget.addNode();
            GuiObject child = create(value.checktable(), parent.ownerModId(), parent.ownerSide(),
                parent.ownerGlobals(), parent.registration(), parent.scheduler(), budget, depth + 1);
            parent.addChild(child);
        }
    }

    private static GuiObject create(LuaTable definition, String ownerModId, dev.mlua.common.enums.Side side,
                                    Globals globals, MLuaRegistration registration, MLuaTaskSchedulerView scheduler,
                                    TreeBudget budget, int depth) {
        String type = definition.get("__type").optjstring("");
        if (type.isBlank() && MLuaTableRegistry.getElement(definition) == null) {
            throw new LuaError("[MLua] UI child definition requires a registered class in __type");
        }
        GuiBase element = MLuaTableRegistry.getElement(definition);
        boolean constructedHere = !(element instanceof GuiObject);
        if (!(element instanceof GuiObject)) {
            LuaTable classTable = registration != null ? registration.classTable(type) : null;
            if (classTable == null) throw new LuaError("[MLua] UI class '" + type + "' is not registered in this addon runtime");
            LuaValue constructor = classTable.get("new");
            if (!constructor.isfunction()) throw new LuaError("[MLua] UI class '" + type + "' has no constructor");
            LuaValue created = constructor.call(classTable);
            if (!created.istable()) throw new LuaError("[MLua] UI class '" + type + "' constructor must return a live instance");
            element = MLuaTableRegistry.getElement(created.checktable());
            if (element != null && registration != null
                && !type.equals(created.checktable().rawget("__type").optjstring(""))) {
                registration.attachClassInstance(created.checktable(), type, null);
            }
        }
        if (!(element instanceof GuiObject object)) {
            throw new LuaError("[MLua] UI class '" + type + "' must create a GuiObject instance");
        }
        object.setRegistration(registration);
        object.setScheduler(scheduler);
        object.setOwnerContext(ownerModId, side, globals);
        try {
            applySignals(object, definition);
            object.applyDefinitionProperties(definition);
            populateChildren(definition, object, budget, depth);
        } catch (RuntimeException | Error failure) {
            if (constructedHere) object.destroy();
            throw failure;
        }
        return object;
    }

    private static int denseArrayLength(LuaTable array, String label) {
        TreeSet<Integer> indices = new TreeSet<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = array.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (key.type() != LuaValue.TNUMBER) {
                throw new LuaError("[MLua] " + label + " must use only positive integer array keys.");
            }
            if (indices.size() >= MAX_CHILDREN_PER_PARENT) {
                throw new LuaError("[MLua] " + label + " exceeds the per-parent limit of " + MAX_CHILDREN_PER_PARENT + " children.");
            }
            double numericIndex = key.todouble();
            int index = key.toint();
            if (index < 1 || numericIndex != index || !indices.add(index)) {
                throw new LuaError("[MLua] " + label + " must use only positive integer array keys.");
            }
        }
        int expected = 1;
        for (int index : indices) {
            if (index != expected++) throw new LuaError("[MLua] " + label + " must be a dense array without gaps.");
        }
        return indices.size();
    }

    private static final class TreeBudget {
        private int nodes;

        private void addNode() {
            if (++nodes > MAX_TREE_NODES) {
                throw new LuaError("[MLua] UI tree exceeds the total limit of " + MAX_TREE_NODES + " objects.");
            }
        }
    }

    private static void applySignals(GuiObject object, LuaTable definition) {
        connect(object, definition, "MouseEnter", object.getMouseEnter());
        connect(object, definition, "MouseLeave", object.getMouseLeave());
        connect(object, definition, "MouseMoved", object.getMouseMoved());
        connect(object, definition, "InputBegan", object.getInputBegan());
        connect(object, definition, "InputChanged", object.getInputChanged());
        connect(object, definition, "InputEnded", object.getInputEnded());
    }

    private static void connect(GuiObject object, LuaTable definition, String name,
                                dev.mlua.core.signal.MLuaSignal signal) {
        LuaValue callback = definition.get(name);
        if (callback.isfunction()) {
            signal.connectOwned(callback, object.ownerModId(), object.ownerSide(), object.ownerGlobals(),
                object.scheduler(), MLuaLuaBindings.boundContext(object.ownerGlobals()));
        }
    }
}
