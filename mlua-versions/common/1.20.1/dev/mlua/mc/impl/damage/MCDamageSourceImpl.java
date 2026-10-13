package dev.mlua.mc.impl.damage;

import dev.mlua.mcapi.damage.MCDamageSource;
import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

public class MCDamageSourceImpl implements MCDamageSource {
    private final DamageSource inner;

    public MCDamageSourceImpl(DamageSource inner) {
        this.inner = inner;
    }

    public DamageSource inner() {
        return inner;
    }

    @Override
    public MCEntity getEntity() {
        var entity = inner.getEntity();
        return entity == null ? null : new MCEntityImpl(entity);
    }

    @Override
    public MCEntity getDirectEntity() {
        var entity = inner.getDirectEntity();
        return entity == null ? null : new MCEntityImpl(entity);
    }

    @Override
    public boolean isProjectile() {
        return inner.is(DamageTypeTags.IS_PROJECTILE);
    }

    @Override
    public boolean isExplosion() {
        return inner.is(DamageTypeTags.IS_EXPLOSION);
    }

    @Override
    public boolean isFall() {
        return inner.is(DamageTypeTags.IS_FALL);
    }

    @Override
    public boolean isFire() {
        return inner.is(DamageTypeTags.IS_FIRE);
    }

    @Override
    public boolean isMagic() {
        return inner.is(DamageTypeTags.WITCH_RESISTANT_TO);
    }
}
