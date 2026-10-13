package dev.mlua.mc.impl.entity;

import dev.mlua.mcapi.entity.MCProjectile;
import net.minecraft.world.entity.projectile.Projectile;

public class MCProjectileImpl extends MCEntityImpl implements MCProjectile {

    public MCProjectileImpl(Projectile inner) {
        super(inner);
    }

    private Projectile projectile() {
        return (Projectile) inner;
    }

    public static MCProjectileImpl fromReal(Projectile inner) {
        return inner == null ? null : new MCProjectileImpl(inner);
    }
}
