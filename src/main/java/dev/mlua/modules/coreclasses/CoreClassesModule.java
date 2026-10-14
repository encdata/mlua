package dev.mlua.modules.coreclasses;

import dev.mlua.core.MLuaContextView;
import dev.mlua.core.MLuaExampleScripts;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import java.util.List;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class CoreClassesModule implements MLuaModule {
    @Override
    public String id() { return "core-classes"; }

    @Override
    public int priority() { return -100; }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        registration.registerClass("Object", null, "Root class in MLua's Lua class hierarchy.");
        registration.registerInstanceBaseClass("Instance", "Object", "Base class for live wrappers around Minecraft objects.");
        registerInstanceMethods(registration);
        registration.registerClass("Signal", registration.requireInstanceBaseClassName(),
            "A live event source that connects callbacks, fires values, and resumes waiters.");
        MLuaClassMethods.registerInstanceMethod(registration, "Signal", "Connect",
            "Connects a callback that runs whenever the signal fires.");
        MLuaClassMethods.registerInstanceMethod(registration, "Signal", "Once",
            "Connects a callback that disconnects after its first call.");
        MLuaClassMethods.registerInstanceMethod(registration, "Signal", "Fire",
            "Calls connected callbacks and resumes waiters with these arguments.");
        MLuaClassMethods.registerInstanceMethod(registration, "Signal", "Wait",
            "Yields the current coroutine until the signal fires, then returns the arguments passed to Fire.");
        registration.registerClass("Connection", registration.requireInstanceBaseClassName(),
            "A live handle for one callback connected to a signal.");
        MLuaClassMethods.registerInstanceMethod(registration, "Connection", "Disconnect",
            "Stops a connected callback from receiving future fires.");
        MLuaClassMethods.registerInstanceMethod(registration, "Connection", "GetConnected",
            "Returns whether this callback is still connected to its signal.");
        MLuaClassMethods.registerInstanceProperty(registration, "Connection", "Connected", "GetConnected", null,
            "boolean", "shared:Shared", "Whether this callback is still connected to its signal.");
        registration.registerServiceBaseClass("Service", registration.requireInstanceBaseClassName(), "Base class for live Lua service instances.");
        registration.registerClassFunction("Service", "GetName", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String instanceClassName = registration.classNameOf(args.arg1());
                if (instanceClassName == null) return LuaValue.NIL;
                LuaTable instanceClass = registration.classTable(instanceClassName);
                LuaValue serviceName = instanceClass == null ? LuaValue.NIL : instanceClass.rawget("__mlua_service_name");
                return serviceName.isstring() ? serviceName : LuaValue.NIL;
            }
        }, "Returns this service's registered name.");
        MLuaClassMethods.registerInstanceProperty(registration, "Service", "Name", "GetName", null,
            "string", "shared:Shared", "This service's registered name.");
    }

    private static void registerInstanceMethods(MLuaRegistration registration) {
        registerInstanceFunction(registration, "GetClassName", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue self = args.arg1();
                String type = registration.classNameOf(self);
                return type == null ? LuaValue.valueOf(self.typename()) : LuaValue.valueOf(type);
            }
        }, "Returns this instance's registered MLua class name.");
        registerInstanceFunction(registration, "GetClass", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String type = registration.classNameOf(args.arg1());
                if (type == null) return LuaValue.NIL;
                LuaValue classTable = registration.classTable(type);
                return classTable == null ? LuaValue.NIL : classTable;
            }
        }, "Returns the registered class table that defines this instance's behavior.");
        registerInstanceFunction(registration, "GetSuperclass", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String type = registration.classNameOf(args.arg1());
                if (type == null) return LuaValue.NIL;
                LuaTable classTable = registration.classTable(type);
                LuaValue parents = classTable == null ? LuaValue.NIL : classTable.rawget("__mlua_doc_parents");
                if (!parents.istable()) return LuaValue.NIL;
                String parentName = parents.checktable().get(1).optjstring("");
                if (parentName.isBlank()) return LuaValue.NIL;
                LuaTable superclass = registration.classTable(parentName);
                return superclass == null ? LuaValue.NIL : superclass;
            }
        }, "Returns the immediate superclass table, or nil when this class has no superclass.");
        registerInstanceFunction(registration, "GetType", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue self = args.arg1();
                String type = registration.classNameOf(self);
                return type == null ? LuaValue.valueOf(self.typename()) : LuaValue.valueOf(type);
            }
        }, "Returns this instance's registered MLua runtime type.");
        registerInstanceFunction(registration, "IsA", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue target = args.arg(2);
                if (target.isnil()) return LuaValue.FALSE;
                String targetName;
                if (target.istable()) {
                    LuaTable targetClass = target.checktable();
                    targetName = targetClass.rawget("__type").optjstring("");
                    if (!targetClass.rawget("__class").toboolean()
                        || registration.classTable(targetName) != targetClass) return LuaValue.FALSE;
                } else {
                    targetName = target.checkjstring();
                }
                return LuaValue.valueOf(registration.isInstanceOf(args.arg1(), targetName));
            }
        }, "Returns whether this instance inherits from or matches the given class.");
        registerInstanceFunction(registration, "ToString", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue self = args.arg1();
                String type = registration.classNameOf(self);
                return type == null ? LuaValue.valueOf(self.typename()) : LuaValue.valueOf(type);
            }
        }, "Returns this instance's registered MLua class name as text.");
    }

    private static void registerInstanceFunction(MLuaRegistration registration, String methodName,
                                                 LuaValue implementation, String description) {
        LuaValue existing = registration.classTable("Instance").rawget(methodName);
        if (existing.isfunction()) implementation = existing;
        registration.registerClassFunction("Instance", methodName, implementation, description);
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.clazz("Signal", "MLuaSignal", "A live event source that connects callbacks, fires values, and resumes waiters.", "classes/signal.html", "Instance");
        docs.clazz("Connection", "MLuaConnection", "A live handle for one callback connected to a signal.", "classes/connection.html", "Instance");
        docs.property("Connection", "Connection.Connected", "boolean", "Whether this callback is still connected to its signal.", "shared:Shared");
        docs.property("Instance", "ClassName", "string",
            "The registered MLua class name for this live instance.", "read:Read");
        docs.property("Service", "Name", "string", "This service's registered name.", "read:Read");
        List<MLuaDocBuilder.GuideStep> gettingStarted = new java.util.ArrayList<>();
        gettingStarted.add(new MLuaDocBuilder.GuideStep("Docs", "Create a server script",
            "Install MLua, launch Minecraft once, then create this file in the MLua scripts folder. The server suffix makes it run when a world starts.",
            "<game directory>/mlua/scripts/hello.server.lua"));
        List<MLuaExampleScripts.ExampleScript> examples = MLuaExampleScripts.scripts();
        MLuaExampleScripts.ExampleScript firstServerScript = examples.stream()
            .filter(example -> example.name().endsWith(".server.lua"))
            .findFirst().orElse(null);
        if (firstServerScript != null) {
            String explanation = firstServerScript.description().isBlank()
                ? "Put this code in hello.server.lua, then join a world. The PlayerAdded signal runs the callback when someone joins."
                : firstServerScript.description() + " Put it in hello.server.lua, then join a world to run it.";
            gettingStarted.add(new MLuaDocBuilder.GuideStep("Docs", "Greet players when they join", explanation,
                firstServerScript.source()));
        }
        gettingStarted.add(new MLuaDocBuilder.GuideStep("Docs", "Change and verify it",
            "Edit the message, save the file, and reload scripts or restart the world. For mod distribution, place the same script under your mod resources at assets/<modid>/mlua/scripts/.",
            "player:SendMessage(\"Welcome to my world!\")"));
        docs.contribution(new MLuaDocBuilder.Contribution("mlua:getting-started", "getting-started",
            "Build your first MLua script", "Create a server script, greet joining players, and change the message.",
            "mlua", 0, gettingStarted, List.of()));

        List<MLuaDocBuilder.GuideStep> scriptGuide = new java.util.ArrayList<>();
        scriptGuide.add(new MLuaDocBuilder.GuideStep("Scripts", "Run scripts from the game folder",
            "Loose scripts run from the generated MLua scripts folder. Bundled starter files are copied to example-scripts and stay inactive until you copy them into scripts.",
            "<game directory>/mlua/scripts/"));
        scriptGuide.add(new MLuaDocBuilder.GuideStep("Scripts", "Package scripts with a mod",
            "Put mod-owned scripts in the mod resources tree. MLua uses the mod id as the registration namespace and keeps each script's filename suffix as its lifecycle declaration.",
            "src/main/resources/assets/<modid>/mlua/scripts/"));
        List<MLuaDocBuilder.Template> scriptTemplates = examples.stream()
            .map(example -> new MLuaDocBuilder.Template(example.name(),
                example.description().isBlank() ? "Starter source for this script lifecycle." : example.description(),
                "mlua/example-scripts/" + example.name(), example.source()))
            .toList();
        docs.contribution(new MLuaDocBuilder.Contribution("mlua:core-script-guide", "Scripts",
            "Script files and starter templates", "Choose a script folder and lifecycle, then start from a bundled example.",
            "mlua", 0, scriptGuide, List.of(), scriptTemplates));

        docs.dataType("Vector3", "A three-dimensional position or direction with X, Y, and Z components.", "data-types/vector3.html");
        docs.dataType("CFrame", "A position and orientation transform with Position and LookVector values.", "data-types/cframe.html");
        docs.dataType("Vector2", "A two-dimensional value with X and Y components.", "data-types/vector2.html");
        docs.dataType("Color3", "An RGB color value used by UI and rendering APIs.", "data-types/color3.html");
        docs.dataType("UDim", "A UI dimension made from a scale and pixel offset.", "data-types/udim.html");
        docs.dataType("UDim2", "A two-axis UI size or position made from scale and pixel offsets.", "data-types/udim2.html");
        docs.library("Globals", "Built-in functions and values available to MLua scripts.", "libraries/globals.html");
        docs.library("Math", "Standard Lua math functions and MLua's Luau-compatible additions.", "libraries/math.html");
        docs.library("Table", "Standard table operations and MLua table helpers.", "libraries/table.html");
        docs.library("String", "Standard string operations available to MLua scripts.", "libraries/string.html");
        docs.library("Random", "Random number generation helpers.", "libraries/random.html");
        docs.library("DateTime", "Date and time construction, formatting, and arithmetic.", "libraries/datetime.html");
        docs.library("Task", "Scheduling, delays, and deferred Lua work.", "libraries/task.html");
        docs.library("Signal", "Connectable events used by services, classes, and UI instances.", "libraries/signal.html");
        docs.library("Number", "The Lua number type and its conversion helpers.", "libraries/number.html");
        docs.library("Boolean", "The Lua boolean type and its conversion helpers.", "libraries/boolean.html");
        docs.library("Void", "The absence of a returned value.", "libraries/void.html");
        docs.library("Any", "A value that can have any Lua type.", "libraries/any.html");
        docs.library("Nil", "The Lua nil value, representing an absent value.", "libraries/nil.html");
        docs.library("EnumValue", "A selected value from an MLua enum table.", "libraries/enum-value.html");

        docs.property("Vector3", "Vector3.X", "number", "The X component.", "read:Read");
        docs.property("Vector3", "Vector3.Y", "number", "The Y component.", "read:Read");
        docs.property("Vector3", "Vector3.Z", "number", "The Z component.", "read:Read");
        docs.property("Vector3", "Vector3.Magnitude", "number", "The length of the vector.", "read:Read");
        docs.property("Vector3", "Vector3.Unit", "Vector3", "A normalized copy of the vector.", "read:Read");
        docs.method("Vector3", "Vector3.new(x, y, z)", "Creates a new Vector3.", "x, y, and z coordinates.", "Vector3", "shared:Shared", null);
        docs.method("Vector3", "Vector3.zero", "Returns Vector3.new(0, 0, 0).", null, "Vector3", "shared:Shared", null);
        docs.method("Vector3", "Vector3.one", "Returns Vector3.new(1, 1, 1).", null, "Vector3", "shared:Shared", null);
        docs.method("Vector3", "Vector3 + other", "Adds two vectors.", "another Vector3", "Vector3", "shared:Shared", null);
        docs.method("Vector3", "Vector3 - other", "Subtracts another vector.", "another Vector3", "Vector3", "shared:Shared", null);
        docs.method("Vector3", "Vector3 * scalar", "Multiplies each component by a scalar.", "a number", "Vector3", "shared:Shared", null);
        docs.method("Vector3", "Vector3 / scalar", "Divides each component by a scalar.", "a non-zero number", "Vector3", "shared:Shared", null);
        docs.method("Vector3", "Vector3:Dot(other)", "Returns the dot product.", "another Vector3", "number", "shared:Shared", null);
        docs.method("Vector3", "Vector3:Cross(other)", "Returns the cross product.", "another Vector3", "Vector3", "shared:Shared", null);
        docs.method("Vector3", "Vector3:Lerp(other, alpha)", "Interpolates between this vector and another.", "another Vector3 and an interpolation value", "Vector3", "shared:Shared", null);
        docs.method("Vector3", "Vector3:Distance(other)", "Returns the distance to another vector.", "another Vector3", "number", "shared:Shared", null);
        docs.method("Vector3", "Vector3:Floor()", "Returns a copy with each component rounded down.", null, "Vector3", "shared:Shared", null);
        docs.method("Vector3", "Vector3:Ceil()", "Returns a copy with each component rounded up.", null, "Vector3", "shared:Shared", null);
        docs.method("Vector3", "Vector3:Abs()", "Returns a copy with the absolute value of each component.", null, "Vector3", "shared:Shared", null);
        docs.guideStep("Vector3", "Move by a direction",
            "Add a direction vector to a position to calculate a destination.",
            "local direction = Vector3.new(1, 0, 0)\nlocal destination = player.Position + direction * 4\nplayer:Teleport(destination)");

        docs.property("Vector2", "Vector2.X", "number", "The X component.", "read:Read");
        docs.property("Vector2", "Vector2.Y", "number", "The Y component.", "read:Read");
        docs.property("Vector2", "Vector2.Magnitude", "number", "The length of the vector.", "read:Read");
        docs.property("Vector2", "Vector2.Unit", "Vector2", "A normalized copy of the vector.", "read:Read");
        docs.method("Vector2", "Vector2.new(x, y)", "Creates a new Vector2.", "x and y coordinates.", "Vector2", "shared:Shared", null);
        docs.method("Vector2", "Vector2 + other", "Adds two vectors.", "another Vector2", "Vector2", "shared:Shared", null);
        docs.method("Vector2", "Vector2 - other", "Subtracts another vector.", "another Vector2", "Vector2", "shared:Shared", null);
        docs.method("Vector2", "Vector2 * scalar", "Multiplies each component by a scalar.", "a number", "Vector2", "shared:Shared", null);
        docs.method("Vector2", "Vector2:Dot(other)", "Returns the dot product.", "another Vector2", "number", "shared:Shared", null);
        docs.method("Vector2", "Vector2:Lerp(other, alpha)", "Interpolates between this vector and another.", "another Vector2 and an interpolation value", "Vector2", "shared:Shared", null);
        docs.guideStep("Vector2", "Measure a 2D offset",
            "Use Magnitude to turn an X/Y offset into a distance.",
            "local offset = Vector2.new(mouseX - centerX, mouseY - centerY)\nif offset.Magnitude < 24 then\n    print(\"Pointer is near the center\")\nend");

        docs.property("Color3", "Color3.R", "number", "The red component, from 0 to 1.", "read:Read");
        docs.property("Color3", "Color3.G", "number", "The green component, from 0 to 1.", "read:Read");
        docs.property("Color3", "Color3.B", "number", "The blue component, from 0 to 1.", "read:Read");
        docs.method("Color3", "Color3.fromRGB(r, g, b)", "Creates a color from RGB values in the 0 to 255 range.", "red, green, and blue components", "Color3", "shared:Shared", null);
        docs.method("Color3", "Color3.fromHSV(h, s, v)", "Creates a color from hue, saturation, and value components in the 0 to 1 range.", "hue, saturation, and value", "Color3", "shared:Shared", null);
        docs.method("Color3", "Color3.fromHex(hex)", "Creates a color from a hexadecimal string such as #ff0000.", "a hex color string", "Color3", "shared:Shared", null);
        docs.method("Color3", "Color3:ToHex()", "Returns this color as a hexadecimal string.", null, "string", "shared:Shared", null);
        docs.method("Color3", "Color3:ToHSV()", "Returns the hue, saturation, and value components.", null, "number, number, number", "shared:Shared", null);
        docs.method("Color3", "Color3:Lerp(other, alpha)", "Interpolates between this color and another.", "another Color3 and an interpolation value", "Color3", "shared:Shared", null);
        docs.guideStep("Color3", "Set a UI color",
            "Create a Color3 value and assign it to a UI object's color property.",
            "local panel = UIObject.new(\"Frame\", \"WarningPanel\")\npanel.BackgroundColor3 = Color3.fromRGB(150, 24, 32)");

        docs.property("CFrame", "CFrame.Position", "Vector3", "The transform's position.", "read:Read");
        docs.property("CFrame", "CFrame.X / CFrame.Y / CFrame.Z", "number", "The position components.", "read:Read");
        docs.property("CFrame", "CFrame.LookVector", "Vector3", "The forward direction.", "read:Read");
        docs.property("CFrame", "CFrame.RightVector", "Vector3", "The right basis vector.", "read:Read");
        docs.property("CFrame", "CFrame.UpVector", "Vector3", "The up basis vector.", "read:Read");
        docs.property("CFrame", "CFrame.Rotation", "Vector3", "The rotation in radians as XYZ Euler angles.", "read:Read");
        docs.property("CFrame", "CFrame.Orientation", "Vector3", "The rotation in degrees as XYZ Euler angles.", "read:Read");
        docs.property("CFrame", "CFrame.Yaw / CFrame.Pitch / CFrame.Roll", "number", "Minecraft rotation components in degrees.", "read:Read");
        docs.method("CFrame", "CFrame.new(x, y, z)", "Creates a transform at a position with identity rotation.", "position coordinates", "CFrame", "shared:Shared", null);
        docs.method("CFrame", "CFrame.new(position)", "Creates a transform at a Vector3 position.", "a Vector3 position", "CFrame", "shared:Shared", null);
        docs.method("CFrame", "CFrame.new(position, target)", "Creates a transform facing from position toward a target.", "position and target Vector3 values", "CFrame", "shared:Shared", null);
        docs.method("CFrame", "CFrame.Angles(rx, ry, rz)", "Creates a rotation transform. Angles are radians.", "XYZ rotation angles in radians", "CFrame", "shared:Shared", null);
        docs.method("CFrame", "CFrame.fromEulerAnglesXYZ(rx, ry, rz)", "Creates a rotation transform from XYZ Euler angles in radians.", "XYZ rotation angles in radians", "CFrame", "shared:Shared", null);
        docs.method("CFrame", "CFrame.fromOrientation(rx, ry, rz)", "Alias for CFrame.Angles using XYZ Euler angles in radians.", "XYZ rotation angles in radians", "CFrame", "shared:Shared", null);
        docs.method("CFrame", "CFrame.fromYawPitch(yaw, pitch)", "Creates a rotation from Minecraft yaw and pitch in degrees.", "yaw and pitch in degrees", "CFrame", "shared:Shared", null);
        docs.method("CFrame", "CFrame.fromYawPitchRadians(yaw, pitch)", "Creates a rotation from yaw and pitch in radians.", "yaw and pitch in radians", "CFrame", "shared:Shared", null);
        docs.method("CFrame", "CFrame.lookAt(position, target)", "Creates a transform facing from position toward a target.", "position and target Vector3 values", "CFrame", "shared:Shared", null);
        docs.method("CFrame", "CFrame.lookAlong(position, direction)", "Creates a transform facing along a direction vector.", "a position and direction Vector3", "CFrame", "shared:Shared", null);
        docs.method("CFrame", "CFrame.identity", "Returns the identity transform.", null, "CFrame", "shared:Shared", null);
        docs.method("CFrame", "CFrame:Inverse()", "Returns the inverse transform.", null, "CFrame", "shared:Shared", null);
        docs.method("CFrame", "CFrame:Lerp(other, alpha)", "Interpolates position and forward direction.", "another CFrame and an interpolation value", "CFrame", "shared:Shared", null);
        docs.method("CFrame", "CFrame:ToWorldSpace(other)", "Composes another CFrame into world space.", "another CFrame", "CFrame", "shared:Shared", null);
        docs.method("CFrame", "CFrame:ToObjectSpace(other)", "Converts another CFrame into this object's local space.", "another CFrame", "CFrame", "shared:Shared", null);
        docs.method("CFrame", "CFrame:PointToWorldSpace(point)", "Transforms a local point into world space.", "a local Vector3 point", "Vector3", "shared:Shared", null);
        docs.method("CFrame", "CFrame:PointToObjectSpace(point)", "Transforms a world point into local space.", "a world Vector3 point", "Vector3", "shared:Shared", null);
        docs.method("CFrame", "CFrame:VectorToWorldSpace(vector)", "Transforms a local direction into world space without applying position.", "a local Vector3 direction", "Vector3", "shared:Shared", null);
        docs.method("CFrame", "CFrame:VectorToObjectSpace(vector)", "Transforms a world direction into local space without applying position.", "a world Vector3 direction", "Vector3", "shared:Shared", null);
        docs.method("CFrame", "CFrame:GetComponents()", "Returns position and rotation matrix components.", null, "12 numbers", "shared:Shared", null);
        docs.method("CFrame", "CFrame1 * CFrame2", "Composes two transforms.", "two CFrame values", "CFrame", "shared:Shared", null);
        docs.method("CFrame", "CFrame + Vector3", "Offsets the transform position.", "a Vector3 offset", "CFrame", "shared:Shared", null);
        docs.guideStep("CFrame", "Rotate at a world position",
            "Multiply a position transform by a rotation to keep the rotation centered at that position. Angles use radians.",
            "local position = CFrame.new(player:GetEyeCFrame().Position)\nlocal rotation = CFrame.Angles(0, math.rad(90), 0)\nlocal facing = position * rotation");

        docs.property("UDim", "UDim.Scale", "number", "The size or position component relative to the parent.", "read:Read");
        docs.property("UDim", "UDim.Offset", "number", "The size or position component in pixels.", "read:Read");
        docs.method("UDim", "UDim.new(scale, offset)", "Creates a dimension from a parent-relative scale and pixel offset.", "a scale number and pixel offset", "UDim", "shared:Shared", null);

        docs.property("UDim2", "UDim2.X", "UDim", "The horizontal dimension.", "read:Read");
        docs.property("UDim2", "UDim2.Y", "UDim", "The vertical dimension.", "read:Read");
        docs.method("UDim2", "UDim2.new(xScale, xOffset, yScale, yOffset)", "Creates a two-axis dimension from scale and pixel offsets.", "X scale and offset followed by Y scale and offset", "UDim2", "shared:Shared", null);
        docs.method("UDim2", "UDim2.fromScale(xScale, yScale)", "Creates a size or position from parent-relative scale values.", "X and Y scale values", "UDim2", "shared:Shared", null);
        docs.method("UDim2", "UDim2.fromOffset(xPixels, yPixels)", "Creates a size or position from pixel offsets.", "X and Y pixel values", "UDim2", "shared:Shared", null);
        docs.guideStep("UDim2", "Size a panel proportionally",
            "Use scale for a responsive fraction of the parent and offset for a fixed pixel margin.",
            "local panel = UIObject.new(\"Frame\", \"Panel\")\npanel.Size = UDim2.new(1, -32, 0, 120)\npanel.Position = UDim2.new(0, 16, 0, 16)");

        docs.guideStep("Number", "Use numbers for game state",
            "Numbers represent health, positions, counters, and time values. Lua numbers are double precision.",
            "local health = 20\nlocal distance = 4.5\nif health > 0 then\n    print(\"Alive at distance\", distance)\nend");
        docs.guideStep("Boolean", "Branch on a boolean value",
            "Only false and nil are falsey in Lua; values such as zero and empty strings are truthy.",
            "local isAlive = true\nif isAlive then\n    print(\"Continue the encounter\")\nend");
        docs.guideStep("Void", "Call a method with no result",
            "A method documented as void performs an action and returns no useful value.",
            "player:SendMessage(\"The door opens\")");
        docs.guideStep("Any", "Handle a value with a dynamic type",
            "An any value can hold a Lua value of any type. Check it before using type-specific operations.",
            "local result = Require(\"encounter/state\")\nif type(result) == \"table\" then\n    print(result.phase)\nend");
        docs.guideStep("Nil", "Check whether a lookup found a value",
            "Nil represents a missing or absent value and can be checked before using the result.",
            "local target = Players:GetPlayerByName(\"unknown\")\nif target == nil then\n    print(\"No matching player\")\nend");
        docs.property("EnumValue", "value.Name", "string", "The name of the selected enum value.", "read:Read");
        docs.guideStep("EnumValue", "Compare named enum values",
            "Enum values are first-class values. Store them, compare them, and pass them to API methods.",
            "local mode = Enum.GameMode.Adventure\nif player.GameMode == mode then\n    player.GameMode = Enum.GameMode.Survival\nend");

        docs.method("Task", "task.spawn(callback, ...)", "Schedules a function to run asynchronously.", "a callback and its arguments", "void", "shared:Shared", null);
        docs.method("Task", "task.defer(callback, ...)", "Schedules a function to run on a later scheduler turn.", "a callback and its arguments", "void", "shared:Shared", null);
        docs.method("Task", "task.delay(ticks, callback, ...)", "Schedules a function after a number of game ticks.", "tick count, callback, and callback arguments", "void", "shared:Shared", null);
        docs.method("Task", "task.wait(ticks)", "Yields the current coroutine for the requested number of game ticks.", "an optional tick count", "void", "shared:Shared", null);
        docs.guideStep("Task", "Run work after a delay",
            "Task delays use game ticks; 20 ticks is approximately one second.",
            "task.delay(40, function()\n    print(\"Two seconds passed\")\nend)");

        docs.method("Signal", "Signal.new()", "Creates a new signal.", null, "Signal", "shared:Shared", null);
        docs.method("Signal", "signal:Connect(callback)", "Connects a callback that runs whenever the signal fires.", "a callback", "Connection", "shared:Shared", null);
        docs.method("Signal", "signal:Once(callback)", "Connects a callback that disconnects after its first call.", "a callback", "Connection", "shared:Shared", null);
        docs.method("Signal", "signal:Fire(...)", "Calls connected callbacks and resumes waiters with these arguments.", "values to pass to listeners and waiters", "void", "shared:Shared", null);
        docs.method("Signal", "signal:Wait()", "Yields the current coroutine until the signal fires, then returns the arguments passed to Fire.", null, "any, ...", "shared:Shared", null);
        docs.method("Signal", "connection:Disconnect()", "Stops a connected callback from receiving future fires.", null, "void", "shared:Shared", null);
        docs.guideStep("Signal", "Wait for an event",
            "Wait yields only the calling coroutine; other scripts and callbacks continue running.",
            "task.spawn(function()\n    local player = Players.PlayerAdded:Wait()\n    print(player.Name, \"joined\")\nend)");

        docs.method("Random", "Random.new(seed)", "Creates a random number generator with an optional seed.", "an optional integer seed", "Random", "shared:Shared", null);
        docs.method("Random", "rng:NextNumber(min, max)", "Returns a random number in the requested range; the defaults are 0 and 1.", "optional minimum and maximum numbers", "number", "shared:Shared", null);
        docs.method("Random", "rng:NextInteger(min, max)", "Returns an integer in the inclusive range.", "minimum and maximum integers", "number", "shared:Shared", null);
        docs.guideStep("Random", "Use a seeded generator",
            "A fixed seed makes a sequence repeatable, which is useful for predictable encounter patterns.",
            "local rng = Random.new(42)\nlocal room = rng:NextInteger(1, 5)\nlocal delay = rng:NextNumber(0.5, 2.0)");

        docs.method("DateTime", "DateTime.now()", "Returns the current local date and time.", null, "DateTime", "shared:Shared", null);
        docs.method("DateTime", "DateTime.fromUnixTimestamp(seconds)", "Creates a date and time from Unix seconds.", "seconds since 1970-01-01 UTC", "DateTime", "shared:Shared", null);
        docs.method("DateTime", "DateTime.fromUnixTimestampMillis(milliseconds)", "Creates a date and time from Unix milliseconds.", "milliseconds since 1970-01-01 UTC", "DateTime", "shared:Shared", null);
        docs.property("DateTime", "date.Year", "number", "The year in the system's local time zone.", "read:Read");
        docs.property("DateTime", "date.Month", "number", "The month from 1 to 12.", "read:Read");
        docs.property("DateTime", "date.Day", "number", "The day of the month.", "read:Read");
        docs.property("DateTime", "date.Hour", "number", "The hour from 0 to 23.", "read:Read");
        docs.property("DateTime", "date.Minute", "number", "The minute from 0 to 59.", "read:Read");
        docs.property("DateTime", "date.Second", "number", "The second from 0 to 59.", "read:Read");
        docs.property("DateTime", "date.DayOfWeek", "number", "The day of the week using Java's 1=Monday through 7=Sunday numbering.", "read:Read");
        docs.property("DateTime", "date.DayOfYear", "number", "The day number from 1 to 366.", "read:Read");
        docs.property("DateTime", "date.UnixTimestamp", "number", "Unix time in whole seconds.", "read:Read");
        docs.property("DateTime", "date.UnixTimestampMillis", "number", "Unix time in milliseconds.", "read:Read");
        docs.property("DateTime", "date.ToISO", "string", "The ISO 8601 date and time text.", "read:Read");
        docs.guideStep("DateTime", "Format a timestamp",
            "Use Unix timestamps for stored times and ToISO for readable output.",
            "local created = DateTime.fromUnixTimestamp(1735689600)\nprint(created.ToISO)");

        docs.method("Math", "math.yaw(degrees)", "Converts degrees to radians for yaw values.", "an angle in degrees", "number", "shared:Shared", null);
        docs.method("Math", "math.pitch(degrees)", "Converts degrees to radians for pitch values.", "an angle in degrees", "number", "shared:Shared", null);
        docs.method("Math", "math.clamp(value, min, max)", "Clamps a number between the supplied bounds.", "a value and minimum and maximum bounds", "number", "shared:Shared", null);
        docs.method("Math", "math.round(value)", "Rounds a number to the nearest integer.", "a number", "number", "shared:Shared", null);
        docs.method("Math", "math.sign(value)", "Returns -1, 0, or 1 based on the number's sign.", "a number", "number", "shared:Shared", null);
        docs.method("Math", "math.lerp(start, finish, alpha)", "Interpolates from start to finish by alpha.", "start and finish values and an interpolation value", "number", "shared:Shared", null);
        docs.guideStep("Math", "Clamp a gameplay value",
            "Clamp user-controlled values before applying them to game state.",
            "local volume = math.clamp(requestedVolume, 0, 1)\nlocal damage = math.round(baseDamage * multiplier)");

        docs.method("Table", "table.clone(source)", "Returns a shallow copy of a table.", "the source table", "table", "shared:Shared", null);
        docs.method("Table", "table.clear(target)", "Removes all entries from a table.", "the table to clear", "void", "shared:Shared", null);
        docs.method("Table", "table.find(source, value, startIndex)", "Returns the first matching array index, or nil if absent.", "the array, value, and optional starting index", "number or nil", "shared:Shared", null);
        docs.method("Table", "table.create(count, value)", "Creates an array table with count entries initialized to value.", "a non-negative count and optional initial value", "table", "shared:Shared", null);
        docs.method("Table", "table.pack(...)", "Packs values into an array and records the argument count in n.", "any number of values", "table", "shared:Shared", null);
        docs.method("Table", "table.move(source, first, last, targetIndex, destination)", "Copies an array range into a destination table and returns that table.", "source range, target index, and optional destination table", "table", "shared:Shared", null);
        docs.guideStep("Table", "Copy addon configuration",
            "A shallow clone lets a script customize a table without changing the shared defaults.",
            "local defaults = { radius = 8, damage = 3 }\nlocal settings = table.clone(defaults)\nsettings.damage = 5");

        docs.method("String", "string.split(text, separator)", "Splits text at each separator and returns an array of parts.", "text and a separator string", "table", "shared:Shared", null);
        docs.method("String", "string.trim(text)", "Removes whitespace from both ends of a string.", "text", "string", "shared:Shared", null);
        docs.method("String", "string.startswith(text, prefix)", "Returns whether text starts with the given prefix.", "text and a prefix", "boolean", "shared:Shared", null);
        docs.method("String", "string.endswith(text, suffix)", "Returns whether text ends with the given suffix.", "text and a suffix", "boolean", "shared:Shared", null);
        docs.guideStep("String", "Read a namespaced identifier",
            "Split a registry id at its namespace separator before handling the path.",
            "local parts = string.split(\"horror:rusty_key\", \":\")\nlocal namespace = parts[1]\nlocal itemName = parts[2]");

        docs.method("Globals", "print(...)", "Writes values to the script log.", "any number of values", "void", "shared:Shared", null);
        docs.method("Globals", "warn(...)", "Writes a warning to the script log.", "any number of values", "void", "shared:Shared", null);
        docs.method("Globals", "typeof(value)", "Returns the MLua type name for a value, including registered class names.", "a Lua value", "string", "shared:Shared", null);
        docs.method("Globals", "Require(path)", "Loads a Lua module from the current addon or script roots.", "a module path", "any", "shared:Shared", null);
        docs.method("Globals", "wait(ticks)", "Yields the current coroutine for a number of game ticks.", "an optional tick count", "void", "shared:Shared", null);
        docs.property("Globals", "Minecraft", "table", "The root object for looking up MLua services.", "read:Read");
        docs.property("Globals", "Enum", "table", "The root table for registered Minecraft and MLua enum values.", "read:Read");
        docs.guideStep("Globals", "Get a service",
            "Services are retrieved from the shared Minecraft root and return live Lua instances.",
            "local Players = Minecraft:GetService(\"Players\")\nprint(Players:GetPlayers())");
    }

    @Override
    public void contributeGlobals(Globals globals, MLuaContextView context) {
        MLuaRegistration registration = context.registration();
        globals.set("Object", registration.classTable("Object"));
        globals.set("Instance", registration.classTable("Instance"));
        globals.set("Service", registration.classTable("Service"));
    }

    @Override
    public List<String> dependencies() { return List.of(); }
}
