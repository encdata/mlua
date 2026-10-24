package dev.mlua.module;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaLogger;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/** Discovers server-safe module providers without initializing client-only classes. */
final class MLuaServerModuleDiscovery {
    private static final String SERVICE_RESOURCE = "META-INF/services/" + MLuaModule.class.getName();

    private MLuaServerModuleDiscovery() { }

    static List<MLuaModule> discover() {
        List<MLuaModule> modules = new ArrayList<>();
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = classLoader.getResources(SERVICE_RESOURCE);
            while (resources.hasMoreElements()) {
                readProviders(resources.nextElement(), classLoader, modules);
            }
        } catch (Throwable error) {
            MLuaLogger.LOGGER.warn("Could not discover MLua server module providers", error);
        }
        return modules;
    }

    private static void readProviders(URL resource, ClassLoader classLoader, List<MLuaModule> modules) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String providerName = line.replaceFirst("#.*$", "").trim();
                if (providerName.isEmpty()) continue;
                loadProvider(providerName, classLoader, modules);
            }
        } catch (Throwable error) {
            MLuaLogger.LOGGER.warn("Could not read MLua server module providers from {}", resource, error);
        }
    }

    private static void loadProvider(String providerName, ClassLoader classLoader, List<MLuaModule> modules) {
        try {
            Class<?> providerType = Class.forName(providerName, false, classLoader);
            MLuaModuleSide metadata = providerType.getAnnotation(MLuaModuleSide.class);
            if (metadata != null && metadata.value() == Side.CLIENT) {
                MLuaLogger.LOGGER.debug("Skipped client-only MLua module on dedicated server: {}", providerName);
                return;
            }
            Class<?> provider = Class.forName(providerName, true, classLoader);
            modules.add((MLuaModule) provider.getDeclaredConstructor().newInstance());
        } catch (Throwable error) {
            MLuaLogger.LOGGER.debug("Skipped unavailable MLua server module provider {}", providerName, error);
        }
    }
}
