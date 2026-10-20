package dev.mlua.runtime.client.model;

import java.util.List;

public record MLuaMeshDefinition(List<MLuaMeshVertex> vertices, List<Integer> indices, String texture) {
   public MLuaMeshDefinition {
      vertices = List.copyOf(vertices);
      indices = List.copyOf(indices);
      if (vertices.size() < 3 || vertices.size() > MLuaMeshLuaParser.MAX_VERTICES) {
         throw new IllegalArgumentException("Mesh must contain between 3 and "
            + MLuaMeshLuaParser.MAX_VERTICES + " vertices");
      }
      if (indices.isEmpty() || indices.size() > MLuaMeshLuaParser.MAX_INDICES || indices.size() % 3 != 0) {
         throw new IllegalArgumentException("Mesh indices must contain complete triangles and not exceed "
            + MLuaMeshLuaParser.MAX_INDICES + " entries");
      }
      for (int index : indices) {
         if (index < 0 || index >= vertices.size()) {
            throw new IllegalArgumentException("Mesh index " + index + " is outside the vertex range");
         }
      }
   }
}
