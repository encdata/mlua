package dev.mlua.modules.modal.service;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaError;
import dev.mlua.core.service.MLuaService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.common.enums.Side;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.HashSet;
import java.util.Set;
import org.luaj.vm2.Globals;
import org.luaj.vm2.Varargs;

@MLuaDescription("Shows client-side modal dialogs and prompts. Screen changes are queued onto Minecraft's client thread when Lua calls from a worker.")
public final class MLuaModalService extends MLuaReflectiveService {
    private static final int MAX_MESSAGE_BOX_BUTTONS = 4;
    private static final Map<MLuaTaskSchedulerView, ModalState> STATES = new WeakHashMap<>();
    private final ModalState state;

    public MLuaModalService(MLuaService.Context context) {
        super(context);
        this.state = stateFor(context.mlua().scheduler());
    }

    public static boolean isGlobalModal() {
        MLuaRuntime runtime = MLuaRuntime.get();
        if (runtime == null || runtime.session() == null) return false;
        return isModalForScheduler(runtime.session().clientScheduler());
    }

    public static void clearForScheduler(MLuaTaskSchedulerView scheduler) {
        if (scheduler == null) return;
        synchronized (STATES) {
            ModalState state = STATES.get(scheduler);
            if (state != null) state.clear();
        }
    }

    public static void clearForSession(MLuaRuntimeSession session, Side side) {
        if (session == null || side == null || (side != Side.CLIENT && side != Side.BOTH)) return;
        synchronized (STATES) {
            STATES.forEach((scheduler, state) -> {
                if (side == Side.BOTH ? session.ownsScheduler(scheduler) : scheduler == session.clientScheduler()) {
                    state.clear();
                }
            });
        }
    }

    public static void clearOwnedForSession(MLuaRuntimeSession session, Side side, String ownerModId) {
        if (session == null || side == null || ownerModId == null
            || (side != Side.CLIENT && side != Side.BOTH)) return;
        synchronized (STATES) {
            STATES.forEach((scheduler, state) -> {
                if (side == Side.BOTH ? session.ownsScheduler(scheduler) : scheduler == session.clientScheduler()) {
                    state.clearOwner(ownerModId);
                }
            });
        }
    }

    private static boolean isModalForScheduler(MLuaTaskSchedulerView scheduler) {
        if (scheduler == null) return false;
        synchronized (STATES) {
            ModalState state = STATES.get(scheduler);
            return state != null && state.hasModal();
        }
    }

    private static ModalState stateFor(MLuaTaskSchedulerView scheduler) {
        java.util.Objects.requireNonNull(scheduler, "scheduler");
        synchronized (STATES) {
            return STATES.computeIfAbsent(scheduler, ignored -> new ModalState());
        }
    }

    @Override
    public String name() {
        return "Modal";
    }

    @MLuaDescription("Sets the active modal state for the calling script.")
    public void SetModal(boolean modal) {
        this.state.setModal(ownerModId(), modal);
    }

    @MLuaDescription("Returns whether a modal is currently active.")
    public boolean IsModal() {
        return this.state.isModal(ownerModId());
    }

    @MLuaDescription("Displays a message dialog to the local player. Optional buttons must be a dense array of one to " + MAX_MESSAGE_BOX_BUTTONS + " non-empty strings; omitted or empty buttons default to OK. Screen changes are queued onto Minecraft's client thread when needed.")
    public LuaTable ShowMessageBox(String title, Optional<String> requestedMessage,
                                   Optional<LuaValue> requestedButtons) {
        String message = requestedMessage.orElse("");
        List<String> buttons = new ArrayList<>();
        LuaValue buttonValue = requestedButtons.orElse(LuaValue.NIL);
        if (!buttonValue.isnil() && !buttonValue.istable()) {
            throw new LuaError("[MLua] Modal:ShowMessageBox buttons must be a dense array of strings.");
        }
        if (buttonValue.istable()) {
            LuaTable buttonTable = buttonValue.checktable();
            java.util.TreeMap<Integer, String> indexedButtons = new java.util.TreeMap<>();
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs entry = buttonTable.next(key);
                key = entry.arg1();
                if (key.isnil()) break;
                if (!key.isnumber() || key.todouble() != key.toint() || key.toint() < 1) {
                    throw new LuaError("[MLua] Modal:ShowMessageBox buttons must use positive integer array indexes.");
                }
                int index = key.toint();
                LuaValue label = entry.arg(2);
                if (!label.isstring() || label.tojstring().isBlank()) {
                    throw new LuaError("[MLua] Modal:ShowMessageBox button " + index + " must be a non-empty string.");
                }
                indexedButtons.put(index, label.tojstring());
                if (indexedButtons.size() > MAX_MESSAGE_BOX_BUTTONS) {
                    throw new LuaError("[MLua] Modal:ShowMessageBox supports at most "
                        + MAX_MESSAGE_BOX_BUTTONS + " buttons.");
                }
            }
            int expectedIndex = 1;
            for (Map.Entry<Integer, String> button : indexedButtons.entrySet()) {
                if (button.getKey() != expectedIndex++) {
                    throw new LuaError("[MLua] Modal:ShowMessageBox buttons must be a dense array without gaps.");
                }
                buttons.add(button.getValue());
            }
        }
        if (buttons.isEmpty()) buttons.add("OK");
        return showMessageBoxImpl(title, message, buttons);
    }

    @MLuaDescription("Displays a text prompt and returns the submitted value. Screen changes are queued onto Minecraft's client thread when needed.")
    public LuaTable ShowPrompt(String title, Optional<String> requestedMessage,
                               Optional<String> requestedDefaultText) {
        return showPromptImpl(title, requestedMessage.orElse(""), requestedDefaultText.orElse(""));
    }

    @MLuaDescription("Displays a confirmation dialog and returns the choice. Screen changes are queued onto Minecraft's client thread when needed.")
    public LuaTable ShowConfirmDialog(String title, Optional<String> requestedMessage) {
        return showConfirmDialogImpl(title, requestedMessage.orElse(""));
    }

    private dev.mlua.core.scheduler.MLuaTaskSchedulerView scheduler() {
        return this.context.mlua().scheduler();
    }

    private String ownerModId() {
        return this.context.mlua().ownerModId();
    }

    private Globals ownerGlobals() {
        return this.context.mlua().globals();
    }

    private dev.mlua.common.enums.Side ownerSide() {
        return this.context.mlua().side();
    }

    private LuaTable showMessageBoxImpl(String title, String message, List<String> buttons) {
        MLuaSignal signal = new MLuaSignal();
        String owner = ownerModId();
        Screen screen = MLuaModalScreenFactory.messageBox(title, message, buttons, signal, state::dialogClosed);
        openOnClientThread(screen, state, owner);
        return MLuaLuaBindings.signalInstance(signal, ownerGlobals(), scheduler(), owner, ownerSide());
    }

    private LuaTable showPromptImpl(String title, String message, String defaultText) {
        MLuaSignal signal = new MLuaSignal();
        String owner = ownerModId();
        Screen screen = MLuaModalScreenFactory.prompt(title, message, defaultText, signal, state::dialogClosed);
        openOnClientThread(screen, state, owner);
        return MLuaLuaBindings.signalInstance(signal, ownerGlobals(), scheduler(), owner, ownerSide());
    }

    private LuaTable showConfirmDialogImpl(String title, String message) {
        MLuaSignal signal = new MLuaSignal();
        String owner = ownerModId();
        Screen screen = MLuaModalScreenFactory.confirmDialog(title, message, signal, state::dialogClosed);
        openOnClientThread(screen, state, owner);
        return MLuaLuaBindings.signalInstance(signal, ownerGlobals(), scheduler(), owner, ownerSide());
    }

    private static void openOnClientThread(Screen screen, ModalState state, String owner) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) throw new LuaError("[MLua] Minecraft client is not available.");
        state.trackDialog(screen, owner);
        Runnable open = () -> {
            if (state.isCurrentDialog(screen, owner)) minecraft.setScreen(screen);
        };
        if (dev.mlua.runtime.client.MLuaClientThread.isClientThread()) open.run();
        else minecraft.execute(open);
    }

    private static final class ModalState {
        private final Set<String> modalOwners = new HashSet<>();
        private Screen dialogScreen;
        private String dialogOwner;

        private synchronized void setModal(String owner, boolean modal) {
            if (modal) modalOwners.add(owner);
            else modalOwners.remove(owner);
        }

        private synchronized boolean isModal(String owner) {
            return modalOwners.contains(owner);
        }

        private synchronized boolean hasModal() {
            return !modalOwners.isEmpty();
        }

        private synchronized void trackDialog(Screen screen, String owner) {
            dialogScreen = screen;
            dialogOwner = owner;
        }

        private synchronized boolean isCurrentDialog(Screen screen, String owner) {
            return dialogScreen == screen && java.util.Objects.equals(dialogOwner, owner);
        }

        private synchronized void dialogClosed(Screen screen) {
            if (dialogScreen == screen) {
                dialogScreen = null;
                dialogOwner = null;
            }
        }

        private void clearOwner(String owner) {
            Screen close = null;
            synchronized (this) {
                modalOwners.remove(owner);
                if (java.util.Objects.equals(dialogOwner, owner)) {
                    close = dialogScreen;
                    dialogScreen = null;
                    dialogOwner = null;
                }
            }
            closeDialog(close);
        }

        private void clear() {
            Screen close;
            synchronized (this) {
                modalOwners.clear();
                close = dialogScreen;
                dialogScreen = null;
                dialogOwner = null;
            }
            closeDialog(close);
        }

        private static void closeDialog(Screen screen) {
            if (screen == null) return;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) return;
            Runnable close = () -> {
                if (minecraft.screen == screen) minecraft.setScreen(null);
            };
            if (dev.mlua.runtime.client.MLuaClientThread.isClientThread()) close.run();
            else minecraft.execute(close);
        }
    }
}
