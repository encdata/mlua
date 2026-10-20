package dev.mlua.modules.players;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

/** Defines the immutable EntityBounds value class and its spatial operations. */
final class MLuaEntityBoundsClassRegistration {
    private MLuaEntityBoundsClassRegistration() {}

    static void register(MLuaRegistration registration) {
        registration.registerClass("EntityBounds", registration.requireInstanceBaseClassName(),
            "An axis-aligned entity bounding box with Vector3 corners and spatial queries.");
        registration.registerClassFunction("EntityBounds", "new", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue minimum = args.arg(1);
                LuaValue maximum = args.arg(2);
                if (!dev.mlua.math.MLuaVector.isVector3(minimum)
                        || !dev.mlua.math.MLuaVector.isVector3(maximum)) {
                    throw new LuaError("[MLua] EntityBounds.new expects minimum and maximum Vector3 values.");
                }
                LuaTable min = minimum.checktable();
                LuaTable max = maximum.checktable();
                return create(registration,
                    min.get("X").todouble(), min.get("Y").todouble(), min.get("Z").todouble(),
                    max.get("X").todouble(), max.get("Y").todouble(), max.get("Z").todouble());
            }
        }, "Creates bounds from ordered minimum and maximum Vector3 corners.");
        for (String name : List.of("Minimum", "Maximum")) {
            String getter = "Get" + name;
            registration.registerClassFunction("EntityBounds", getter, new OneArgFunction() {
                @Override public LuaValue call(LuaValue self) { return self.checktable().get(name); }
            }, "Returns the " + name.toLowerCase() + " corner as a Vector3.");
            MLuaClassMethods.registerInstanceProperty(registration, "EntityBounds", name, getter, null,
                "Vector3", "shared:Shared", "The " + name.toLowerCase() + " corner of the bounds.");
        }
        registration.registerClassFunction("EntityBounds", "GetCenter", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                return dev.mlua.math.MLuaVector.vector3(
                    (min.get("X").todouble() + max.get("X").todouble()) * 0.5,
                    (min.get("Y").todouble() + max.get("Y").todouble()) * 0.5,
                    (min.get("Z").todouble() + max.get("Z").todouble()) * 0.5);
            }
        }, "Returns the center point as a Vector3.");
        MLuaClassMethods.registerInstanceProperty(registration, "EntityBounds", "Center", "GetCenter", null,
            "Vector3", "shared:Shared", "The center point of the bounds.");
        registration.registerClassFunction("EntityBounds", "GetSize", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                return dev.mlua.math.MLuaVector.vector3(
                    max.get("X").todouble() - min.get("X").todouble(),
                    max.get("Y").todouble() - min.get("Y").todouble(),
                    max.get("Z").todouble() - min.get("Z").todouble());
            }
        }, "Returns the box dimensions as a Vector3.");
        MLuaClassMethods.registerInstanceProperty(registration, "EntityBounds", "Size", "GetSize", null,
            "Vector3", "shared:Shared", "The dimensions of the bounds.");
        registration.registerClassFunction("EntityBounds", "GetVolume", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                return LuaValue.valueOf((max.get("X").todouble() - min.get("X").todouble())
                    * (max.get("Y").todouble() - min.get("Y").todouble())
                    * (max.get("Z").todouble() - min.get("Z").todouble()));
            }
        }, "Returns the volume of this box in cubic blocks.");
        MLuaClassMethods.registerInstanceProperty(registration, "EntityBounds", "Volume", "GetVolume", null,
            "number", "shared:Shared", "The volume of the bounds in cubic blocks.");
        registration.registerClassFunction("EntityBounds", "Contains", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue point = args.arg(2);
                if (!dev.mlua.math.MLuaVector.isVector3(point)) {
                    throw new LuaError("[MLua] EntityBounds:Contains expects a Vector3.");
                }
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                LuaTable position = point.checktable();
                return LuaValue.valueOf(position.get("X").todouble() >= min.get("X").todouble()
                    && position.get("X").todouble() <= max.get("X").todouble()
                    && position.get("Y").todouble() >= min.get("Y").todouble()
                    && position.get("Y").todouble() <= max.get("Y").todouble()
                    && position.get("Z").todouble() >= min.get("Z").todouble()
                    && position.get("Z").todouble() <= max.get("Z").todouble());
            }
        }, "Returns whether a Vector3 point lies inside or on the box.");
        registration.registerClassFunction("EntityBounds", "Intersects", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue other = args.arg(2);
                if (!other.istable()) {
                    throw new LuaError("[MLua] EntityBounds:Intersects expects an EntityBounds instance.");
                }
                LuaTable otherBounds = other.checktable();
                LuaValue type = otherBounds.get("__type");
                if (!type.isstring() || !type.checkjstring().equals("EntityBounds")) {
                    throw new LuaError("[MLua] EntityBounds:Intersects expects an EntityBounds instance.");
                }
                LuaTable self = args.checktable(1);
                LuaTable firstMin = self.get("Minimum").checktable();
                LuaTable firstMax = self.get("Maximum").checktable();
                LuaTable secondMin = otherBounds.get("Minimum").checktable();
                LuaTable secondMax = otherBounds.get("Maximum").checktable();
                return LuaValue.valueOf(firstMax.get("X").todouble() >= secondMin.get("X").todouble()
                    && firstMin.get("X").todouble() <= secondMax.get("X").todouble()
                    && firstMax.get("Y").todouble() >= secondMin.get("Y").todouble()
                    && firstMin.get("Y").todouble() <= secondMax.get("Y").todouble()
                    && firstMax.get("Z").todouble() >= secondMin.get("Z").todouble()
                    && firstMin.get("Z").todouble() <= secondMax.get("Z").todouble());
            }
        }, "Returns whether this box overlaps another EntityBounds instance.");
        registration.registerClassFunction("EntityBounds", "Inflate", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                double amount = args.checkdouble(2);
                if (!Double.isFinite(amount) || amount < 0) {
                    throw new LuaError("[MLua] EntityBounds:Inflate expects a finite, non-negative amount.");
                }
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                return create(registration,
                    min.get("X").todouble() - amount, min.get("Y").todouble() - amount, min.get("Z").todouble() - amount,
                    max.get("X").todouble() + amount, max.get("Y").todouble() + amount, max.get("Z").todouble() + amount);
            }
        }, "Returns a new bounds instance expanded equally on each side.");
        registration.registerClassFunction("EntityBounds", "Deflate", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                double amount = args.checkdouble(2);
                if (!Double.isFinite(amount) || amount < 0) {
                    throw new LuaError("[MLua] EntityBounds:Deflate expects a finite, non-negative amount.");
                }
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                return create(registration,
                    min.get("X").todouble() + amount, min.get("Y").todouble() + amount, min.get("Z").todouble() + amount,
                    max.get("X").todouble() - amount, max.get("Y").todouble() - amount, max.get("Z").todouble() - amount);
            }
        }, "Returns a new bounds instance contracted equally on each side.");
        registration.registerClassFunction("EntityBounds", "Move", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue offset = args.arg(2);
                if (!dev.mlua.math.MLuaVector.isVector3(offset)) {
                    throw new LuaError("[MLua] EntityBounds:Move expects a Vector3 offset.");
                }
                LuaTable delta = offset.checktable();
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                double x = delta.get("X").todouble();
                double y = delta.get("Y").todouble();
                double z = delta.get("Z").todouble();
                return create(registration,
                    min.get("X").todouble() + x, min.get("Y").todouble() + y, min.get("Z").todouble() + z,
                    max.get("X").todouble() + x, max.get("Y").todouble() + y, max.get("Z").todouble() + z);
            }
        }, "Returns a new bounds instance translated by a Vector3 offset.");
        registration.registerClassFunction("EntityBounds", "Union", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue other = args.arg(2);
                if (!other.istable()) throw new LuaError("[MLua] EntityBounds:Union expects an EntityBounds instance.");
                LuaTable otherBounds = other.checktable();
                LuaValue type = otherBounds.get("__type");
                if (!type.isstring() || !type.checkjstring().equals("EntityBounds")) {
                    throw new LuaError("[MLua] EntityBounds:Union expects an EntityBounds instance.");
                }
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                LuaTable otherMin = otherBounds.get("Minimum").checktable();
                LuaTable otherMax = otherBounds.get("Maximum").checktable();
                return create(registration,
                    Math.min(min.get("X").todouble(), otherMin.get("X").todouble()),
                    Math.min(min.get("Y").todouble(), otherMin.get("Y").todouble()),
                    Math.min(min.get("Z").todouble(), otherMin.get("Z").todouble()),
                    Math.max(max.get("X").todouble(), otherMax.get("X").todouble()),
                    Math.max(max.get("Y").todouble(), otherMax.get("Y").todouble()),
                    Math.max(max.get("Z").todouble(), otherMax.get("Z").todouble()));
            }
        }, "Returns a new bounds instance enclosing both boxes.");
        registration.registerClassFunction("EntityBounds", "DistanceTo", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue point = args.arg(2);
                if (!dev.mlua.math.MLuaVector.isVector3(point)) {
                    throw new LuaError("[MLua] EntityBounds:DistanceTo expects a Vector3.");
                }
                LuaTable position = point.checktable();
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                double dx = Math.max(Math.max(min.get("X").todouble() - position.get("X").todouble(), 0),
                    position.get("X").todouble() - max.get("X").todouble());
                double dy = Math.max(Math.max(min.get("Y").todouble() - position.get("Y").todouble(), 0),
                    position.get("Y").todouble() - max.get("Y").todouble());
                double dz = Math.max(Math.max(min.get("Z").todouble() - position.get("Z").todouble(), 0),
                    position.get("Z").todouble() - max.get("Z").todouble());
                return LuaValue.valueOf(Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
        }, "Returns the shortest distance from a Vector3 point to this box.");
        registration.registerClassFunction("EntityBounds", "ClosestPoint", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue point = args.arg(2);
                if (!dev.mlua.math.MLuaVector.isVector3(point)) {
                    throw new LuaError("[MLua] EntityBounds:ClosestPoint expects a Vector3.");
                }
                LuaTable position = point.checktable();
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                return dev.mlua.math.MLuaVector.vector3(
                    Math.max(min.get("X").todouble(), Math.min(max.get("X").todouble(), position.get("X").todouble())),
                    Math.max(min.get("Y").todouble(), Math.min(max.get("Y").todouble(), position.get("Y").todouble())),
                    Math.max(min.get("Z").todouble(), Math.min(max.get("Z").todouble(), position.get("Z").todouble())));
            }
        }, "Returns the nearest point on or inside this box.");
    }

    static LuaTable create(MLuaRegistration registration,
                           double minX, double minY, double minZ,
                           double maxX, double maxY, double maxZ) {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                || minX > maxX || minY > maxY || minZ > maxZ) {
            throw new LuaError("[MLua] EntityBounds corners must be finite and ordered from minimum to maximum.");
        }
        LuaTable result = registration.createClassInstance("EntityBounds");
        result.rawset("Minimum", dev.mlua.math.MLuaVector.vector3(minX, minY, minZ));
        result.rawset("Maximum", dev.mlua.math.MLuaVector.vector3(maxX, maxY, maxZ));
        result.freeze();
        return result;
    }

}
