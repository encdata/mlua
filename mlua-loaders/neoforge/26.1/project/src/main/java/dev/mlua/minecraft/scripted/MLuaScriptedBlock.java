package dev.mlua.minecraft.scripted;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.server.level.ServerPlayer;
public class MLuaScriptedBlock extends Block {
    private final String behaviorKey;
    public MLuaScriptedBlock(BlockBehaviour.Properties properties, String behaviorKey, Supplier<BlockEntityType<?>> blockEntityType) { super(properties); this.behaviorKey = behaviorKey; }
    @Override protected InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("block", this.behaviorKey, "onRightClick");
        if (!callback.isnil()) try {
            var registration = MLuaScriptRuntimeRegistration.forLevel(level);
            LuaTable ctx = new LuaTable();
            ctx.set("World", level.dimension().identifier().toString());
            LuaTable coordinates = new LuaTable();
            coordinates.set("X", pos.getX());
            coordinates.set("Y", pos.getY());
            coordinates.set("Z", pos.getZ());
            ctx.set("Position", coordinates);
            ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, registration));
            if (player instanceof ServerPlayer serverPlayer) {
                ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(serverPlayer), registration));
            }
            ctx.set("Face", hit.getDirection().getName());
            Varargs result = callback.invoke(ctx);
            if (result.arg1().isboolean() && result.arg1().toboolean()) return InteractionResult.SUCCESS;
        } catch (Exception exception) { MLuaLogger.LOGGER.error("MLua block onRightClick callback failed for '{}': {}", this.behaviorKey, exception.getMessage()); }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
    public static MLuaScriptedBlock create(BlockBehaviour.Properties properties, String behaviorKey, Supplier<?> blockEntityType) { return new MLuaScriptedBlock(properties, behaviorKey, null); }
}
