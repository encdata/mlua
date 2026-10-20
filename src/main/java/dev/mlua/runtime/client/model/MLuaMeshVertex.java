package dev.mlua.runtime.client.model;

public record MLuaMeshVertex(float x, float y, float z, float u, float v, int color, float nx, float ny, float nz) {
   public MLuaMeshVertex {
      if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
            || !Float.isFinite(u) || !Float.isFinite(v)
            || !Float.isFinite(nx) || !Float.isFinite(ny) || !Float.isFinite(nz)) {
         throw new IllegalArgumentException("Mesh vertex coordinates, UVs, and normals must be finite");
      }
   }

   public MLuaMeshVertex(float x, float y, float z, float u, float v, int color) {
      this(x, y, z, u, v, color, 0.0F, 1.0F, 0.0F);
   }
}
