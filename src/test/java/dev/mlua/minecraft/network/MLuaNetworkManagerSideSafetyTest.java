package dev.mlua.minecraft.network;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaNetworkManagerSideSafetyTest {
    @Test
    void sharedNetworkManagerHasNoClientOnlyTypesInItsSignatures() {
        Class<?> manager = MLuaNetworkManager.class;

        List<String> clientTypes = Stream.concat(
                Arrays.stream(manager.getDeclaredFields()).map(field -> field.getType()),
                Arrays.stream(manager.getDeclaredMethods()).flatMap(method -> {
                    Stream<Class<?>> parameters = Arrays.stream(method.getParameterTypes());
                    return Stream.concat(Stream.of(method.getReturnType()), parameters);
                }))
            .filter(type -> !type.isPrimitive())
            .map(Class::getName)
            .filter(name -> name.startsWith("net.minecraft.client."))
            .toList();

        assertTrue(clientTypes.isEmpty(),
            "Dedicated-server network manager exposes client-only types: " + clientTypes);
    }
}
