package net.minecraft.core.particles;

import com.mojang.brigadier.StringReader;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;

public class SimpleParticleType extends ParticleType<SimpleParticleType> implements ParticleOptions {
   private static final ParticleOptions.Deserializer<SimpleParticleType> f_123833_ = new ParticleOptions.Deserializer<SimpleParticleType>() {
      public SimpleParticleType m_5739_(ParticleType<SimpleParticleType> p_123846_, StringReader p_123847_) {
         return (SimpleParticleType)p_123846_;
      }

      public SimpleParticleType m_6507_(ParticleType<SimpleParticleType> p_123849_, FriendlyByteBuf p_123850_) {
         return (SimpleParticleType)p_123849_;
      }
   };
   private final Codec<SimpleParticleType> f_123834_ = Codec.unit(this::m_6012_);

   public SimpleParticleType(boolean p_123837_) {
      super(p_123837_, f_123833_);
   }

   public SimpleParticleType m_6012_() {
      return this;
   }

   public Codec<SimpleParticleType> m_7652_() {
      return this.f_123834_;
   }

   public void m_7711_(FriendlyByteBuf p_123840_) {
   }

   public String m_5942_() {
      return BuiltInRegistries.f_257034_.m_7981_(this).toString();
   }
}
