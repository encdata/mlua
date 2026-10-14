package dev.mlua.modules.coregui;

import dev.mlua.core.service.MLuaService;
import dev.mlua.modules.coregui.Service.MLuaGuiService;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.common.enums.Side;
import java.util.List;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public class GuiModule implements MLuaModule {
    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        if (side == Side.CLIENT || side == Side.BOTH) {
            dev.mlua.core.MLuaRuntimeSession session = (dev.mlua.core.MLuaRuntimeSession) runtimeSession;
            MLuaGuiService.clearForScheduler(session.clientScheduler());
        }
    }

    @Override
    public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        if (side == Side.CLIENT || side == Side.BOTH) {
            dev.mlua.core.MLuaRuntimeSession session = (dev.mlua.core.MLuaRuntimeSession) runtimeSession;
            MLuaGuiService gui = MLuaGuiService.getForScheduler(session.clientScheduler());
            if (gui != null) gui.clearOwned(ownerModId);
        }
    }

    @Override
    public String id() { return "gui"; }

    @Override
    public Side side() { return Side.CLIENT; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder gui = ServiceEntry.builder("Gui", MLuaGuiService::new)
            .description("Manages live UI instances, screen overlays, native Minecraft controls, and chat rendering. ScreenOpened supplies the screen class name, its live NativeControl instances, and a live NativeScreen; ChatLineAdding supplies a short-lived ChatLine whose Text, Prefix, and Suffix can be changed before Minecraft stores the message.");
        gui.function("GetEnabled", "Returns whether MLua GUI overlays are enabled.")
            .function("SetEnabled", "Enables or disables all MLua GUI overlays.")
            .function("GetScreens", "Returns live ScreenGui instances attached to the overlay.")
            .function("CreateScreen", "Creates and attaches a ScreenGui; parent UIObject instances to it to compose a screen.")
            .function("OpenNativeScreen", "Opens a real Minecraft Screen. Its init callback receives AddText(text, position, color), AddButton(label, position, size, callback), and SetOnClose(callback); positions and sizes are UDim2 values.")
            .function("OpenScreen", "Creates a registered Screen subclass and opens it as a native Minecraft Screen. Its Init method runs during Minecraft's screen init lifecycle.")
            .function("RemoveScreen", "Removes and destroys the first screen matching an instance or name.")
            .function("SetCoreGuiEnabled", "Shows or hides MLua overlays associated with a CoreGuiType channel.")
            .function("GetCoreGuiEnabled", "Returns whether MLua overlays associated with a CoreGuiType channel are enabled.")
            .function("AddScreenGui", "Adds a live ScreenGui instance or builds one from a definition table; Children must be a dense array so draw order matches the list, and child __type names resolve against the calling addon's registered UI classes.")
            .function("RemoveScreenGui", "Removes and destroys a ScreenGui by instance or name, returning whether one was removed.")
            .function("GetScreenGuis", "Returns live ScreenGui instances attached to the active overlay.")
            .function("GetCoreGui", "Returns the shared MLua ScreenGui overlay for a CoreGuiType channel, or nil if none exists.")
            .function("CreateCoreGui", "Gets or creates a shared MLua ScreenGui overlay associated with one CoreGuiType channel.")
            .function("RegisterScreenTarget", "Registers an addon-owned screen alias that matches a Minecraft screen class or interface and can be used by ScreenGui.TargetScreen and SetNativeLayout.")
            .function("UnregisterScreenTarget", "Removes this addon's screen alias and stops it from matching overlays or native layout rules.")
            .function("GetNativeControls", "Returns live NativeControl instances for widgets on the currently open Minecraft screen. Selectors can match one-based Index, a Java class or interface by simple or fully qualified name, Text, or any readable public bean property with a string, number, or boolean value.")
            .function("GetNativeControl", "Returns the first live NativeControl matching Index, a Java class or interface, Text, or a readable public bean property on the current screen.")
            .function("SetNativeLayout", "Registers a bulk layout rule and applies it to the current screen immediately when it matches. Class selectors include subclasses and implemented interfaces; property selectors compare readable public bean properties. Position and Size require native UDim2 values; Visible, Text, Replacement, Before, and After are supported, along with additional public bean properties whose setters accept supported Lua values. Before and After require an Index selector.")
            .function("RemoveNativeLayout", "Removes a native widget layout rule by its returned identifier.");
        return List.of(gui.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Gui", "MLuaGuiService",
                "Manages live UI instances, screen overlays, native Minecraft controls, and chat rendering. ScreenOpened supplies the screen class name, its live NativeControl instances, and a live NativeScreen; ChatLineAdding supplies a short-lived ChatLine whose Text, Prefix, and Suffix can be changed before Minecraft stores the message.",
                "services/gui.html");
        docs.event("Gui", "Gui.ScreenOpened", "screenClass: string, controls: NativeControl[], screen: NativeScreen",
                "Fires after Minecraft creates a screen's widgets. The third argument is a live NativeScreen instance; check CanInspectControls when an empty controls list could mean the screen does not expose Minecraft's standard child-widget API.", "client:Client");
        docs.guideStep("Gui", "Build a native Minecraft screen",
                "OpenNativeScreen creates and opens a real Minecraft Screen. Its callback runs during the screen's init lifecycle. AddText draws through Minecraft's screen graphics callback, and AddButton adds a native Minecraft button. The returned NativeScreen can inspect controls and close the screen.",
                "local Gui = Minecraft:GetService(\"Gui\")\nlocal screen = Gui:OpenNativeScreen(\"Radio Console\", function(screen)\n    screen:AddText(\"Emergency channel\", UDim2.fromOffset(40, 32), 0xFFFFFF)\n    screen:AddButton(\"Tune\", UDim2.fromOffset(40, 60), UDim2.fromOffset(120, 20), function()\n        print(\"Radio tuned\")\n    end)\n    screen:SetOnClose(function()\n        print(\"Radio console closed\")\n    end)\nend)\n\nprint(screen.IsOpen)");
        docs.guideStep("Gui", "Extend the native Screen class",
                "Register a subclass of Screen and add native widgets from Init. Gui:OpenScreen constructs the registered class, installs native widget methods, then runs Init during the actual Minecraft Screen init callback.",
                "Minecraft:RegisterClass(\"RadioScreen\", \"Screen\", \"A radio tuning screen.\")\nMinecraft:RegisterClassFunction(\"RadioScreen\", \"Init\", function(self)\n    self:AddText(\"Emergency channel\", UDim2.fromOffset(40, 32), 0xFFFFFF)\n    self:AddButton(\"Tune\", UDim2.fromOffset(40, 60), UDim2.fromOffset(120, 20), function()\n        print(\"Radio tuned\")\n    end)\nend, \"Builds the native radio screen widgets.\")\n\nlocal Gui = Minecraft:GetService(\"Gui\")\nlocal screen = Gui:OpenScreen(\"RadioScreen\", \"Radio Console\")");
        docs.guideStep("Gui", "Build a screen from live UI instances",
                "Create a ScreenGui, then parent normal UIObject instances into it. The instances use inherited properties and signals, so a TextButton can be composed inside a Frame like any other GuiObject.",
            "local Gui = Minecraft:GetService(\"Gui\")\nlocal screen = Gui:CreateScreen(\"HorrorHUD\", { DisplayOrder = 20 })\nlocal panel = UIObject.new(\"Frame\", \"FearPanel\")\nlocal title = UIObject.new(\"TextLabel\", \"Title\")\nlocal button = UIObject.new(\"TextButton\", \"StartButton\")\n\npanel.Size = UDim2.fromOffset(220, 110)\ntitle.Text = \"Something is nearby\"\nbutton.Text = \"Hide\"\nbutton.Activated:Connect(function()\n    print(\"Hide button activated\")\nend)\n\ntitle.Parent = panel\nbutton.Parent = panel\npanel.Parent = screen");
        docs.guideStep("Gui", "Handle pointer input on UI objects",
                "InputBegan and InputEnded fire for the object that received a mouse press. InputChanged and MouseMoved fire when the pointer moves over it; MouseWheelForward and MouseWheelBackward fire while scrolling over it. The event table includes UserInputType and Position.X/Position.Y; wheel events also include WheelDelta.",
                "local button = UIObject.new(\"TextButton\", \"InspectButton\")\nbutton.Text = \"Inspect\"\n\nbutton.InputBegan:Connect(function(input)\n    if input.UserInputType == \"MouseButton1\" then\n        print(\"Press at\", input.Position.X, input.Position.Y)\n    end\nend)\n\nbutton.Activated:Connect(function()\n    print(\"Released on the button\")\nend)");
        docs.guideStep("Gui", "Extend a UI class for an addon",
                "Register an addon class with a UI parent such as Frame. It inherits the parent's native constructor, properties, and methods. UIObject.new passes the selected class as self, so the inherited constructor creates a live native-backed instance of your subclass. Initial properties are applied before Init methods run from base class to subclass, once per class level; use RegisterClassFunction for each class method and include its description at registration.",
                "local FearPanel = Minecraft:RegisterClass(\"FearPanel\", \"Frame\", \"A reusable panel for horror warnings.\")\n\nMinecraft:RegisterClassFunction(\"FearPanel\", \"Init\", function(self, fields)\n    self.Size = fields.Size or UDim2.fromOffset(220, 80)\nend, \"Applies the initial size to a warning panel.\")\n\nMinecraft:RegisterClassFunction(\"FearPanel\", \"SetWarning\", function(self, text)\n    local label = self:FindFirstChild(\"Warning\")\n    if label then\n        label.Text = text\n    end\nend, \"Updates the panel's Warning label when it exists.\")\n\nlocal panel = UIObject.new(\"FearPanel\", \"WarningPanel\", {\n    Size = UDim2.fromOffset(260, 100),\n})\nlocal label = UIObject.new(\"TextLabel\", \"Warning\")\nlabel.Parent = panel\npanel:SetWarning(\"Something is behind you\")");
        docs.guideStep("Gui", "Change player chat lines",
                "ChatLineAdding runs before Minecraft stores each chat line. ChatLine is a short-lived live instance: OriginalText lets you inspect the input, Modified shows whether any addon changed it, Prefix and Suffix preserve the original formatting, and Text accepts either literal text or a Java Component so rich formatting can be retained.",
                "local Gui = Minecraft:GetService(\"Gui\")\nlocal Java = Minecraft:GetService(\"Java\")\nlocal Component = Java:LoadClass(\"net.minecraft.network.chat.Component\")\n\nGui.ChatLineAdding:Connect(function(line)\n    if line.OriginalText:find(\"emergency\") then\n        line.Text = Component.literal(\"[RADIO] Emergency call\")\n    else\n        line.Prefix = \"[RADIO] \"\n    end\n    if line.Modified then\n        print(\"An earlier listener changed this message\")\n    end\nend)");
        docs.guideStep("Gui", "Add content beside a native control",
                "ScreenOpened provides the screen class name, its live NativeControl instances, and a live NativeScreen. Check CanInspectControls before treating an empty list as a screen with no widgets. Find a widget by its Java class instead of relying on a version-specific list position, then place addon UI beside it with Before or After.",
                "local Gui = Minecraft:GetService(\"Gui\")\n\nGui.ScreenOpened:Connect(function(screenClass, controls, screen)\n    if screenClass:find(\"ChatScreen\") then\n        local inputs = screen:FindControls(\"EditBox\")\n        local chatInput = inputs[1]\n        if chatInput then\n            local badge = UIObject.new(\"TextLabel\", \"RadioBadge\")\n            badge.Text = \"RADIO\"\n            badge.Size = UDim2.fromOffset(60, 20)\n            chatInput.Before = badge\n            print(chatInput.WidgetClass, screen.IsOpen, #controls)\n        end\n    end\nend)");
        docs.guideStep("Gui", "Reposition a native control",
                "Use a native UDim2 for both dimensions in a layout rule. The rule applies to matching widgets when screens open and can be removed by its returned id.",
                "local Gui = Minecraft:GetService(\"Gui\")\nlocal layoutId = Gui:SetNativeLayout(\"ChatScreen\", { Index = 1 }, {\n    Position = UDim2.fromOffset(24, 24),\n    Size = UDim2.new(1, -48, 0, 22),\n})\n\nprint(\"Active layout\", layoutId)");
        docs.guideStep("Gui", "Target screens by class",
                "Set ScreenGui.TargetScreen to a Minecraft screen class name. MLua matches the concrete screen, its superclasses, and its interfaces, so custom screens can target a stable base class instead of a version-specific subclass name.",
                "local Gui = Minecraft:GetService(\"Gui\")\nlocal overlay = Gui:CreateScreen(\"ContainerTools\", { TargetScreen = \"AbstractContainerScreen\", RenderLayer = \"After\" })\nlocal panel = UIObject.new(\"Frame\", \"ContainerPanel\")\npanel.Parent = overlay");
        docs.guideStep("Gui", "Register a screen alias for an addon screen",
                "Register an addon-owned alias once, then use it from ScreenGui.TargetScreen or SetNativeLayout. The alias matches the named Java class, its superclasses, and its interfaces, and is removed when the registering addon reloads.",
                "local Gui = Minecraft:GetService(\"Gui\")\nGui:RegisterScreenTarget(\"MyModJournal\", \"com.example.mymod.JournalScreen\")\n\nlocal overlay = Gui:CreateScreen(\"JournalOverlay\", { TargetScreen = \"MyModJournal\" })\nlocal panel = UIObject.new(\"Frame\", \"JournalPanel\")\npanel.Parent = overlay");
        docs.guideStep("Gui", "Read additional native widget properties",
                "NativeControl exposes common properties directly. For other public widget properties, GetProperty and SetProperty find matching getX/isX/setX methods at runtime. Use those same readable values in selectors to find a control without relying on its index. Property access supports strings, numbers, booleans, enum names, and text components; unsupported Java property types produce an error.",
                "local Gui = Minecraft:GetService(\"Gui\")\n\nlocal input = Gui:GetNativeControl({ Class = \"EditBox\", Value = \"\" })\nif input then\n    input:SetProperty(\"Value\", \"radio channel open\")\nend");
        docs.guideStep("Gui", "Call a native widget method",
                "NativeControl:GetMethods lists the public methods on a live Minecraft widget. Invoke calls a method when MLua can convert its arguments; it returns supported strings, numbers, booleans, enum names, or text values, and returns nil for other Java result types.",
                "Gui.ScreenOpened:Connect(function(screenClass, controls)\n    local input = controls[1]\n    if not input then return end\n\n    for _, signature in ipairs(input:GetMethods()) do\n        print(signature)\n    end\n\n    input:Invoke(\"setMessage\", \"radio channel open\")\nend)");
        docs.guideStep("Gui", "Persist another native widget property",
                "SetNativeLayout accepts additional public bean properties when their setters accept a string, number, boolean, enum name, or text value. The property must also have a public getter so MLua can restore the original value when the layout is removed.",
                "local Gui = Minecraft:GetService(\"Gui\")\n\nlocal layoutId = Gui:SetNativeLayout(\"ChatScreen\", { Index = 1 }, {\n    Value = \"radio channel open\"\n})");
    }
}
