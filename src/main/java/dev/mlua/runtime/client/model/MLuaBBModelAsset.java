package dev.mlua.runtime.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Normalized subset of Blockbench's versioned .bbmodel project format. */
public final class MLuaBBModelAsset {
   public static final int MAX_CUBES = 8_192;
   public static final int MAX_BONES = 2_048;
   public static final int MAX_TEXTURES = 256;
   public static final int MAX_ANIMATIONS = 256;
   public static final int MAX_BONE_DEPTH = 256;
   public static final int MAX_JSON_CHARS = 8 * 1024 * 1024;
   private static final int MAX_KEYFRAMES_PER_ANIMATION = 32_768;
   private final String formatVersion;
   private final String modelFormat;
   private final boolean flipY;
   private final int textureWidth;
   private final int textureHeight;
   private final List<String> textures;
   private final List<String> embeddedTextures;
   private final List<Cube> cubes;
   private final List<Bone> bones;
   private final Map<String, String> cubeParents;
   private final Map<String, Animation> animations;
   private final Map<String, List<Bone>> cubeBoneChains;

   private MLuaBBModelAsset(String formatVersion, String modelFormat, boolean flipY, int textureWidth, int textureHeight,
                            List<String> textures, List<String> embeddedTextures, List<Cube> cubes, List<Bone> bones, Map<String, String> cubeParents, Map<String, Animation> animations) {
      this.formatVersion = formatVersion;
      this.modelFormat = modelFormat;
      this.flipY = flipY;
      this.textureWidth = textureWidth;
      this.textureHeight = textureHeight;
      this.textures = List.copyOf(textures);
      this.embeddedTextures = List.copyOf(embeddedTextures);
      this.cubes = List.copyOf(cubes);
      this.bones = List.copyOf(bones);
      this.cubeParents = Map.copyOf(cubeParents);
      this.animations = Map.copyOf(animations);
      this.cubeBoneChains = resolveBoneChains(this.bones, this.cubeParents);
   }

   public static MLuaBBModelAsset parse(String json) {
      if (json.length() > MAX_JSON_CHARS) throw new IllegalArgumentException("Blockbench model exceeds the 8 MiB source limit");
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      JsonObject meta = object(root, "meta");
      String version = string(meta, "format_version", string(root, "format_version", "unknown"));
      String format = string(meta, "model_format", string(root, "model_format", "generic"));
      boolean flipY = bool(root, "modded_entity_flip_y", false);
      JsonObject resolution = object(root, "resolution");
      int width = integer(resolution, "width", 16);
      int height = integer(resolution, "height", 16);

      List<Cube> cubes = new ArrayList<>();
      List<String> textures = new ArrayList<>();
      List<String> embeddedTextures = new ArrayList<>();
      List<Bone> bones = new ArrayList<>();
      Map<String, String> cubeParents = new LinkedHashMap<>();
      for (JsonElement texture : array(root, "textures")) {
         if (!texture.isJsonObject()) continue;
         JsonObject value = texture.getAsJsonObject();
         String path = string(value, "relative_path", string(value, "path", string(value, "name", "")));
         if (!path.isBlank()) {
            requireCapacity(textures.size(), MAX_TEXTURES, "textures");
            textures.add(path);
         }
         embeddedTextures.add(string(value, "data_url", string(value, "source", "")));
      }
      JsonArray elements = array(root, "elements");
      for (JsonElement element : elements) {
         if (!element.isJsonObject()) continue;
         JsonObject value = element.getAsJsonObject();
         if (value.has("from") && value.has("to")) {
            requireCapacity(cubes.size(), MAX_CUBES, "cubes");
            cubes.add(Cube.from(value));
         }
      }

      // Bedrock projects store geometry under minecraft:geometry instead of
      // the generic elements/outliner pair. Normalize their bones and cubes
      // into the same runtime representation.
      JsonArray geometries = array(root, "minecraft:geometry");
      if (cubes.isEmpty() && !geometries.isEmpty()) {
         JsonObject description = object(geometries.get(0).getAsJsonObject(), "description");
         if (description.has("texture_width")) width = integer(description, "texture_width", width);
         if (description.has("texture_height")) height = integer(description, "texture_height", height);
         JsonArray bedrockBones = array(geometries.get(0).getAsJsonObject(), "bones");
         for (JsonElement entry : bedrockBones) {
            if (!entry.isJsonObject()) continue;
            requireCapacity(bones.size(), MAX_BONES, "bones");
            JsonObject bone = entry.getAsJsonObject();
            String uuid = string(bone, "name", "bone_" + bones.size());
            bones.add(new Bone(uuid, uuid, string(bone, "parent", null), vectorOrZero(bone, "pivot"), vectorOrZero(bone, "rotation")));
            for (JsonElement rawCube : array(bone, "cubes")) {
               if (!rawCube.isJsonObject()) continue;
               requireCapacity(cubes.size(), MAX_CUBES, "cubes");
               JsonObject cube = rawCube.getAsJsonObject();
               String cubeId = uuid + "#" + cubes.size();
               cubes.add(Cube.fromBedrock(cube, cubeId));
               cubeParents.put(cubeId, uuid);
            }
         }
      }

      JsonElement hierarchy = root.get("outliner");
      if (hierarchy != null && hierarchy.isJsonArray()) {
         readOutliner(hierarchy.getAsJsonArray(), bones, cubeParents, null, 0);
      }
      if (bones.isEmpty()) {
         JsonArray groups = array(root, "groups");
         for (JsonElement group : groups) {
            if (group.isJsonObject()) {
               JsonObject value = group.getAsJsonObject();
               requireCapacity(bones.size(), MAX_BONES, "bones");
               bones.add(Bone.from(value, null));
            }
         }
      }

      Map<String, Animation> animations = new LinkedHashMap<>();
      JsonArray animationArray = array(root, "animations");
      for (JsonElement animation : animationArray) {
         if (!animation.isJsonObject()) continue;
         requireCapacity(animations.size(), MAX_ANIMATIONS, "animations");
         JsonObject value = animation.getAsJsonObject();
         String name = string(value, "name", "animation_" + animations.size());
         if (animations.putIfAbsent(name, Animation.from(value, version)) != null) {
            throw new IllegalArgumentException("Blockbench model contains duplicate animation '" + name + "'");
         }
      }
      while (embeddedTextures.size() < textures.size()) embeddedTextures.add("");
      if (flipY) flipY(cubes, bones);
      return new MLuaBBModelAsset(version, format, flipY, width, height, textures, embeddedTextures, cubes, bones, cubeParents, animations);
   }

   private static void readOutliner(JsonArray entries, List<Bone> bones, Map<String, String> cubeParents,
                                    String parent, int depth) {
      if (depth > MAX_BONE_DEPTH) throw new IllegalArgumentException("Blockbench bone hierarchy exceeds " + MAX_BONE_DEPTH + " levels");
      for (JsonElement entry : entries) {
         if (!entry.isJsonObject()) continue;
         requireCapacity(bones.size(), MAX_BONES, "bones");
         JsonObject value = entry.getAsJsonObject();
         String uuid = string(value, "uuid", string(value, "name", "bone_" + bones.size()));
         String name = string(value, "name", uuid);
         bones.add(Bone.from(value, parent));
         JsonArray children = array(value, "children");
         for (JsonElement child : children) {
            if (child.isJsonPrimitive()) cubeParents.put(child.getAsString(), uuid);
         }
         if (!children.isEmpty()) readOutliner(children, bones, cubeParents, uuid, depth + 1);
      }
   }

   public String formatVersion() { return formatVersion; }
   public String modelFormat() { return modelFormat; }
   public boolean flipY() { return flipY; }
   public int textureWidth() { return textureWidth; }
   public int textureHeight() { return textureHeight; }
   public List<String> textures() { return textures; }
   public List<String> embeddedTextures() { return embeddedTextures; }
   public List<Cube> cubes() { return cubes; }
   public List<Bone> bones() { return bones; }
   public List<Bone> boneChainForCube(String cubeUuid) {
      return cubeBoneChains.getOrDefault(cubeUuid, List.of());
   }
   public Map<String, Animation> animations() { return animations; }

   public MLuaBBModelAsset withAnimations(Map<String, Animation> additional) {
      Map<String, Animation> merged = new LinkedHashMap<>(animations);
      for (Map.Entry<String, Animation> entry : additional.entrySet()) {
         if (!merged.containsKey(entry.getKey()) && merged.size() >= MAX_ANIMATIONS) {
            throw new IllegalArgumentException("Combined Blockbench asset exceeds the limit of " + MAX_ANIMATIONS + " animations");
         }
         merged.put(entry.getKey(), entry.getValue());
      }
      return new MLuaBBModelAsset(formatVersion, modelFormat, flipY, textureWidth, textureHeight,
         textures, embeddedTextures, cubes, bones, cubeParents, merged);
   }

   public static Map<String, Animation> parseAnimations(String json) {
      if (json.length() > MAX_JSON_CHARS) throw new IllegalArgumentException("Blockbench animation file exceeds the 8 MiB source limit");
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      String version = string(object(root, "meta"), "format_version", string(root, "format_version", "unknown"));
      Map<String, Animation> result = new LinkedHashMap<>();
      JsonArray animationArray = array(root, "animations");
      if (animationArray.size() > MAX_ANIMATIONS) {
         throw new IllegalArgumentException("Blockbench animation file exceeds the limit of " + MAX_ANIMATIONS + " animations");
      }
      for (JsonElement element : animationArray) {
         if (!element.isJsonObject()) continue;
         JsonObject value = element.getAsJsonObject();
         String name = string(value, "name", "animation_" + result.size());
         if (result.putIfAbsent(name, Animation.from(value, version)) != null) {
            throw new IllegalArgumentException("Blockbench animation file contains duplicate animation '" + name + "'");
         }
      }
      return result;
   }

   private static Map<String, List<Bone>> resolveBoneChains(List<Bone> bones, Map<String, String> cubeParents) {
      Map<String, Bone> byId = new LinkedHashMap<>();
      for (Bone bone : bones) {
         if (byId.putIfAbsent(bone.uuid(), bone) != null) {
            throw new IllegalArgumentException("Blockbench model contains duplicate bone id '" + bone.uuid() + "'");
         }
      }
      Map<String, List<Bone>> byBone = new LinkedHashMap<>();
      java.util.Set<String> resolving = new java.util.HashSet<>();
      for (String id : byId.keySet()) resolveBoneChain(id, byId, byBone, resolving, 0);
      Map<String, List<Bone>> byCube = new LinkedHashMap<>();
      cubeParents.forEach((cube, parent) -> {
         List<Bone> chain = byBone.get(parent);
         if (chain == null && parent != null) {
            throw new IllegalArgumentException("Blockbench cube references missing bone '" + parent + "'");
         }
         byCube.put(cube, chain == null ? List.of() : chain);
      });
      return Map.copyOf(byCube);
   }

   private static List<Bone> resolveBoneChain(String id, Map<String, Bone> byId,
         Map<String, List<Bone>> resolved, java.util.Set<String> resolving, int depth) {
      List<Bone> cached = resolved.get(id);
      if (cached != null) return cached;
      if (depth >= MAX_BONE_DEPTH) {
         throw new IllegalArgumentException("Blockbench bone hierarchy exceeds " + MAX_BONE_DEPTH + " levels");
      }
      if (!resolving.add(id)) throw new IllegalArgumentException("Blockbench bone hierarchy contains a cycle at '" + id + "'");
      Bone bone = byId.get(id);
      if (bone == null) throw new IllegalArgumentException("Blockbench bone references missing parent '" + id + "'");
      List<Bone> chain = new ArrayList<>();
      if (bone.parent() != null && !bone.parent().isBlank()) {
         chain.addAll(resolveBoneChain(bone.parent(), byId, resolved, resolving, depth + 1));
      }
      chain.add(bone);
      resolving.remove(id);
      List<Bone> immutable = List.copyOf(chain);
      resolved.put(id, immutable);
      return immutable;
   }

   private static void requireCapacity(int current, int maximum, String field) {
      if (current >= maximum) {
         throw new IllegalArgumentException("Blockbench model exceeds the limit of " + maximum + " " + field);
      }
   }

   public record Cube(String uuid, String name, float[] from, float[] to, Map<String, Face> faces) {
      static Cube from(JsonObject value) {
         return new Cube(string(value, "uuid", ""), string(value, "name", "cube"), vector(value.getAsJsonArray("from")),
            vector(value.getAsJsonArray("to")), parseFaces(value.getAsJsonObject("faces")));
      }
      static Cube fromBedrock(JsonObject value, String uuid) {
         float[] from = vectorOrZero(value, "origin");
         float[] size = vectorOrZero(value, "size");
         return new Cube(uuid, uuid, from, new float[]{from[0] + size[0], from[1] + size[1], from[2] + size[2]}, Map.of());
      }
   }

   public record Face(int texture, float[] uv, int rotation) { }
   public record Bone(String uuid, String name, String parent, float[] pivot, float[] rotation) {
      static Bone from(JsonObject value, String parent) {
         return new Bone(string(value, "uuid", string(value, "name", "root")), string(value, "name", "root"), parent,
            value.has("origin") && value.get("origin").isJsonArray() ? vector(value.getAsJsonArray("origin")) : new float[]{0, 0, 0},
            value.has("rotation") && value.get("rotation").isJsonArray() ? vector(value.getAsJsonArray("rotation")) : new float[]{0, 0, 0});
      }
   }
   public record Animation(String name, String loop, float length, Map<String, Map<String, List<Keyframe>>> tracks) {
      static Animation from(JsonObject value, String version) {
         Map<String, Map<String, List<Keyframe>>> tracks = new LinkedHashMap<>();
         JsonObject animators = object(value, "animators");
         int totalKeyframes = 0;
         for (Map.Entry<String, JsonElement> animator : animators.entrySet()) {
            if (!animator.getValue().isJsonObject()) continue;
            JsonObject channels = animator.getValue().getAsJsonObject();
            Map<String, List<Keyframe>> channelMap = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> channel : channels.entrySet()) {
               if (!channel.getValue().isJsonArray()) continue;
               List<Keyframe> keyframes = new ArrayList<>();
               JsonArray rawKeyframes = channel.getValue().getAsJsonArray();
               totalKeyframes += rawKeyframes.size();
               if (totalKeyframes > MAX_KEYFRAMES_PER_ANIMATION) {
                  throw new IllegalArgumentException("Blockbench animation exceeds the limit of "
                     + MAX_KEYFRAMES_PER_ANIMATION + " keyframes");
               }
               for (JsonElement keyframe : rawKeyframes) {
                  if (keyframe.isJsonObject()) keyframes.add(Keyframe.from(keyframe.getAsJsonObject(), version, channel.getKey()));
               }
               keyframes.sort(Comparator.comparingDouble(Keyframe::time));
               channelMap.put(channel.getKey(), keyframes);
            }
            tracks.put(animator.getKey(), channelMap);
         }
         return new Animation(string(value, "name", "animation"), string(value, "loop", "once"),
            number(value, "length", 0.0F), tracks);
      }
   }

   public record Keyframe(float time, float[] value, String interpolation, String easing) {
      static Keyframe from(JsonObject value, String version, String channel) {
         float[] points;
         JsonArray data = array(value, "data_points");
         if (!data.isEmpty() && data.get(0).isJsonObject()) {
            points = vector(data.get(0).getAsJsonObject());
         } else if (value.has("data") && value.get("data").isJsonArray()) {
            points = vector(value.getAsJsonArray("data"));
         } else {
            points = new float[]{0, 0, 0};
         }
         // Blockbench 5 fixed the historical x-axis inversion for legacy
         // position/rotation tracks; scale values were never inverted.
         if ((channel.equals("position") || channel.equals("rotation")) && (version.startsWith("4") || version.startsWith("3"))) {
            points[0] = -points[0];
         }
         return new Keyframe(number(value, "time", 0.0F), points,
            string(value, "interpolation", "linear"), string(value, "easing", "linear"));
      }
   }

   private static Map<String, Face> parseFaces(JsonObject value) {
      if (value == null) return Map.of();
      Map<String, Face> result = new LinkedHashMap<>();
      for (Map.Entry<String, JsonElement> entry : value.entrySet()) {
         if (!entry.getValue().isJsonObject()) continue;
         JsonObject face = entry.getValue().getAsJsonObject();
         result.put(entry.getKey(), new Face(integer(face, "texture", -1), vector(face.getAsJsonArray("uv")), integer(face, "rotation", 0)));
      }
      return result;
   }

   private static float[] vector(JsonArray array) {
      if (array == null) return new float[]{0, 0, 0};
      float[] result = new float[Math.max(3, array.size())];
      for (int i = 0; i < array.size(); i++) result[i] = array.get(i).getAsFloat();
      return result;
   }

   private static float[] vector(JsonObject value) {
      return new float[]{number(value, "x", 0), number(value, "y", 0), number(value, "z", 0)};
   }

   private static float[] vectorOrZero(JsonObject value, String key) {
      JsonElement element = value == null ? null : value.get(key);
      return element != null && element.isJsonArray() ? vector(element.getAsJsonArray()) : new float[]{0, 0, 0};
   }

   private static boolean bool(JsonObject value, String key, boolean fallback) {
      JsonElement element = value == null ? null : value.get(key);
      return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : fallback;
   }

   private static void flipY(List<Cube> cubes, List<Bone> bones) {
      for (Cube cube : cubes) {
         cube.from()[1] = -cube.from()[1];
         cube.to()[1] = -cube.to()[1];
         if (cube.from()[1] > cube.to()[1]) {
            float value = cube.from()[1]; cube.from()[1] = cube.to()[1]; cube.to()[1] = value;
         }
      }
      for (Bone bone : bones) {
         bone.pivot()[1] = -bone.pivot()[1];
         bone.rotation()[0] = -bone.rotation()[0];
         bone.rotation()[2] = -bone.rotation()[2];
      }
   }

   private static JsonObject object(JsonObject value, String key) {
      JsonElement element = value == null ? null : value.get(key);
      return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
   }

   private static JsonArray array(JsonObject value, String key) {
      JsonElement element = value == null ? null : value.get(key);
      return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
   }

   private static String string(JsonObject value, String key, String fallback) {
      JsonElement element = value == null ? null : value.get(key);
      return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
   }

   private static int integer(JsonObject value, String key, int fallback) {
      try { return value.has(key) ? value.get(key).getAsInt() : fallback; } catch (Exception ignored) { return fallback; }
   }

   private static float number(JsonObject value, String key, float fallback) {
      try { return value.has(key) ? value.get(key).getAsFloat() : fallback; } catch (Exception ignored) { return fallback; }
   }
}
