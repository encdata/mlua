package dev.mlua.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.runtime.client.MLuaClientThread;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.luaj.vm2.LuaError;
import org.lwjgl.glfw.GLFW;

/** Tracks addon-owned logical key presses and restores the physical key state on release. */
final class MLuaSimulatedKeyState {
    private static final Map<KeyMapping, Set<PressOwner>> PRESSES = new IdentityHashMap<>();
    private static final Map<KeyMapping, Boolean> BASELINE_DOWN = new IdentityHashMap<>();

    private MLuaSimulatedKeyState() { }

    static synchronized boolean isDown(KeyMapping mapping) {
        if (mapping == null) return false;
        Set<PressOwner> owners = PRESSES.get(mapping);
        return owners != null && !owners.isEmpty() || MLuaKeyMappingAccess.isDown(mapping);
    }

    static synchronized void refresh() {
        if (!MLuaClientThread.isClientThread()) {
            throw new IllegalStateException("[MLua] Simulated key states must be refreshed on Minecraft's client thread.");
        }
        for (Map.Entry<KeyMapping, Set<PressOwner>> entry : PRESSES.entrySet()) {
            if (!entry.getValue().isEmpty()) MLuaKeyMappingAccess.setDown(entry.getKey(), true);
        }
    }

    static synchronized void setDown(KeyMapping mapping, boolean down,
            MLuaRuntimeSession runtimeSession, String ownerModId) {
        requireClientThread("SetKeyDown");
        if (mapping == null) throw new IllegalArgumentException("Key mapping must not be null");
        PressOwner owner = new PressOwner(runtimeSession, ownerModId == null ? "" : ownerModId);
        Set<PressOwner> owners = PRESSES.get(mapping);
        if (down) {
            if (owners == null || owners.isEmpty()) {
                boolean baseline = isDown(mapping);
                MLuaKeyMappingAccess.setDown(mapping, true);
                owners = new LinkedHashSet<>();
                owners.add(owner);
                BASELINE_DOWN.put(mapping, baseline);
                PRESSES.put(mapping, owners);
                return;
            }
            MLuaKeyMappingAccess.setDown(mapping, true);
            owners.add(owner);
            return;
        }
        // An addon can release only the logical press it owns.
        if (owners == null || !owners.remove(owner)) return;
        if (owners.isEmpty()) {
            PRESSES.remove(mapping);
            restoreUnsimulatedState(mapping);
        }
    }

    static synchronized void setDownBatch(List<MLuaKeybindController.KeyState> states,
            MLuaRuntimeSession runtimeSession, String ownerModId) {
        requireClientThread("SetKeyStates");
        if (runtimeSession == null || ownerModId == null || ownerModId.isBlank()) {
            throw new IllegalArgumentException("Key state batches require an addon runtime and owner");
        }
        List<MLuaKeybindController.KeyState> requested = List.copyOf(states);
        Set<KeyMapping> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        PressOwner owner = new PressOwner(runtimeSession, ownerModId);
        List<Boolean> previous = new ArrayList<>(requested.size());
        for (MLuaKeybindController.KeyState state : requested) {
            if (state == null || state.keyMapping() == null) {
                throw new IllegalArgumentException("Key state batches cannot contain a missing mapping");
            }
            if (!seen.add(state.keyMapping())) {
                throw new IllegalArgumentException("Key state batch contains the same mapping more than once");
            }
            Set<PressOwner> owners = PRESSES.get(state.keyMapping());
            previous.add(owners != null && owners.contains(owner));
        }
        int applied = 0;
        try {
            for (int index = 0; index < requested.size(); index++) {
                MLuaKeybindController.KeyState state = requested.get(index);
                applied = index + 1;
                setDown(state.keyMapping(), state.pressed(), runtimeSession, ownerModId);
            }
        } catch (RuntimeException failure) {
            for (int index = applied - 1; index >= 0; index--) {
                MLuaKeybindController.KeyState state = requested.get(index);
                try {
                    setDown(state.keyMapping(), previous.get(index), runtimeSession, ownerModId);
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            String reason = failure.getMessage();
            throw new IllegalStateException("[MLua] Failed to apply key state batch"
                + (reason == null || reason.isBlank() ? "" : ": " + reason)
                + "; previous addon-owned states were restored where possible.", failure);
        }
    }

    static synchronized void releaseAll() {
        releaseOwners(owner -> true);
    }

    static synchronized void releaseSession(MLuaRuntimeSession session) {
        releaseOwners(owner -> owner.runtimeSession == session);
    }

    static synchronized void releaseOwner(MLuaRuntimeSession session, String modId) {
        if (session != null && modId != null) {
            releaseOwners(owner -> owner.runtimeSession == session && owner.modId.equals(modId));
        }
    }

    private static void releaseOwners(Predicate<PressOwner> predicate) {
        Minecraft minecraft = MLuaMinecraftClient.instance();
        if (!MLuaClientThread.isClientThread()) {
            minecraft.execute(() -> {
                synchronized (MLuaSimulatedKeyState.class) {
                    releaseOwners(predicate);
                }
            });
            return;
        }
        List<KeyMapping> release = new ArrayList<>();
        PRESSES.entrySet().removeIf(entry -> {
            Set<PressOwner> owners = entry.getValue();
            owners.removeIf(predicate);
            if (!owners.isEmpty()) return false;
            release.add(entry.getKey());
            return true;
        });
        for (KeyMapping mapping : release) restoreUnsimulatedState(mapping);
    }

    private static void restoreUnsimulatedState(KeyMapping mapping) {
        boolean baseline = BASELINE_DOWN.getOrDefault(mapping, false);
        BASELINE_DOWN.remove(mapping);
        MLuaKeyMappingAccess.setDown(mapping, baseline || isPhysicalKeyDown(mapping));
    }

    private static boolean isPhysicalKeyDown(KeyMapping mapping) {
        try {
            InputConstants.Key inputKey = MLuaKeyMappingAccess.key(mapping);
            int value = MLuaKeyMappingAccess.keyValue(inputKey);
            if (value < 0) return false;
            //--version 26.1
            long window = MLuaMinecraftClient.instance().getWindow().handle();
            //--endversion
            //--version 1.20.1,1.21.1
            long window = MLuaMinecraftClient.instance().getWindow().getWindow();
            //--endversion
            Type type = MLuaKeyMappingAccess.keyType(inputKey);
            if (type == Type.SCANCODE) return false;
            if (type == Type.MOUSE) return GLFW.glfwGetMouseButton(window, value) == GLFW.GLFW_PRESS;
            int state = GLFW.glfwGetKey(window, value);
            return state == GLFW.GLFW_PRESS || state == GLFW.GLFW_REPEAT;
        } catch (RuntimeException | LinkageError unavailable) {
            // Keep the captured baseline when GLFW cannot inspect a non-standard mapping.
            return false;
        }
    }

    private static void requireClientThread(String operation) {
        if (!MLuaClientThread.isClientExecutionContext()) {
            throw new LuaError("[MLua] InputService:" + operation
                + " must run on Minecraft's client thread.");
        }
    }

    private record PressOwner(MLuaRuntimeSession runtimeSession, String modId) {
        @Override public boolean equals(Object other) {
            return other instanceof PressOwner owner
                && runtimeSession == owner.runtimeSession && modId.equals(owner.modId);
        }

        @Override public int hashCode() {
            return 31 * System.identityHashCode(runtimeSession) + modId.hashCode();
        }
    }
}
