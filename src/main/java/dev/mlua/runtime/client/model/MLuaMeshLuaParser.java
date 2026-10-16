package dev.mlua.runtime.client.model;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Converts the Lua-facing mesh definition into the immutable render representation. */
public final class MLuaMeshLuaParser {
    public static final int MAX_VERTICES = 16_384;
    public static final int MAX_INDICES = 49_152;

    private MLuaMeshLuaParser() { }

    public static MLuaMeshDefinition parse(LuaTable definition) {
        List<MLuaMeshVertex> vertices = parseVertices(definition.get("Vertices"));
        List<Integer> indices = parseIndices(definition.get("Indices"), vertices.size());
        LuaValue textureValue = definition.get("Texture");
        if (!textureValue.isnil() && !textureValue.isstring()) {
            throw error("Texture must be a resource identifier string.");
        }
        return new MLuaMeshDefinition(vertices, indices, textureValue.isnil() ? null : textureValue.tojstring());
    }

    private static List<MLuaMeshVertex> parseVertices(LuaValue value) {
        if (!value.istable()) throw error("Vertices must be an array.");
        LuaTable table = value.checktable();
        int count = denseArrayLength(table, "Vertices", MAX_VERTICES);
        List<MLuaMeshVertex> vertices = new ArrayList<>(count);
        for (int position = 1; position <= count; position++) {
            LuaValue vertexValue = table.get(position);
            if (!vertexValue.istable()) throw error("Vertices[" + position + "] must be a table.");
            LuaTable vertex = vertexValue.checktable();
            double[] coordinates = vector3(vertex.get("Position"), "Vertices[" + position + "].Position");
            LuaValue uvValue = vertex.get("UV");
            double[] uv = uvValue.isnil() ? new double[]{0.0, 0.0}
                : vector2(uvValue, "Vertices[" + position + "].UV");
            int color = vertex.get("Color").isnil() ? 0xFFFFFFFF : packedColor(vertex.get("Color"), position);
            vertices.add(new MLuaMeshVertex(
                meshFloat(coordinates[0], "vertex position X"),
                meshFloat(coordinates[1], "vertex position Y"),
                meshFloat(coordinates[2], "vertex position Z"),
                meshFloat(uv[0], "vertex UV X"), meshFloat(uv[1], "vertex UV Y"), color));
        }
        if (vertices.size() < 3) throw error("Vertices must contain at least three points.");
        return vertices;
    }

    private static List<Integer> parseIndices(LuaValue value, int vertexCount) {
        if (!value.istable()) throw error("Indices must be an array.");
        LuaTable table = value.checktable();
        int count = denseArrayLength(table, "Indices", MAX_INDICES);
        List<Integer> indices = new ArrayList<>(count);
        for (int position = 1; position <= count; position++) {
            int index = wholeIndex(table.get(position), position);
            if (index < 0 || index >= vertexCount) {
                throw error("index " + index + " is outside the zero-based vertex range 0.." + (vertexCount - 1) + ".");
            }
            indices.add(index);
        }
        if (indices.isEmpty() || indices.size() % 3 != 0) {
            throw error("Indices must contain complete triangles (groups of three zero-based vertex indices).");
        }
        return indices;
    }

    private static int denseArrayLength(LuaTable table, String field, int limit) {
        int count = 0;
        int maximum = 0;
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = table.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (!key.isnumber()) throw error(field + " must use positive whole-number array indices.");
            double index = key.todouble();
            if (!Double.isFinite(index) || index < 1 || index != Math.rint(index) || index > Integer.MAX_VALUE) {
                throw error(field + " must use positive whole-number array indices.");
            }
            count++;
            if (count > limit) throw error(field + " cannot contain more than " + limit + " entries.");
            maximum = Math.max(maximum, (int)index);
        }
        if (maximum != count) throw error(field + " must be a dense array without missing entries.");
        return count;
    }

    private static int wholeIndex(LuaValue value, int position) {
        if (!value.isnumber()) throw error("Indices[" + position + "] must be a zero-based whole-number vertex index.");
        double number = value.todouble();
        if (!Double.isFinite(number) || number != Math.rint(number)
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw error("Indices[" + position + "] must be a zero-based whole-number vertex index.");
        }
        return (int)number;
    }

    private static double[] vector3(LuaValue value, String field) {
        double[] vector;
        try {
            vector = MLuaCFrame.vector(value);
        } catch (RuntimeException invalid) {
            throw error(field + " must be a Vector3 or CFrame position.");
        }
        for (double component : vector) {
            if (!Double.isFinite(component)) throw error(field + " components must be finite.");
        }
        return vector;
    }

    private static double[] vector2(LuaValue value, String field) {
        if (!MLuaVector.isVector2(value)) throw error(field + " must be a Vector2.");
        LuaTable vector = value.checktable();
        return new double[]{finite(vector.get("X"), field), finite(vector.get("Y"), field)};
    }

    private static int packedColor(LuaValue value, int position) {
        if (!value.isnumber()) throw error("Vertices[" + position + "].Color must be a packed integer color.");
        double color = value.todouble();
        if (!Double.isFinite(color) || color != Math.rint(color) || color < Integer.MIN_VALUE || color > 0xFFFFFFFFL) {
            throw error("Vertices[" + position + "].Color must be a packed 32-bit integer color.");
        }
        return (int)(long)color;
    }

    private static float meshFloat(double value, String field) {
        if (!Double.isFinite(value) || Math.abs(value) > Float.MAX_VALUE) {
            throw error(field + " must be finite and representable by the render vertex format.");
        }
        return (float)value;
    }

    private static double finite(LuaValue value, String field) {
        if (!value.isnumber()) throw error(field + " components must be finite numbers.");
        double number = value.todouble();
        if (!Double.isFinite(number)) throw error(field + " components must be finite numbers.");
        return number;
    }

    private static LuaError error(String message) {
        return new LuaError("[MLua] RenderService:CreateMesh " + message);
    }
}
