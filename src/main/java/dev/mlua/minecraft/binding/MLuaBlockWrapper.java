package dev.mlua.minecraft.binding;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.core.service.MLuaServiceProperties;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.MLuaResourceLocations;
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
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaBlockWrapper {
   private static final Map<MLuaRegistration, Map<net.minecraft.world.level.Level, Map<net.minecraft.core.BlockPos, WeakReference<LuaTable>>>> BLOCK_INSTANCES = new WeakHashMap<>();
   private static final Map<MLuaRegistration, Map<net.minecraft.world.level.block.entity.BlockEntity, Map<String, WeakReference<LuaTable>>>> BLOCK_ENTITY_INSTANCES = new WeakHashMap<>();

   private MLuaBlockWrapper() {
   }

   public static LuaTable blockTable(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
      return blockTable(level, pos, MLuaRuntime.registrationForLevel(level));
   }

   public static LuaTable blockTable(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, MLuaRegistration registration) {
      net.minecraft.core.BlockPos immutablePosition = pos.immutable();
      synchronized (BLOCK_INSTANCES) {
         Map<net.minecraft.world.level.Level, Map<net.minecraft.core.BlockPos, WeakReference<LuaTable>>> worlds =
            BLOCK_INSTANCES.computeIfAbsent(registration, ignored -> new WeakHashMap<>());
         Map<net.minecraft.core.BlockPos, WeakReference<LuaTable>> positions =
            worlds.computeIfAbsent(level, ignored -> new WeakHashMap<>());
         WeakReference<LuaTable> reference = positions.get(immutablePosition);
         LuaTable cached = reference != null ? reference.get() : null;
         net.minecraft.world.level.block.state.BlockState state = level.getBlockState(immutablePosition);
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

   private static LuaTable blockEntityTable(net.minecraft.world.level.block.entity.BlockEntity entity,
                                            net.minecraft.world.level.Level level,
                                            net.minecraft.core.BlockPos pos, String className,
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
         Map<net.minecraft.world.level.block.entity.BlockEntity, Map<String, WeakReference<LuaTable>>> entities =
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
            Map<net.minecraft.world.level.block.entity.BlockEntity, Map<String, WeakReference<LuaTable>>> entities =
               BLOCK_ENTITY_INSTANCES.get(registration);
            Map<String, WeakReference<LuaTable>> variants = entities == null ? null : entities.get(entity);
            if (variants != null) {
               variants.remove(resolvedClass, instanceReference);
            }
         }
         throw failure;
      }
   }

   public static LuaTable classTable() {
      return classTable(MLuaRuntime.get());
   }

   public static LuaTable classTable(MLuaRegistration registration) {
      return createClass(registration);
   }

   public static net.minecraft.core.BlockPos blockPosFromLua(LuaValue value) {
      if (!value.istable()) {
         return null;
      }
      LuaValue stored = value.checktable().rawget("__block_pos");
      if (stored.isuserdata(net.minecraft.core.BlockPos.class)) {
         return (net.minecraft.core.BlockPos)stored.touserdata(net.minecraft.core.BlockPos.class);
      }
      LuaValue position = value.checktable().get("Position");
      if (position.istable()) {
         return vectorToBlockPos(position);
      }
      return vectorToBlockPos(value);
   }

   public static net.minecraft.world.level.Level levelFromLua(LuaValue value) {
      if (!value.istable()) {
         return null;
      }
      LuaValue stored = value.checktable().rawget("__level");
      return stored.isuserdata(net.minecraft.world.level.Level.class) ? (net.minecraft.world.level.Level)stored.touserdata(net.minecraft.world.level.Level.class) : null;
   }

   public static net.minecraft.core.BlockPos vectorToBlockPos(LuaValue value) {
      if (!value.istable()) {
         return null;
      }
      LuaTable table = value.checktable();
      LuaValue x = firstNonNil(table.get("X"), table.get("x"), table.get(1));
      LuaValue y = firstNonNil(table.get("Y"), table.get("y"), table.get(2));
      LuaValue z = firstNonNil(table.get("Z"), table.get("z"), table.get(3));
      return x.isnumber() && y.isnumber() && z.isnumber() ? net.minecraft.core.BlockPos.containing(x.todouble(), y.todouble(), z.todouble()) : null;
   }

   public static boolean toggleDoor(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, boolean open, boolean toggle) {
      net.minecraft.world.level.block.state.BlockState state = doorBaseState(level, pos);
      if (!(state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock doorBlock) || !state.hasProperty(net.minecraft.world.level.block.DoorBlock.OPEN)) {
         return false;
      }
      net.minecraft.core.BlockPos basePos = doorBasePos(level, pos);
      boolean isOpen = state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
      if (toggle) {
         doorBlock.setOpen(null, level, state, basePos, !isOpen);
      } else if (isOpen != open) {
         doorBlock.setOpen(null, level, state, basePos, open);
      }
      return true;
   }

   public static net.minecraft.core.BlockPos doorBasePos(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
      net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock && state.hasProperty(net.minecraft.world.level.block.DoorBlock.HALF) && state.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
         return pos.below();
      }
      return pos;
   }

   public static net.minecraft.world.level.block.state.BlockState doorBaseState(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
      return level.getBlockState(doorBasePos(level, pos));
   }

   private static LuaTable createClass(MLuaRegistration registration) {
      LuaTable blockEntityClass = registration.registerClass("BlockEntity", registration.requireInstanceBaseClassName(),
         "A live block entity attached to a block position, with persistent state and optional inventory.");
      if (!blockEntityClass.rawget("__initialized").toboolean()) {
         registration.registerClassFunction("BlockEntity", "GetPosition", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               net.minecraft.world.level.block.entity.BlockEntity entity = blockEntity(args.arg1());
               return MLuaVector.vector3(entity.getBlockPos().getX(), entity.getBlockPos().getY(), entity.getBlockPos().getZ());
            }
         }, "Returns this block entity's world position.");
         registration.registerClassFunction("BlockEntity", "GetBlock", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               LuaTable self = args.arg1().checktable();
               return blockTable((net.minecraft.world.level.Level)self.rawget("__level").touserdata(net.minecraft.world.level.Level.class),
                  (net.minecraft.core.BlockPos)self.rawget("__block_pos").touserdata(net.minecraft.core.BlockPos.class), registration);
            }
         }, "Returns the live block that owns this block entity.");
         registration.registerClassFunction("BlockEntity", "GetType", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               return LuaValue.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity(args.arg1()).getType()).toString());
            }
         }, "Returns this block entity type's registry identifier.");
         registration.registerClassFunction("BlockEntity", "GetNbt", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               LuaTable self = args.arg1().checktable();
               return nbtTable(level(self), pos(self), registration);
            }
         }, "Returns this block entity's live NBT data.");
         registration.registerClassFunction("BlockEntity", "SetNbt", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               LuaTable self = args.arg1().checktable();
               MCLevel level = level(self);
               MCBlockPos pos = pos(self);
               if (args.narg() == 2 && args.arg(2).istable()) {
                  return LuaValue.valueOf(replaceBlockEntityNbt(level, pos, args.arg(2)));
               }
               return LuaValue.valueOf(setBlockEntityNbt(level, pos, args.checkjstring(2), args.arg(3)));
            }
         }, "Sets one block entity NBT value or replaces its data.");
         registration.registerClassFunction("BlockEntity", "GetContainer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               return getContainer(args.arg1().checktable(), registration);
            }
         }, "Returns the live Container owned by this block entity, when available.");
         registration.registerClassFunction("BlockEntity", "GetInventory",
            blockEntityClass.rawget("GetContainer"), "Alias for GetContainer.");
         registration.registerClassFunction("BlockEntity", "HasContainer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               LuaTable self = args.arg1().checktable();
               return LuaValue.valueOf(container(level(self), pos(self)) != null);
            }
         }, "Whether this block entity owns a live Container.");
         registration.registerClassFunction("BlockEntity", "SetChanged", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               blockEntity(args.arg1()).setChanged();
               return LuaValue.NONE;
            }
         }, "Marks this block entity's saved state as changed.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "Position", "GetPosition", null,
            "Vector3", "shared:Shared", "This block entity's world position.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "Block", "GetBlock", null,
            "Block", "shared:Shared", "The live block that owns this block entity.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "Type", "GetType", null,
            "string", "shared:Shared", "This block entity type's registry identifier.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "Nbt", "GetNbt", "SetNbt",
            "Nbt|table", "shared:Shared", "This block entity's live NBT view; assign a table or Nbt value to replace its data.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "Container", "GetContainer", null,
            "Container|nil", "shared:Shared", "The live container owned by this block entity, when available.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "Inventory", "GetInventory", null,
            "Container|nil", "shared:Shared", "Alias for Container.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "HasContainer", "HasContainer", null,
            "boolean", "shared:Shared", "Whether this block entity owns a live Container.");
         blockEntityClass.set("__initialized", LuaValue.TRUE);
      }
      LuaTable classTable = registration.registerClass("Block", registration.requireInstanceBaseClassName(), "A live block instance at a position in a Minecraft world.");
      if (!classTable.rawget("__initialized").toboolean()) {
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetBlockEntity", selfBlock((self, ignored) -> {
            net.minecraft.world.level.Level rawLevel = ((MCLevelImpl)level(self)).inner();
            net.minecraft.core.BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            net.minecraft.world.level.block.entity.BlockEntity entity = rawLevel.getBlockEntity(rawPos);
            if (entity == null) return LuaValue.NIL;
            String id = BuiltInRegistries.BLOCK.getKey(rawLevel.getBlockState(rawPos).getBlock()).toString();
            String entityClass = MLuaBehaviorRegistry.getContentClass("block_entity", id);
            return blockEntityTable(entity, rawLevel, rawPos, entityClass, registration);
         }), "Returns the block entity at this position, or nil when none exists.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetId", selfBlock((self, ignored) -> LuaValue.valueOf(blockId(level(self), pos(self)))), "Returns the registered block identifier at this position.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetLevel", selfBlock((self, ignored) -> MLuaLevelWrapper.levelTable(((MCLevelImpl) level(self)).inner(), registration)), "Returns the live world containing this block.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetDimension", selfBlock((self, ignored) -> LuaValue.valueOf(((MCLevelImpl) level(self)).inner().dimension().location().toString())), "Returns the dimension identifier containing this block.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetName", classTable.rawget("GetId"), "Returns the registered block identifier at this position.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetPosition", selfBlock((self, ignored) -> {
            MCBlockPos pos = pos(self);
            return MLuaVector.vector3((double)pos.getX(), (double)pos.getY(), (double)pos.getZ());
         }), "Returns this block's position.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetIsAir", selfBlock((self, ignored) -> LuaValue.valueOf(level(self).getBlockState(pos(self)).isAir())), "Returns whether this block position contains air.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetIsSolid", selfBlock((self, ignored) -> LuaValue.valueOf(level(self).getBlockState(pos(self)).isSolid())), "Returns whether this block state is solid.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "Break", selfBlock((self, ignored) -> {
            level(self).destroyBlock(pos(self), true);
            return LuaValue.TRUE;
         }), "Breaks this block and drops its resources.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "Set", selfBlock((self, args) -> {
            String blockId = args.checkjstring(2);
            MCBlock newBlock = MCBlockImpl.fromReal((net.minecraft.world.level.block.Block) MCApiRuntime.lookupRegistry("block", blockId));
            if (newBlock == null) {
               throw new LuaError("[MLua] Unknown block: " + blockId);
            }
            level(self).setBlock(pos(self), newBlock.defaultBlockState(), 3);
            return LuaValue.TRUE;
         }), "Replaces this block with a registered block identifier.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "ScheduleTick", selfBlock((self, args) -> {
            int delay = args.checkint(2);
            if (delay < 1) throw new LuaError("[MLua] Block:ScheduleTick delay must be at least one tick");
            net.minecraft.world.level.Level rawLevel = ((MCLevelImpl)level(self)).inner();
            if (rawLevel.isClientSide) return LuaValue.FALSE;
            net.minecraft.core.BlockPos rawPos = ((MCBlockPosImpl)pos(self)).inner();
            net.minecraft.world.level.block.Block block = rawLevel.getBlockState(rawPos).getBlock();
            rawLevel.scheduleTick(rawPos, block, delay);
            return LuaValue.TRUE;
         }), "Schedules this block's onTick callback after the given server tick delay.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "GetNbt", selfBlock((self, args) -> {
            if (args.narg() < 2 || args.arg(2).isnil()) {
               return nbtTable(level(self), pos(self), registration);
            }
            return MLuaNbtWrapper.get(((dev.mlua.mc.impl.nbt.MCCompoundTagImpl)blockEntityTag(level(self), pos(self))).inner(), args.checkjstring(2));
         }), "Returns this block entity's NBT data or one value by key.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "getNbt", classTable.rawget("GetNbt"), "Returns this block entity's NBT data or one value by key.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "SetNbt", selfBlock((self, args) -> {
            if (args.narg() == 2 && args.arg(2).istable()) {
               return LuaValue.valueOf(replaceBlockEntityNbt(level(self), pos(self), args.arg(2)));
            }
            return LuaValue.valueOf(setBlockEntityNbt(level(self), pos(self), args.checkjstring(2), args.arg(3)));
         }), "Sets one block entity NBT value or replaces its data.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "setNbt", classTable.rawget("SetNbt"), "Sets one block entity NBT value or replaces its data.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "OpenDoor", selfBlock((self, ignored) -> LuaValue.valueOf(toggleDoor(((MCLevelImpl)level(self)).inner(), ((dev.mlua.mc.impl.core.MCBlockPosImpl)pos(self)).inner(), true, true))), "Opens the door at this block position.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "CloseDoor", selfBlock((self, ignored) -> LuaValue.valueOf(toggleDoor(((MCLevelImpl)level(self)).inner(), ((dev.mlua.mc.impl.core.MCBlockPosImpl)pos(self)).inner(), false, false))), "Closes the door at this block position.");
         MLuaClassMethods.registerClassMethod(registration, "Block", classTable, "IsDoorOpen", selfBlock((self, ignored) -> {
            net.minecraft.world.level.block.state.BlockState state = doorBaseState(((MCLevelImpl)level(self)).inner(), ((dev.mlua.mc.impl.core.MCBlockPosImpl)pos(self)).inner());
            return LuaValue.valueOf(state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock && state.hasProperty(net.minecraft.world.level.block.DoorBlock.OPEN) && state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN));
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
            {"Dimension", "GetDimension", null, "string", "The dimension identifier containing this block."},
            {"Position", "GetPosition", null, "Vector3", "This block's position."},
            {"IsAir", "GetIsAir", null, "boolean", "Whether this block position contains air."},
            {"IsSolid", "GetIsSolid", null, "boolean", "Whether this block state is solid."},
            {"BlockEntity", "GetBlockEntity", null, "BlockEntity|nil", "The block entity at this position, or nil when none exists."},
            {"Nbt", "GetNbt", "SetNbt", "Nbt|table", "This block entity's live NBT view; assign a table to replace its data."},
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
      String key = MCApiRuntime.registryKey("block", rawLevel.getBlockState(rawPos).getBlock());
      return key != null ? key : "unknown";
   }

   private static LuaTable nbtTable(MCLevel level, MCBlockPos pos, MLuaRegistration registration) {
      return MLuaNbtWrapper.table(() -> ((dev.mlua.mc.impl.nbt.MCCompoundTagImpl)blockEntityTag(level, pos)).inner(),
         tag -> applyBlockEntityTag(level, pos, dev.mlua.mc.impl.nbt.MCCompoundTagImpl.fromReal(tag)), registration);
   }

   private static MCCompoundTag blockEntityTag(MCLevel level, MCBlockPos pos) {
      MCBlockEntity blockEntity = level.getBlockEntity(pos);
      return blockEntity == null ? dev.mlua.mc.impl.nbt.MCCompoundTagImpl.fromReal(new net.minecraft.nbt.CompoundTag()) : blockEntity.saveWithFullMetadata();
   }

   private static MCContainer container(MCLevel level, MCBlockPos pos) {
      MCBlockEntity blockEntity = level.getBlockEntity(pos);
      Object raw = blockEntity == null ? null : ((MCBlockEntityImpl)blockEntity).inner();
      if (raw instanceof net.minecraft.world.Container c
         && (!(raw instanceof dev.mlua.runtime.client.MLuaVisualBlockEntity scripted) || scripted.hasContainer())) {
         return new MCContainerImpl(c);
      }
      return null;
   }

   private static LuaValue getContainer(LuaTable self, MLuaRegistration registration) {
      MCContainer inventory = container(level(self), pos(self));
      boolean writable = !((MCLevelImpl)level(self)).inner().isClientSide;
      return inventory == null ? LuaValue.NIL : MLuaContainer.containerTable(inventory, writable, registration);
   }

   private static boolean setBlockEntityNbt(MCLevel level, MCBlockPos pos, String key, LuaValue value) {
      MCBlockEntity blockEntity = level.getBlockEntity(pos);
      if (blockEntity == null) {
         return false;
      }
      MCCompoundTag tag = blockEntity.saveWithFullMetadata();
      MLuaNbtWrapper.set(((dev.mlua.mc.impl.nbt.MCCompoundTagImpl)tag).inner(), key, value);
      applyBlockEntityTag(level, pos, tag);
      return true;
   }

   private static boolean replaceBlockEntityNbt(MCLevel level, MCBlockPos pos, LuaValue replacement) {
      MCBlockEntity blockEntity = level.getBlockEntity(pos);
      if (blockEntity == null) {
         return false;
      }
      MCCompoundTag tag = dev.mlua.mc.impl.nbt.MCCompoundTagImpl.fromReal(MLuaNbtWrapper.toCompound(replacement));
      applyBlockEntityTag(level, pos, tag);
      return true;
   }

   private static void applyBlockEntityTag(MCLevel level, MCBlockPos pos, MCCompoundTag tag) {
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

   private static MCLevel level(LuaValue self) {
      LuaValue value = self.checktable().rawget("__level");
      net.minecraft.world.level.Level level = value.isuserdata(net.minecraft.world.level.Level.class) ? (net.minecraft.world.level.Level)value.touserdata(net.minecraft.world.level.Level.class) : null;
      if (level == null) {
         throw new LuaError("[MLua] Block instance lost its level reference");
      }
      return new MCLevelImpl(level);
   }

   private static MCBlockPos pos(LuaValue self) {
      LuaValue value = self.checktable().rawget("__block_pos");
      net.minecraft.core.BlockPos pos = value.isuserdata(net.minecraft.core.BlockPos.class) ? (net.minecraft.core.BlockPos)value.touserdata(net.minecraft.core.BlockPos.class) : null;
      if (pos == null) {
         throw new LuaError("[MLua] Block instance lost its position reference");
      }
      return dev.mlua.mc.impl.core.MCBlockPosImpl.fromReal(pos);
   }

   private static net.minecraft.world.level.block.entity.BlockEntity blockEntity(LuaValue self) {
      LuaValue value = self.checktable().rawget("__block_entity");
      if (!value.isuserdata(net.minecraft.world.level.block.entity.BlockEntity.class)) {
         throw new LuaError("[MLua] BlockEntity instance lost its block entity reference");
      }
      return (net.minecraft.world.level.block.entity.BlockEntity)value.touserdata(net.minecraft.world.level.block.entity.BlockEntity.class);
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
