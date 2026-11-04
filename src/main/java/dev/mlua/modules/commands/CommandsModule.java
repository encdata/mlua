package dev.mlua.modules.commands;

import dev.mlua.modules.commands.service.MLuaCommandsService;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.Side;
import dev.mlua.minecraft.command.MLuaCommandRegistry;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import java.util.List;

public class CommandsModule implements MLuaModule {
    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        MLuaCommandRegistry registry = MLuaCommandRegistry.get();
        if (side == Side.SERVER || side == Side.BOTH) registry.clear();
        registry.clearClientCallbacks(runtime);
    }

    @Override
    public void beforeScriptPhase(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side,
                                  RegistrationPhase phase, Object phaseContext) {
        if (phase != RegistrationPhase.COMMANDS) return;
        if (side == Side.SERVER) MLuaCommandRegistry.get().clear();
        MLuaCommandRegistry.get().clearClientCallbacks(runtime);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterScriptPhase(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side,
                                 RegistrationPhase phase, Object phaseContext) {
        if (side == Side.SERVER && phase == RegistrationPhase.COMMANDS
            && phaseContext instanceof CommandDispatcher<?> dispatcher) {
            MLuaCommandRegistry.get().attach((CommandDispatcher<CommandSourceStack>) dispatcher);
        }
    }

    @Override
    public String id() { return "commands"; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder commands = ServiceEntry.builder("Commands", MLuaCommandsService::new)
            .reflective(MLuaCommandsService.class);
        return List.of(commands.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.signature("Commands", "Commands:Register(name: string, definition: CommandDefinition)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Commands", "Commands:Execute(command: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("CommandContext", "CommandContext:Reply(message: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("CommandContext", "CommandContext:Broadcast(message: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("CommandContext", "CommandContext:Error(message: string)", null));

        docs.register(MLuaDocBuilder.Registration.type("CommandDefinition", null, "Options and callbacks for registering an MLua command."));
        docs.register(MLuaDocBuilder.Registration.field("CommandDefinition", "description", "string", "Command description shown in help output."));
        docs.register(MLuaDocBuilder.Registration.field("CommandDefinition", "aliases?", "string[]", "Alternative command names."));
        docs.register(MLuaDocBuilder.Registration.field("CommandDefinition", "callback", "fun(context: CommandContext, args: string[])", "Runs when the command executes."));
        docs.register(MLuaDocBuilder.Registration.contribution(new MLuaDocBuilder.Contribution(
            "mlua:core-commands-guide", "Commands", "Register a command",
            "Add commands from a .commands.lua script and handle server or client execution.",
            "mlua", 0,
            List.of(
                new MLuaDocBuilder.GuideStep("Commands", "Register during the command phase",
                    "Commands must be declared in a .commands.lua script. The server callback receives the command context and parsed arguments. Set permission to an operator level from 0 to 4; the default is 0.",
                    "local Commands = Minecraft:GetService(\"Commands\")\n\nCommands:Register(\"mlua_echo\", {\n    description = \"Echo a message\",\n    permission = 0,\n    arguments = {\n        { name = \"message\", type = \"greedy_string\" }\n    },\n    run = function(ctx, args)\n        ctx:Reply(\"server echo: \" .. args.message)\n    end\n})"),
                new MLuaDocBuilder.GuideStep("Commands", "Add client-side feedback",
                    "An optional run_client callback executes on the client of the player who invoked the command. Strings, numbers, booleans, positions, vectors, resource locations, and dimensions can cross to the client; player and players arguments are server-only.",
                    "-- Add this callback to the command definition from the previous step.\nrun_client = function(ctx, args)\n    ctx:Reply(\"client echo: \" .. args.message)\nend"),
                new MLuaDocBuilder.GuideStep("Commands", "Choose an argument type",
                    "Use string for one word, greedy_string for the remaining words (it must be the last argument), integer or number for numeric input, boolean for true/false, and player for a player selector.",
                    "arguments = {\n    { name = \"count\", type = \"integer\" },\n    { name = \"target\", type = \"player\" },\n    { name = \"message\", type = \"greedy_string\" } -- greedy_string goes last\n}")),
            List.of())));
    }
}
