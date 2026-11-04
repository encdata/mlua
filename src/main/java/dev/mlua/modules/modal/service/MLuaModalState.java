package dev.mlua.modules.modal.service;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Owns modal flags and the current dialog lifecycle for one client runtime. */
final class MLuaModalState {
    private final Set<String> modalOwners = new HashSet<>();
    private Screen dialogScreen;
    private String dialogOwner;

    synchronized void setModal(String owner, boolean modal) {
        if (modal) modalOwners.add(owner);
        else modalOwners.remove(owner);
    }

    synchronized boolean isModal(String owner) {
        return modalOwners.contains(owner);
    }

    synchronized boolean hasModal() {
        return !modalOwners.isEmpty();
    }

    synchronized void trackDialog(Screen screen, String owner) {
        dialogScreen = screen;
        dialogOwner = owner;
    }

    synchronized boolean isCurrentDialog(Screen screen, String owner) {
        return dialogScreen == screen && java.util.Objects.equals(dialogOwner, owner);
    }

    synchronized void dialogClosed(Screen screen) {
        if (dialogScreen == screen) {
            dialogScreen = null;
            dialogOwner = null;
        }
    }

    void clearOwner(String owner) {
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

    void clear() {
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
