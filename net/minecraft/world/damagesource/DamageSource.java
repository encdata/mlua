package net.minecraft.world.damagesource;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class DamageSource {
   private final Holder<DamageType> f_268495_;
   @Nullable
   private final Entity f_268569_;
   @Nullable
   private final Entity f_268595_;
   @Nullable
   private final Vec3 f_268454_;

   public String toString() {
      return "DamageSource (" + this.m_269415_().f_268677_() + ")";
   }

   public float m_19377_() {
      return this.m_269415_().f_268663_();
   }

   public boolean m_269014_() {
      return this.f_268569_ != this.f_268595_;
   }

   public DamageSource(Holder<DamageType> p_270906_, @Nullable Entity p_270796_, @Nullable Entity p_270459_, @Nullable Vec3 p_270623_) {
      this.f_268495_ = p_270906_;
      this.f_268569_ = p_270459_;
      this.f_268595_ = p_270796_;
      this.f_268454_ = p_270623_;
   }

   public DamageSource(Holder<DamageType> p_270818_, @Nullable Entity p_270162_, @Nullable Entity p_270115_) {
      this(p_270818_, p_270162_, p_270115_, (Vec3)null);
   }

   public DamageSource(Holder<DamageType> p_270690_, Vec3 p_270579_) {
      this(p_270690_, (Entity)null, (Entity)null, p_270579_);
   }

   public DamageSource(Holder<DamageType> p_270811_, @Nullable Entity p_270660_) {
      this(p_270811_, p_270660_, p_270660_);
   }

   public DamageSource(Holder<DamageType> p_270475_) {
      this(p_270475_, (Entity)null, (Entity)null, (Vec3)null);
   }

   @Nullable
   public Entity m_7640_() {
      return this.f_268595_;
   }

   @Nullable
   public Entity m_7639_() {
      return this.f_268569_;
   }

   public Component m_6157_(LivingEntity p_19343_) {
      String s = "death.attack." + this.m_269415_().f_268677_();
      if (this.f_268569_ == null && this.f_268595_ == null) {
         LivingEntity livingentity1 = p_19343_.m_21232_();
         String s1 = s + ".player";
         return livingentity1 != null ? Component.m_237110_(s1, p_19343_.m_5446_(), livingentity1.m_5446_()) : Component.m_237110_(s, p_19343_.m_5446_());
      } else {
         Component component = this.f_268569_ == null ? this.f_268595_.m_5446_() : this.f_268569_.m_5446_();
         Entity entity = this.f_268569_;
         ItemStack itemstack1;
         if (entity instanceof LivingEntity) {
            LivingEntity livingentity = (LivingEntity)entity;
            itemstack1 = livingentity.m_21205_();
         } else {
            itemstack1 = ItemStack.f_41583_;
         }

         ItemStack itemstack = itemstack1;
         return !itemstack.m_41619_() && itemstack.m_41788_() ? Component.m_237110_(s + ".item", p_19343_.m_5446_(), component, itemstack.m_41611_()) : Component.m_237110_(s, p_19343_.m_5446_(), component);
      }
   }

   public String m_19385_() {
      return this.m_269415_().f_268677_();
   }

   public boolean m_7986_() {
      boolean flag;
      switch (this.m_269415_().f_268501_()) {
         case NEVER:
            flag = false;
            break;
         case WHEN_CAUSED_BY_LIVING_NON_PLAYER:
            flag = this.f_268569_ instanceof LivingEntity && !(this.f_268569_ instanceof Player);
            break;
         case ALWAYS:
            flag = true;
            break;
         default:
            throw new IncompatibleClassChangeError();
      }

      return flag;
   }

   public boolean m_19390_() {
      Entity entity = this.m_7639_();
      if (entity instanceof Player player) {
         if (player.m_150110_().f_35937_) {
            return true;
         }
      }

      return false;
   }

   @Nullable
   public Vec3 m_7270_() {
      if (this.f_268454_ != null) {
         return this.f_268454_;
      } else {
         return this.f_268595_ != null ? this.f_268595_.m_20182_() : null;
      }
   }

   @Nullable
   public Vec3 m_269181_() {
      return this.f_268454_;
   }

   public boolean m_269533_(TagKey<DamageType> p_270890_) {
      return this.f_268495_.m_203656_(p_270890_);
   }

   public boolean m_276093_(ResourceKey<DamageType> p_276108_) {
      return this.f_268495_.m_203565_(p_276108_);
   }

   public DamageType m_269415_() {
      return this.f_268495_.m_203334_();
   }

   public Holder<DamageType> m_269150_() {
      return this.f_268495_;
   }
}
