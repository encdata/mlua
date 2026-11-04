package dev.mlua.core;

import dev.mlua.common.mixin.MLuaClassViewer;
import dev.mlua.common.mixin.MLuaMappingResolvers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Owns MLua's command tree and its Java class inspection output. */
final class MLuaRuntimeCommands {
    private MLuaRuntimeCommands() { }

    static void register(MLuaRuntime runtime, CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mlua")
            .then(Commands.literal("reload")
                .executes(context -> {
                    runtime.executeReload(context.getSource());
                    return 1;
                })
            )
            .then(Commands.literal("inspect")
                .then(Commands.argument("class", StringArgumentType.word())
                    .executes(context -> {
                        inspectClass(context.getSource(), StringArgumentType.getString(context, "class"));
                        return 1;
                    })
                )
            )
            .then(Commands.literal("viewclass")
                .then(Commands.argument("class", StringArgumentType.word())
                    .executes(context -> {
                        inspectClass(context.getSource(), StringArgumentType.getString(context, "class"));
                        return 1;
                    })
                )
            )
        );
    }

    private static void inspectClass(CommandSourceStack source, String canonicalName) {
        try {
            String runtimeName = MLuaMappingResolvers.current().mapClassName(canonicalName);
            Class<?> type = null;
            for (String name : new String[] {runtimeName, canonicalName}) {
                try {
                    type = Class.forName(name, false, Thread.currentThread().getContextClassLoader());
                    break;
                } catch (ClassNotFoundException ignored) {
                }
            }
            if (type == null) {
                source.sendFailure(Component.literal("[MLua] Class not found: " + canonicalName));
                return;
            }
            Class<?> resolvedType = type;
            String[] lines = MLuaClassViewer.renderLua(resolvedType).split("\\R");
            source.sendSuccess(() -> Component.literal("[MLua] Class view: " + canonicalName
                + " (runtime: " + resolvedType.getName() + ")"), false);
            for (String line : lines) {
                if (!line.isBlank()) source.sendSuccess(() -> Component.literal(line), false);
            }
        } catch (Throwable throwable) {
            source.sendFailure(Component.literal("[MLua] Inspect failed: " + throwable.getMessage()));
        }
    }
}
