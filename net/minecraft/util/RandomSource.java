package net.minecraft.util;

import io.netty.util.internal.ThreadLocalRandom;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource;

public interface RandomSource {
   /** @deprecated */
   @Deprecated
   double f_216326_ = 2.297D;

   static RandomSource m_216327_() {
      return m_216335_(RandomSupport.m_224599_());
   }

   /** @deprecated */
   @Deprecated
   static RandomSource m_216337_() {
      return new ThreadSafeLegacyRandomSource(RandomSupport.m_224599_());
   }

   static RandomSource m_216335_(long p_216336_) {
      return new LegacyRandomSource(p_216336_);
   }

   static RandomSource m_216343_() {
      return new SingleThreadedRandomSource(ThreadLocalRandom.current().nextLong());
   }

   RandomSource m_213769_();

   PositionalRandomFactory m_188582_();

   void m_188584_(long p_216342_);

   int m_188502_();

   int m_188503_(int p_216331_);

   default int m_216332_(int p_216333_, int p_216334_) {
      return this.m_188503_(p_216334_ - p_216333_ + 1) + p_216333_;
   }

   long m_188505_();

   boolean m_188499_();

   float m_188501_();

   double m_188500_();

   double m_188583_();

   default double m_216328_(double p_216329_, double p_216330_) {
      return p_216329_ + p_216330_ * (this.m_188500_() - this.m_188500_());
   }

   default void m_190110_(int p_216338_) {
      for(int i = 0; i < p_216338_; ++i) {
         this.m_188502_();
      }

   }

   default int m_216339_(int p_216340_, int p_216341_) {
      if (p_216340_ >= p_216341_) {
         throw new IllegalArgumentException("bound - origin is non positive");
      } else {
         return p_216340_ + this.m_188503_(p_216341_ - p_216340_);
      }
   }
}
