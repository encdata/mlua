package dev.mlua.transformer;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MLuaSelfModLocator implements IModFileCandidateLocator {
   private static final String EMBEDDED_RUNTIME_JAR = "embedded/mlua/mlua-runtime.jar";
   private static final Logger LOGGER = LoggerFactory.getLogger("MLuaSelfLocator");
   private boolean initialized;
   private Path selfJar;
   private Path runtimeJar;

   public void initArguments(Map<String, ?> arguments) {
      ensureInitialized();
   }

   private void ensureInitialized() {
      if (initialized) {
         return;
      }
      initialized = true;
      if (isDevEnvironment()) {
         LOGGER.info("[MLua] Self locator: dev environment detected, skipping runtime jar extraction");
         return;
      }
      selfJar = resolveSelfJar();
      LOGGER.info("[MLua] Self locator init: selfJar={}", selfJar);
      if (selfJar != null) {
         runtimeJar = materializeEmbeddedRuntimeJar(selfJar);
      }
      LOGGER.info("[MLua] Self locator init: runtimeJar={}", runtimeJar);
   }

   @Override
   public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
      ensureInitialized();
      LOGGER.info("[MLua] Self locator scanMods: runtimeJar={} exists={}",
         runtimeJar, runtimeJar != null && Files.exists(runtimeJar));
      if (runtimeJar == null || Files.notExists(runtimeJar)) {
         return;
      }
      if (context.isLocated(runtimeJar)) {
         LOGGER.info("[MLua] Self locator skipping already-located runtime jar {}", runtimeJar);
         return;
      }
      try {
         LOGGER.info("[MLua] Self locator adding candidate path {}", runtimeJar);
         var modFile = pipeline.addPath(runtimeJar, ModFileDiscoveryAttributes.DEFAULT.withLocator(this), IncompatibleFileReporting.ERROR);
         LOGGER.info("[MLua] Self locator addPath({}) -> present={}", runtimeJar, modFile.isPresent());
      } catch (Exception e) {
         LOGGER.warn("[MLua] Self locator failed to register runtime jar {}: {}", runtimeJar, e.toString());
      }
   }

   private static boolean isDevEnvironment() {
      return Boolean.getBoolean("mlua.dev.runtimeClasspath");
   }

   private static Path resolveSelfJar() {
      URL location = MLuaSelfModLocator.class.getProtectionDomain().getCodeSource().getLocation();
      if (location == null) {
         LOGGER.warn("[MLua] Self locator resolveSelfJar: code source location is null");
         return null;
      }

      LOGGER.info("[MLua] Self locator resolveSelfJar: code source={}", location);
      Path jarPath = extractJarPath(location.toExternalForm());
      if (jarPath == null) {
         LOGGER.warn("[MLua] Self locator resolveSelfJar: failed to extract jar path from {}", location);
         return null;
      }

      return jarPath.toAbsolutePath().normalize();
   }

   private static Path extractJarPath(String source) {
      String lower = source.toLowerCase();
      int jarIndex = lower.indexOf(".jar");
      if (jarIndex < 0) {
         return null;
      }

      String jarUri = source.substring(0, jarIndex + 4).replace("%23", "#");
      while (jarUri.startsWith("union:") || jarUri.startsWith("jar:")) {
         int colon = jarUri.indexOf(':');
         jarUri = jarUri.substring(colon + 1);
      }

      try {
         if (jarUri.startsWith("file:")) {
            return Paths.get(URI.create(jarUri));
         }
         jarUri = decodeUriPath(jarUri);
         if (jarUri.startsWith("/") && jarUri.length() > 2 && jarUri.charAt(2) == ':') {
            jarUri = jarUri.substring(1);
         }
         return Paths.get(jarUri);
      } catch (Exception e) {
         LOGGER.warn("[MLua] Self locator extractJarPath failed for {}: {}", source, e.toString());
         return null;
      }
   }

   private static String decodeUriPath(String value) {
      byte[] bytes = new byte[value.length()];
      int byteCount = 0;
      StringBuilder decoded = new StringBuilder(value.length());
      for (int index = 0; index < value.length(); index++) {
         char current = value.charAt(index);
         if (current == '%' && index + 2 < value.length()) {
            int high = Character.digit(value.charAt(index + 1), 16);
            int low = Character.digit(value.charAt(index + 2), 16);
            if (high >= 0 && low >= 0) {
               bytes[byteCount++] = (byte) ((high << 4) + low);
               index += 2;
               continue;
            }
         }

         if (byteCount > 0) {
            decoded.append(new String(bytes, 0, byteCount, StandardCharsets.UTF_8));
            byteCount = 0;
         }
         decoded.append(current);
      }

      if (byteCount > 0) {
         decoded.append(new String(bytes, 0, byteCount, StandardCharsets.UTF_8));
      }
      return decoded.toString();
   }

   private static Path materializeEmbeddedRuntimeJar(Path selfJar) {
      try (InputStream input = MLuaSelfModLocator.class.getClassLoader().getResourceAsStream(EMBEDDED_RUNTIME_JAR)) {
         if (input == null) {
            LOGGER.warn("[MLua] Self locator could not find embedded runtime resource {}", EMBEDDED_RUNTIME_JAR);
            return null;
         }

         byte[] bytes = input.readAllBytes();
         Path gameDir = selfJar.getParent() != null && selfJar.getParent().getParent() != null
            ? selfJar.getParent().getParent()
            : Paths.get(".").toAbsolutePath().normalize();
         cleanupLegacyModsBootstrap(gameDir);
         Path cacheDir = gameDir.resolve(".mlua").resolve("bootstrap");
         Files.createDirectories(cacheDir);

         String digest = sha256(bytes).substring(0, 12);
         Path extracted = cacheDir.resolve("mlua-runtime-" + digest + ".jar");
         LOGGER.info("[MLua] Self locator materialize: gameDir={} cacheDir={} extracted={}",
            gameDir, cacheDir, extracted);
         if (Files.notExists(extracted) || Files.size(extracted) != bytes.length) {
            Path tempFile = Files.createTempFile(cacheDir, "mlua-runtime-", ".jar.tmp");
            Files.write(tempFile, bytes);
            Files.move(tempFile, extracted, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("[MLua] Self locator wrote embedded runtime jar to {}", extracted);
         } else {
            LOGGER.info("[MLua] Self locator reusing existing embedded runtime jar {}", extracted);
         }
         return extracted;
      } catch (Exception e) {
         LOGGER.warn("[MLua] Self locator failed to materialize runtime jar from {}: {}", selfJar, e.toString());
         return null;
      }
   }

   private static void cleanupLegacyModsBootstrap(Path gameDir) {
      Path legacyDir = gameDir.resolve("mods").resolve(".mlua");
      if (Files.notExists(legacyDir)) {
         return;
      }

      try (Stream<Path> walk = Files.walk(legacyDir)) {
         walk.sorted(Comparator.reverseOrder()).forEach(path -> {
            try {
               Files.deleteIfExists(path);
            } catch (Exception e) {
               LOGGER.warn("[MLua] Self locator failed to delete legacy path {}: {}", path, e.toString());
            }
         });
         LOGGER.info("[MLua] Self locator removed legacy runtime cache at {}", legacyDir);
      } catch (Exception e) {
         LOGGER.warn("[MLua] Self locator failed to clean legacy runtime cache {}: {}", legacyDir, e.toString());
      }
   }

   private static String sha256(byte[] bytes) throws Exception {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(bytes);
      StringBuilder builder = new StringBuilder(hash.length * 2);
      for (byte value : hash) {
         builder.append(String.format("%02x", value));
      }
      return builder.toString();
   }
}
