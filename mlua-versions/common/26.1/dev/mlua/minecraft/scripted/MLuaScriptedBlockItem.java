package dev.mlua.minecraft.scripted;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.core.MLuaRuntime;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class MLuaScriptedBlockItem extends BlockItem {
    private final String behaviorKey;
    public MLuaScriptedBlockItem(Block block, Item.Properties properties, String behaviorKey) { super(block, properties); this.behaviorKey = behaviorKey; }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", this.behaviorKey, "onUse");
        if (!callback.isnil()) try {
            var registration = MLuaRuntime.registrationForLevel(level);
            LuaTable ctx = new LuaTable();
            ctx.set("World", level.dimension().identifier().toString());
            ctx.set("Hand", hand.name().toLowerCase(java.util.Locale.ROOT));
            ctx.set("Item", MLuaItemWrapper.itemTable(player.getItemInHand(hand), registration));
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                ctx.set("Player", dev.mlua.modules.players.classes.MLuaPlayerWrapper.playerTable(
                    dev.mlua.mc.impl.entity.MCServerPlayerImpl.fromReal(serverPlayer), registration));
            }
            Varargs result = MLuaBehaviorRegistry.invokeCallback("item", this.behaviorKey, "onUse", callback,
                LuaValue.varargsOf(new LuaValue[]{ctx}));
            if (result.arg1().isboolean() && result.arg1().toboolean()) return InteractionResult.SUCCESS;
        } catch (Exception exception) { MLuaLogger.LOGGER.error("MLua block item onUse callback failed for '{}': {}", this.behaviorKey, exception.getMessage()); }
        return super.use(level, player, hand);
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", this.behaviorKey, "onUseOnBlock");
        if (!callback.isnil()) try {
            var registration = MLuaRuntime.registrationForLevel(context.getLevel());
            var position = context.getClickedPos();
            LuaTable values = new LuaTable();
            values.set("World", context.getLevel().dimension().identifier().toString());
            values.set("Position", dev.mlua.modules.players.classes.MLuaPlayerWrapper.positionTable(
                position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5));
            values.set("Face", context.getClickedFace().getName());
            values.set("Hand", context.getHand().name().toLowerCase(java.util.Locale.ROOT));
            values.set("Block", MLuaBlockWrapper.blockTable(context.getLevel(), position, registration));
            values.set("Item", MLuaItemWrapper.itemTable(context.getItemInHand(), registration));
            if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player) {
                values.set("Player", dev.mlua.modules.players.classes.MLuaPlayerWrapper.playerTable(
                    dev.mlua.mc.impl.entity.MCServerPlayerImpl.fromReal(player), registration));
            }
            Varargs result = MLuaBehaviorRegistry.invokeCallback("item", this.behaviorKey, "onUseOnBlock", callback,
                LuaValue.varargsOf(new LuaValue[]{values}));
            if (result.arg1().isboolean() && result.arg1().toboolean()) return InteractionResult.SUCCESS;
        } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block item onUseOnBlock callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
        }
        return super.useOn(context);
    }
}
