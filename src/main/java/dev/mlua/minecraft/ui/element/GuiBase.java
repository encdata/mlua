package dev.mlua.minecraft.ui.element;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import dev.mlua.minecraft.binding.MLuaUIBindings;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.engine.MLuaLuauTypes;
import dev.mlua.math.MLuaVector;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.lang.reflect.Method;
import dev.mlua.common.enums.Side;

public abstract class GuiBase {
    protected String name = "";
    protected GuiBase parent;
    protected final List<GuiBase> children = new ArrayList<>();
    protected LuaTable selfTable;
    protected MLuaTaskSchedulerView scheduler;
    protected MLuaRegistration registration;
    protected String ownerModId;
    protected Side ownerSide;
    protected Globals ownerGlobals;
    private boolean destroyed;

    public void addChild(GuiBase child) {
        if (this.destroyed) throw new IllegalStateException("A destroyed UI instance cannot own children");
        if (child == null) throw new IllegalArgumentException("UI child cannot be null");
        if (child.destroyed) throw new IllegalArgumentException("A destroyed UI instance cannot be reparented");
        if (child == this) throw new IllegalArgumentException("A UI instance cannot be its own child");
        for (GuiBase ancestor = this; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor == child) throw new IllegalArgumentException("A UI parent cannot be placed inside its descendant");
        }
        if (child.parent == this) return;
        if (child instanceof ScreenGui screen) removeFromServiceIfScreenGui(screen);
        if (child.parent != null) {
            child.parent.children.remove(child);
        }
        child.parent = this;
        if (child.scheduler == null) {
            child.scheduler = this.scheduler;
        }
        if (child.registration == null) child.setRegistration(this.registration);
        if (child.ownerModId == null) child.setOwnerContext(this.ownerModId, this.ownerSide, this.ownerGlobals);
        children.add(child);
        updateParentRef(child);
    }

    public void setScheduler(MLuaTaskSchedulerView scheduler) {
        this.scheduler = scheduler;
        refreshSignalBindings();
        for (GuiBase child : children) {
            if (child.scheduler == null) {
                child.setScheduler(scheduler);
            }
        }
    }

    public MLuaTaskSchedulerView scheduler() {
        return this.scheduler;
    }

    public void setRegistration(MLuaRegistration registration) {
        this.registration = registration;
        for (GuiBase child : children) {
            if (child.registration == null) child.setRegistration(registration);
        }
    }

    public MLuaRegistration registration() { return registration; }

    public String ownerModId() { return this.ownerModId; }
    public Side ownerSide() { return this.ownerSide; }
    public Globals ownerGlobals() { return this.ownerGlobals; }

    public void setOwnerContext(String ownerModId, Side side) {
        this.setOwnerContext(ownerModId, side, null);
    }

    public void setOwnerContext(String ownerModId, Side side, Globals globals) {
        boolean unowned = this.ownerModId == null || this.ownerModId.isBlank();
        if (unowned) {
            this.ownerModId = ownerModId;
            this.ownerSide = side;
            this.ownerGlobals = globals;
        } else if (this.ownerModId.equals(ownerModId)) {
            this.ownerSide = side;
            this.ownerGlobals = globals;
        }
        refreshSignalBindings();
        for (GuiBase child : children) {
            if (child.ownerModId == null || child.ownerModId.isBlank()
                || child.ownerModId.equals(this.ownerModId)) {
                child.setOwnerContext(this.ownerModId, this.ownerSide, this.ownerGlobals);
            }
        }
    }

    private void refreshSignalBindings() {
        if (this.selfTable == null) return;
        for (Method method : this.getClass().getMethods()) {
            if (!method.getName().startsWith("get") || method.getName().length() <= 3
                || method.getParameterCount() != 0
                || !dev.mlua.core.signal.MLuaSignal.class.isAssignableFrom(method.getReturnType())) continue;
            try {
                if (!method.canAccess(this) && !method.trySetAccessible()) {
                    throw new IllegalAccessException("signal getter is not accessible");
                }
                dev.mlua.core.signal.MLuaSignal signal = (dev.mlua.core.signal.MLuaSignal) method.invoke(this);
                String property = method.getName().substring(3);
                this.selfTable.rawset(property, MLuaLuaBindings.signalInstance(signal, this.ownerGlobals,
                    this.scheduler, this.ownerModId, this.ownerSide));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not bind UI signal '" + method.getName() + "'", failure);
            }
        }
    }

    public void clearOwnerContext() {
        String previousOwner = this.ownerModId;
        this.ownerModId = null;
        this.ownerSide = null;
        this.ownerGlobals = null;
        this.registration = null;
        refreshSignalBindings();
        for (GuiBase child : children) {
            if (previousOwner == null || child.ownerModId == null || previousOwner.equals(child.ownerModId)) {
                child.clearOwnerContext();
            }
        }
    }

    public void removeChild(GuiBase child) {
        if (children.remove(child)) {
            child.parent = null;
            if (child.selfTable != null) {
                child.selfTable.rawset("Parent", LuaValue.NIL);
            }
        }
    }

    public int removeOwnedChildren(String owner) {
        if (owner == null || owner.isBlank()) return 0;
        int removed = 0;
        for (GuiBase child : new ArrayList<>(this.children)) {
            if (owner.equals(child.ownerModId)) {
                removeChild(child);
                child.destroyPreservingForeign(owner);
                removed++;
            } else {
                removed += child.removeOwnedChildren(owner);
            }
        }
        return removed;
    }

    public void destroyPreservingForeign(String owner) {
        detachForeignBranches(this, owner);
        this.destroy();
    }

    private static void detachForeignBranches(GuiBase node, String owner) {
        for (GuiBase child : new ArrayList<>(node.children)) {
            if (owner == null || !owner.equals(child.ownerModId)) node.removeChild(child);
            else detachForeignBranches(child, owner);
        }
    }

    public GuiBase getParent() { return parent; }

    public List<GuiBase> getChildren() { return List.copyOf(children); }

    public boolean isVisible() {
        if (this instanceof GuiObject o) return o.visible;
        if (this instanceof LayerCollector l) return l.enabled;
        return true;
    }

    public ScreenGui findRootScreenGui() {
        GuiBase current = this;
        while (current != null) {
            if (current instanceof ScreenGui sg) return sg;
            current = current.parent;
        }
        return null;
    }

    public LuaTable toLua() {
        if (selfTable == null) {
            selfTable = new LuaTable();
            writeProperties(selfTable);
            setupElementMeta(selfTable);
            if (this.destroyed) selfTable.rawset("__mlua_destroyed", LuaValue.TRUE);
            else MLuaTableRegistry.register(selfTable, this);
        }
        if (parent != null) selfTable.rawset("Parent", parent.getSelfTable());
        else if (!isInService()) selfTable.rawset("Parent", LuaValue.NIL);
        return selfTable;
    }

    protected String elementName() { return this.name; }
    public String getName() { return this.name; }
    public void setName(String name) {
        this.name = name == null ? "" : name;
        syncLuaProperty("Name", LuaValue.valueOf(this.name));
    }
    public LuaTable getSelfTable() { if (selfTable == null) toLua(); return selfTable; }
    protected void syncLuaProperty(String key, LuaValue value) { if (selfTable != null) selfTable.rawset(key, value); }
    protected void writeProperties(LuaTable table) { }

    public void applyDefinitionProperties(LuaTable properties) {
        if (properties == null) return;
        LuaValue key = LuaValue.NIL;
        while (true) {
            org.luaj.vm2.Varargs entry = properties.next(key);
            key = entry.arg1();
            if (key.isnil()) return;
            if (!key.isstring()) continue;
            String name = key.tojstring();
            if (name.equals("Parent") || name.equals("Children") || name.equals("__type")
                || isSignalValue(this.toLua().rawget(key))) continue;
            if (name.equals("__class") || name.equals("ClassName") || name.startsWith("__mlua_")) continue;
            this.toLua().set(key, entry.arg(2));
        }
    }

    private static boolean isSignalValue(LuaValue value) {
        return value.istable() && value.get("Connect").isfunction();
    }

    private static void setupElementMeta(LuaTable table) {
        LuaTable meta = new LuaTable();
        meta.set("__index", new TwoArgFunction() {
            public LuaValue call(LuaValue tbl, LuaValue key) {
                GuiBase base = MLuaTableRegistry.getElement(tbl.checktable());
                if (base == null) return LuaValue.NIL;
                String k = key.tojstring();
                LuaTable descriptor = registeredPropertyDescriptor(base, k);
                if (descriptor != null && descriptor.rawget("get").isfunction()) {
                    LuaValue getter = descriptor.rawget("get");
                    return getter.invoke(LuaValue.varargsOf(new LuaValue[]{tbl})).arg1();
                }
                LuaValue property = readJavaProperty(base, k);
                if (!property.isnil()) return property;
                LuaValue own = tbl.rawget(key);
                if (!own.isnil()) return own;
                LuaTable classTable = base.registration() != null
                    ? MLuaUIBindings.getClassTable(MLuaUIBindings.classNameOf(base), base.registration())
                    : MLuaUIBindings.getClassTable(MLuaUIBindings.classNameOf(base));
                return classTable.get(key);
            }
        });
        meta.set("__newindex", new ThreeArgFunction() {
            public LuaValue call(LuaValue tbl, LuaValue key, LuaValue val) {
                String k = key.tojstring();
                if (k.equals("__type") || k.equals("__class") || k.equals("ClassName") || k.startsWith("__mlua_")) {
                    throw new org.luaj.vm2.LuaError("[MLua] Cannot overwrite reserved UI metadata field '" + k + "'.");
                }
                if (tbl.rawget("__mlua_destroyed").toboolean()) {
                    throw new org.luaj.vm2.LuaError("[MLua] Cannot change a UI instance after it has been destroyed.");
                }
                if (k.equals("Parent")) { handleParentSet(tbl.checktable(), val); return LuaValue.NIL; }
                if (k.equals("SelectionChanged") || k.equals("SelectionGained") || k.equals("SelectionLost")
                    || k.equals("DragBegin") || k.equals("DragStopped")) {
                    throw new org.luaj.vm2.LuaError("[MLua] UI event '" + k + "' is not supported yet; connect to InputBegan, InputChanged, InputEnded, MouseEnter, MouseLeave, or Activated instead.");
                }
                GuiBase base = MLuaTableRegistry.getElement(tbl.checktable());
                LuaTable descriptor = registeredPropertyDescriptor(base, k);
                if (descriptor != null) {
                    LuaValue setter = descriptor.rawget("set");
                    if (!setter.isfunction()) {
                        throw new org.luaj.vm2.LuaError("[MLua] UI property '" + k + "' is read-only.");
                    }
                    setter.invoke(LuaValue.varargsOf(new LuaValue[]{tbl, val}));
                    return LuaValue.NIL;
                }
                if (base instanceof GuiObject || base instanceof ScreenGui) {
                    if (!writeJavaProperty(base, k, val)) {
                        if (hasJavaGetter(base, k)) {
                            throw new org.luaj.vm2.LuaError("[MLua] UI property '" + k + "' is read-only.");
                        }
                        tbl.checktable().rawset(key, val);
                    }
                    return LuaValue.NIL;
                }
                if (base != null) {
                    if (hasJavaGetter(base, k)) {
                        throw new org.luaj.vm2.LuaError("[MLua] UI property '" + k + "' is read-only.");
                    }
                    tbl.checktable().rawset(key, val);
                    return LuaValue.NIL;
                }
                tbl.checktable().rawset(key, val);
                return LuaValue.NIL;
            }
        });
        table.setmetatable(meta);
    }

    private static LuaTable registeredPropertyDescriptor(GuiBase instance, String propertyName) {
        if (instance == null || instance.registration() == null) return null;
        LuaValue instanceType = instance.getSelfTable().rawget("__type");
        if (!instanceType.isstring()) return null;
        return registeredPropertyDescriptor(instance.registration(), instanceType.tojstring(), propertyName, new HashSet<>());
    }

    private static LuaTable registeredPropertyDescriptor(MLuaRegistration registration, String className,
            String propertyName, java.util.Set<String> visited) {
        if (className == null || className.isBlank() || !visited.add(className)) return null;
        LuaTable classTable = registration.classTable(className);
        if (classTable == null) return null;
        LuaValue properties = classTable.rawget("__mlua_properties");
        LuaValue descriptor = properties.istable() ? properties.checktable().rawget(propertyName) : LuaValue.NIL;
        if (descriptor.istable()) return descriptor.checktable();
        String parentName = registration.superclassName(className);
        if (parentName != null) {
            return registeredPropertyDescriptor(registration, parentName, propertyName, visited);
        }
        return null;
    }

    static dev.mlua.minecraft.ui.UDim2 parseUDim2(LuaValue v) {
        if (!MLuaLuauTypes.isUDim2(v)) throw new org.luaj.vm2.LuaError("[MLua] UI Position and Size require UDim2 values created by UDim2.new, UDim2.fromScale, or UDim2.fromOffset");
        LuaTable t = v.checktable();
        LuaValue x = firstNonNil(t.get("X"), t.get("x"));
        LuaValue y = firstNonNil(t.get("Y"), t.get("y"));
        return new dev.mlua.minecraft.ui.UDim2(parseUDim(x), parseUDim(y));
    }

    private static dev.mlua.minecraft.ui.UDim parseUDim(LuaValue v) {
        if (!MLuaLuauTypes.isUDim(v)) throw new org.luaj.vm2.LuaError("[MLua] UDim2.X and UDim2.Y require UDim.new(scale, offset) values");
        LuaTable t = v.checktable();
        if (!t.get("Scale").isnumber() || !t.get("Offset").isnumber()) throw new org.luaj.vm2.LuaError("[MLua] UDim values require numeric Scale and Offset properties");
        return new dev.mlua.minecraft.ui.UDim(t.get("Scale").todouble(), t.get("Offset").toint());
    }

    private static LuaValue firstNonNil(LuaValue... values) {
        for (LuaValue value : values) if (!value.isnil()) return value;
        return LuaValue.NIL;
    }

    private static String propertySuffix(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static Method propertyGetter(Object target, String property) {
        String suffix = propertySuffix(property);
        try { return target.getClass().getMethod("get" + suffix); }
        catch (NoSuchMethodException ignored) {
            try { return target.getClass().getMethod("is" + suffix); }
            catch (NoSuchMethodException absent) { return null; }
        }
    }

    private static boolean hasJavaGetter(Object target, String property) {
        return target instanceof GuiBase base && hasJavaPropertyMetadata(base, property)
            && propertyGetter(target, property) != null;
    }

    private static boolean hasJavaPropertyMetadata(GuiBase instance, String property) {
        LuaTable self = instance.getSelfTable();
        LuaValue instanceMetadata = self.rawget("__mlua_doc_properties");
        if (instanceMetadata.istable() && instanceMetadata.checktable().rawget(property).istable()) return true;
        String className = MLuaUIBindings.classNameOf(instance);
        MLuaRegistration registration = instance.registration() != null
            ? instance.registration() : MLuaRuntime.get();
        return hasJavaPropertyMetadata(registration, className, property, new HashSet<>());
    }

    private static boolean hasJavaPropertyMetadata(MLuaRegistration registration, String className,
                                                    String property, java.util.Set<String> visited) {
        if (className == null || className.isBlank() || !visited.add(className)) return false;
        LuaTable classTable = registration.classTable(className);
        if (classTable == null) return false;
        LuaValue metadata = classTable.rawget("__mlua_doc_properties");
        if (metadata.istable() && metadata.checktable().rawget(property).istable()) return true;
        String parentName = registration.superclassName(className);
        return parentName != null && hasJavaPropertyMetadata(registration, parentName, property, visited);
    }

    private static LuaValue readJavaProperty(Object target, String property) {
        if (!(target instanceof GuiBase base) || !hasJavaPropertyMetadata(base, property)) return LuaValue.NIL;
        Method getter = propertyGetter(target, property);
        if (getter == null || getter.getReturnType() == Void.TYPE) {
            throw new org.luaj.vm2.LuaError("[MLua] UI property '" + property
                + "' is documented but has no public Java bean getter.");
        }
        try {
            Object value = getter.invoke(target);
            if (value == null) return LuaValue.NIL;
            if (value instanceof LuaValue lua) return lua;
            if (value instanceof GuiBase gui) return gui.getSelfTable();
            if (value instanceof dev.mlua.core.signal.MLuaSignal) return LuaValue.NIL;
            if (value instanceof String string) return LuaValue.valueOf(string);
            if (value instanceof Boolean bool) return LuaValue.valueOf(bool);
            if (value instanceof Number number) {
                if (property.endsWith("Color3") && number instanceof Integer color) {
                    return dev.mlua.core.engine.MLuaLuauTypes.color3FromArgb(color);
                }
                return LuaValue.valueOf(number.doubleValue());
            }
            if (value instanceof dev.mlua.minecraft.ui.UDim2 udim) return toLuaUDim2(udim);
            if (value instanceof dev.mlua.minecraft.ui.data.Vector2 vector) return toLuaVector2(vector);
            if (value instanceof dev.mlua.minecraft.ui.data.Rect rect) return toLuaRect(rect);
            if (value instanceof Enum<?> enumValue) return enumLuaValue(enumValue);
            throw new org.luaj.vm2.LuaError("[MLua] UI property '" + property + "' returns unsupported Java type "
                + getter.getReturnType().getTypeName() + ". Register a Lua property descriptor for this value.");
        } catch (org.luaj.vm2.LuaError error) {
            throw error;
        } catch (ReflectiveOperationException error) {
            throw new org.luaj.vm2.LuaError("[MLua] Could not read UI property '" + property + "': " + error.getMessage());
        }
    }

    private static boolean writeJavaProperty(Object target, String property, LuaValue value) {
        if (!(target instanceof GuiBase base) || !hasJavaPropertyMetadata(base, property)) return false;
        String setterName = "set" + propertySuffix(property);
        Method setter = null;
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                setter = method;
                break;
            }
        }
        if (setter == null) {
            if (propertyGetter(target, property) == null) {
                throw new org.luaj.vm2.LuaError("[MLua] UI property '" + property
                    + "' is documented but has no public Java bean accessor.");
            }
            return false;
        }
        Class<?> type = setter.getParameterTypes()[0];
        Object converted;
        try {
            if (type == String.class) converted = luaString(value);
            else if (type == boolean.class || type == Boolean.class) converted = value.toboolean();
            else if (type == int.class || type == Integer.class) {
                converted = property.endsWith("Color3") ? parseColor3Property(value)
                    : MLuaLuaArguments.wholeInt(value, "UIObject:" + property, "value");
            }
            else if (type == long.class || type == Long.class) converted = value.checklong();
            else if (type == float.class || type == Float.class) converted = (float)value.checkdouble();
            else if (type == double.class || type == Double.class) converted = value.checkdouble();
            else if (type == dev.mlua.minecraft.ui.UDim2.class) converted = parseUDim2(value);
            else if (type == dev.mlua.minecraft.ui.data.Vector2.class) converted = parseVector2(value);
            else if (type == dev.mlua.minecraft.ui.data.Rect.class) converted = parseRect(value);
            else if (LuaValue.class.isAssignableFrom(type) && type.isInstance(value)) converted = value;
            else if (type.isEnum()) {
                String enumName = value.istable() ? value.get("Name").optjstring(null) : value.optjstring(null);
                if (enumName == null) throw new IllegalArgumentException("expected an enum value");
                Object enumValue = null;
                for (Object constant : type.getEnumConstants()) {
                    if (((Enum<?>)constant).name().equals(enumName)) { enumValue = constant; break; }
                }
                if (enumValue == null) throw new IllegalArgumentException("unknown " + type.getSimpleName() + " value '" + enumName + "'");
                converted = enumValue;
            } else return false;
            setter.invoke(target, converted);
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException error) {
            throw new org.luaj.vm2.LuaError("[MLua] Invalid value for UI property '" + property + "': " + error.getMessage());
        }
    }

    private static int parseColor3Property(LuaValue value) {
        if (dev.mlua.core.engine.MLuaLuauTypes.isColor3(value)) {
            return dev.mlua.core.engine.MLuaLuauTypes.color3Argb(value);
        }
        if (value.isnumber()) return MLuaLuaArguments.wholeInt32Bits(value, "UIObject", "packed color");
        throw new org.luaj.vm2.LuaError("[MLua] UI color properties require a native Color3 value or packed color integer.");
    }

    private static String luaString(LuaValue value) {
        if (value.istable()) {
            LuaValue name = value.get("Name");
            if (name.isstring()) {
                String enumName = name.tojstring();
                if (value.getmetatable() != null && value.getmetatable().get("__isEnum").toboolean()
                    && enumName.matches("[A-Z][A-Z0-9_]*")) {
                    StringBuilder display = new StringBuilder();
                    for (String part : enumName.split("_")) {
                        if (!part.isEmpty()) display.append(Character.toUpperCase(part.charAt(0)))
                            .append(part.substring(1).toLowerCase(java.util.Locale.ROOT));
                    }
                    return display.toString();
                }
                return enumName;
            }
        }
        return value.checkjstring();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static LuaValue enumLuaValue(Enum<?> value) {
        return dev.mlua.modules.enums.MLuaEnumValue.forJava((Enum)value);
    }

    private static dev.mlua.minecraft.ui.data.Vector2 parseVector2(LuaValue v) {
        if (!MLuaVector.isVector2(v)) throw new org.luaj.vm2.LuaError("[MLua] UI Vector2 properties require Vector2.new(x, y)");
        double[] components = MLuaVector.components2(v);
        return new dev.mlua.minecraft.ui.data.Vector2(components[0], components[1]);
    }

    protected static LuaTable toLuaUDim2(dev.mlua.minecraft.ui.UDim2 udim2) {
        return MLuaLuauTypes.udim2(udim2.x().scale(), udim2.x().offset(), udim2.y().scale(), udim2.y().offset());
    }

    protected static LuaTable toLuaVector2(dev.mlua.minecraft.ui.data.Vector2 v) {
        return MLuaVector.vector2(v.x(), v.y());
    }

    protected static LuaTable toLuaRect(dev.mlua.minecraft.ui.data.Rect rect) {
        LuaTable t = new LuaTable();
        t.set("X", rect.x());
        t.set("Y", rect.y());
        t.set("Width", rect.width());
        t.set("Height", rect.height());
        return t;
    }

    static dev.mlua.minecraft.ui.data.Rect parseRect(LuaValue v) {
        if (!v.istable()) return new dev.mlua.minecraft.ui.data.Rect(0, 0, 0, 0);
        LuaTable t = v.checktable();
        LuaValue x = firstNonNil(t.get("X"), t.get("x"), t.get(1));
        LuaValue y = firstNonNil(t.get("Y"), t.get("y"), t.get(2));
        LuaValue w = firstNonNil(t.get("Width"), t.get("width"), t.get(3));
        LuaValue h = firstNonNil(t.get("Height"), t.get("height"), t.get(4));
        return new dev.mlua.minecraft.ui.data.Rect((float)x.optdouble(0), (float)y.optdouble(0), (float)w.optdouble(0), (float)h.optdouble(0));
    }

    public void destroy() {
        if (this.destroyed) return;
        this.destroyed = true;
        if (parent != null) parent.removeChild(this);
        else removeFromServiceIfScreenGui(this);
        for (GuiBase child : new ArrayList<>(children)) child.destroy();
        children.clear();
        if (selfTable != null) {
            selfTable.rawset("Parent", LuaValue.NIL);
            selfTable.rawset("__mlua_destroyed", LuaValue.TRUE);
            MLuaTableRegistry.unregister(selfTable);
        }
    }

    private static void handleParentSet(LuaTable childTable, LuaValue newParent) {
        GuiBase child = MLuaTableRegistry.getElement(childTable);
        if (child == null) {
            if (childTable.rawget("__mlua_destroyed").toboolean()) {
                throw new org.luaj.vm2.LuaError("[MLua] A destroyed UI instance cannot be reparented.");
            }
            throw new org.luaj.vm2.LuaError("[MLua] Parent can only be set on a live UI instance");
        }
        if (newParent.isnil()) {
            if (child.parent != null) child.parent.removeChild(child);
            else removeFromServiceIfScreenGui(child);
            return;
        }
        if (!newParent.istable()) throw new org.luaj.vm2.LuaError("[MLua] Parent expects a UI instance or nil");
        LuaTable target = newParent.checktable();
        GuiBase parent = MLuaTableRegistry.getElement(target);
        if (parent != null) {
            try {
                parent.addChild(child);
            } catch (IllegalArgumentException failure) {
                throw new org.luaj.vm2.LuaError("[MLua] " + failure.getMessage());
            }
            return;
        }
        if (!(child instanceof ScreenGui screen) || !"Gui".equals(target.get("__type").optjstring(""))) {
            throw new org.luaj.vm2.LuaError("[MLua] Parent must be a live UI instance or the Gui service for a ScreenGui");
        }
        addScreenGuiToService(screen, "Gui");
    }

    private static void addScreenGuiToService(ScreenGui sg, String type) {
        if (type.equals("Gui")) {
            if (sg.scheduler() == null) {
                MLuaRuntime runtime = MLuaRuntime.get();
                if (runtime == null || runtime.session() == null) {
                    throw new org.luaj.vm2.LuaError("[MLua] Gui service is not active for this ScreenGui");
                }
                sg.setScheduler(runtime.session().clientScheduler());
                sg.setRegistration(runtime);
            }
            var gui = dev.mlua.modules.coregui.Service.MLuaGuiService.getForScheduler(sg.scheduler());
            if (gui == null) throw new org.luaj.vm2.LuaError("[MLua] Gui service is not active for this ScreenGui");
            if (sg.parent != null) sg.parent.removeChild(sg);
            else gui.removeScreenGui(sg);
            gui.addScreenGui(sg);
        }
    }

    private boolean isInService() {
        if (!(this instanceof ScreenGui screen)) return false;
        var gui = dev.mlua.modules.coregui.Service.MLuaGuiService.getForScheduler(screen.scheduler());
        return gui != null && gui.getScreenGuis().contains(screen);
    }

    private static void removeFromServiceIfScreenGui(GuiBase child) {
        if (!(child instanceof ScreenGui sg)) return;
        var gui = dev.mlua.modules.coregui.Service.MLuaGuiService.getForScheduler(sg.scheduler());
        if (gui != null) gui.removeScreenGui(sg);
    }

    private static void updateParentRef(GuiBase child) {
        if (child.selfTable != null) child.selfTable.rawset("Parent", child.parent != null ? child.parent.getSelfTable() : LuaValue.NIL);
        for (GuiBase grandchild : child.children) updateParentRef(grandchild);
    }
}
