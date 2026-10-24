package dev.mlua.minecraft.binding;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.math.MLuaVector;
import dev.mlua.mc.impl.world.MCLevelImpl;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Registers the BlockEntity class and its live state and inventory API. */
final class MLuaBlockEntityClass {
    private MLuaBlockEntityClass() { }

    static void register(MLuaRegistration registration) {
      LuaTable blockEntityClass = registration.registerClass("BlockEntity", registration.requireInstanceBaseClassName(),
         "A live block entity attached to a block position, with persistent state and optional inventory.");
      if (!blockEntityClass.rawget("__initialized").toboolean()) {
         registration.registerClassFunction("BlockEntity", "GetPosition", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               BlockEntity entity = MLuaBlockWrapper.blockEntity(args.arg1());
               return MLuaVector.vector3(entity.getBlockPos().getX(), entity.getBlockPos().getY(), entity.getBlockPos().getZ());
            }
         }, "Returns this block entity's world position.");
         registration.registerClassFunction("BlockEntity", "OnLoad", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.NONE; }
         }, "Runs when Minecraft attaches this block entity to a level.");
         registration.registerClassFunction("BlockEntity", "OnUnload", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.NONE; }
         }, "Runs when Minecraft removes this block entity from a level.");
         registration.registerClassFunction("BlockEntity", "OnContainerChanged", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.NONE; }
         }, "Runs on the server after this block entity's container contents change.");
         registration.registerClassFunction("BlockEntity", "GetLevel", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               LuaTable self = args.arg1().checktable();
               return MLuaLevelWrapper.levelTable(((MCLevelImpl)MLuaBlockWrapper.level(self)).inner(), registration);
            }
         }, "Returns the live dimension containing this block entity.");
         registration.registerClassFunction("BlockEntity", "GetWorld", blockEntityClass.rawget("GetLevel"),
            "Returns the live dimension containing this block entity as a Level instance.");
         registration.registerClassFunction("BlockEntity", "GetDimension", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               Level level = ((MCLevelImpl)MLuaBlockWrapper.level(args.arg1().checktable())).inner();
               return LuaValue.valueOf(level.dimension().location().toString());
            }
         }, "Returns the dimension identifier containing this block entity.");
         registration.registerClassFunction("BlockEntity", "GetBlock", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               LuaTable self = args.arg1().checktable();
               return blockTable((Level)self.rawget("__level").touserdata(Level.class),
                  (BlockPos)self.rawget("__block_pos").touserdata(BlockPos.class), registration);
            }
         }, "Returns the live block that owns this block entity.");
         registration.registerClassFunction("BlockEntity", "GetBlockEntityType", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               return LuaValue.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(MLuaBlockWrapper.blockEntity(args.arg1()).getType()).toString());
            }
         }, "Returns this block entity type's registry identifier.");
         registration.registerClassFunction("BlockEntity", "GetIsRemoved", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               return LuaValue.valueOf(MLuaBlockWrapper.blockEntity(args.arg1()).isRemoved());
            }
         }, "Returns whether Minecraft has removed this block entity from its dimension.");
         registration.registerClassFunction("BlockEntity", "GetNbt", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               return MLuaBlockWrapper.nbtTable(MLuaBlockWrapper.blockEntity(args.arg1()), registration);
            }
         }, "Returns this block entity's live NBT data.");
         registration.registerClassFunction("BlockEntity", "SetNbt", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               LuaTable self = args.arg1().checktable();
               MLuaBlockWrapper.requireServerBlockEntity(self, "SetNbt");
               BlockEntity entity = MLuaBlockWrapper.blockEntity(self);
               if (args.narg() == 2 && args.arg(2).istable()) {
                  return LuaValue.valueOf(MLuaBlockWrapper.replaceBlockEntityNbt(entity, args.arg(2)));
               }
               return LuaValue.valueOf(MLuaBlockWrapper.setBlockEntityNbt(entity, args.checkjstring(2), args.arg(3)));
            }
         }, "Sets one block entity NBT value or replaces its data on the server.");
         registration.registerClassFunction("BlockEntity", "GetContainer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               return MLuaBlockWrapper.getContainer(args.arg1().checktable(), registration);
            }
         }, "Returns the live Container owned by this block entity, when available.");
         registration.registerClassFunction("BlockEntity", "GetInventory",
            blockEntityClass.rawget("GetContainer"), "Alias for GetContainer.");
         registration.registerClassFunction("BlockEntity", "HasContainer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               return LuaValue.valueOf(MLuaBlockWrapper.container(MLuaBlockWrapper.blockEntity(args.arg1())) != null);
            }
         }, "Whether this block entity owns a live Container.");
         registration.registerClassFunction("BlockEntity", "SetChanged", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               LuaTable self = args.arg1().checktable();
               MLuaBlockWrapper.requireServerBlockEntity(self, "SetChanged");
               MLuaBlockWrapper.blockEntity(self).setChanged();
               return LuaValue.NONE;
            }
         }, "Marks this block entity's saved state as changed. Server-side only.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "Position", "GetPosition", null,
            "Vector3", "shared:Shared", "This block entity's world position.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "Level", "GetLevel", null,
            "Level", "shared:Shared", "The live dimension containing this block entity.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "World", "GetWorld", null,
            "Level", "shared:Shared", "The live dimension containing this block entity.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "Dimension", "GetDimension", null,
            "string", "shared:Shared", "The dimension identifier containing this block entity.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "Block", "GetBlock", null,
            "Block", "shared:Shared", "The live block that owns this block entity.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "Type", "GetBlockEntityType", null,
            "string", "shared:Shared", "This block entity type's registry identifier.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "IsRemoved", "GetIsRemoved", null,
            "boolean", "shared:Shared", "Whether Minecraft has removed this block entity from its dimension.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "Nbt", "GetNbt", "SetNbt",
            "Nbt|table", "shared:Shared", "This block entity's live NBT view; reads are available on both sides, while writes are server-side only.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "Container", "GetContainer", null,
            "Container|nil", "shared:Shared", "The live container owned by this block entity, when available.");
         MLuaClassMethods.registerInstanceProperty(registration, "BlockEntity", "Inventory", "GetInventory", null,
            "Container|nil", "shared:Shared", "Alias for Container.");
         blockEntityClass.set("__initialized", LuaValue.TRUE);
      }
    }
}
