package dev.mlua.modules.modal.Service;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.common.enums.Side;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class MLuaModalService extends MLuaServiceBase {
    private static final Map<MLuaTaskSchedulerView, ModalState> STATES = new WeakHashMap<>();
    private final ModalState state;

    public MLuaModalService(MLuaService.Context context) {
        super(context);
        this.state = stateFor(context.mlua().scheduler());
    }

    public static boolean isGlobalModal() {
        return isModalForScheduler(MLuaRuntime.get().session().clientScheduler());
    }

    public static void clearForScheduler(MLuaTaskSchedulerView scheduler) {
        if (scheduler == null) return;
        synchronized (STATES) {
            ModalState state = STATES.get(scheduler);
            if (state != null) state.modal = false;
        }
    }

    public static void clearForSession(MLuaRuntimeSession session, Side side) {
        if (session == null || side == null || (side != Side.CLIENT && side != Side.BOTH)) return;
        synchronized (STATES) {
            STATES.forEach((scheduler, state) -> {
                if (side == Side.BOTH ? session.ownsScheduler(scheduler) : scheduler == session.clientScheduler()) {
                    state.modal = false;
                }
            });
        }
    }

    private static boolean isModalForScheduler(MLuaTaskSchedulerView scheduler) {
        if (scheduler == null) return false;
        synchronized (STATES) {
            ModalState state = STATES.get(scheduler);
            return state != null && state.modal;
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

    public boolean isModal() {
        return this.state.modal;
    }

    public void setModal(boolean modal) {
        this.state.modal = modal;
    }

    private dev.mlua.core.scheduler.MLuaTaskSchedulerView scheduler() {
        return this.context.mlua().scheduler();
    }

    private String ownerModId() {
        return this.context.mlua().ownerModId();
    }

    private org.luaj.vm2.Globals ownerGlobals() {
        return this.context.mlua().globals();
    }

    private dev.mlua.common.enums.Side ownerSide() {
        return this.context.mlua().side();
    }

    private LuaTable showMessageBoxImpl(String title, String message, java.util.List<String> buttons) {
        MLuaSignal signal = new MLuaSignal();

        Screen screen = new Screen(Component.literal(title)) {
            @Override
            protected void init() {
                super.init();
                int buttonWidth = Math.min(200, (this.width - (buttons.size() + 1) * 10) / buttons.size());
                int totalWidth = buttons.size() * buttonWidth + (buttons.size() - 1) * 10;
                int startX = (this.width - totalWidth) / 2;
                int y = this.height / 2 + 20;

                for (int i = 0; i < buttons.size(); i++) {
                    final int index = i + 1;
                    this.addRenderableWidget(Button.builder(Component.literal(buttons.get(i)), (btn) -> {
                        signal.fire(LuaValue.valueOf(index));
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(null);
                        }
                    }).bounds(startX + i * (buttonWidth + 10), y, buttonWidth, 20).build());
                }
            }

            @Override
            //--version 26.1
            public void extractRenderState(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            //--endversion
            //--version 1.20.1,1.21.1
            public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            //--endversion
                //--version 26.1
                this.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
                //--endversion
                //--version 1.21.1
                this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
                //--endversion
                //--version 1.20.1
                this.renderBackground(guiGraphics);
                //--endversion
                //--version 26.1
                super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
                //--endversion
                //--version 1.20.1,1.21.1
                super.render(guiGraphics, mouseX, mouseY, partialTick);
                //--endversion
                //--version 26.1
                guiGraphics.centeredText(this.font, title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
                //--endversion
                //--version 1.20.1,1.21.1
                guiGraphics.drawCenteredString(this.font, title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
                //--endversion

                if (message != null && !message.isEmpty()) {
                    String[] lines = message.split("\n");
                    int lineY = this.height / 2 - 20;
                    for (String line : lines) {
                        //--version 26.1
                        guiGraphics.centeredText(this.font, line, this.width / 2, lineY, 0xAAAAAA);
                        //--endversion
                        //--version 1.20.1,1.21.1
                        guiGraphics.drawCenteredString(this.font, line, this.width / 2, lineY, 0xAAAAAA);
                        //--endversion
                        lineY += 12;
                    }
                }
            }
        };

        Minecraft.getInstance().setScreen(screen);
        return MLuaLuaBindings.signalInstance(signal, ownerGlobals(), scheduler(), ownerModId(), ownerSide());
    }

    private LuaTable showPromptImpl(String title, String message, String defaultText) {
        MLuaSignal signal = new MLuaSignal();

        Screen screen = new Screen(Component.literal(title)) {
            private EditBox textBox;
            private boolean closed = false;

            @Override
            protected void init() {
                super.init();
                int centerX = this.width / 2;
                int centerY = this.height / 2;

                this.textBox = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20, Component.literal(""));
                this.textBox.setValue(defaultText != null ? defaultText : "");
                this.textBox.setFocused(true);
                this.addRenderableWidget(this.textBox);
                this.setFocused(this.textBox);

                this.addRenderableWidget(Button.builder(Component.literal("OK"), (btn) -> {
                    if (this.closed) return;
                    this.closed = true;
                    String text = this.textBox.getValue();
                    signal.fire(LuaValue.valueOf(text));
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(null);
                    }
                }).bounds(centerX - 110, centerY + 30, 100, 20).build());

                this.addRenderableWidget(Button.builder(Component.literal("Cancel"), (btn) -> {
                    if (this.closed) return;
                    this.closed = true;
                    signal.fire(LuaValue.NIL);
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(null);
                    }
                }).bounds(centerX + 10, centerY + 30, 100, 20).build());
            }

            @Override
            //--version 26.1
            public void extractRenderState(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            //--endversion
            //--version 1.20.1,1.21.1
            public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            //--endversion
                //--version 26.1
                this.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
                //--endversion
                //--version 1.21.1
                this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
                //--endversion
                //--version 1.20.1
                this.renderBackground(guiGraphics);
                //--endversion
                //--version 26.1
                super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
                //--endversion
                //--version 1.20.1,1.21.1
                super.render(guiGraphics, mouseX, mouseY, partialTick);
                //--endversion
                //--version 26.1
                guiGraphics.centeredText(this.font, title, this.width / 2, this.height / 2 - 50, 0xFFFFFF);
                //--endversion
                //--version 1.20.1,1.21.1
                guiGraphics.drawCenteredString(this.font, title, this.width / 2, this.height / 2 - 50, 0xFFFFFF);
                //--endversion

                if (message != null && !message.isEmpty()) {
                    //--version 26.1
                    guiGraphics.centeredText(this.font, message, this.width / 2, this.height / 2 - 30, 0xAAAAAA);
                    //--endversion
                    //--version 1.20.1,1.21.1
                    guiGraphics.drawCenteredString(this.font, message, this.width / 2, this.height / 2 - 30, 0xAAAAAA);
                    //--endversion
                }
            }

            @Override
            public void onClose() {
                if (!this.closed) {
                    this.closed = true;
                    signal.fire(LuaValue.NIL);
                }
                super.onClose();
            }
        };

        Minecraft.getInstance().setScreen(screen);
        return MLuaLuaBindings.signalInstance(signal, ownerGlobals(), scheduler(), ownerModId(), ownerSide());
    }

    private LuaTable showConfirmDialogImpl(String title, String message) {
        MLuaSignal signal = new MLuaSignal();

        Screen screen = new Screen(Component.literal(title)) {
            private boolean closed = false;

            @Override
            protected void init() {
                super.init();
                int centerX = this.width / 2;
                int centerY = this.height / 2;

                this.addRenderableWidget(Button.builder(Component.literal("Yes"), (btn) -> {
                    if (this.closed) return;
                    this.closed = true;
                    signal.fire(LuaValue.TRUE);
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(null);
                    }
                }).bounds(centerX - 110, centerY + 20, 100, 20).build());

                this.addRenderableWidget(Button.builder(Component.literal("No"), (btn) -> {
                    if (this.closed) return;
                    this.closed = true;
                    signal.fire(LuaValue.FALSE);
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(null);
                    }
                }).bounds(centerX + 10, centerY + 20, 100, 20).build());
            }

            @Override
            //--version 26.1
            public void extractRenderState(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            //--endversion
            //--version 1.20.1,1.21.1
            public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            //--endversion
                //--version 26.1
                this.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
                //--endversion
                //--version 1.21.1
                this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
                //--endversion
                //--version 1.20.1
                this.renderBackground(guiGraphics);
                //--endversion
                //--version 26.1
                super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
                //--endversion
                //--version 1.20.1,1.21.1
                super.render(guiGraphics, mouseX, mouseY, partialTick);
                //--endversion
                //--version 26.1
                guiGraphics.centeredText(this.font, title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
                //--endversion
                //--version 1.20.1,1.21.1
                guiGraphics.drawCenteredString(this.font, title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
                //--endversion

                if (message != null && !message.isEmpty()) {
                    String[] lines = message.split("\n");
                    int lineY = this.height / 2 - 20;
                    for (String line : lines) {
                        //--version 26.1
                        guiGraphics.centeredText(this.font, line, this.width / 2, lineY, 0xAAAAAA);
                        //--endversion
                        //--version 1.20.1,1.21.1
                        guiGraphics.drawCenteredString(this.font, line, this.width / 2, lineY, 0xAAAAAA);
                        //--endversion
                        lineY += 12;
                    }
                }
            }

            @Override
            public void onClose() {
                if (!this.closed) {
                    this.closed = true;
                    signal.fire(LuaValue.FALSE);
                }
                super.onClose();
            }
        };

        Minecraft.getInstance().setScreen(screen);
        return MLuaLuaBindings.signalInstance(signal, ownerGlobals(), scheduler(), ownerModId(), ownerSide());
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = new LuaTable();

        table.set("SetModal", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue arg = args.arg(args.narg());
                setModal(arg.checkboolean());
                return LuaValue.NIL;
            }
        });

        table.set("IsModal", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(isModal());
            }
        });

        table.set("ShowMessageBox", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String title = args.checkjstring(2);
                String message = args.optjstring(3, "");

                List<String> buttons = new ArrayList<>();
                LuaTable buttonsTable = args.opttable(4, null);
                if (buttonsTable != null) {
                    for (int i = 1; i <= buttonsTable.length(); i++) {
                        buttons.add(buttonsTable.get(i).checkjstring());
                    }
                }
                if (buttons.isEmpty()) {
                    buttons.add("OK");
                }

                return showMessageBoxImpl(title, message, buttons);
            }
        });

        table.set("ShowPrompt", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String title = args.checkjstring(2);
                String message = args.optjstring(3, "");
                String defaultText = args.optjstring(4, "");
                return showPromptImpl(title, message, defaultText);
            }
        });

        table.set("ShowConfirmDialog", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String title = args.checkjstring(2);
                String message = args.optjstring(3, "");
                return showConfirmDialogImpl(title, message);
            }
        });

        return table;
    }

    private static final class ModalState {
        private volatile boolean modal;
    }
}
