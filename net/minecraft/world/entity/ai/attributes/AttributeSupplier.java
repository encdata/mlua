package net.minecraft.world.entity.ai.attributes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;

public class AttributeSupplier {
   private final Map<Attribute, AttributeInstance> f_22241_;

   public AttributeSupplier(Map<Attribute, AttributeInstance> p_22243_) {
      this.f_22241_ = ImmutableMap.copyOf(p_22243_);
   }

   private AttributeInstance m_22260_(Attribute p_22261_) {
      AttributeInstance attributeinstance = this.f_22241_.get(p_22261_);
      if (attributeinstance == null) {
         throw new IllegalArgumentException("Can't find attribute " + BuiltInRegistries.f_256951_.m_7981_(p_22261_));
      } else {
         return attributeinstance;
      }
   }

   public double m_22245_(Attribute p_22246_) {
      return this.m_22260_(p_22246_).m_22135_();
   }

   public double m_22253_(Attribute p_22254_) {
      return this.m_22260_(p_22254_).m_22115_();
   }

   public double m_22247_(Attribute p_22248_, UUID p_22249_) {
      AttributeModifier attributemodifier = this.m_22260_(p_22248_).m_22111_(p_22249_);
      if (attributemodifier == null) {
         throw new IllegalArgumentException("Can't find modifier " + p_22249_ + " on attribute " + BuiltInRegistries.f_256951_.m_7981_(p_22248_));
      } else {
         return attributemodifier.m_22218_();
      }
   }

   @Nullable
   public AttributeInstance m_22250_(Consumer<AttributeInstance> p_22251_, Attribute p_22252_) {
      AttributeInstance attributeinstance = this.f_22241_.get(p_22252_);
      if (attributeinstance == null) {
         return null;
      } else {
         AttributeInstance attributeinstance1 = new AttributeInstance(p_22252_, p_22251_);
         attributeinstance1.m_22102_(attributeinstance);
         return attributeinstance1;
      }
   }

   public static AttributeSupplier.Builder m_22244_() {
      return new AttributeSupplier.Builder();
   }

   public boolean m_22258_(Attribute p_22259_) {
      return this.f_22241_.containsKey(p_22259_);
   }

   public boolean m_22255_(Attribute p_22256_, UUID p_22257_) {
      AttributeInstance attributeinstance = this.f_22241_.get(p_22256_);
      return attributeinstance != null && attributeinstance.m_22111_(p_22257_) != null;
   }

   public static class Builder {
      private final Map<Attribute, AttributeInstance> f_22262_ = Maps.newHashMap();
      private boolean f_22263_;

      private AttributeInstance m_22274_(Attribute p_22275_) {
         AttributeInstance attributeinstance = new AttributeInstance(p_22275_, (p_258260_) -> {
            if (this.f_22263_) {
               throw new UnsupportedOperationException("Tried to change value for default attribute instance: " + BuiltInRegistries.f_256951_.m_7981_(p_22275_));
            }
         });
         this.f_22262_.put(p_22275_, attributeinstance);
         return attributeinstance;
      }

      public AttributeSupplier.Builder m_22266_(Attribute p_22267_) {
         this.m_22274_(p_22267_);
         return this;
      }

      public AttributeSupplier.Builder m_22268_(Attribute p_22269_, double p_22270_) {
         AttributeInstance attributeinstance = this.m_22274_(p_22269_);
         attributeinstance.m_22100_(p_22270_);
         return this;
      }

      public AttributeSupplier m_22265_() {
         this.f_22263_ = true;
         return new AttributeSupplier(this.f_22262_);
      }
   }
}
