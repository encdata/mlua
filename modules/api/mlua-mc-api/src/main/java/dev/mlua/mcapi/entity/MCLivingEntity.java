package dev.mlua.mcapi.entity;

import dev.mlua.mcapi.damage.MCDamageSource;
import dev.mlua.mcapi.effect.MCMobEffect;
import dev.mlua.mcapi.effect.MCMobEffectInstance;
import dev.mlua.mcapi.entity.attributes.MCAttribute;
import dev.mlua.mcapi.entity.attributes.MCAttributeInstance;
import dev.mlua.mcapi.item.MCItemStack;

public interface MCLivingEntity extends MCEntity {
    float getHealth();
    void setHealth(float health);
    float getMaxHealth();
    float getArmorValue();
    boolean hurt(MCDamageSource source, float amount);
    boolean addEffect(MCMobEffectInstance effect);
    boolean removeEffect(MCMobEffect effect);
    boolean hasEffect(MCMobEffect effect);
    boolean removeAllEffects();
    MCItemStack getMainHandItem();
    MCItemStack getOffhandItem();
    MCItemStack getItemBySlot(MCEquipmentSlot slot);
    MCAttributeInstance getAttribute(MCAttribute attribute);
    boolean isAlive();
    Iterable<MCItemStack> getArmorSlots();
    Iterable<MCItemStack> getAllSlots();
}
