package dev.mlua.minecraft.ui.element;

import dev.mlua.common.enums.CoreGuiType;
import org.luaj.vm2.LuaValue;

public class ScreenGui extends LayerCollector {
    public String name;
    public int displayOrder = 0;
    private boolean clipToDeviceSafeArea = true;
    private boolean ignoreGuiInset = false;
    private CoreGuiType coreGuiType = CoreGuiType.All;
    private String targetScreen = "Game";
    private String renderLayer = "After";

    public ScreenGui(String name) {
        this.name = name;
    }

    public void setDisplayOrder(int order) { this.displayOrder = order; }
    public int getDisplayOrder() { return displayOrder; }
    @Override public void setName(String name) { this.name = name == null ? "" : name; syncLuaProperty("Name", LuaValue.valueOf(this.name)); }
    @Override public String getName() { return name; }
    public void setClipToDeviceSafeArea(boolean clip) { this.clipToDeviceSafeArea = clip; }
    public boolean getClipToDeviceSafeArea() { return clipToDeviceSafeArea; }
    public void setIgnoreGuiInset(boolean ignore) { this.ignoreGuiInset = ignore; }
    public boolean getIgnoreGuiInset() { return ignoreGuiInset; }
    public void setCoreGuiType(CoreGuiType type) { this.coreGuiType = type; }
    public CoreGuiType getCoreGuiType() { return coreGuiType; }
    public void setTargetScreen(String targetScreen) {
        if (targetScreen == null || targetScreen.isBlank()) throw new IllegalArgumentException("TargetScreen must be non-empty");
        this.targetScreen = targetScreen;
    }
    public String getTargetScreen() { return targetScreen; }
    public void setRenderLayer(String renderLayer) {
        if (renderLayer == null || (!renderLayer.equalsIgnoreCase("Before") && !renderLayer.equalsIgnoreCase("After"))) {
            throw new IllegalArgumentException("RenderLayer must be 'Before' or 'After'");
        }
        this.renderLayer = renderLayer.equalsIgnoreCase("Before") ? "Before" : "After";
    }
    public String getRenderLayer() { return renderLayer; }

    public boolean targetsScreen(Object screen) {
        if ("Any".equalsIgnoreCase(this.targetScreen)) return true;
        if (screen == null) return "Game".equalsIgnoreCase(this.targetScreen)
            || "InGame".equalsIgnoreCase(this.targetScreen)
            || "HUD".equalsIgnoreCase(this.targetScreen)
            || "ChatHud".equalsIgnoreCase(this.targetScreen);
        if ("HUD".equalsIgnoreCase(this.targetScreen) || "ChatHud".equalsIgnoreCase(this.targetScreen)) return false;
        String screenName = screen.getClass().getSimpleName();
        String target = this.targetScreen;
        if ("Settings".equalsIgnoreCase(target) || "Options".equalsIgnoreCase(target)) {
            return screenName.equals("OptionsScreen") || screenName.contains("Options")
                || screenName.equals("ControlsScreen") || screenName.equals("LanguageSelectScreen");
        }
        if ("Multiplayer".equalsIgnoreCase(target)) {
            return screenName.contains("Multiplayer") || screenName.contains("ServerSelection");
        }
        if ("PauseMenu".equalsIgnoreCase(target) || "Pause".equalsIgnoreCase(target)) {
            return screenName.equals("PauseScreen");
        }
        if ("Chat".equalsIgnoreCase(target)) return screenName.equals("ChatScreen");
        if ("Inventory".equalsIgnoreCase(target)) return screenName.contains("InventoryScreen");
        if ("Container".equalsIgnoreCase(target)) {
            return screenName.contains("ContainerScreen") || screenName.contains("InventoryScreen");
        }
        if ("Title".equalsIgnoreCase(target) || "MainMenu".equalsIgnoreCase(target)) {
            return screenName.equals("TitleScreen");
        }
        if ("WorldSelect".equalsIgnoreCase(target)) {
            return screenName.contains("SelectWorld") || screenName.contains("CreateWorld");
        }
        if ("ResourcePacks".equalsIgnoreCase(target)) return screenName.contains("PackSelection");
        if ("Death".equalsIgnoreCase(target)) return screenName.equals("DeathScreen");
        if (dev.mlua.modules.coregui.Service.MLuaGuiService.matchesRegisteredScreenTarget(
                this.scheduler(), target, screen)) return true;
        return matchesScreenType(screen.getClass(), target, new java.util.HashSet<>());
    }

    public static boolean isBuiltInScreenTarget(String target) {
        if (target == null) return false;
        return java.util.Set.of("any", "game", "ingame", "hud", "chathud", "settings", "options",
            "multiplayer", "pausemenu", "pause", "chat", "inventory", "container", "title",
            "mainmenu", "worldselect", "resourcepacks", "death").contains(target.toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean matchesScreenClass(Class<?> type, String target) {
        return matchesScreenType(type, target, new java.util.HashSet<>());
    }

    private static boolean matchesScreenType(Class<?> type, String target, java.util.Set<Class<?>> visited) {
        if (type == null || !visited.add(type)) return false;
        if (type.getSimpleName().equalsIgnoreCase(target) || type.getName().equals(target)) return true;
        for (Class<?> implemented : type.getInterfaces()) {
            if (matchesScreenType(implemented, target, visited)) return true;
        }
        return matchesScreenType(type.getSuperclass(), target, visited);
    }

    @Override protected String elementName() { return name; }

}
