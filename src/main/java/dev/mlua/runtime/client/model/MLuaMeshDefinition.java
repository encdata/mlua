package dev.mlua.runtime.client.model;

import java.util.List;

public record MLuaMeshDefinition(List<MLuaMeshVertex> vertices, List<Integer> indices, String texture) {
   public MLuaMeshDefinition {
      vertices = List.copyOf(vertices);
      indices = List.copyOf(indices);
   }
}
