package dev.mlua.mcapi.damage;

import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mcapi.entity.MCLivingEntity;
import dev.mlua.mcapi.entity.MCPlayer;

public interface MCDamageSource {
    MCEntity getEntity();
    MCEntity getDirectEntity();
    boolean isProjectile();
    boolean isExplosion();
    boolean isFall();
    boolean isFire();
    boolean isMagic();

    static MCDamageSource playerAttack(MCPlayer player) {
        return new MCDamageSource() {
            @Override
            public MCEntity getEntity() {
                return player;
            }

            @Override
            public MCEntity getDirectEntity() {
                return player;
            }

            @Override
            public boolean isProjectile() {
                return false;
            }

            @Override
            public boolean isExplosion() {
                return false;
            }

            @Override
            public boolean isFall() {
                return false;
            }

            @Override
            public boolean isFire() {
                return false;
            }

            @Override
            public boolean isMagic() {
                return false;
            }
        };
    }

    static MCDamageSource mobAttack(MCLivingEntity livingEntity) {
        return new MCDamageSource() {
            @Override
            public MCEntity getEntity() {
                return livingEntity;
            }

            @Override
            public MCEntity getDirectEntity() {
                return livingEntity;
            }

            @Override
            public boolean isProjectile() {
                return false;
            }

            @Override
            public boolean isExplosion() {
                return false;
            }

            @Override
            public boolean isFall() {
                return false;
            }

            @Override
            public boolean isFire() {
                return false;
            }

            @Override
            public boolean isMagic() {
                return false;
            }
        };
    }

    static MCDamageSource indirectMagic(MCEntity source, MCEntity indirectEntity) {
        return new MCDamageSource() {
            @Override
            public MCEntity getEntity() {
                return source;
            }

            @Override
            public MCEntity getDirectEntity() {
                return indirectEntity;
            }

            @Override
            public boolean isProjectile() {
                return false;
            }

            @Override
            public boolean isExplosion() {
                return false;
            }

            @Override
            public boolean isFall() {
                return false;
            }

            @Override
            public boolean isFire() {
                return false;
            }

            @Override
            public boolean isMagic() {
                return true;
            }
        };
    }

    static MCDamageSource arrow(MCEntity source, MCEntity indirectEntity) {
        return new MCDamageSource() {
            @Override
            public MCEntity getEntity() {
                return source;
            }

            @Override
            public MCEntity getDirectEntity() {
                return indirectEntity;
            }

            @Override
            public boolean isProjectile() {
                return true;
            }

            @Override
            public boolean isExplosion() {
                return false;
            }

            @Override
            public boolean isFall() {
                return false;
            }

            @Override
            public boolean isFire() {
                return false;
            }

            @Override
            public boolean isMagic() {
                return false;
            }
        };
    }
}
