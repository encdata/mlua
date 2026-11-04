package dev.mlua.runtime.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
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
   private static final int MAX_TEXTURE_DIMENSION = 16_384;
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
   private final Map<String, Bone> bonesById;
   private final Map<String, List<Bone>> bonesByName;
   private final Map<String, List<Bone>> childrenByParent;
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
      Map<String, Bone> indexedBones = new LinkedHashMap<>();
      Map<String, List<Bone>> indexedNames = new LinkedHashMap<>();
      Map<String, List<Bone>> indexedChildren = new LinkedHashMap<>();
      for (Bone bone : this.bones) {
         if (indexedBones.putIfAbsent(bone.uuid(), bone) != null) {
            throw new IllegalArgumentException("Blockbench model contains duplicate bone id '" + bone.uuid() + "'");
         }
         indexedNames.computeIfAbsent(bone.name(), ignored -> new ArrayList<>()).add(bone);
         if (bone.parent() != null) indexedChildren.computeIfAbsent(bone.parent(), ignored -> new ArrayList<>()).add(bone);
      }
      this.bonesById = Map.copyOf(indexedBones);
      Map<String, List<Bone>> immutableNames = new LinkedHashMap<>();
      indexedNames.forEach((name, matches) -> immutableNames.put(name, List.copyOf(matches)));
      this.bonesByName = Map.copyOf(immutableNames);
      Map<String, List<Bone>> immutableChildren = new LinkedHashMap<>();
      indexedChildren.forEach((parent, children) -> immutableChildren.put(parent, List.copyOf(children)));
      this.childrenByParent = Map.copyOf(immutableChildren);
      this.cubeParents = Map.copyOf(cubeParents);
      this.animations = Collections.unmodifiableMap(new LinkedHashMap<>(animations));
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
         Map<String, JsonObject> groupsById = indexGroups(array(root, "groups"));
         readOutliner(hierarchy.getAsJsonArray(), bones, cubeParents, groupsById, null, 0);
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
      validateTextureDimensions(width, height);
      if (flipY) flipY(cubes, bones);
      return new MLuaBBModelAsset(version, format, flipY, width, height, textures, embeddedTextures, cubes, bones, cubeParents, animations);
   }

   private static Map<String, JsonObject> indexGroups(JsonArray groups) {
      Map<String, JsonObject> groupsById = new LinkedHashMap<>();
      for (JsonElement element : groups) {
         if (!element.isJsonObject()) continue;
         JsonObject group = element.getAsJsonObject();
         String uuid = string(group, "uuid", "");
         if (uuid.isBlank()) continue;
         requireCapacity(groupsById.size(), MAX_BONES, "groups");
         if (groupsById.putIfAbsent(uuid, group) != null) {
            throw new IllegalArgumentException("Blockbench model contains duplicate group id '" + uuid + "'");
         }
      }
      return groupsById;
   }

   private static void readOutliner(JsonArray entries, List<Bone> bones, Map<String, String> cubeParents,
                                    Map<String, JsonObject> groupsById, String parent, int depth) {
      if (depth > MAX_BONE_DEPTH) throw new IllegalArgumentException("Blockbench bone hierarchy exceeds " + MAX_BONE_DEPTH + " levels");
      for (JsonElement entry : entries) {
         if (!entry.isJsonObject()) continue;
         requireCapacity(bones.size(), MAX_BONES, "bones");
         JsonObject value = entry.getAsJsonObject();
         String uuid = string(value, "uuid", string(value, "name", "bone_" + bones.size()));
         JsonObject group = groupsById.get(uuid);
         bones.add(Bone.from(group == null ? value : group, parent));
         JsonArray children = array(value, "children");
         for (JsonElement child : children) {
            if (child.isJsonPrimitive()) cubeParents.put(child.getAsString(), uuid);
         }
         if (!children.isEmpty()) readOutliner(children, bones, cubeParents, groupsById, uuid, depth + 1);
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
   public Bone boneById(String uuid) { return uuid == null ? null : bonesById.get(uuid); }
   public List<Bone> bonesNamed(String name) { return name == null ? List.of() : bonesByName.getOrDefault(name, List.of()); }
   public List<Bone> childrenOf(String parentUuid) { return parentUuid == null ? List.of() : childrenByParent.getOrDefault(parentUuid, List.of()); }
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
      return Collections.unmodifiableMap(new LinkedHashMap<>(result));
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

   public record Cube(String uuid, String name, float[] from, float[] to, float[] origin,
                      float[] rotation, float inflate, Map<String, Face> faces) {
      public Cube {
         from = copyVector(from, 3, "cube from");
         to = copyVector(to, 3, "cube to");
         origin = copyVector(origin, 3, "cube origin");
         rotation = copyVector(rotation, 3, "cube rotation");
         if (!Float.isFinite(inflate)) throw new IllegalArgumentException("Blockbench cube inflate must be finite");
         faces = Collections.unmodifiableMap(new LinkedHashMap<>(faces));
      }

      public Cube(String uuid, String name, float[] from, float[] to, Map<String, Face> faces) {
         this(uuid, name, from, to, midpoint(from, to), new float[]{0, 0, 0}, 0.0F, faces);
      }

      @Override public float[] from() { return from.clone(); }
      @Override public float[] to() { return to.clone(); }
      @Override public float[] origin() { return origin.clone(); }
      @Override public float[] rotation() { return rotation.clone(); }
      public float from(int axis) { return from[axis]; }
      public float to(int axis) { return to[axis]; }
      public float origin(int axis) { return origin[axis]; }
      public float rotation(int axis) { return rotation[axis]; }
      public float renderFrom(int axis) { return from[axis] - inflate; }
      public float renderTo(int axis) { return to[axis] + inflate; }
      private void flipY() {
         from[1] = -from[1];
         to[1] = -to[1];
         if (from[1] > to[1]) { float value = from[1]; from[1] = to[1]; to[1] = value; }
         origin[1] = -origin[1];
         rotation[0] = -rotation[0];
         rotation[2] = -rotation[2];
      }

      static Cube from(JsonObject value) {
         float[] from = vector(value.getAsJsonArray("from"));
         float[] to = vector(value.getAsJsonArray("to"));
         return new Cube(string(value, "uuid", ""), string(value, "name", "cube"), from, to,
            vectorOrDefault(value, "origin", midpoint(from, to)), vectorOrZero(value, "rotation"),
            number(value, "inflate", 0.0F), parseFaces(value.getAsJsonObject("faces")));
      }
      static Cube fromBedrock(JsonObject value, String uuid) {
         float[] from = vectorOrZero(value, "origin");
         float[] size = vectorOrZero(value, "size");
         float[] to = {from[0] + size[0], from[1] + size[1], from[2] + size[2]};
         return new Cube(uuid, uuid, from, to, midpoint(from, to), new float[]{0, 0, 0}, 0.0F, Map.of());
      }

      private static float[] midpoint(float[] from, float[] to) {
         if (from == null || to == null || from.length < 3 || to.length < 3) {
            return new float[]{0, 0, 0};
         }
         return new float[]{(from[0] + to[0]) * 0.5F, (from[1] + to[1]) * 0.5F,
            (from[2] + to[2]) * 0.5F};
      }
   }

   public record Face(int texture, float[] uv, int rotation) {
      public Face {
         uv = uv == null ? null : uv.clone();
         if (uv != null) {
            for (float component : uv) {
               if (!Float.isFinite(component)) throw new IllegalArgumentException("Blockbench face UV values must be finite");
            }
         }
      }
      @Override public float[] uv() { return uv == null ? null : uv.clone(); }
      public boolean hasUv() { return uv != null && uv.length >= 4; }
      public float uv(int component) { return uv[component]; }
   }
   public record Bone(String uuid, String name, String parent, float[] pivot, float[] rotation) {
      public Bone {
         pivot = copyVector(pivot, 3, "bone pivot");
         rotation = copyVector(rotation, 3, "bone rotation");
      }
      @Override public float[] pivot() { return pivot.clone(); }
      @Override public float[] rotation() { return rotation.clone(); }
      public float pivot(int axis) { return pivot[axis]; }
      public float rotation(int axis) { return rotation[axis]; }
      private void flipY() { pivot[1] = -pivot[1]; rotation[0] = -rotation[0]; rotation[2] = -rotation[2]; }

      static Bone from(JsonObject value, String parent) {
         return new Bone(string(value, "uuid", string(value, "name", "root")), string(value, "name", "root"), parent,
            value.has("origin") && value.get("origin").isJsonArray() ? vector(value.getAsJsonArray("origin")) : new float[]{0, 0, 0},
            value.has("rotation") && value.get("rotation").isJsonArray() ? vector(value.getAsJsonArray("rotation")) : new float[]{0, 0, 0});
      }
   }
   public record Animation(String name, String loop, float length,
                           Map<String, Map<String, List<Keyframe>>> tracks,
                           Map<String, Boolean> quaternionRotation) {
      public Animation {
         if (!Float.isFinite(length) || length < 0.0F) {
            throw new IllegalArgumentException("Blockbench animation length must be finite and non-negative");
         }
         LinkedHashMap<String, Map<String, List<Keyframe>>> trackCopy = new LinkedHashMap<>();
         tracks.forEach((bone, channels) -> {
            LinkedHashMap<String, List<Keyframe>> channelCopy = new LinkedHashMap<>();
            channels.forEach((channel, frames) -> channelCopy.put(channel, List.copyOf(frames)));
            trackCopy.put(bone, Collections.unmodifiableMap(channelCopy));
         });
         tracks = Collections.unmodifiableMap(trackCopy);
         quaternionRotation = quaternionRotation == null ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(quaternionRotation));
      }

      public Animation(String name, String loop, float length,
                       Map<String, Map<String, List<Keyframe>>> tracks) {
         this(name, loop, length, tracks, Map.of());
      }

      public boolean usesQuaternionRotation(String bone) {
         return quaternionRotation.getOrDefault(bone, false);
      }

      static Animation from(JsonObject value, String version) {
         Map<String, Map<String, List<Keyframe>>> tracks = new LinkedHashMap<>();
         Map<String, Boolean> quaternionRotation = new LinkedHashMap<>();
         JsonObject animators = object(value, "animators");
         int totalKeyframes = 0;
         float lastKeyframeTime = 0.0F;
         for (Map.Entry<String, JsonElement> animator : animators.entrySet()) {
            if (!animator.getValue().isJsonObject()) continue;
            JsonObject animatorData = animator.getValue().getAsJsonObject();
            Map<String, List<Keyframe>> channelMap = new LinkedHashMap<>();
            if (animatorData.has("quaternion_interpolation")) {
               quaternionRotation.put(animator.getKey(), bool(animatorData, "quaternion_interpolation", false));
            }
            JsonElement serializedKeyframes = animatorData.get("keyframes");
            if (serializedKeyframes != null && serializedKeyframes.isJsonArray()) {
               JsonArray rawKeyframes = serializedKeyframes.getAsJsonArray();
               totalKeyframes += rawKeyframes.size();
               requireAnimationKeyframeCapacity(totalKeyframes);
               for (JsonElement rawKeyframe : rawKeyframes) {
                  if (!rawKeyframe.isJsonObject()) continue;
                  JsonObject keyframe = rawKeyframe.getAsJsonObject();
                  lastKeyframeTime = Math.max(lastKeyframeTime, nonNegativeNumber(keyframe, "time", 0.0F));
                  String channel = string(keyframe, "channel", "");
                  if (!isTransformChannel(channel)) continue;
                  channelMap.computeIfAbsent(channel, ignored -> new ArrayList<>())
                     .add(Keyframe.from(keyframe, version, channel));
               }
            } else {
               for (Map.Entry<String, JsonElement> channel : animatorData.entrySet()) {
                  if (!channel.getValue().isJsonArray()) continue;
                  JsonArray rawKeyframes = channel.getValue().getAsJsonArray();
                  totalKeyframes += rawKeyframes.size();
                  requireAnimationKeyframeCapacity(totalKeyframes);
                  List<Keyframe> keyframes = new ArrayList<>();
                  for (JsonElement keyframe : rawKeyframes) {
                     if (keyframe.isJsonObject()) {
                        keyframes.add(Keyframe.from(keyframe.getAsJsonObject(), version, channel.getKey()));
                     }
                  }
                  channelMap.put(channel.getKey(), keyframes);
               }
            }
            channelMap.values().forEach(keyframes -> keyframes.sort(Comparator.comparingDouble(Keyframe::time)));
            for (List<Keyframe> keyframes : channelMap.values()) {
               for (Keyframe keyframe : keyframes) lastKeyframeTime = Math.max(lastKeyframeTime, keyframe.time());
            }
            tracks.put(animator.getKey(), channelMap);
         }
         float declaredLength = nonNegativeNumber(value, "length", 0.0F);
         float animationLength = Math.max(declaredLength, lastKeyframeTime);
         return new Animation(string(value, "name", "animation"), string(value, "loop", "once"),
            animationLength, tracks, quaternionRotation);
      }

      private static void requireAnimationKeyframeCapacity(int total) {
         if (total > MAX_KEYFRAMES_PER_ANIMATION) {
            throw new IllegalArgumentException("Blockbench animation exceeds the limit of "
               + MAX_KEYFRAMES_PER_ANIMATION + " keyframes");
         }
      }

      private static boolean isTransformChannel(String channel) {
         return channel.equals("position") || channel.equals("rotation") || channel.equals("scale");
      }
   }

   /** `easing` remains available for compatibility; Blockbench sampling uses `interpolation`. */
   public record Keyframe(float time, float[] value, float[] outgoingValue,
                          float[] bezierLeftTime, float[] bezierLeftValue,
                          float[] bezierRightTime, float[] bezierRightValue,
                          String interpolation, String easing) {
      public Keyframe {
         if (!Float.isFinite(time) || time < 0.0F) {
            throw new IllegalArgumentException("Blockbench keyframe time must be finite and non-negative");
         }
         value = copyVector(value, 3, "keyframe value");
         outgoingValue = outgoingValue == null ? null : copyVector(outgoingValue, 3, "keyframe outgoing value");
         bezierLeftTime = copyVector(bezierLeftTime, 3, "Bezier left time");
         bezierLeftValue = copyVector(bezierLeftValue, 3, "Bezier left value");
         bezierRightTime = copyVector(bezierRightTime, 3, "Bezier right time");
         bezierRightValue = copyVector(bezierRightValue, 3, "Bezier right value");
      }

      public Keyframe(float time, float[] value, String interpolation, String easing) {
         this(time, value, null, DEFAULT_BEZIER_LEFT_TIME, ZERO_VECTOR,
            DEFAULT_BEZIER_RIGHT_TIME, ZERO_VECTOR, interpolation, easing);
      }

      @Override public float[] value() { return value.clone(); }
      @Override public float[] outgoingValue() { return outgoingValue == null ? null : outgoingValue.clone(); }
      @Override public float[] bezierLeftTime() { return bezierLeftTime.clone(); }
      @Override public float[] bezierLeftValue() { return bezierLeftValue.clone(); }
      @Override public float[] bezierRightTime() { return bezierRightTime.clone(); }
      @Override public float[] bezierRightValue() { return bezierRightValue.clone(); }
      public float value(int axis) { return value[axis]; }
      public float outgoingValue(int axis) { return outgoingValue == null ? value[axis] : outgoingValue[axis]; }
      public boolean hasSeparateOutgoingValue() { return outgoingValue != null; }
      public float bezierLeftTime(int axis) { return bezierLeftTime[axis]; }
      public float bezierLeftValue(int axis) { return bezierLeftValue[axis]; }
      public float bezierRightTime(int axis) { return bezierRightTime[axis]; }
      public float bezierRightValue(int axis) { return bezierRightValue[axis]; }

      private static final float[] ZERO_VECTOR = {0, 0, 0};
      private static final float[] DEFAULT_BEZIER_LEFT_TIME = {-0.1F, -0.1F, -0.1F};
      private static final float[] DEFAULT_BEZIER_RIGHT_TIME = {0.1F, 0.1F, 0.1F};

      static Keyframe from(JsonObject value, String version, String channel) {
         float[] incoming;
         float[] outgoing = null;
         JsonArray data = array(value, "data_points");
         if (!data.isEmpty() && data.get(0).isJsonObject()) {
            incoming = keyframeVector(data.get(0), channel);
            if (data.size() > 1 && data.get(1).isJsonObject()) {
               outgoing = keyframeVector(data.get(1), channel);
            }
         } else if (value.has("data") && value.get("data").isJsonArray()) {
            incoming = keyframeVector(value.get("data"), channel);
         } else {
            incoming = keyframeVector(null, channel);
         }
         boolean legacyKeyframeAxes = isLegacyAnimationVersion(version)
            && (channel.equals("position") || channel.equals("rotation"));
         boolean rotationChannel = channel.equals("rotation");
         if (legacyKeyframeAxes) {
            incoming[0] = -incoming[0];
            if (outgoing != null) outgoing[0] = -outgoing[0];
            if (rotationChannel) {
               incoming[1] = -incoming[1];
               if (outgoing != null) outgoing[1] = -outgoing[1];
            }
         }
         String interpolation = string(value, "interpolation", "linear");
         float[] bezierLeftTime = vectorOrDefault(value, "bezier_left_time", DEFAULT_BEZIER_LEFT_TIME);
         float[] bezierLeftValue = vectorOrDefault(value, "bezier_left_value", ZERO_VECTOR).clone();
         float[] bezierRightTime = vectorOrDefault(value, "bezier_right_time", DEFAULT_BEZIER_RIGHT_TIME);
         float[] bezierRightValue = vectorOrDefault(value, "bezier_right_value", ZERO_VECTOR).clone();
         if (legacyKeyframeAxes && interpolation.equals("bezier")) {
            bezierLeftValue[0] = -bezierLeftValue[0];
            bezierRightValue[0] = -bezierRightValue[0];
            if (rotationChannel) {
               bezierLeftValue[1] = -bezierLeftValue[1];
               bezierRightValue[1] = -bezierRightValue[1];
            }
         }
         return new Keyframe(nonNegativeNumber(value, "time", 0.0F), incoming, outgoing,
            bezierLeftTime, bezierLeftValue, bezierRightTime, bezierRightValue,
            interpolation, string(value, "easing", "linear"));
      }
   }

   private static boolean isLegacyAnimationVersion(String version) {
      int separator = version.indexOf('.');
      String major = separator < 0 ? version : version.substring(0, separator);
      try {
         return Integer.parseInt(major) < 5;
      } catch (NumberFormatException ignored) {
         return false;
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
      for (int i = 0; i < array.size(); i++) {
         result[i] = finiteFloat(array.get(i), "vector component " + (i + 1));
      }
      return result;
   }

   private static float[] keyframeVector(JsonElement value, String channel) {
      float defaultValue = channel.equals("scale") ? 1.0F : 0.0F;
      float[] result = {defaultValue, defaultValue, defaultValue};
      if (value == null) return result;
      if (value.isJsonObject()) {
         JsonObject object = value.getAsJsonObject();
         result[0] = number(object, "x", defaultValue);
         result[1] = number(object, "y", defaultValue);
         result[2] = number(object, "z", defaultValue);
      } else if (value.isJsonArray()) {
         JsonArray array = value.getAsJsonArray();
         for (int axis = 0; axis < Math.min(3, array.size()); axis++) {
            result[axis] = finiteFloat(array.get(axis), "keyframe vector component " + (axis + 1));
         }
      }
      return result;
   }

   private static float[] vector(JsonObject value) {
      return new float[]{number(value, "x", 0), number(value, "y", 0), number(value, "z", 0)};
   }

   private static float[] vectorOrZero(JsonObject value, String key) {
      JsonElement element = value == null ? null : value.get(key);
      return element != null && element.isJsonArray() ? vector(element.getAsJsonArray()) : new float[]{0, 0, 0};
   }

   private static float[] vectorOrDefault(JsonObject value, String key, float[] fallback) {
      JsonElement element = value == null ? null : value.get(key);
      return element != null && element.isJsonArray() ? vector(element.getAsJsonArray()) : fallback;
   }

   private static boolean bool(JsonObject value, String key, boolean fallback) {
      JsonElement element = value == null ? null : value.get(key);
      return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : fallback;
   }

   private static void flipY(List<Cube> cubes, List<Bone> bones) {
      for (Cube cube : cubes) cube.flipY();
      for (Bone bone : bones) bone.flipY();
   }

   private static float[] copyVector(float[] value, int minimumLength, String name) {
      if (value == null || value.length < minimumLength) {
         throw new IllegalArgumentException("Blockbench " + name + " must contain at least " + minimumLength + " values");
      }
      for (float component : value) {
         if (!Float.isFinite(component)) throw new IllegalArgumentException("Blockbench " + name + " values must be finite");
      }
      return value.clone();
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
      return value != null && value.has(key) ? finiteFloat(value.get(key), key) : fallback;
   }

   private static float nonNegativeNumber(JsonObject value, String key, float fallback) {
      float result = number(value, key, fallback);
      if (result < 0.0F) {
         throw new IllegalArgumentException("Blockbench " + key + " must not be negative");
      }
      return result;
   }

   private static float finiteFloat(JsonElement value, String field) {
      final float number;
      try {
         number = value.getAsFloat();
      } catch (RuntimeException invalid) {
         throw new IllegalArgumentException("Blockbench " + field + " must be a finite number", invalid);
      }
      if (!Float.isFinite(number)) {
         throw new IllegalArgumentException("Blockbench " + field + " must be a finite number");
      }
      return number;
   }

   private static void validateTextureDimensions(int width, int height) {
      if (width < 1 || height < 1 || width > MAX_TEXTURE_DIMENSION || height > MAX_TEXTURE_DIMENSION) {
         throw new IllegalArgumentException("Blockbench texture dimensions must be between 1 and "
            + MAX_TEXTURE_DIMENSION + " pixels");
      }
   }
}
