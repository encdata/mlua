package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

/** Version-specific item hooks backed by the shared scripted behavior registry. */
public class MLuaScriptedItem extends Item {
    private final String behaviorKey;

    public MLuaScriptedItem(Properties properties, String behaviorKey) {
        super(properties);
        this.behaviorKey = behaviorKey;
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", behaviorKey, "onUse");
        if (!callback.isnil()) try {
            LuaTable context = new LuaTable();
            context.set("World", level.dimension().identifier().toString());
            context.set("Hand", hand.name().toLowerCase(java.util.Locale.ROOT));
            context.set("Position", position(player.blockPosition()));
            context.set("Item", MLuaItemWrapper.itemTable(player.getItemInHand(hand), MLuaScriptRuntimeRegistration.forLevel(level)));
            if (player instanceof ServerPlayer serverPlayer) {
                context.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(serverPlayer), MLuaScriptRuntimeRegistration.forLevel(level)));
            }
            Varargs result = callback.invoke(context);
            if (result.arg1().isboolean() && result.arg1().toboolean()) return InteractionResult.SUCCESS;
        } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua item onUse callback failed for '{}': {}", behaviorKey, exception.getMessage());
        }
        return super.use(level, player, hand);
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", behaviorKey, "onUseOnBlock");
        if (!callback.isnil()) try {
            Level level = context.getLevel();
            BlockPos position = context.getClickedPos();
            var registration = MLuaScriptRuntimeRegistration.forLevel(level);
            LuaTable values = new LuaTable();
            values.set("World", level.dimension().identifier().toString());
            values.set("Position", position(position));
            values.set("Face", context.getClickedFace().getName());
            values.set("Hand", context.getHand().name().toLowerCase(java.util.Locale.ROOT));
            values.set("Block", MLuaBlockWrapper.blockTable(level, position, registration));
            values.set("Item", MLuaItemWrapper.itemTable(context.getItemInHand(), registration));
            if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                values.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(serverPlayer), registration));
            }
            Varargs result = callback.invoke(values);
            if (result.arg1().isboolean() && result.arg1().toboolean()) return InteractionResult.SUCCESS;
        } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua item onUseOnBlock callback failed for '{}': {}", behaviorKey, exception.getMessage());
        }
        return super.useOn(context);
    }

    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", behaviorKey, "onConsume");
            if (!callback.isnil()) try {
                var registration = MLuaScriptRuntimeRegistration.forLevel(level);
                LuaTable context = new LuaTable();
                context.set("World", level.dimension().identifier().toString());
                context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
                context.set("Entity", MLuaEntityWrapper.entityTable(entity, registration));
                if (entity instanceof ServerPlayer player) {
                    context.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration));
                }
                callback.invoke(context);
            } catch (Exception exception) {
                MLuaLogger.LOGGER.error("MLua item onConsume callback failed for '{}': {}", behaviorKey, exception.getMessage());
            }
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", behaviorKey, "onInventoryTick");
        if (!callback.isnil()) try {
            var registration = MLuaScriptRuntimeRegistration.forLevel(level);
            LuaTable context = new LuaTable();
            context.set("World", level.dimension().identifier().toString());
            context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
            context.set("Slot", slot);
            context.set("Selected", LuaValue.valueOf(selected));
            context.set("Entity", MLuaEntityWrapper.entityTable(entity, registration));
            if (entity instanceof ServerPlayer player) {
                context.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration));
            }
            callback.invoke(context);
        } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua item onInventoryTick callback failed for '{}': {}", behaviorKey, exception.getMessage());
        }
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", behaviorKey, "onAttackEntity");
        if (!callback.isnil()) try {
            var registration = MLuaScriptRuntimeRegistration.forLevel(target.level());
            LuaTable context = new LuaTable();
            context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
            LuaTable targetTable = target instanceof ServerPlayer player
                ? MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration)
                : MLuaEntityWrapper.entityTable(target, registration);
            LuaTable attackerTable = attacker instanceof ServerPlayer player
                ? MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration)
                : MLuaEntityWrapper.entityTable(attacker, registration);
            context.set("Entity", targetTable);
            context.set("Target", targetTable);
            context.set("Attacker", attackerTable);
            if (attacker instanceof ServerPlayer player) {
                context.set("Player", attackerTable);
            }
            Varargs result = callback.invoke(context);
            if (result.arg1().isboolean() && !result.arg1().toboolean()) return false;
        } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua item onAttackEntity callback failed for '{}': {}", behaviorKey, exception.getMessage());
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override public void onCraftedBy(ItemStack stack, Level level, Player player) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", behaviorKey, "onCrafted");
        if (!callback.isnil()) try {
            var registration = MLuaScriptRuntimeRegistration.forLevel(level);
            LuaTable context = new LuaTable();
            context.set("World", level.dimension().identifier().toString());
            context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
            if (player instanceof ServerPlayer serverPlayer) {
                context.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(serverPlayer), registration));
            }
            callback.invoke(context);
        } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua item onCrafted callback failed for '{}': {}", behaviorKey, exception.getMessage());
        }
        super.onCraftedBy(stack, level, player);
    }

    private static LuaTable position(BlockPos position) {
        LuaTable value = new LuaTable();
        value.set("X", position.getX());
        value.set("Y", position.getY());
        value.set("Z", position.getZ());
        return value;
    }
}
