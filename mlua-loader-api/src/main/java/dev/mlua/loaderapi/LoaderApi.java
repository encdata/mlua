package dev.mlua.loaderapi;

import dev.mlua.common.mixin.MLuaMappingResolver;
import dev.mlua.common.mixin.MLuaMappingResolvers;
import java.util.Objects;

public final class LoaderApi {
    private static final JavaClassBridge DEFAULT_JAVA_CLASSES = (canonicalName, preferredLoaders) -> {
        if (preferredLoaders != null) {
            for (ClassLoader loader : preferredLoaders) {
                if (loader == null) continue;
                try {
                    return Class.forName(canonicalName, false, loader);
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
        try {
            return Class.forName(canonicalName, false, ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    };

    private static volatile LoaderProvider provider;

    private LoaderApi() {
    }

    public static void install(LoaderProvider installed) {
        provider = Objects.requireNonNull(installed, "installed");
    }

    private static LoaderProvider provider() {
        LoaderProvider installed = provider;
        if (installed == null) throw new IllegalStateException("MLua loader is not installed yet");
        return installed;
    }

    public static PlatformBridge platform() { return provider().platform(); }
    public static EventBridge events() { return provider().events(); }
    public static RegistryBridge registry() { return provider().registry(); }
    public static NetworkBridge network() { return provider().network(); }
    public static ConfigBridge config() { return provider().config(); }
    public static LifecycleBridge lifecycle() { return provider().lifecycle(); }
    public static ClientBridge client() { return provider().client(); }

    public static MLuaMappingResolver mappings() {
        MLuaMappingResolver resolver = provider().mappings();
        return resolver != null ? resolver : MLuaMappingResolvers.current();
    }

    public static JavaClassBridge javaClasses() { return provider().javaClasses(); }

    public interface LoaderProvider {
        PlatformBridge platform();
        EventBridge events();
        RegistryBridge registry();
        NetworkBridge network();
        ConfigBridge config();
        LifecycleBridge lifecycle();
        ClientBridge client();

        default MLuaMappingResolver mappings() { return null; }
        default JavaClassBridge javaClasses() { return DEFAULT_JAVA_CLASSES; }
    }
}
