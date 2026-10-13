package dev.mlua.runtime.client.model;

public record MLuaMeshVertex(float x, float y, float z, float u, float v, int color, float nx, float ny, float nz) {
   public MLuaMeshVertex(float x, float y, float z, float u, float v, int color) {
      this(x, y, z, u, v, color, 0.0F, 1.0F, 0.0F);
   }
}
