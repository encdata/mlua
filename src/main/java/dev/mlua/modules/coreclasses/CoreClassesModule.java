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
            "A live handle for an owned callback registration, including signals, render passes, and addon events.");
        MLuaClassMethods.registerInstanceMethod(registration, "Connection", "Disconnect",
            "Removes this callback registration so it no longer receives events or render frames.");
        MLuaClassMethods.registerInstanceMethod(registration, "Connection", "GetConnected",
            "Returns whether this callback registration is still active.");
        MLuaClassMethods.registerInstanceProperty(registration, "Connection", "Connected", "GetConnected", null,
            "boolean", "shared:Shared", "Whether this callback registration is still active.");
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
        docs.register(MLuaDocBuilder.Registration.clazz("Signal", "MLuaSignal", "A live event source that connects callbacks, fires values, and resumes waiters.", "classes/signal.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.clazz("Connection", "MLuaConnection", "A live handle for an owned callback registration, including signals, render passes, and addon events.", "classes/connection.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.method("Connection", "Connection:Disconnect()", "Removes this callback registration so it no longer receives events or render frames.", null, "void", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Connection", "Connection:GetConnected()", "Returns whether this callback registration is still active.", null, "boolean", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.property("Connection", "Connection.Connected", "boolean", "Whether this callback registration is still active.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Instance", "ClassName", "string",
            "The registered MLua class name for this live instance.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Service", "Name", "string", "This service's registered name.", "read:Read"));
        List<MLuaDocBuilder.GuideStep> gettingStarted = new java.util.ArrayList<>();
        gettingStarted.add(new MLuaDocBuilder.GuideStep("Docs", "Create a server script",
            "After the first launch, MLua copies inactive examples to <game directory>/mlua/example-scripts/. Copy 01-basic-server.server.lua into <game directory>/mlua/scripts/hello.server.lua, creating the scripts folder if needed. The .server.lua suffix runs on the logical server; use this side for changing player or world state.",
            "<game directory>/mlua/scripts/hello.server.lua"));
        List<MLuaExampleScripts.ExampleScript> examples = MLuaExampleScripts.scripts();
        MLuaExampleScripts.ExampleScript firstServerScript = examples.stream()
            .filter(example -> example.name().endsWith(".server.lua"))
            .findFirst().orElse(null);
        if (firstServerScript != null) {
            String explanation = firstServerScript.description().isBlank()
                ? "Put this code in hello.server.lua. PlayerAdded runs the callback when a player joins, so leave and rejoin the world after reloading the script to see the message."
                : firstServerScript.description() + " Put it in hello.server.lua. Then use /mlua reload and leave and rejoin the world to trigger PlayerAdded.";
            gettingStarted.add(new MLuaDocBuilder.GuideStep("Docs", "Greet players when they join", explanation,
                firstServerScript.source()));
        }
        gettingStarted.add(new MLuaDocBuilder.GuideStep("Docs", "Change and verify it",
            "Change the message, save the file, run /mlua reload, then reconnect to the world. The reload command reloads scripts without restarting Minecraft. Scripts that register native blocks, items, or entities use .registry.lua and require a full game restart after edits. To ship a script inside your mod, place it under assets/<modid>/mlua/scripts/.",
            "Players.PlayerAdded:Connect(function(player)\n    player:SendMessage(\"Welcome to my world!\")\nend)"));
        docs.register(MLuaDocBuilder.Registration.contribution(new MLuaDocBuilder.Contribution("mlua:getting-started", "getting-started",
            "Build your first MLua script", "Create a server script, greet joining players, and change the message.",
            "mlua", 0, gettingStarted, List.of())));

        List<MLuaDocBuilder.GuideStep> scriptGuide = new java.util.ArrayList<>();
        scriptGuide.add(new MLuaDocBuilder.GuideStep("Scripts", "Choose when a script runs",
            "Use .registry.lua for native content registration; these scripts run once during startup, and changing registered content requires a game restart. Use .commands.lua for command registration; it may also define run_commands(ctx), which runs in the command phase on server and client. Use .server.lua for server startup and .client.lua for client startup. A plain .lua file can declare run_registry, run_commands, run_server, run_client, run_server_stopping, run_world_load, or run_world_unload entrypoints. World hooks receive event.Level, event.Dimension, and event.Side; .server.lua receives server worlds and .client.lua receives client worlds. run_world_load and run_world_unload fire for server world lifecycle and client world transitions; client transition callbacks run after client startup scripts. Already loaded worlds receive run_world_load when their runtime starts or reloads. run_server_stopping(ctx) runs after server world-unload hooks and before server runtime teardown.",
            "-- save as lifecycle.lua\nfunction run_world_load(ctx, event)\n    print(\"Loaded world:\", event.Dimension)\nend\n\nfunction run_world_unload(ctx, event)\n    print(\"Unloading world:\", event.Dimension)\nend\n\nfunction run_server_stopping(ctx)\n    print(\"MLua server scripts are stopping\")\nend"));
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
        docs.register(MLuaDocBuilder.Registration.contribution(new MLuaDocBuilder.Contribution("mlua:core-script-guide", "Scripts",
            "Script files and starter templates", "Choose a script folder and lifecycle, then start from a bundled example.",
            "mlua", 0, scriptGuide, List.of(), scriptTemplates)));

        docs.register(MLuaDocBuilder.Registration.contribution(MLuaDocBuilder.Contribution.builder("mlua", "mlua:addon-global-guide")
            .section("Java addon integration")
            .title("Expose a Java addon API to Lua")
            .description("Use a global for a small namespace of constants or helper functions. Use a service when the API needs live state, per-runtime context, or a larger method surface.")
            .order(20)
            .step("Java mod initialization", "Build a Lua table",
                "Create the table during your loader's normal mod initialization callback, before MLua creates script contexts. Keep the global name unique to your addon. This example exposes one pure helper and a version value.",
                "import dev.mlua.api.MLuaApi;\nimport org.luaj.vm2.LuaTable;\nimport org.luaj.vm2.LuaValue;\nimport org.luaj.vm2.lib.TwoArgFunction;\n\nprivate void registerLuaApi() {\n    LuaTable api = new LuaTable();\n    api.set(\"VERSION\", LuaValue.valueOf(\"1.0\"));\n    api.set(\"ClampFear\", new TwoArgFunction() {\n        @Override\n        public LuaValue call(LuaValue value, LuaValue maximum) {\n            double limit = maximum.checknumber().todouble();\n            return LuaValue.valueOf(Math.max(0, Math.min(value.checknumber().todouble(), limit)));\n        }\n    });\n\n    MLuaApi.registerGlobal(\"myaddon\", \"MyAddon\", api);\n}")
            .step("Java mod initialization", "Register and retain the owner handle",
                "Use the scoped overload when your integration can be unloaded independently of MLua. Keep its handle and close it from that integration's real teardown callback; mod loaders do not share one unload method. Closing updates Lua bindings the next time a script context is activated. The unscoped overload from the previous step is suitable for APIs that live as long as the mod.",
                "import dev.mlua.api.MLuaApi;\nimport dev.mlua.common.enums.Side;\nimport org.luaj.vm2.LuaTable;\n\nprivate MLuaApi.Registration luaApi;\n\nprivate void registerClientLuaApi(LuaTable api) {\n    luaApi = MLuaApi.registerGlobalScoped(\"myaddon\", \"MyAddon\", api, Side.CLIENT);\n}\n\n// In your loader's actual teardown callback:\n// if (luaApi != null) luaApi.close();")
            .step("Lua script", "Call the registered helper",
                "The global is available to scripts on the selected side. For example, pass the result into your own encounter logic rather than putting mutable world state in a shared Java global.",
                "local fear = MyAddon.ClampFear(125, 100)\nprint(MyAddon.VERSION, fear)\n\nif fear >= 80 then\n    print(\"The encounter is at maximum tension\")\nend")
            .step("Choose the API shape", "Move stateful APIs into a service",
                "A global table is a good fit for constants and small pure helpers. Register a named service when methods need MLuaService.Context, live game state, documented service methods, or a separate instance for each script runtime.",
                "local Encounter = Minecraft:GetService(\"Encounter\")\nEncounter:Start(player)\nprint(Encounter:GetFear(player))")
            .build()));

        docs.register(MLuaDocBuilder.Registration.contribution(MLuaDocBuilder.Contribution.builder("mlua", "mlua:addon-docs-guide")
            .section("Lua addon guides")
            .title("Add a guide page from a Lua addon")
            .description("A registry script can contribute setup steps, examples, and copyable templates to the in-game documentation. Keep the guide beside the API it teaches and explain when each script runs.")
            .order(30)
            .step("Registry script", "Register a namespaced guide",
                "Use Docs:Register from a .registry.lua file so the guide is added during startup. An id without a namespace is prefixed with the registering addon's id; explicit ids must use that same namespace.",
                "Docs:Register({\n    id = \"fear-system\",\n    section = \"Horror addon\",\n    title = \"Add a fear meter\",\n    description = \"Track fear on the server and show changes to each player.\",\n    order = 10,\n    steps = {\n        {\n            title = \"Create the server script\",\n            description = \"Keep player state and world changes on the server.\",\n            example = 'local Players = Minecraft:GetService(\"Players\")\\nPlayers.PlayerAdded:Connect(function(player)\\n    player:SendMessage(\"The night is watching.\")\\nend)'\n        }\n    }\n})")
            .step("Documentation content", "Give each step a runnable example",
                "Every step needs a title, a short explanation, and an example. Add examples for complete scripts or templates for starter files; avoid repeating the registration snippet as if it were the finished feature.",
                "examples = {\n    {\n        title = \"Show a low fear warning\",\n        description = \"Run this in a server script when the fear value changes.\",\n        code = 'if fear >= 60 then\\n    player:SendMessage(\"You hear something nearby.\")\\nend'\n    }\n},\ntemplates = {\n    {\n        title = \"Fear meter server script\",\n        description = \"Starter file for server-owned fear state.\",\n        filename = \"fear.server.lua\",\n        code = 'local Players = Minecraft:GetService(\"Players\")\\n\\nPlayers.PlayerAdded:Connect(function(player)\\n    player:SetData({ fear = 0 })\\nend)'\n    }\n}")
            .step("Guide maintenance", "Remove a page when the addon no longer owns it",
                "Docs:Register returns the owner-qualified id. A contribution must contain at least one step, example, or template. Save the returned id when later registry code needs to remove the page; Docs:Unregister accepts that id or the addon's short id.",
                "local guideId = Docs:Register({\n    id = \"fear-system\",\n    section = \"Horror addon\",\n    title = \"Add a fear meter\",\n    description = \"Track fear and react to changes.\",\n    steps = {\n        {\n            title = \"Start with server state\",\n            description = \"Keep gameplay state on the server.\",\n            example = 'local Players = Minecraft:GetService(\"Players\")\\nPlayers.PlayerAdded:Connect(function(player)\\n    player:SendMessage(\"The night is watching.\")\\nend)'\n        }\n    }\n})\n\n-- Later, if this addon removes the guide:\n-- Docs:Unregister(guideId)")
            .build()));

        docs.register(MLuaDocBuilder.Registration.dataType("Vector3", "A three-dimensional position or direction with X, Y, and Z components.", "data-types/vector3.html"));
        docs.register(MLuaDocBuilder.Registration.dataType("CFrame", "A position and orientation transform with Position and LookVector values.", "data-types/cframe.html"));
        docs.register(MLuaDocBuilder.Registration.dataType("Vector2", "A two-dimensional value with X and Y components.", "data-types/vector2.html"));
        docs.register(MLuaDocBuilder.Registration.dataType("Color3", "An RGB color value used by UI and rendering APIs.", "data-types/color3.html"));
        docs.register(MLuaDocBuilder.Registration.dataType("UDim", "A UI dimension made from a scale and pixel offset.", "data-types/udim.html"));
        docs.register(MLuaDocBuilder.Registration.dataType("UDim2", "A two-axis UI size or position made from scale and pixel offsets.", "data-types/udim2.html"));
        docs.register(MLuaDocBuilder.Registration.library("Globals", "Built-in functions and values available to MLua scripts.", "libraries/globals.html"));
        docs.register(MLuaDocBuilder.Registration.library("Math", "Standard Lua math functions and MLua's Luau-compatible additions.", "libraries/math.html"));
        docs.register(MLuaDocBuilder.Registration.library("Table", "Standard table operations and MLua table helpers.", "libraries/table.html"));
        docs.register(MLuaDocBuilder.Registration.library("String", "Standard string operations available to MLua scripts.", "libraries/string.html"));
        docs.register(MLuaDocBuilder.Registration.library("Random", "Random number generation helpers.", "libraries/random.html"));
        docs.register(MLuaDocBuilder.Registration.library("DateTime", "Date and time construction, formatting, and arithmetic.", "libraries/datetime.html"));
        docs.register(MLuaDocBuilder.Registration.library("Task", "Scheduling, delays, and deferred Lua work.", "libraries/task.html"));
        docs.register(MLuaDocBuilder.Registration.library("Signal", "Connectable events used by services, classes, and UI instances.", "libraries/signal.html"));
        docs.register(MLuaDocBuilder.Registration.library("Number", "The Lua number type and its conversion helpers.", "libraries/number.html"));
        docs.register(MLuaDocBuilder.Registration.library("Boolean", "The Lua boolean type and its conversion helpers.", "libraries/boolean.html"));
        docs.register(MLuaDocBuilder.Registration.library("Void", "The absence of a returned value.", "libraries/void.html"));
        docs.register(MLuaDocBuilder.Registration.library("Any", "A value that can have any Lua type.", "libraries/any.html"));
        docs.register(MLuaDocBuilder.Registration.library("Nil", "The Lua nil value, representing an absent value.", "libraries/nil.html"));
        docs.register(MLuaDocBuilder.Registration.library("EnumValue", "A selected value from an MLua enum table.", "libraries/enum-value.html"));

        docs.register(MLuaDocBuilder.Registration.property("Vector3", "Vector3.X", "number", "The X component.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Vector3", "Vector3.Y", "number", "The Y component.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Vector3", "Vector3.Z", "number", "The Z component.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Vector3", "Vector3.Magnitude", "number", "The length of the vector.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Vector3", "Vector3.Unit", "Vector3", "A normalized copy of the vector.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3.new(x, y, z)", "Creates a new Vector3.", "x, y, and z coordinates.", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3.zero", "Returns Vector3.new(0, 0, 0).", null, "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3.one", "Returns Vector3.new(1, 1, 1).", null, "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3 + other", "Adds two vectors.", "another Vector3", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3 - other", "Subtracts another vector.", "another Vector3", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3 * scalar", "Multiplies each component by a scalar.", "a number", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3 / scalar", "Divides each component by a scalar.", "a non-zero number", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3:Dot(other)", "Returns the dot product.", "another Vector3", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3:Angle(other)", "Returns the unsigned angle to another non-zero vector in radians.", "another non-zero Vector3", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3:Cross(other)", "Returns the cross product.", "another Vector3", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3:Lerp(other, alpha)", "Interpolates between this vector and another.", "another Vector3 and an interpolation value", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3:Distance(other)", "Returns the distance to another vector.", "another Vector3", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3:ProjectOn(axis)", "Returns the projection onto an axis; a zero axis returns Vector3.zero.", "another Vector3", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3:Reflect(normal)", "Reflects this vector across a surface normal. The normal should be unit length.", "a unit Vector3 normal", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3:Min(other)", "Returns the component-wise minimum.", "another Vector3", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3:Max(other)", "Returns the component-wise maximum.", "another Vector3", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3:Floor()", "Returns a copy with each component rounded down.", null, "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3:Ceil()", "Returns a copy with each component rounded up.", null, "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector3", "Vector3:Abs()", "Returns a copy with the absolute value of each component.", null, "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("Vector3", "Move by a direction",
            "Add a direction vector to a position to calculate a destination.",
            "local direction = Vector3.new(1, 0, 0)\nlocal destination = player.Position + direction * 4\nplayer:Teleport(destination)"));

        docs.register(MLuaDocBuilder.Registration.property("Vector2", "Vector2.X", "number", "The X component.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Vector2", "Vector2.Y", "number", "The Y component.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Vector2", "Vector2.Magnitude", "number", "The length of the vector.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Vector2", "Vector2.Unit", "Vector2", "A normalized copy of the vector.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.method("Vector2", "Vector2.new(x, y)", "Creates a new Vector2.", "x and y coordinates.", "Vector2", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector2", "Vector2 + other", "Adds two vectors.", "another Vector2", "Vector2", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector2", "Vector2 - other", "Subtracts another vector.", "another Vector2", "Vector2", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector2", "Vector2 * scalar", "Multiplies each component by a scalar.", "a number", "Vector2", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector2", "Vector2:Dot(other)", "Returns the dot product.", "another Vector2", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector2", "Vector2:Angle(other)", "Returns the signed angle to another non-zero vector in radians.", "another non-zero Vector2", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector2", "Vector2:Cross(other)", "Returns the signed 2D cross product, useful for determining turn direction.", "another Vector2", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector2", "Vector2:Min(other)", "Returns the component-wise minimum.", "another Vector2", "Vector2", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector2", "Vector2:Max(other)", "Returns the component-wise maximum.", "another Vector2", "Vector2", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector2", "Vector2:Lerp(other, alpha)", "Interpolates between this vector and another.", "another Vector2 and an interpolation value", "Vector2", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("Vector2", "Measure a 2D offset",
            "Use Magnitude to turn an X/Y offset into a distance.",
            "local offset = Vector2.new(mouseX - centerX, mouseY - centerY)\nif offset.Magnitude < 24 then\n    print(\"Pointer is near the center\")\nend"));

        docs.register(MLuaDocBuilder.Registration.property("Color3", "Color3.R", "number", "The red component, from 0 to 1.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Color3", "Color3.G", "number", "The green component, from 0 to 1.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Color3", "Color3.B", "number", "The blue component, from 0 to 1.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.method("Color3", "Color3.new(r, g, b)", "Creates a color from normalized red, green, and blue channels. Values are clamped to 0 through 1.", "three normalized channel values", "Color3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Color3", "Color3.fromRGB(r, g, b)", "Creates a color from RGB values in the 0 to 255 range. It also accepts a Vector3 containing those channel values.", "three byte-range channel values or one Vector3", "Color3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Color3", "Color3.fromVector3(rgb)", "Creates a color from normalized RGB values stored in a Vector3.", "a Vector3 with channels from 0 to 1", "Color3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Color3", "Color3.fromHSV(h, s, v)", "Creates a color from hue, saturation, and value components in the 0 to 1 range.", "hue, saturation, and value", "Color3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Color3", "Color3.fromHex(hex)", "Creates a color from a hexadecimal string such as #ff0000.", "a hex color string", "Color3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Color3", "Color3:ToHex()", "Returns this color as a hexadecimal string.", null, "string", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Color3", "Color3:ToHSV()", "Returns the hue, saturation, and value components.", null, "number, number, number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Color3", "Color3:ToVector3()", "Returns the normalized RGB channels as a Vector3.", null, "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Color3", "Color3:Lerp(other, alpha)", "Interpolates between this color and another.", "another Color3 and an interpolation value", "Color3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Color3", "Color3 + other / Color3 - other", "Adds or subtracts matching color channels. Results stay in the 0 to 1 range.", "another Color3", "Color3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Color3", "Color3 * number", "Scales each color channel by a number. The number can appear on either side.", "a number", "Color3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Color3", "Color3 / number", "Divides each color channel by a non-zero number.", "a non-zero number", "Color3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Color3", "Color3 * other", "Multiplies matching color channels.", "another Color3", "Color3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Color3", "Color3 / other", "Divides matching color channels. Division by a zero channel raises an error.", "another Color3 with non-zero channels", "Color3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("Color3", "Set a UI color",
            "Create a Color3 value and assign it to a UI object's color property.",
            "local panel = UIObject.new(\"Frame\", \"WarningPanel\")\npanel.BackgroundColor3 = Color3.fromRGB(150, 24, 32)"));

        docs.register(MLuaDocBuilder.Registration.method("Vector helpers", "vectors.vec(x, y[, z[, w]])", "Creates a Vector2, Vector3, or Vec4 based on the number of supplied components.", "two, three, or four numbers", "Vector2, Vector3, or Vec4", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector helpers", "vectors.rgbToInt(rgb) / vectors.rgbToInt(r, g, b)", "Packs normalized RGB channels into a 24-bit integer.", "a normalized Vector3 or three normalized channels", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector helpers", "vectors.intToRGB(color)", "Unpacks a 24-bit RGB integer into normalized Vector3 channels.", "a packed RGB integer", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector helpers", "vectors.hexToRGB(hex)", "Parses a three- or six-digit hex string into normalized RGB channels.", "a hex color string, with an optional # prefix", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector helpers", "vectors.hsvToRGB(h, s, v) / vectors.hsvToRGB(hsv)", "Converts normalized HSV channels into normalized RGB channels.", "three numbers or one Vector3", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector helpers", "vectors.rgbToHSV(r, g, b) / vectors.rgbToHSV(rgb)", "Converts normalized RGB channels into normalized HSV channels.", "three numbers or one Vector3", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Vector helpers", "vectors.rgbToHex(rgb)", "Converts normalized RGB channels into a #rrggbb string.", "a normalized Vector3 or three normalized channels", "string", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("Vector helpers", "Convert color data for a Minecraft API",
            "Use Color3 for APIs that expect a color object. Use the vector helpers when you need packed RGB data or normalized channels.",
            "local tint = Color3.fromHex(\"#38a8e8\")\nlocal packed = vectors.rgbToInt(tint:ToVector3())\nlocal channels = vectors.intToRGB(packed)"));

        docs.register(MLuaDocBuilder.Registration.property("CFrame", "CFrame.Position", "Vector3", "The transform's position.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("CFrame", "CFrame.X / CFrame.Y / CFrame.Z", "number", "The position components.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("CFrame", "CFrame.LookVector", "Vector3", "The forward direction.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("CFrame", "CFrame.RightVector", "Vector3", "The right basis vector.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("CFrame", "CFrame.UpVector", "Vector3", "The up basis vector.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("CFrame", "CFrame.Rotation", "Vector3", "The rotation in radians as XYZ Euler angles.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("CFrame", "CFrame.Orientation", "Vector3", "The rotation in degrees as XYZ Euler angles.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("CFrame", "CFrame.Yaw / CFrame.Pitch / CFrame.Roll", "number", "Minecraft rotation components in degrees.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame.new(x, y, z)", "Creates a transform at a position with identity rotation.", "position coordinates", "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame.new(position)", "Creates a transform at a Vector3 position.", "a Vector3 position", "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame.new(position, target)", "Creates a transform facing from position toward a target.", "position and target Vector3 values", "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame.Angles(rx, ry, rz)", "Creates a rotation transform. Angles are radians.", "XYZ rotation angles in radians", "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame.fromEulerAnglesXYZ(rx, ry, rz)", "Creates a rotation transform from XYZ Euler angles in radians.", "XYZ rotation angles in radians", "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame.fromOrientation(rx, ry, rz)", "Alias for CFrame.Angles using XYZ Euler angles in radians.", "XYZ rotation angles in radians", "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame.fromYawPitch(yaw, pitch)", "Creates a rotation from Minecraft yaw and pitch in degrees.", "yaw and pitch in degrees", "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame.fromYawPitchRadians(yaw, pitch)", "Creates a rotation from yaw and pitch in radians.", "yaw and pitch in radians", "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame.lookAt(position, target)", "Creates a transform facing from position toward a target.", "position and target Vector3 values", "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame.lookAlong(position, direction)", "Creates a transform facing along a direction vector.", "a position and direction Vector3", "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame.identity", "Returns the identity transform.", null, "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame:Inverse()", "Returns the inverse transform.", null, "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame:Lerp(other, alpha)", "Interpolates position and forward direction.", "another CFrame and an interpolation value", "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame:ToWorldSpace(other)", "Composes another CFrame into world space.", "another CFrame", "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame:ToObjectSpace(other)", "Converts another CFrame into this object's local space.", "another CFrame", "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame:PointToWorldSpace(point)", "Transforms a local point into world space.", "a local Vector3 point", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame:PointToObjectSpace(point)", "Transforms a world point into local space.", "a world Vector3 point", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame:VectorToWorldSpace(vector)", "Transforms a local direction into world space without applying position.", "a local Vector3 direction", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame:VectorToObjectSpace(vector)", "Transforms a world direction into local space without applying position.", "a world Vector3 direction", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame:GetComponents()", "Returns position and rotation matrix components.", null, "12 numbers", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame1 * CFrame2", "Composes two transforms.", "two CFrame values", "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("CFrame", "CFrame + Vector3", "Offsets the transform position.", "a Vector3 offset", "CFrame", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("CFrame", "Rotate at a world position",
            "Multiply a position transform by a rotation to keep the rotation centered at that position. Angles use radians.",
            "local position = CFrame.new(player:GetEyeCFrame().Position)\nlocal rotation = CFrame.Angles(0, math.rad(90), 0)\nlocal facing = position * rotation"));

        docs.register(MLuaDocBuilder.Registration.property("UDim", "UDim.Scale", "finite number", "The size or position component relative to the parent.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("UDim", "UDim.Offset", "whole number", "The size or position component in integer pixels.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.method("UDim", "UDim.new(scale: finite number, offset: whole number)", "Creates a dimension from a parent-relative scale and integer pixel offset.", "a finite scale number and whole-number pixel offset", "UDim", "shared:Shared", null));

        docs.register(MLuaDocBuilder.Registration.property("UDim2", "UDim2.X", "UDim", "The horizontal dimension.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("UDim2", "UDim2.Y", "UDim", "The vertical dimension.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.method("UDim2", "UDim2.new(xScale: finite number, xOffset: whole number, yScale: finite number, yOffset: whole number)", "Creates a two-axis dimension from finite parent-relative scales and integer pixel offsets. It also accepts two UDim values.", "X scale and offset followed by Y scale and offset, or X and Y UDim values", "UDim2", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("UDim2", "UDim2.fromScale(xScale: finite number, yScale: finite number)", "Creates a size or position from finite parent-relative scale values.", "X and Y finite scale values", "UDim2", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("UDim2", "UDim2.fromOffset(xPixels: whole number, yPixels: whole number)", "Creates a size or position from integer pixel offsets.", "X and Y whole-number pixel values", "UDim2", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("UDim2", "Size a panel proportionally",
            "Use scale for a responsive fraction of the parent and offset for a fixed pixel margin.",
            "local panel = UIObject.new(\"Frame\", \"Panel\")\npanel.Size = UDim2.new(1, -32, 0, 120)\npanel.Position = UDim2.new(0, 16, 0, 16)"));

        docs.register(MLuaDocBuilder.Registration.guideStep("Number", "Use numbers for game state",
            "Numbers represent health, positions, counters, and time values. Lua numbers are double precision.",
            "local health = 20\nlocal distance = 4.5\nif health > 0 then\n    print(\"Alive at distance\", distance)\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Boolean", "Branch on a boolean value",
            "Only false and nil are falsey in Lua; values such as zero and empty strings are truthy.",
            "local isAlive = true\nif isAlive then\n    print(\"Continue the encounter\")\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Void", "Call a method with no result",
            "A method documented as void performs an action and returns no useful value.",
            "player:SendMessage(\"The door opens\")"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Any", "Handle a value with a dynamic type",
            "An any value can hold a Lua value of any type. Check it before using type-specific operations.",
            "local result = Require(\"encounter/state\")\nif type(result) == \"table\" then\n    print(result.phase)\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Nil", "Check whether a lookup found a value",
            "Nil represents a missing or absent value and can be checked before using the result.",
            "local target = Players:GetPlayerByName(\"unknown\")\nif target == nil then\n    print(\"No matching player\")\nend"));
        docs.register(MLuaDocBuilder.Registration.property("EnumValue", "value.Name", "string", "The name of the selected enum value.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.guideStep("EnumValue", "Compare named enum values",
            "Enum values are first-class values. Store them, compare them, and pass them to API methods.",
            "local mode = Enum.GameMode.Adventure\nif player.GameMode == mode then\n    player.GameMode = Enum.GameMode.Survival\nend"));

        docs.register(MLuaDocBuilder.Registration.method("Task", "task.spawn(callback, ...)", "Schedules a function to run asynchronously.", "a callback and its arguments", "void", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Task", "task.defer(callback, ...)", "Schedules a function to run on a later scheduler turn.", "a callback and its arguments", "void", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Task", "task.delay(ticks, callback, ...)", "Schedules a function after a number of game ticks.", "tick count, callback, and callback arguments", "void", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Task", "task.wait(ticks)", "Yields the current coroutine for the requested number of game ticks.", "an optional tick count", "void", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("Task", "Run work after a delay",
            "Task delays use game ticks; 20 ticks is approximately one second.",
            "task.delay(40, function()\n    print(\"Two seconds passed\")\nend)"));

        docs.register(MLuaDocBuilder.Registration.method("Signal", "Signal.new()", "Creates a new signal.", null, "Signal", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Signal", "signal:Connect(callback)", "Connects a callback that runs whenever the signal fires. Each signal allows up to " + dev.mlua.core.signal.MLuaSignal.MAX_LISTENERS_PER_OWNER + " listeners per addon and " + dev.mlua.core.signal.MLuaSignal.MAX_LISTENERS_PER_SIGNAL + " listeners total.", "a callback function", "Connection", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Signal", "signal:Once(callback)", "Connects a callback that disconnects after its first call. It uses the same per-signal listener limits as Connect.", "a callback function", "Connection", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Signal", "signal:Fire(...)", "Calls connected callbacks and resumes waiters with these arguments.", "values to pass to listeners and waiters", "void", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Signal", "signal:Wait()", "Yields the current coroutine until the signal fires, then returns the arguments passed to Fire. Each signal allows up to " + dev.mlua.core.signal.MLuaSignal.MAX_WAITERS_PER_OWNER + " waiters per addon and " + dev.mlua.core.signal.MLuaSignal.MAX_WAITERS_PER_SIGNAL + " waiters total.", null, "any, ...", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Signal", "connection:Disconnect()", "Stops a connected callback from receiving future fires.", null, "void", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("Signal", "Wait for an event",
            "Wait yields only the calling coroutine; other scripts and callbacks continue running.",
            "task.spawn(function()\n    local player = Players.PlayerAdded:Wait()\n    print(player.Name, \"joined\")\nend)"));

        docs.register(MLuaDocBuilder.Registration.method("Random", "Random.new(seed)", "Creates a random number generator with an optional seed.", "an optional integer seed", "Random", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Random", "rng:NextNumber(min, max)", "Returns a random number in the requested range; the defaults are 0 and 1.", "optional minimum and maximum numbers", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Random", "rng:NextInteger(min, max)", "Returns an integer in the inclusive range.", "minimum and maximum integers", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("Random", "Use a seeded generator",
            "A fixed seed makes a sequence repeatable, which is useful for predictable encounter patterns.",
            "local rng = Random.new(42)\nlocal room = rng:NextInteger(1, 5)\nlocal delay = rng:NextNumber(0.5, 2.0)"));

        docs.register(MLuaDocBuilder.Registration.method("DateTime", "DateTime.now()", "Returns the current local date and time.", null, "DateTime", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("DateTime", "DateTime.fromUnixTimestamp(seconds)", "Creates a date and time from Unix seconds.", "seconds since 1970-01-01 UTC", "DateTime", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("DateTime", "DateTime.fromUnixTimestampMillis(milliseconds)", "Creates a date and time from Unix milliseconds.", "milliseconds since 1970-01-01 UTC", "DateTime", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.property("DateTime", "date.Year", "number", "The year in the system's local time zone.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("DateTime", "date.Month", "number", "The month from 1 to 12.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("DateTime", "date.Day", "number", "The day of the month.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("DateTime", "date.Hour", "number", "The hour from 0 to 23.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("DateTime", "date.Minute", "number", "The minute from 0 to 59.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("DateTime", "date.Second", "number", "The second from 0 to 59.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("DateTime", "date.DayOfWeek", "number", "The day of the week using Java's 1=Monday through 7=Sunday numbering.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("DateTime", "date.DayOfYear", "number", "The day number from 1 to 366.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("DateTime", "date.UnixTimestamp", "number", "Unix time in whole seconds.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("DateTime", "date.UnixTimestampMillis", "number", "Unix time in milliseconds.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("DateTime", "date.ToISO", "string", "The ISO 8601 date and time text.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.guideStep("DateTime", "Format a timestamp",
            "Use Unix timestamps for stored times and ToISO for readable output.",
            "local created = DateTime.fromUnixTimestamp(1735689600)\nprint(created.ToISO)"));

        docs.register(MLuaDocBuilder.Registration.method("Math", "math.yaw(degrees)", "Converts degrees to radians for yaw values.", "an angle in degrees", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Math", "math.pitch(degrees)", "Converts degrees to radians for pitch values.", "an angle in degrees", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Math", "math.clamp(value, min, max)", "Clamps a number between the supplied bounds.", "a value and minimum and maximum bounds", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Math", "math.round(value)", "Rounds a number to the nearest integer.", "a number", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Math", "math.sign(value)", "Returns -1, 0, or 1 based on the number's sign.", "a number", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Math", "math.lerp(start, finish, alpha)", "Interpolates numbers, vectors, colors, matrices, and CFrames. Vector and color interpolation is component-wise; CFrame interpolation blends its transform.", "matching endpoint types and an interpolation value", "the endpoint type", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Math", "math.inverseLerp(start, finish, value)", "Returns the relative position of value between two endpoints; values outside the interval remain outside 0 to 1.", "two different endpoints and a value", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Math", "math.smoothstep(edge0, edge1, value)", "Maps a value between two edges to a clamped smooth curve from 0 to 1.", "two different edges and a value", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Math", "math.wrap(value, min, max)", "Wraps a value into the half-open range from min up to max.", "a value and a finite increasing range", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Math", "math.noise(x, y[, z[, w[, v]]])", "Returns deterministic smooth noise from one through five coordinate dimensions, bounded from -1 to 1.", "one to five finite coordinates", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("Math", "Clamp a gameplay value",
            "Clamp user-controlled values before applying them to game state.",
            "local volume = math.clamp(requestedVolume, 0, 1)\nlocal damage = math.round(baseDamage * multiplier)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Math", "Sample terrain noise",
            "Use the same coordinates to get the same smooth noise value. Scale coordinates to change feature size; add another coordinate to animate through a changing noise field.",
            "local scale = 48\nlocal height = 64 + math.noise(x / scale, z / scale) * 18\nlocal cave = math.noise(x / 24, y / 24, z / 24)\nlocal animated = math.noise(x / 32, z / 32, gameTime / 80)"));

        docs.register(MLuaDocBuilder.Registration.method("Table", "table.clone(source)", "Returns a shallow copy of a table.", "the source table", "table", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Table", "table.clear(target)", "Removes all entries from a table.", "the table to clear", "void", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Table", "table.find(source, value, startIndex)", "Returns the first matching array index, or nil if absent.", "the array, value, and optional starting index", "number or nil", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Table", "table.create(count, value)", "Creates an array table with count entries initialized to value.", "a non-negative count and optional initial value", "table", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Table", "table.pack(...)", "Packs values into an array and records the argument count in n.", "any number of values", "table", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Table", "table.move(source, first, last, targetIndex, destination)", "Copies an array range into a destination table and returns that table.", "source range, target index, and optional destination table", "table", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("Table", "Copy addon configuration",
            "A shallow clone lets a script customize a table without changing the shared defaults.",
            "local defaults = { radius = 8, damage = 3 }\nlocal settings = table.clone(defaults)\nsettings.damage = 5"));

        docs.register(MLuaDocBuilder.Registration.method("String", "string.split(text, separator)", "Splits text at each separator and returns an array of parts.", "text and a separator string", "table", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("String", "string.trim(text)", "Removes whitespace from both ends of a string.", "text", "string", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("String", "string.startswith(text, prefix)", "Returns whether text starts with the given prefix.", "text and a prefix", "boolean", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("String", "string.endswith(text, suffix)", "Returns whether text ends with the given suffix.", "text and a suffix", "boolean", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("String", "Read a namespaced identifier",
            "Split a registry id at its namespace separator before handling the path.",
            "local parts = string.split(\"horror:rusty_key\", \":\")\nlocal namespace = parts[1]\nlocal itemName = parts[2]"));

        docs.register(MLuaDocBuilder.Registration.method("Globals", "print(...)", "Writes values to the script log.", "any number of values", "void", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Globals", "warn(...)", "Writes a warning to the script log.", "any number of values", "void", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Globals", "typeof(value)", "Returns the MLua type name for a value, including registered class names.", "a Lua value", "string", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Globals", "Require(path)", "Loads a Lua module from the current addon or script roots.", "a module path", "any", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Globals", "wait(ticks)", "Yields the current coroutine for a number of game ticks.", "an optional tick count", "void", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.property("Globals", "Minecraft", "table", "The root object for looking up MLua services.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Globals", "Enum", "table", "The root table for registered Minecraft and MLua enum values.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Globals", "Get a service",
            "Services are retrieved from the shared Minecraft root and return live Lua instances.",
            "local Players = Minecraft:GetService(\"Players\")\nprint(Players:GetPlayers())"));
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
