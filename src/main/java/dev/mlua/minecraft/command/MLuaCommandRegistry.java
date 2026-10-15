package dev.mlua.minecraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.mlua.common.enums.CommandArgumentType;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.network.MLuaNetworkManager;
import dev.mlua.minecraft.network.MLuaNetworkSerializer;
import dev.mlua.minecraft.network.MLuaNetworkValue;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.core.MLuaLogger;
import dev.mlua.math.MLuaVector;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

public final class MLuaCommandRegistry {
   private static final MLuaCommandRegistry INSTANCE = new MLuaCommandRegistry();
   private static final Pattern COMMAND_NAME = Pattern.compile("[a-z0-9_][a-z0-9_-]{0,63}");
   private static final Pattern ARGUMENT_NAME = Pattern.compile("[A-Za-z0-9_]{1,64}");
   private final Map<String, MLuaCommandDefinition> commands = new LinkedHashMap();
   private final Map<MLuaRegistration, Map<String, LuaValue>> clientCallbacks = new java.util.IdentityHashMap<>();

   public static MLuaCommandRegistry get() {
      return INSTANCE;
   }

   public void clear() {
      this.commands.clear();
   }

   public void clearClientCallbacks() {
      synchronized (this.clientCallbacks) { this.clientCallbacks.clear(); }
   }

   public void clearClientCallbacks(MLuaRegistration registration) {
      synchronized (this.clientCallbacks) { this.clientCallbacks.remove(registration); }
   }

   public void register(MLuaCommandDefinition definition) {
      validateCommandName(definition.name());
      if (this.commands.containsKey(definition.name())) {
         throw new LuaError("[MLua] Duplicate command registration: " + definition.name());
      } else {
         this.commands.put(definition.name(), definition);
      }
   }

   public void registerClientCallback(String name, LuaValue callback) {
      this.registerClientCallback(name, callback, null);
   }

   public void registerClientCallback(String name, LuaValue callback, MLuaRegistration registration) {
      validateCommandName(name);
      if (callback.isfunction()) {
         synchronized (this.clientCallbacks) {
            this.clientCallbacks.computeIfAbsent(registration, ignored -> new LinkedHashMap<>()).put(name, callback);
         }
      }
   }

   public List<String> names() {
      return List.copyOf(this.commands.keySet());
   }

   public void attach(CommandDispatcher<CommandSourceStack> dispatcher) {
      for (MLuaCommandDefinition definition : this.commands.values()) {
         dispatcher.register(this.build(definition));
         for (String alias : definition.aliases()) {
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(alias).requires((source) -> {
               return source.hasPermission(definition.permission());
            })).redirect(dispatcher.getRoot().getChild(definition.name())));
         }
      }
   }

   private LiteralArgumentBuilder<CommandSourceStack> build(MLuaCommandDefinition definition) {
      LiteralArgumentBuilder<CommandSourceStack> root = (LiteralArgumentBuilder)Commands.literal(definition.name()).requires((source) -> {
         return source.hasPermission(definition.permission());
      });
      if (definition.arguments().isEmpty()) {
         root.executes((context) -> {
            return this.execute(definition, context);
         });
         return root;
      } else {
         List<RequiredArgumentBuilder<CommandSourceStack, ?>> builders = new ArrayList();
         for (MLuaCommandArgument argument : definition.arguments()) {
            builders.add(Commands.argument(argument.name(), this.brigadierType(argument.type())));
         }

         ((RequiredArgumentBuilder)builders.get(builders.size() - 1)).executes((context) -> {
            return this.execute(definition, context);
         });

         for(int i = builders.size() - 2; i >= 0; --i) {
            ((RequiredArgumentBuilder)builders.get(i)).then((ArgumentBuilder)builders.get(i + 1));
         }

         root.then((ArgumentBuilder)builders.get(0));
         return root;
      }
   }

   private ArgumentType<?> brigadierType(CommandArgumentType type) {
      return switch (type) {
         case STRING -> StringArgumentType.word();
         case GREEDY_STRING -> StringArgumentType.greedyString();
         case INTEGER -> IntegerArgumentType.integer();
         case NUMBER -> DoubleArgumentType.doubleArg();
         case BOOLEAN -> BoolArgumentType.bool();
         case PLAYER -> EntityArgument.player();
         case PLAYERS -> EntityArgument.players();
         case BLOCK_POS -> BlockPosArgument.blockPos();
         case VEC3 -> Vec3Argument.vec3();
         case RESOURCE_LOCATION -> ResourceLocationArgument.id();
         case DIMENSION -> DimensionArgument.dimension();
      };
   }

   private int execute(MLuaCommandDefinition definition, CommandContext<CommandSourceStack> context) {
      try {
         MinecraftServer server = context.getSource().getServer();
         MLuaRegistration registration = MLuaRuntime.registrationForServer(server);
         LuaTable args = this.argsTable(definition, context, registration);
         if (definition.callback().isfunction()) {
            definition.callback().invoke(LuaValue.varargsOf(new LuaValue[]{MLuaCommandContext.toLua((CommandSourceStack)context.getSource(), registration), args}));
         }

         if (definition.clientCallback().isfunction() && context.getSource().getPlayer() != null) {
            MLuaNetworkManager.get().sendCommandToClient(context.getSource().getPlayer(), definition.name(), this.clientArguments(definition, context));
         }

         return 1;
      } catch (Exception var4) {
         MLuaLogger.LOGGER.error("MLua command '{}' failed", definition.name(), var4);
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("[MLua] Command failed: " + var4.getMessage()));
         return 0;
      }
   }

   public void executeClient(String commandName, List<MLuaNetworkValue> arguments) {
      this.executeClient(commandName, arguments, null);
   }

   public void executeClient(String commandName, List<MLuaNetworkValue> arguments, MLuaRegistration registration) {
      LuaValue callback;
      synchronized (this.clientCallbacks) {
         Map<String, LuaValue> callbacks = this.clientCallbacks.get(registration);
         callback = callbacks != null ? callbacks.get(commandName) : null;
      }
      if (callback == null || !callback.isfunction()) {
         return;
      }

      try {
         LuaTable args = new LuaTable();
         LuaValue[] payload = MLuaNetworkSerializer.toLuaArray(arguments);

         for (int i = 0; i < payload.length; ++i) {
            LuaValue value = payload[i];
            if (value.istable()) {
               LuaTable table = value.checktable();
               LuaValue name = table.get("__argName");
               if (name.isstring()) {
                  args.set(name.tojstring(), table.get("value"));
               }
            }
         }

         callback.invoke(LuaValue.varargsOf(new LuaValue[]{MLuaCommandContext.toClientLua(registration), args}));
      } catch (Exception e) {
         MLuaLogger.LOGGER.error("MLua client command '{}' failed", commandName, e);
         LocalPlayer player = dev.mlua.minecraft.client.MLuaMinecraftClient.localPlayer();
         if (player != null) {
            player.displayClientMessage(Component.literal("[MLua] Client command failed: " + e.getMessage()), false);
         }
      }
   }

   private LuaTable argsTable(MLuaCommandDefinition definition, CommandContext<CommandSourceStack> context, MLuaRegistration registration) throws CommandSyntaxException {
      LuaTable args = new LuaTable();
      for (MLuaCommandArgument argument : definition.arguments()) {
         switch (argument.type()) {
            case STRING:
            case GREEDY_STRING:
               args.set(argument.name(), StringArgumentType.getString(context, argument.name()));
               break;
            case INTEGER:
               args.set(argument.name(), IntegerArgumentType.getInteger(context, argument.name()));
               break;
            case NUMBER:
               args.set(argument.name(), LuaValue.valueOf(DoubleArgumentType.getDouble(context, argument.name())));
               break;
            case BOOLEAN:
               args.set(argument.name(), LuaValue.valueOf(BoolArgumentType.getBool(context, argument.name())));
               break;
            case PLAYER:
                ServerPlayer player = EntityArgument.getPlayer(context, argument.name());
                args.set(argument.name(), MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration));
               break;
            case PLAYERS:
               Collection<ServerPlayer> players = EntityArgument.getPlayers(context, argument.name());
               LuaTable list = new LuaTable();
               int i = 1;
                for (ServerPlayer p : players) {
                   list.set(i++, MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(p), registration));
               }
               args.set(argument.name(), list);
               break;
            case BLOCK_POS:
               BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, argument.name());
               args.set(argument.name(), MLuaVector.vector3((double)pos.getX(), (double)pos.getY(), (double)pos.getZ()));
               break;
            case VEC3:
               Vec3 vec = Vec3Argument.getVec3(context, argument.name());
               args.set(argument.name(), MLuaVector.vector3(vec.x, vec.y, vec.z));
               break;
            case RESOURCE_LOCATION:
               ResourceLocation id = ResourceLocationArgument.getId(context, argument.name());
               args.set(argument.name(), id.toString());
               break;
            case DIMENSION:
               ServerLevel dimension = DimensionArgument.getDimension(context, argument.name());
               args.set(argument.name(), dimension.toString());
         }
      }
      return args;
   }

   public static MLuaCommandDefinition fromLua(String name, LuaTable definition) {
      validateCommandName(name);
      LuaValue callback = definition.get("run");
      LuaValue clientCallback = definition.get("run_client");
      if (!callback.isfunction() && !clientCallback.isfunction()) {
         throw new LuaError("[MLua] Command '" + name + "' requires a run and/or run_client function");
      } else {
         int permission = definition.get("permission").isnil() ? 0
            : MLuaLuaArguments.wholeInt(definition.get("permission"), "Commands:Register", "permission");
         if (permission < 0 || permission > 4) {
            throw new LuaError("[MLua] Commands:Register permission must be a whole number from 0 to 4.");
         }
         String description = definition.get("description").isnil() ? "" : definition.get("description").checkjstring();
         List<MLuaCommandArgument> arguments = parseArguments(name, definition.get("arguments"));
         List<String> aliases = parseAliases(definition.get("aliases"));
         return new MLuaCommandDefinition(name, description, permission, arguments, aliases, callback, clientCallback);
      }
   }

   private List<MLuaNetworkValue> clientArguments(MLuaCommandDefinition definition, CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      List<MLuaNetworkValue> values = new ArrayList();

      for (MLuaCommandArgument argument : definition.arguments()) {
         LuaTable entry = new LuaTable();
         entry.set("__argName", argument.name());
         entry.set("value", this.clientArgumentValue(argument, context));
         values.add(MLuaNetworkSerializer.fromLuaArgs(LuaValue.varargsOf(new LuaValue[]{entry}), 1).get(0));
      }

      return values;
   }

   private LuaValue clientArgumentValue(MLuaCommandArgument argument, CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      return switch (argument.type()) {
         case STRING, GREEDY_STRING -> LuaValue.valueOf(StringArgumentType.getString(context, argument.name()));
         case INTEGER -> LuaValue.valueOf(IntegerArgumentType.getInteger(context, argument.name()));
         case NUMBER -> LuaValue.valueOf(DoubleArgumentType.getDouble(context, argument.name()));
         case BOOLEAN -> LuaValue.valueOf(BoolArgumentType.getBool(context, argument.name()));
         case BLOCK_POS -> {
            BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, argument.name());
            yield MLuaVector.vector3((double)pos.getX(), (double)pos.getY(), (double)pos.getZ());
         }
         case VEC3 -> {
            Vec3 vec = Vec3Argument.getVec3(context, argument.name());
            yield MLuaVector.vector3(vec.x, vec.y, vec.z);
         }
         case RESOURCE_LOCATION -> LuaValue.valueOf(ResourceLocationArgument.getId(context, argument.name()).toString());
         case DIMENSION -> LuaValue.valueOf(DimensionArgument.getDimension(context, argument.name()).dimension().location().toString());
         case PLAYER, PLAYERS -> throw new LuaError("[MLua] run_client does not yet support player or players command arguments");
      };
   }

   private static List<String> parseAliases(LuaValue value) {
      List<String> aliases = new ArrayList();
      if (value.isnil()) {
         return aliases;
      } else if (!value.istable()) {
         return aliases;
      } else {
         LuaTable table = value.checktable();
         int index = 1;

         while(!table.get(index).isnil()) {
            String alias = table.get(index++).tojstring();
            if (COMMAND_NAME.matcher(alias).matches()) {
               aliases.add(alias);
            }
         }

         return aliases;
      }
   }

   private static List<MLuaCommandArgument> parseArguments(String commandName, LuaValue value) {
      List<MLuaCommandArgument> arguments = new ArrayList();
      if (value.isnil()) {
         return arguments;
      } else if (!value.istable()) {
         throw new LuaError("[MLua] Command '" + commandName + "' arguments must be a table");
      } else {
         LuaTable table = value.checktable();

         for(int index = 1; !table.get(index).isnil(); ++index) {
            LuaTable arg = table.get(index).checktable();
            String name = arg.get("name").checkjstring();
            if (!ARGUMENT_NAME.matcher(name).matches()) {
               throw new LuaError("[MLua] Invalid command argument name: " + name);
            }

            arguments.add(new MLuaCommandArgument(name, CommandArgumentType.parse(arg.get("type").checkjstring())));
         }

         return arguments;
      }
   }

   private static void validateCommandName(String name) {
      if (name == null || !COMMAND_NAME.matcher(name).matches()) {
         throw new LuaError("[MLua] Invalid command name: " + name);
      }
   }
}
