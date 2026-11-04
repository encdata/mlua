package dev.mlua.runtime.registry;

public record MLuaEntityVisualDescriptor(String texture, String model, float shadowRadius) {
   public String normalizedModel() {
      return this.model != null && !this.model.isBlank() ? this.model : "zombie";
   }

   public String normalizedTexture() {
      return this.texture != null && !this.texture.isBlank() ? this.texture : "minecraft:textures/entity/zombie/zombie.png";
   }
}
