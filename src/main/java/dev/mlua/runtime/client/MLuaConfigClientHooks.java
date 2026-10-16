package dev.mlua.runtime.client;

import dev.mlua.runtime.config.MLuaConfigRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class MLuaConfigClientHooks {
   private MLuaConfigClientHooks() {
   }

   public static void registerConfigScreens() {
      registerMluaConfigScreen();
      ModList.get().forEachModContainer((modId, container) -> {
         if (!"mlua".equals(modId) && !MLuaConfigRegistry.get().attachedTo(modId).isEmpty() && container.getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class).isEmpty()) {
            container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () -> {
               return new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> {
                  return new MLuaConfigScreen(modId, parent);
               });
            });
         }

      });
   }

   private static void registerMluaConfigScreen() {
      if (ModLoadingContext.get().getActiveContainer().getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class).isEmpty()) {
         ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () -> {
            return new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> {
               return new MLuaConfigScreen("mlua", parent);
            });
         });
      }

   }

   private static final class MLuaConfigScreen extends Screen {
      private static final int VIEW_TOP = 54;
      private static final int VIEW_BOTTOM_MARGIN = 38;
      private static final int ROW_HEIGHT = 24;
      private static final int SECTION_HEIGHT = 22;
      private final String attachedModId;
      private final Screen parent;
      private final Map<String, Boolean> collapsed = new HashMap();
      private final Map<String, String> drafts = new HashMap();
      private final Map<String, Object> pendingValues = new HashMap();
      private final List<RowLayout> rowLayouts = new ArrayList();
      private List<ConfigSection> sections = List.of();
      private int scrollOffset;
      private int maxScroll;
      private int tickCounter;
      private ScrollbarWidget scrollbar;

      private MLuaConfigScreen(String attachedModId, Screen parent) {
         super(Component.literal("MLua Config"));
         this.attachedModId = attachedModId;
         this.parent = parent;
      }

      protected void init() {
         this.refreshSections();
         this.rebuildEditor();
      }

      //--version 1.21.1
      public void tick() {
         super.tick();
         ++this.tickCounter;
         if (this.tickCounter % 20 == 0 && this.sections.stream().anyMatch((section) -> {
            return !section.loaded();
         })) {
            this.refreshSections();
            this.rebuildEditor();
         }

      }

      public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
         if (this.maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
         } else {
            this.scrollOffset = clamp(this.scrollOffset - (int)Math.signum(deltaY) * ROW_HEIGHT, 0, this.maxScroll);
            this.rebuildEditor();
            return true;
         }
      }
      //--endversion
      //--version 1.20.1
      public void tick() {
         super.tick();
         ++this.tickCounter;
         this.children().forEach((child) -> {
            if (child instanceof EditBox editBox) {
               editBox.tick();
            }

         });
         if (this.tickCounter % 20 == 0 && this.sections.stream().anyMatch((section) -> {
            return !section.loaded();
         })) {
            this.refreshSections();
            this.rebuildEditor();
         }

      }

      public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
         if (this.maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
         } else {
            this.scrollOffset = clamp(this.scrollOffset - (int)Math.signum(delta) * ROW_HEIGHT, 0, this.maxScroll);
            this.rebuildEditor();
            return true;
         }
      }
      //--endversion

      public void onClose() {
         this.applyAllEdits();
         this.savePendingChanges();
         Minecraft minecraft = this.minecraft;
         if (minecraft != null) {
            minecraft.setScreen(this.parent);
         }

      }

      public void removed() {
         this.applyAllEdits();
         super.removed();
      }

      public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
         //--version 1.21.1
         this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
         //--endversion
         //--version 1.20.1
         this.renderBackground(guiGraphics);
         //--endversion
         super.render(guiGraphics, mouseX, mouseY, partialTick);
         guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 16777215);
         Optional<? extends ModContainer> container = ModList.get().getModContainerById(this.attachedModId);
         String subtitle = container.map((value) -> {
            return "Attached to mod: " + value.getModInfo().getDisplayName() + " (" + this.attachedModId + ")";
         }).orElse("Attached to mod: " + this.attachedModId);
         guiGraphics.drawCenteredString(this.font, subtitle, this.width / 2, 28, 11184810);
         guiGraphics.fill(10, VIEW_TOP - 4, this.width - 10, this.height - VIEW_BOTTOM_MARGIN, -1879048192);

         for(RowLayout layout : this.rowLayouts) {
            if (layout.kind() == RowKind.ENTRY) {
               guiGraphics.drawString(this.font, layout.label(), 18, layout.y() + 8, layout.loaded() ? 16777215 : 11184810);
            } else if (layout.kind() == RowKind.INFO) {
               guiGraphics.drawString(this.font, layout.label(), 18, layout.y() + 6, 11184810);
            }
         }

         if (this.sections.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "No MLua configs attached.", this.width / 2, VIEW_TOP + 10, 16777215);
         }
      }

      private void refreshSections() {
         List<MLuaConfigRegistry.InstalledConfigSet> installed = MLuaConfigRegistry.get().attachedTo(this.attachedModId);
         boolean multipleOwners = installed.stream().map(MLuaConfigRegistry.InstalledConfigSet::ownerModId).distinct().count() > 1L;
         ArrayList<ConfigSection> newSections = new ArrayList();
         installed.stream().sorted(Comparator.comparing(MLuaConfigRegistry.InstalledConfigSet::type).thenComparing(MLuaConfigRegistry.InstalledConfigSet::ownerModId)).forEach((set) -> {
            ArrayList<ConfigEntry> entries = new ArrayList();
            set.values().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach((entry) -> {
               MLuaConfigRegistry.ConfigValue value = (MLuaConfigRegistry.ConfigValue)entry.getValue();
               String key = entryKey(set.ownerModId(), set.type(), (String)entry.getKey());
               Object currentValue = this.pendingValues.containsKey(key) ? this.pendingValues.get(key) : value.get();
               String valueType = value.typeName();
               entries.add(new ConfigEntry(set.ownerModId(), set.type(), (String)entry.getKey(), labelFor((String)entry.getKey()), valueType, currentValue, set.loaded(), value));
            });
            String title = friendlyType(set.type()) + (multipleOwners ? " - " + set.ownerModId() : "");
            String sectionKey = sectionKey(set.ownerModId(), set.type());
            this.collapsed.putIfAbsent(sectionKey, true);
            newSections.add(new ConfigSection(sectionKey, title, set.loaded(), entries));
         });
         this.sections = newSections;
         this.maxScroll = Math.max(0, contentHeight(this.sections, this.collapsed) - (this.height - VIEW_TOP - VIEW_BOTTOM_MARGIN));
         this.scrollOffset = clamp(this.scrollOffset, 0, this.maxScroll);
      }

      private void rebuildEditor() {
         this.clearWidgets();
         this.rowLayouts.clear();
         this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), (button) -> {
            this.onClose();
         }).bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
         this.scrollbar = this.addRenderableWidget(new ScrollbarWidget(this, this.width - 18, VIEW_TOP, 8, this.height - VIEW_TOP - VIEW_BOTTOM_MARGIN, Component.literal("Scrollbar")));
         this.scrollbar.visible = this.maxScroll > 0;
         this.scrollbar.active = this.maxScroll > 0;

         int viewportBottom = this.height - VIEW_BOTTOM_MARGIN;
         int yCursor = VIEW_TOP - this.scrollOffset;
         for(ConfigSection section : this.sections) {
            boolean collapsedSection = (Boolean)this.collapsed.getOrDefault(section.key(), false);
            if (visible(yCursor, SECTION_HEIGHT, viewportBottom)) {
               this.addRenderableWidget(Button.builder(Component.literal((collapsedSection ? "[+] " : "[-] ") + section.title()), (button) -> {
                  this.collapsed.put(section.key(), !((Boolean)this.collapsed.getOrDefault(section.key(), false)));
                  this.maxScroll = Math.max(0, contentHeight(this.sections, this.collapsed) - (this.height - VIEW_TOP - VIEW_BOTTOM_MARGIN));
                  this.scrollOffset = clamp(this.scrollOffset, 0, this.maxScroll);
                  this.rebuildEditor();
               }).bounds(14, yCursor, this.width - 80, SECTION_HEIGHT).build());
               this.rowLayouts.add(new RowLayout(RowKind.SECTION, section.title(), yCursor, section.loaded()));
            }

            yCursor += SECTION_HEIGHT + 2;
            if (!collapsedSection) {
               if (visible(yCursor, 18, viewportBottom)) {
                  this.rowLayouts.add(new RowLayout(RowKind.INFO, section.loaded() ? "Loaded from Forge config" : "Not loaded yet, showing defaults", yCursor, section.loaded()));
               }

               yCursor += 18;

               for(ConfigEntry entry : section.entries()) {
                  if (visible(yCursor, ROW_HEIGHT, viewportBottom)) {
                     this.addEntryWidget(entry, yCursor);
                     this.rowLayouts.add(new RowLayout(RowKind.ENTRY, entry.label(), yCursor, entry.loaded()));
                  }

                  yCursor += ROW_HEIGHT;
               }
            }
         }

      }

      private void addEntryWidget(ConfigEntry entry, int y) {
         int widgetX = this.width - 160;
         int widgetWidth = 140;
         if ("boolean".equals(entry.valueType())) {
            Button toggle = Button.builder(Component.literal(Boolean.TRUE.equals(entry.value()) ? "On" : "Off"), (button) -> {
               boolean newValue = !Boolean.TRUE.equals(entry.value());
               this.pendingValues.put(entryKey(entry), newValue);
               this.refreshSections();
               this.rebuildEditor();
            }).bounds(widgetX, y, widgetWidth, 20).build();
            toggle.active = entry.loaded();
            applyDescriptionTooltip(toggle, entry);
            this.addRenderableWidget(toggle);
         } else if ("enum".equals(entry.valueType())) {
            List<String> options = entry.configValue().options();
            Button choice = Button.builder(Component.literal(String.valueOf(entry.value())), (button) -> {
               int current = options.indexOf(String.valueOf(entry.value()));
               String next = options.get((current + 1 + options.size()) % options.size());
               this.pendingValues.put(entryKey(entry), next);
               this.refreshSections();
               this.rebuildEditor();
            }).bounds(widgetX, y, widgetWidth, 20).build();
            choice.active = entry.loaded() && !options.isEmpty();
            applyDescriptionTooltip(choice, entry);
            this.addRenderableWidget(choice);
         } else {
            ConfigEditBox editBox = new ConfigEditBox(this, entry, widgetX, y, widgetWidth, 20);
            applyDescriptionTooltip(editBox, entry);
            String key = entryKey(entry);
            editBox.setValue((String)this.drafts.getOrDefault(key, String.valueOf(entry.value())));
            editBox.setFilter((text) -> {
               return filterValue(entry.valueType(), text);
            });
            editBox.setCanLoseFocus(true);
            editBox.setEditable(entry.loaded());
            editBox.active = entry.loaded();
            editBox.refreshState();
            this.addRenderableWidget(editBox);
         }

      }

      private void applyEdit(ConfigEntry entry, String text) {
         String key = entryKey(entry);
         this.drafts.put(key, text);
         if (!entry.loaded()) {
            return;
         }

         try {
            Object parsed = parseValue(entry.valueType(), text);
            if (!entry.configValue().isAllowedValue(parsed)) return;
            this.pendingValues.put(key, parsed);
            this.refreshSections();
         } catch (RuntimeException var5) {
         }

      }

      private void applyAllEdits() {
         this.children().forEach((child) -> {
            if (child instanceof ConfigEditBox editBox) {
               editBox.applyValue();
            }

         });
      }

      private void savePendingChanges() {
         Map<String, ModConfig.Type> savedScopes = new HashMap();
         this.pendingValues.forEach((key, value) -> {
            String[] parts = key.split("\\|", 3);
            String ownerModId = parts[0];
            ModConfig.Type type = ModConfig.Type.valueOf(parts[1]);
            String path = parts[2];
            MLuaConfigRegistry.get().setValue(ownerModId, type, path, value);
            savedScopes.put(ownerModId + "|" + type.name(), type);
         });
         savedScopes.keySet().forEach((scopeKey) -> {
            String[] parts = scopeKey.split("\\|", 2);
            MLuaConfigRegistry.get().save(parts[0], (ModConfig.Type)savedScopes.get(scopeKey));
         });
         this.pendingValues.clear();
      }

      private static boolean visible(int y, int height, int viewportBottom) {
         return y + height >= VIEW_TOP && y <= viewportBottom;
      }

      private static int contentHeight(List<ConfigSection> sections, Map<String, Boolean> collapsed) {
         int total = 0;

         for(ConfigSection section : sections) {
            total += SECTION_HEIGHT + 2;
            if (!(Boolean)collapsed.getOrDefault(section.key(), false)) {
               total += 18;
               total += section.entries().size() * ROW_HEIGHT;
            }
         }

         return total;
      }

      private net.minecraft.client.gui.Font fontRenderer() {
         return this.font;
      }

      private static int clamp(int value, int min, int max) {
         return Math.max(min, Math.min(max, value));
      }

      private void setScrollOffset(int value) {
         int clamped = clamp(value, 0, this.maxScroll);
         if (clamped != this.scrollOffset) {
            this.scrollOffset = clamped;
            this.rebuildEditor();
         }

      }
   }

   private static final class ConfigEditBox extends EditBox {
      private final MLuaConfigScreen screen;
      private final ConfigEntry entry;

      private ConfigEditBox(MLuaConfigScreen screen, ConfigEntry entry, int x, int y, int width, int height) {
         super(screen.fontRenderer(), x, y, width, height, Component.literal(entry.label()));
         this.screen = screen;
         this.entry = entry;
         this.setResponder((text) -> {
            screen.drafts.put(entryKey(entry), text);
            this.refreshState();
         });
      }

      public void setFocused(boolean focused) {
         boolean wasFocused = this.isFocused();
         super.setFocused(focused);
         if (wasFocused && !focused) {
            this.applyValue();
         }

      }

      public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
         boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
         if (keyCode == 257 || keyCode == 335) {
            this.applyValue();
            return true;
         } else {
            return handled;
         }
      }

      private void refreshState() {
         this.setTextColor(validValue(this.entry.valueType(), this.getValue()) ? 14737632 : 16733525);
      }

      private void applyValue() {
         if (validValue(this.entry.valueType(), this.getValue())) {
            this.screen.applyEdit(this.entry, this.getValue());
         }

      }
   }

   private static void applyDescriptionTooltip(net.minecraft.client.gui.components.AbstractWidget widget, ConfigEntry entry) {
      String description = entry.configValue().description();
      if (description != null) widget.setTooltip(Tooltip.create(Component.literal(description)));
   }

   private static boolean filterValue(String type, String text) {
      switch (type) {
         case "int":
            return text.matches("-?\\d*");
         case "double":
            return text.matches("-?(\\d+)?(\\.\\d*)?");
         case "long":
            return text.matches("-?\\d*");
         case "string":
         case "enum":
            return true;
         default:
            return false;
      }
   }

   private static boolean validValue(String type, String text) {
      if ("string".equals(type)) {
         return true;
      } else if (text == null || text.isBlank() || "-".equals(text) || ".".equals(text) || "-.".equals(text)) {
         return false;
      } else {
         try {
            parseValue(type, text);
            return true;
         } catch (RuntimeException var3) {
            return false;
         }
      }
   }

   private static Object parseValue(String type, String text) {
      switch (type) {
         case "int":
            return Integer.parseInt(text);
         case "long":
            return Long.parseLong(text);
         case "double":
            return Double.parseDouble(text);
         case "string":
         case "enum":
            return text;
         default:
            throw new IllegalArgumentException("Unsupported UI config type: " + type);
      }
   }

   private static String friendlyType(ModConfig.Type type) {
      return type.name().substring(0, 1) + type.name().substring(1).toLowerCase(Locale.ROOT);
   }

   private static String labelFor(String path) {
      int idx = path.lastIndexOf(46);
      return idx >= 0 ? path.substring(idx + 1) : path;
   }

   private static String inferValueType(Object defaultValue) {
      if (defaultValue instanceof Boolean) {
         return "boolean";
      } else if (defaultValue instanceof Integer) {
         return "int";
      } else if (defaultValue instanceof Long) {
         return "long";
      } else {
         return defaultValue instanceof Number ? "double" : "string";
      }
   }

   private static String sectionKey(String ownerModId, ModConfig.Type type) {
      return ownerModId + "|" + type.name();
   }

   private static String entryKey(ConfigEntry entry) {
      return entryKey(entry.ownerModId(), entry.type(), entry.path());
   }

   private static String entryKey(String ownerModId, ModConfig.Type type, String path) {
      return ownerModId + "|" + type.name() + "|" + path;
   }

   private static record ConfigSection(String key, String title, boolean loaded, List<ConfigEntry> entries) {
   }

   private static record ConfigEntry(String ownerModId, ModConfig.Type type, String path, String label,
                                     String valueType, Object value, boolean loaded,
                                     MLuaConfigRegistry.ConfigValue configValue) {
   }

   private static record RowLayout(RowKind kind, String label, int y, boolean loaded) {
   }

   private static enum RowKind {
      SECTION,
      INFO,
      ENTRY;
   }

   private static final class ScrollbarWidget extends net.minecraft.client.gui.components.AbstractWidget {
      private final MLuaConfigScreen screen;
      private boolean dragging;

      private ScrollbarWidget(MLuaConfigScreen screen, int x, int y, int width, int height, Component message) {
         super(x, y, width, height, message);
         this.screen = screen;
      }

      protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
         guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, -13421773);
         int thumbHeight = this.thumbHeight();
         int thumbY = this.thumbY();
         guiGraphics.fill(this.getX(), thumbY, this.getX() + this.width, thumbY + thumbHeight, this.isHoveredOrFocused() || this.dragging ? -1 : -7829368);
      }

      public boolean mouseClicked(double mouseX, double mouseY, int button) {
         if (button == 0 && this.isMouseOver(mouseX, mouseY) && this.active) {
            this.dragging = true;
            this.updateFromMouse(mouseY);
            return true;
         } else {
            return false;
         }
      }

      public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
         if (this.dragging && this.active) {
            this.updateFromMouse(mouseY);
            return true;
         } else {
            return false;
         }
      }

      public boolean mouseReleased(double mouseX, double mouseY, int button) {
         this.dragging = false;
         return false;
      }

      private void updateFromMouse(double mouseY) {
         int thumbHeight = this.thumbHeight();
         int trackHeight = Math.max(1, this.height - thumbHeight);
         int top = this.getY();
         double relative = clampDouble(mouseY - (double)top - (double)thumbHeight / 2.0, 0.0, (double)trackHeight);
         int value = this.screen.maxScroll <= 0 ? 0 : (int)Math.round(relative / (double)trackHeight * (double)this.screen.maxScroll);
         this.screen.setScrollOffset(value);
      }

      private int thumbHeight() {
         int viewport = this.height;
         int content = Math.max(viewport + this.screen.maxScroll, 1);
         return Math.max(24, (int)Math.round((double)viewport * ((double)viewport / (double)content)));
      }

      private int thumbY() {
         int thumbHeight = this.thumbHeight();
         int trackHeight = Math.max(0, this.height - thumbHeight);
         if (this.screen.maxScroll <= 0) {
            return this.getY();
         } else {
            return this.getY() + (int)Math.round((double)trackHeight * ((double)this.screen.scrollOffset / (double)this.screen.maxScroll));
         }
      }

      protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput narration) {
         this.defaultButtonNarrationText(narration);
      }

      private static double clampDouble(double value, double min, double max) {
         return Math.max(min, Math.min(max, value));
      }
   }
}
