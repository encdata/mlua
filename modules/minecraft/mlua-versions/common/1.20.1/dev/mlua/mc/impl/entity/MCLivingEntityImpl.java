package dev.mlua.mc.impl.entity;

import dev.mlua.mcapi.damage.MCDamageSource;
import dev.mlua.mcapi.effect.MCMobEffect;
import dev.mlua.mcapi.effect.MCMobEffectInstance;
import dev.mlua.mcapi.entity.MCEquipmentSlot;
import dev.mlua.mcapi.entity.MCLivingEntity;
import dev.mlua.mcapi.entity.attributes.MCAttribute;
import dev.mlua.mcapi.entity.attributes.MCAttributeInstance;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mc.impl.damage.MCDamageSourceImpl;
import dev.mlua.mc.impl.effect.MCMobEffectImpl;
import dev.mlua.mc.impl.effect.MCMobEffectInstanceImpl;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class MCLivingEntityImpl extends MCEntityImpl implements MCLivingEntity {
    private final LivingEntity inner;

    public MCLivingEntityImpl(LivingEntity inner) {
        super(inner);
        this.inner = inner;
    }

    @Override
    public float getHealth() {
        return inner.getHealth();
    }

    @Override
    public void setHealth(float health) {
        inner.setHealth(health);
    }

    @Override
    public float getMaxHealth() {
        return inner.getMaxHealth();
    }

    @Override
    public float getArmorValue() {
        return inner.getArmorValue();
    }

    @Override
    public boolean hurt(MCDamageSource source, float amount) {
        //--version 26.1
        inner.hurt(((MCDamageSourceImpl) source).inner(), amount);
        return true;
        //--endversion
        //--version 1.20.1,1.21.1
        return inner.hurt(((MCDamageSourceImpl) source).inner(), amount);
        //--endversion
    }

    @Override
    public boolean addEffect(MCMobEffectInstance effect) {
        return inner.addEffect(((MCMobEffectInstanceImpl) effect).inner());
    }

    @Override
    public boolean removeEffect(MCMobEffect effect) {
        var mcEffect = ((MCMobEffectImpl) effect).inner();
        var instance = inner.getEffect(mcEffect);
        if (instance != null) {
            inner.removeEffect(mcEffect);
            return true;
        }
        return false;
    }

    @Override
    public boolean hasEffect(MCMobEffect effect) {
        return inner.hasEffect(((MCMobEffectImpl) effect).inner());
    }

    @Override
    public boolean removeAllEffects() {
        inner.removeAllEffects();
        return true;
    }

    @Override
    public MCItemStack getMainHandItem() {
        return MCItemStackImpl.fromReal(inner.getMainHandItem());
    }

    @Override
    public MCItemStack getOffhandItem() {
        return MCItemStackImpl.fromReal(inner.getOffhandItem());
    }

    @Override
    public MCItemStack getItemBySlot(MCEquipmentSlot slot) {
        if (slot == null) {
            return MCItemStackImpl.fromReal(ItemStack.EMPTY);
        }
        return MCItemStackImpl.fromReal(switch (slot) {
            case MAINHAND -> inner.getItemBySlot(EquipmentSlot.MAINHAND);
            case OFFHAND -> inner.getItemBySlot(EquipmentSlot.OFFHAND);
            case FEET -> inner.getItemBySlot(EquipmentSlot.FEET);
            case LEGS -> inner.getItemBySlot(EquipmentSlot.LEGS);
            case CHEST -> inner.getItemBySlot(EquipmentSlot.CHEST);
            case HEAD -> inner.getItemBySlot(EquipmentSlot.HEAD);
        });
    }

    @Override
    public MCAttributeInstance getAttribute(MCAttribute attribute) {
        return null;
    }

    @Override
    public Iterable<MCItemStack> getArmorSlots() {
        //--version 26.1
        return wrapSlots(List.of(
            inner.getItemBySlot(EquipmentSlot.FEET),
            inner.getItemBySlot(EquipmentSlot.LEGS),
            inner.getItemBySlot(EquipmentSlot.CHEST),
            inner.getItemBySlot(EquipmentSlot.HEAD)
        ));
        //--endversion
        //--version 1.20.1,1.21.1
        List<MCItemStack> list = new ArrayList<>();
        for (var stack : inner.getArmorSlots()) {
            list.add(MCItemStackImpl.fromReal(stack));
        }
        return list;
        //--endversion
    }

    @Override
    public Iterable<MCItemStack> getAllSlots() {
        //--version 26.1
        return wrapSlots(List.of(
            inner.getItemBySlot(EquipmentSlot.MAINHAND),
            inner.getItemBySlot(EquipmentSlot.OFFHAND),
            inner.getItemBySlot(EquipmentSlot.FEET),
            inner.getItemBySlot(EquipmentSlot.LEGS),
            inner.getItemBySlot(EquipmentSlot.CHEST),
            inner.getItemBySlot(EquipmentSlot.HEAD)
        ));
        //--endversion
        //--version 1.20.1,1.21.1
        List<MCItemStack> list = new ArrayList<>();
        for (var stack : inner.getAllSlots()) {
            list.add(MCItemStackImpl.fromReal(stack));
        }
        return list;
        //--endversion
    }

    private List<MCItemStack> wrapSlots(Iterable<ItemStack> slots) {
        List<MCItemStack> wrapped = new ArrayList<>();
        for (ItemStack stack : slots) {
            wrapped.add(MCItemStackImpl.fromReal(stack));
        }
        return wrapped;
    }
}
