package dev.mlua.minecraft.binding;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.core.service.MLuaServiceProperties;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.modules.world.MLuaBlockUpdateFlags;
import dev.mlua.mc.impl.block.MCBlockImpl;
import dev.mlua.mc.impl.block.entity.MCBlockEntityImpl;
import dev.mlua.mc.impl.container.MCContainerImpl;
import dev.mlua.mc.impl.core.MCBlockPosImpl;
import dev.mlua.mc.impl.nbt.MCCompoundTagImpl;
import dev.mlua.mc.impl.world.MCLevelImpl;
import dev.mlua.mcapi.block.MCBlock;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.mcapi.block.MCBlockState;
import dev.mlua.mcapi.block.entity.MCBlockEntity;
import dev.mlua.mcapi.container.MCContainer;
import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.nbt.MCCompoundTag;
import dev.mlua.mcapi.world.MCLevel;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.nbt.CompoundTag;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaBlockWrapper {
   private static final Map<MLuaRegistration, Map<Level, Map<BlockPos, WeakReference<LuaTable>>>> BLOCK_INSTANCES = new WeakHashMap<>();
   private static final Map<MLuaRegistration, Map<BlockEntity, Map<String, WeakReference<LuaTable>>>> BLOCK_ENTITY_INSTANCES = new WeakHashMap<>();

   private MLuaBlockWrapper() {
   }

   public static LuaTable blockTable(Level level, BlockPos pos) {
      return blockTable(level, pos, MLuaRuntime.registrationForLevel(level));
   }

   public static LuaTable blockTable(Level level, BlockPos pos, MLuaRegistration registration) {
      return blockTable(level, pos, level.getBlockState(pos), registration);
   }

   /** Builds a block wrapper from an already available state without causing a chunk load. */
   public static LuaTable blockTable(Level level, BlockPos pos, BlockState state, MLuaRegistration registration) {
      BlockPos immutablePosition = pos.immutable();
      synchronized (BLOCK_INSTANCES) {
         Map<Level, Map<BlockPos, WeakReference<LuaTable>>> worlds =
            BLOCK_INSTANCES.computeIfAbsent(registration, ignored -> new WeakHashMap<>());
         Map<BlockPos, WeakReference<LuaTable>> positions =
            worlds.computeIfAbsent(level, ignored -> new WeakHashMap<>());
         WeakReference<LuaTable> reference = positions.get(immutablePosition);
         LuaTable cached = reference != null ? reference.get() : null;
         String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
         String className = MLuaBehaviorRegistry.getContentClass("block", blockId);
         if (className == null) className = "Block";
         if (cached != null && className.equals(registration.classNameOf(cached))) return cached;
         LuaTable fields = new LuaTable();
         fields.rawset("__side", LuaValue.valueOf("Both"));
         fields.rawset("__level", LuaValue.userdataOf(level));
         fields.rawset("__block_pos", LuaValue.userdataOf(immutablePosition));
         LuaTable table = registration.createClassInstance(className, fields);
         MLuaServiceProperties.attach(table);
         positions.put(immutablePosition, new WeakReference<>(table));
         return table;
      }
   }

   /** Returns the typed live block entity at a position, or nil when it has none. */
   public static LuaValue blockEntityAt(Level level, BlockPos pos, MLuaRegistration registration) {
      BlockEntity entity = level.getBlockEntity(pos);
      if (entity == null) return LuaValue.NIL;
      String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
      String entityClass = MLuaBehaviorRegistry.getContentClass("block_entity", id);
      return blockEntityTable(entity, level, pos, entityClass, registration);
   }

   private static LuaTable blockEntityTable(BlockEntity entity,
                                            Level level,
                                            BlockPos pos, String className,
                                            MLuaRegistration registration) {
      String resolvedClass = className == null ? "BlockEntity" : className;
      LuaTable fields = new LuaTable();
      fields.rawset("__side", LuaValue.valueOf("Both"));
      fields.rawset("__block_entity", LuaValue.userdataOf(entity));
      fields.rawset("__level", LuaValue.userdataOf(level));
      fields.rawset("__block_pos", LuaValue.userdataOf(pos.immutable()));
      LuaTable instance;
      WeakReference<LuaTable> instanceReference;
      synchronized (BLOCK_ENTITY_INSTANCES) {
         Map<BlockEntity, Map<String, WeakReference<LuaTable>>> entities =
            BLOCK_ENTITY_INSTANCES.computeIfAbsent(registration, ignored -> new WeakHashMap<>());
         Map<String, WeakReference<LuaTable>> variants = entities.computeIfAbsent(entity, ignored -> new HashMap<>());
         WeakReference<LuaTable> reference = variants.get(resolvedClass);
         LuaTable cached = reference == null ? null : reference.get();
         if (cached != null && resolvedClass.equals(registration.classNameOf(cached))) {
            if (cached.rawget("__mlua_block_entity_initializing").toboolean()) {
               throw new LuaError("[MLua] Circular block entity initialization for class '" + resolvedClass + "'.");
            }
            return cached;
         }
         instance = registration.attachClassInstance(new LuaTable(), resolvedClass, null);
         instance.rawset("__mlua_block_entity_initializing", LuaValue.TRUE);
         instanceReference = new WeakReference<>(instance);
         variants.put(resolvedClass, instanceReference);
      }
      try {
         registration.initializeClassInstance(instance, resolvedClass, fields);
         MLuaServiceProperties.attach(instance);
         instance.rawset("__mlua_block_entity_initializing", LuaValue.NIL);
         return instance;
      } catch (RuntimeException | Error failure) {
         instance.rawset("__mlua_block_entity_initializing", LuaValue.NIL);
         synchronized (BLOCK_ENTITY_INSTANCES) {
            Map<BlockEntity, Map<String, WeakReference<LuaTable>>> entities =
               BLOCK_ENTITY_INSTANCES.get(registration);
            Map<String, WeakReference<LuaTable>> variants = entities == null ? null : entities.get(entity);
            if (variants != null) {
               variants.remove(resolvedClass, instanceReference);
            }
         }
         throw failure;
      }
   }

   public static LuaTable blockEntityTable(BlockEntity entity,
                                            Level level, String className,
                                            MLuaRegistration registration) {
      return blockEntityTable(entity, level, entity.getBlockPos(), className, registration);
   }

   public static LuaTable classTable() {
      return classTable(MLuaRuntime.get());
   }

   public static LuaTable classTable(MLuaRegistration registration) {
      return createClass(registration);
   }

   public static BlockPos blockPosFromLua(LuaValue value) {
      if (!value.istable()) {
         return null;
      }
      LuaValue stored = value.checktable().rawget("__block_pos");
      if (stored.isuserdata(BlockPos.class)) {
         return (BlockPos)stored.touserdata(BlockPos.class);
      }
      LuaValue position = value.checktable().get("Position");
      if (position.istable()) {
         return vectorToBlockPos(position);
      }
      return vectorToBlockPos(value);
   }

   public static Level levelFromLua(LuaValue value) {
      if (!value.istable()) {
         return null;
      }
      LuaValue stored = value.checktable().rawget("__level");
      return stored.isuserdata(Level.class) ? (Level)stored.touserdata(Level.class) : null;
   }

   public static BlockPos vectorToBlockPos(LuaValue value) {
      if (!MLuaVector.isVector3(value) && !MLuaCFrame.isCFrame(value)) return null;
      double[] position = MLuaCFrame.vector(value);
      return BlockPos.containing(position[0], position[1], position[2]);
   }

   public static boolean toggleDoor(Level level, BlockPos pos, boolean open, boolean toggle) {
      BlockState state = doorBaseState(level, pos);
      if (!(state.getBlock() instanceof DoorBlock doorBlock) || !state.hasProperty(DoorBlock.OPEN)) {
         return false;
      }
      BlockPos basePos = doorBasePos(level, pos);
      boolean isOpen = state.getValue(DoorBlock.OPEN);
      if (toggle) {
         doorBlock.setOpen(null, level, state, basePos, !isOpen);
      } else if (isOpen != open) {
         doorBlock.setOpen(null, level, state, basePos, open);
      }
      return true;
   }

   public static BlockPos doorBasePos(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.HALF) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
         return pos.below();
      }
      return pos;
   }

   public static BlockState doorBaseState(Level level, BlockPos pos) {
      return level.getBlockState(doorBasePos(level, pos));
   }

   private static LuaTable createClass(MLuaRegistration registration) {
      MLuaBlockEntityClass.register(registration);
      LuaTable classTable = registration.registerClass("Block", registration.requireInstanceBaseClassName(), "A live block instance at a position in a Minecraft world.");
      if (!classTable.rawget("__initialized").toboolean()) {
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetBlockEntity", selfBlock((self, ignored) -> {
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            return blockEntityAt(rawLevel, rawPos, registration);
         }), "Returns the block entity at this position, or nil when none exists.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetId", selfBlock((self, ignored) -> LuaValue.valueOf(blockId(level(self), pos(self)))), "Returns the registered block identifier at this position.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetLevel", selfBlock((self, ignored) -> MLuaLevelWrapper.levelTable(((MCLevelImpl) level(self)).inner(), registration)), "Returns the live world containing this block.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetWorld", classTable.rawget("GetLevel"), "Returns the live dimension containing this block as a Level instance.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetDimension", selfBlock((self, ignored) -> LuaValue.valueOf(((MCLevelImpl) level(self)).inner().dimension().location().toString())), "Returns the dimension identifier containing this block.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetName", classTable.rawget("GetId"), "Returns the registered block identifier at this position.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetClassName",
            selfBlock((self, ignored) -> self.rawget("__type")),
            "Returns this block instance's registered Lua class name, including an addon subclass.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetPosition", selfBlock((self, ignored) -> {
            MCBlockPos pos = pos(self);
            return MLuaVector.vector3((double)pos.getX(), (double)pos.getY(), (double)pos.getZ());
         }), "Returns this block's position.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetRelative", selfBlock((self, args) -> {
            double[] offset = MLuaVector.components3(args.arg(2));
            for (double component : offset) {
               if (component != Math.rint(component) || component < Integer.MIN_VALUE || component > Integer.MAX_VALUE) {
                  throw new LuaError("[MLua] Block:GetRelative offset components must be whole numbers in the integer range.");
               }
            }
            BlockPos target = ((MCBlockPosImpl)pos(self)).inner().offset(
               (int)offset[0], (int)offset[1], (int)offset[2]);
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            return blockTable(rawLevel, target, registration);
         }), "Returns the live Block instance at an integer offset, using the target block's registered Lua class.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetIsAir", selfBlock((self, ignored) -> LuaValue.valueOf(level(self).getBlockState(pos(self)).isAir())), "Returns whether this block position contains air.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetIsSolid", selfBlock((self, ignored) -> LuaValue.valueOf(level(self).getBlockState(pos(self)).isSolid())), "Returns whether this block state is solid.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetState", selfBlock((self, ignored) -> {
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            return MLuaBlockStateWrapper.wrap(rawLevel.getBlockState(rawPos), rawLevel, rawPos, registration);
         }), "Returns the immutable Minecraft BlockState at this position.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetStateProperties", selfBlock((self, ignored) -> {
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            return MLuaBlockStateProperties.read(rawLevel.getBlockState(rawPos));
         }), "Returns the current values of this block's Minecraft state properties.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "SetStateProperties", selfBlock((self, args) -> {
            ServerLevel rawLevel = requireServerLevel(self, "SetStateProperties");
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            BlockState state =
               MLuaBlockStateProperties.apply(rawLevel.getBlockState(rawPos), args.arg(2));
            int updateFlags = MLuaBlockUpdateFlags.read(args.arg(3), "Block:SetStateProperties");
            return LuaValue.valueOf(rawLevel.setBlock(rawPos, state, updateFlags));
         }), "Updates this block's Minecraft state properties while keeping its block type. Optional Minecraft update flags default to 3. Server-side only.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetFluid", selfBlock((self, ignored) -> {
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            FluidState fluid = rawLevel.getFluidState(rawPos);
            if (fluid.isEmpty()) return LuaValue.NIL;
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.getType());
            return id == null ? LuaValue.NIL : LuaValue.valueOf(id.toString());
         }), "Returns the fluid identifier at this position, or nil when empty.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetFluidState", selfBlock((self, ignored) -> {
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            return MLuaFluidStateWrapper.wrap(rawLevel.getFluidState(rawPos), rawLevel, rawPos, registration);
         }), "Returns the typed FluidState at this position.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetLightLevel", selfBlock((self, ignored) -> {
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            return LuaValue.valueOf(rawLevel.getMaxLocalRawBrightness(rawPos));
         }), "Returns the maximum local light level at this position.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetSkyLight", selfBlock((self, ignored) -> {
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            return LuaValue.valueOf(rawLevel.getBrightness(LightLayer.SKY, rawPos));
         }), "Returns sky light at this position.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetBlockLight", selfBlock((self, ignored) -> {
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            return LuaValue.valueOf(rawLevel.getBrightness(LightLayer.BLOCK, rawPos));
         }), "Returns block light at this position.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetCanSeeSky", selfBlock((self, ignored) -> {
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            return LuaValue.valueOf(rawLevel.canSeeSky(rawPos));
         }), "Returns whether the sky is visible from this position.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetRedstonePower", selfBlock((self, ignored) -> {
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            return LuaValue.valueOf(rawLevel.getBestNeighborSignal(rawPos));
         }), "Returns the strongest redstone signal from neighboring blocks.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetIsPowered", selfBlock((self, ignored) -> {
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            return LuaValue.valueOf(rawLevel.hasNeighborSignal(rawPos));
         }), "Returns whether this position receives a redstone signal.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetDestroySpeed", selfBlock((self, ignored) -> {
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            return LuaValue.valueOf(rawLevel.getBlockState(rawPos).getDestroySpeed(rawLevel, rawPos));
         }), "Returns this block state's mining hardness in its current world context.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetCanSurvive", selfBlock((self, ignored) -> {
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            return LuaValue.valueOf(rawLevel.getBlockState(rawPos).canSurvive(rawLevel, rawPos));
         }), "Checks whether this block state can remain at its current position.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetCanBeReplaced", selfBlock((self, ignored) -> {
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            return LuaValue.valueOf(rawLevel.getBlockState(rawPos).canBeReplaced());
         }), "Checks whether this block state can be replaced by placement.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetHasBlockEntity", selfBlock((self, ignored) -> {
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            return LuaValue.valueOf(rawLevel.getBlockState(rawPos).hasBlockEntity());
         }), "Checks whether this block state owns a block entity.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetIsRandomlyTicking", selfBlock((self, ignored) -> {
            Level rawLevel = ((MCLevelImpl)level(self)).inner();
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            return LuaValue.valueOf(rawLevel.getBlockState(rawPos).isRandomlyTicking());
         }), "Checks whether this block state receives random game ticks.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "Break", selfBlock((self, args) -> {
            ServerLevel server = requireServerLevel(self, "Break");
            return LuaValue.valueOf(server.destroyBlock(((MCBlockPosImpl)pos(self)).inner(), args.optboolean(2, true)));
         }), "Breaks this block, optionally dropping its resources. Server-side only.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "Set", selfBlock((self, args) -> {
            String blockId = args.checkjstring(2);
            Block newBlock =
               (Block)MCApiRuntime.lookupRegistry("block", blockId);
            if (newBlock == null) {
               throw new LuaError("[MLua] Unknown block: " + blockId);
            }
            ServerLevel rawLevel = requireServerLevel(self, "Set");
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            LuaValue properties = args.arg(3);
            if (!properties.isnil() && !properties.istable() && !properties.isnumber()) {
               throw new LuaError("[MLua] Block:Set expects a state property table followed by optional update flags.");
            }
            BlockState state = MLuaBlockStateProperties.apply(newBlock.defaultBlockState(), properties.istable() ? properties : LuaValue.NIL);
            int updateFlags = MLuaBlockUpdateFlags.read(args.arg(properties.istable() ? 4 : 3), "Block:Set");
            return LuaValue.valueOf(rawLevel.setBlock(rawPos, state, updateFlags));
         }), "Replaces this block with a registered block identifier and optional state properties. Optional Minecraft update flags default to 3. Server-side only.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "ScheduleTick", selfBlock((self, args) -> {
            int delay = MLuaLuaArguments.wholeInt(args, 2, "Block:ScheduleTick", "delay");
            if (delay < 1) throw new LuaError("[MLua] Block:ScheduleTick delay must be at least one tick");
            ServerLevel rawLevel = requireServerLevel(self, "ScheduleTick");
            BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            Block block = rawLevel.getBlockState(rawPos).getBlock();
            rawLevel.scheduleTick(rawPos, block, delay);
            return LuaValue.NONE;
         }), "Schedules this block's onTick callback after the given server tick delay. Server-side only.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetNbt", selfBlock((self, args) -> {
            if (level(self).getBlockEntity(pos(self)) == null && (args.narg() < 2 || args.arg(2).isnil())) {
               return LuaValue.NIL;
            }
            if (args.narg() < 2 || args.arg(2).isnil()) {
               return nbtTable(level(self), pos(self), registration);
            }
            return MLuaNbtWrapper.get(((dev.mlua.mc.impl.nbt.MCCompoundTagImpl)blockEntityTag(level(self), pos(self))).inner(), args.checkjstring(2));
         }), "Returns this block entity's NBT data or one value by key.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "getNbt", classTable.rawget("GetNbt"), "Returns this block entity's NBT data or one value by key.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "SetNbt", selfBlock((self, args) -> {
            requireServerLevel(self, "SetNbt");
            if (args.narg() == 2 && args.arg(2).istable()) {
               return LuaValue.valueOf(replaceBlockEntityNbt(level(self), pos(self), args.arg(2)));
            }
            return LuaValue.valueOf(setBlockEntityNbt(level(self), pos(self), args.checkjstring(2), args.arg(3)));
         }), "Sets one block entity NBT value or replaces its data. Server-side only.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "setNbt", classTable.rawget("SetNbt"), "Sets one block entity NBT value or replaces its data. Server-side only.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "OpenDoor", selfBlock((self, ignored) -> LuaValue.valueOf(toggleDoor(requireServerLevel(self, "OpenDoor"), ((dev.mlua.mc.impl.core.MCBlockPosImpl)pos(self)).inner(), true, true))), "Opens the door at this block position. Server-side only.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "CloseDoor", selfBlock((self, ignored) -> LuaValue.valueOf(toggleDoor(requireServerLevel(self, "CloseDoor"), ((dev.mlua.mc.impl.core.MCBlockPosImpl)pos(self)).inner(), false, false))), "Closes the door at this block position. Server-side only.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "IsDoorOpen", selfBlock((self, ignored) -> {
            BlockState state = doorBaseState(((MCLevelImpl)level(self)).inner(), ((dev.mlua.mc.impl.core.MCBlockPosImpl)pos(self)).inner());
            return LuaValue.valueOf(state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.OPEN) && state.getValue(DoorBlock.OPEN));
         }), "Returns whether the door at this block position is open.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "HasInventory", selfBlock((self, ignored) -> LuaValue.valueOf(container(level(self), pos(self)) != null)), "Returns whether this block has an inventory.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "HasContainer",
            classTable.rawget("HasInventory"), "Returns whether this block exposes a live container.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetContainer",
            selfBlock((self, ignored) -> getContainer(self.checktable(), registration)),
            "Returns this block's live Container instance, when available.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetInventory",
            classTable.rawget("GetContainer"), "Alias for GetContainer.");
         String[][] properties = {
            {"Id", "GetId", null, "string", "The registered block identifier at this position."},
            {"Name", "GetName", null, "string", "The registered block identifier at this position."},
            {"Level", "GetLevel", null, "Level", "The live world containing this block."},
            {"World", "GetWorld", null, "Level", "The live dimension containing this block."},
            {"Dimension", "GetDimension", null, "string", "The dimension identifier containing this block."},
            {"Position", "GetPosition", null, "Vector3", "This block's position."},
            {"IsAir", "GetIsAir", null, "boolean", "Whether this block position contains air."},
            {"IsSolid", "GetIsSolid", null, "boolean", "Whether this block state is solid."},
            {"StateProperties", "GetStateProperties", "SetStateProperties", "BlockStateProperties", "Current Minecraft state values, such as facing, half, age, or waterlogged."},
            {"Fluid", "GetFluid", null, "string|nil", "Fluid identifier at this position, or nil when empty."},
            {"LightLevel", "GetLightLevel", null, "number", "Maximum local light level at this position."},
            {"SkyLight", "GetSkyLight", null, "number", "Sky light at this position."},
            {"BlockLight", "GetBlockLight", null, "number", "Block light at this position."},
            {"CanSeeSky", "GetCanSeeSky", null, "boolean", "Whether the sky is visible from this position."},
            {"RedstonePower", "GetRedstonePower", null, "number", "Strongest redstone signal from neighboring blocks."},
            {"IsPowered", "GetIsPowered", null, "boolean", "Whether this position receives a redstone signal."},
            {"DestroySpeed", "GetDestroySpeed", null, "number", "Mining hardness of this state in its current world context."},
            {"CanSurvive", "GetCanSurvive", null, "boolean", "Whether this state can remain at its current position."},
            {"CanBeReplaced", "GetCanBeReplaced", null, "boolean", "Whether this state can be replaced by placement."},
            {"HasBlockEntity", "GetHasBlockEntity", null, "boolean", "Whether this state owns a block entity."},
            {"IsRandomlyTicking", "GetIsRandomlyTicking", null, "boolean", "Whether this state receives random game ticks."},
            {"BlockEntity", "GetBlockEntity", null, "BlockEntity|nil", "The block entity at this position, or nil when none exists."},
            {"Nbt", "GetNbt", "SetNbt", "Nbt|table|nil", "This block entity's live NBT view, or nil when this block has no block entity; reads are available on both sides, while writes are server-side only."},
            {"DoorOpen", "IsDoorOpen", null, "boolean", "Whether the door at this block position is open."},
            {"Container", "GetContainer", null, "Container|nil", "The live container at this position, when available."},
            {"Inventory", "GetInventory", null, "Container|nil", "The live inventory at this position, when available."}
         };
         for (String[] property : properties) {
            MLuaClassMethods.registerInstanceProperty(registration, "Block", property[0], property[1], property[2],
               property[3], "shared:Shared", property[4]);
         }
         classTable.rawset("__initialized", LuaValue.TRUE);
      }
      return classTable;
   }

   private static String blockId(MCLevel level, MCBlockPos pos) {
      var rawLevel = ((MCLevelImpl)level).inner();
      var rawPos = ((dev.mlua.mc.impl.core.MCBlockPosImpl)pos).inner();
      var key = BuiltInRegistries.BLOCK.getKey(rawLevel.getBlockState(rawPos).getBlock());
      return key != null ? key.toString() : "unknown";
   }

   static LuaTable nbtTable(MCLevel level, MCBlockPos pos, MLuaRegistration registration) {
      return MLuaNbtWrapper.table(() -> ((dev.mlua.mc.impl.nbt.MCCompoundTagImpl)blockEntityTag(level, pos)).inner(),
         tag -> applyBlockEntityTag(level, pos, dev.mlua.mc.impl.nbt.MCCompoundTagImpl.fromReal(tag)), registration);
   }

   static LuaTable nbtTable(BlockEntity entity, MLuaRegistration registration) {
      MCBlockEntity wrapped = MCBlockEntityImpl.fromReal(entity);
      return MLuaNbtWrapper.table(
         () -> ((dev.mlua.mc.impl.nbt.MCCompoundTagImpl)wrapped.saveWithFullMetadata()).inner(),
         tag -> applyBlockEntityTag(entity, dev.mlua.mc.impl.nbt.MCCompoundTagImpl.fromReal(tag)), registration);
   }

   private static MCCompoundTag blockEntityTag(MCLevel level, MCBlockPos pos) {
      MCBlockEntity blockEntity = level.getBlockEntity(pos);
      return blockEntity == null ? dev.mlua.mc.impl.nbt.MCCompoundTagImpl.fromReal(new CompoundTag()) : blockEntity.saveWithFullMetadata();
   }

   static MCContainer container(MCLevel level, MCBlockPos pos) {
      MCBlockEntity blockEntity = level.getBlockEntity(pos);
      Object raw = blockEntity == null ? null : ((MCBlockEntityImpl)blockEntity).inner();
      if (raw instanceof Container c
         && (!(raw instanceof dev.mlua.runtime.client.MLuaVisualBlockEntity scripted) || scripted.hasContainer())) {
         return new MCContainerImpl(c);
      }
      return null;
   }

   static MCContainer container(BlockEntity entity) {
      if (entity instanceof Container container
         && (!(entity instanceof dev.mlua.runtime.client.MLuaVisualBlockEntity scripted)
            || scripted.hasContainer())) {
         return new MCContainerImpl(container);
      }
      return null;
   }

   static LuaValue getContainer(LuaTable self, MLuaRegistration registration) {
      BlockEntity entity = blockEntity(self);
      MCContainer inventory = container(entity);
      Level level = entity.getLevel();
      boolean writable = level != null && !level.isClientSide;
      return inventory == null ? LuaValue.NIL : MLuaContainer.containerTable(inventory, writable, registration);
   }

   static boolean setBlockEntityNbt(BlockEntity entity, String key, LuaValue value) {
      MCBlockEntity wrapped = MCBlockEntityImpl.fromReal(entity);
      MCCompoundTag tag = wrapped.saveWithFullMetadata();
      MLuaNbtWrapper.set(((dev.mlua.mc.impl.nbt.MCCompoundTagImpl)tag).inner(), key, value);
      return applyBlockEntityTag(entity, tag);
   }

   static boolean replaceBlockEntityNbt(BlockEntity entity, LuaValue replacement) {
      return applyBlockEntityTag(entity,
         dev.mlua.mc.impl.nbt.MCCompoundTagImpl.fromReal(MLuaNbtWrapper.toCompound(replacement)));
   }

   private static boolean applyBlockEntityTag(BlockEntity entity, MCCompoundTag tag) {
      Level level = entity.getLevel();
      if (!(level instanceof ServerLevel)) {
         throw new LuaError("[MLua] BlockEntity:SetNbt is server-side only.");
      }
      MCBlockEntity wrapped = MCBlockEntityImpl.fromReal(entity);
      wrapped.load(tag);
      wrapped.setChanged();
      BlockPos position = entity.getBlockPos();
      BlockState state = entity.getBlockState();
      level.sendBlockUpdated(position, state, state, 3);
      return true;
   }

   static boolean setBlockEntityNbt(MCLevel level, MCBlockPos pos, String key, LuaValue value) {
      MCBlockEntity blockEntity = level.getBlockEntity(pos);
      if (blockEntity == null) {
         return false;
      }
      MCCompoundTag tag = blockEntity.saveWithFullMetadata();
      MLuaNbtWrapper.set(((dev.mlua.mc.impl.nbt.MCCompoundTagImpl)tag).inner(), key, value);
      applyBlockEntityTag(level, pos, tag);
      return true;
   }

   static boolean replaceBlockEntityNbt(MCLevel level, MCBlockPos pos, LuaValue replacement) {
      MCBlockEntity blockEntity = level.getBlockEntity(pos);
      if (blockEntity == null) {
         return false;
      }
      MCCompoundTag tag = dev.mlua.mc.impl.nbt.MCCompoundTagImpl.fromReal(MLuaNbtWrapper.toCompound(replacement));
      applyBlockEntityTag(level, pos, tag);
      return true;
   }

   private static void applyBlockEntityTag(MCLevel level, MCBlockPos pos, MCCompoundTag tag) {
      if (!(((MCLevelImpl)level).inner() instanceof ServerLevel)) {
         throw new LuaError("[MLua] Block entity NBT writes are server-side only.");
      }
      MCBlockEntity blockEntity = level.getBlockEntity(pos);
      if (blockEntity == null) {
         return;
      }
      blockEntity.load(tag);
      blockEntity.setChanged();
      MCBlockState state = level.getBlockState(pos);
      level.sendBlockUpdated(pos, state, state, 3);
   }

   private static LuaValue firstNonNil(LuaValue... values) {
      for (LuaValue value : values) {
         if (!value.isnil()) {
            return value;
         }
      }
      return LuaValue.NIL;
   }

   static MCLevel level(LuaValue self) {
      LuaValue value = self.checktable().rawget("__level");
      Level level = value.isuserdata(Level.class) ? (Level)value.touserdata(Level.class) : null;
      if (level == null) {
         throw new LuaError("[MLua] Block instance lost its level reference");
      }
      return new MCLevelImpl(level);
   }

   private static ServerLevel requireServerLevel(LuaValue self, String method) {
      Level rawLevel = ((MCLevelImpl)level(self)).inner();
      if (!(rawLevel instanceof ServerLevel server)) {
         throw new LuaError("[MLua] Block:" + method + " is server-side only.");
      }
      return server;
   }

   static ServerLevel requireServerBlockEntity(LuaTable self, String method) {
      LuaValue value = self.rawget("__level");
      if (!value.isuserdata(Level.class)) {
         throw new LuaError("[MLua] BlockEntity has no live dimension.");
      }
      Level rawLevel =
         (Level)value.touserdata(Level.class);
      if (!(rawLevel instanceof ServerLevel server)) {
         throw new LuaError("[MLua] BlockEntity:" + method + " is server-side only.");
      }
      return server;
   }

   private static MCBlockPos pos(LuaValue self) {
      LuaValue value = self.checktable().rawget("__block_pos");
      BlockPos pos = value.isuserdata(BlockPos.class) ? (BlockPos)value.touserdata(BlockPos.class) : null;
      if (pos == null) {
         throw new LuaError("[MLua] Block instance lost its position reference");
      }
      return dev.mlua.mc.impl.core.MCBlockPosImpl.fromReal(pos);
   }

   static BlockEntity blockEntity(LuaValue self) {
      LuaValue value = self.checktable().rawget("__block_entity");
      if (!value.isuserdata(BlockEntity.class)) {
         throw new LuaError("[MLua] BlockEntity instance lost its block entity reference");
      }
      return (BlockEntity)value.touserdata(BlockEntity.class);
   }

   private static LuaValue selfBlock(BiFunction<LuaValue, Varargs, LuaValue> body) {
      return new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            return body.apply(args.arg1(), args);
         }
      };
   }
}
