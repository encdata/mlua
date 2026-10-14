package dev.mlua.modules.ui;

import dev.mlua.minecraft.binding.MLuaUIBindings;
import dev.mlua.minecraft.binding.MLuaNativeControl;
import dev.mlua.minecraft.binding.MLuaChatLine;
import dev.mlua.minecraft.binding.MLuaNativeScreen;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.MLuaContextView;
import dev.mlua.common.enums.Side;
import org.luaj.vm2.Globals;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public class UIModule implements MLuaModule {
    @Override
    public String id() { return "ui"; }

    @Override
    public Side side() { return Side.CLIENT; }

    @Override
    public int priority() { return -25; }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        MLuaUIBindings.registerClasses(registration);
        MLuaNativeControl.registerClass(registration);
        MLuaNativeScreen.registerClass(registration);
        MLuaChatLine.registerClass(registration);
        registration.registerInstantiableClass("Screen", registration.requireInstanceBaseClassName(),
            "Base class for Lua-defined Minecraft screens opened through the Gui service.");
    }

    @Override
    public void contributeGlobals(Globals globals, MLuaContextView context) {
        globals.set("UIObject", MLuaUIBindings.uiObjectTable(context.registration(), context.scheduler(),
            context.ownerModId(), context.side(), globals));
        globals.set("NativeControl", context.registration().classTable("NativeControl"));
        globals.set("NativeScreen", context.registration().classTable("NativeScreen"));
        globals.set("ChatLine", context.registration().classTable("ChatLine"));
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.clazz("NativeControl", "MLuaNativeControl", "A live handle to a native Minecraft screen widget. Properties update the original widget.", "classes.ui/native-control.html", "Instance");
        docs.clazz("NativeScreen", "MLuaNativeScreen", "A live handle to a native Minecraft screen and its native widget controls.", "classes.ui/native-screen.html", "Instance");
        docs.clazz("Screen", "MLuaScriptScreen", "Base class for Lua-defined native Minecraft screens. Init builds native widgets; Tick, Render, and OnClose follow Minecraft's screen lifecycle.", "classes.ui/screen.html", "Instance");
        docs.property("Screen", "Screen.NativeScreen", "NativeScreen", "The underlying live Minecraft screen handle.", "read:Read");
        docs.method("Screen", "Screen:Init(fields)", "Runs when Minecraft initializes the native screen and should add its native widgets here.", "Optional fields passed to Gui:OpenScreen", "void", "client:Client", null);
        docs.method("Screen", "Screen:Render(canvas, mouseX, mouseY, delta)", "Runs during Minecraft's native screen render lifecycle. Canvas provides Width, Height, Fill, and DrawText; DrawText accepts a string or Java Component.", "Canvas, pointer coordinates, and frame delta", "void", "client:Client", null);
        docs.method("Screen", "Screen:AddText(text, position, color)", "Draws native screen text from the screen initialization callback. Text accepts a string or Java Component.", "Text or Java Component, UDim2 position, and optional RGB color integer", "void", "client:Client", null);
        docs.method("Screen", "Screen:AddButton(text, position, size, callback)", "Adds a native Minecraft button and returns its live NativeControl. The label accepts a string or Java Component, and the click callback also receives the control.", "Label or Java Component, UDim2 position, UDim2 size, and callback accepting NativeControl", "NativeControl", "client:Client", null);
        docs.method("Screen", "Screen:AddEditBox(position, size, initialValue, onChanged)", "Adds a native Minecraft EditBox and returns its live NativeControl. onChanged receives the current value and control.", "UDim2 position, UDim2 size, initial value, and optional change callback", "NativeControl", "client:Client", null);
        docs.method("Screen", "Screen:SetOnClose(callback)", "Registers a callback for Minecraft's screen close lifecycle.", "Close callback", "void", "client:Client", null);
        docs.method("Screen", "Screen:SetOnRender(callback)", "Registers a per-frame draw callback for screens opened with Gui:OpenNativeScreen. Canvas DrawText accepts a string or Java Component.", "Callback receiving canvas, mouseX, mouseY, and delta", "void", "client:Client", null);
        docs.method("Screen", "Screen:Tick()", "Runs once for each native Minecraft screen tick when defined on a Screen subclass.", "No arguments", "void", "client:Client", null);
        docs.method("Screen", "Screen:KeyPressed(keyCode, scanCode, modifiers)", "Runs before the native screen handles a key press. Return true to consume it; return false or nil to let Minecraft handle it normally.", "Minecraft key code, scan code, and modifier mask", "boolean", "client:Client", null);
        docs.method("Screen", "Screen:KeyReleased(keyCode, scanCode, modifiers)", "Runs before the native screen handles a key release. Return true to consume it; return false or nil to let Minecraft handle it normally.", "Minecraft key code, scan code, and modifier mask", "boolean", "client:Client", null);
        docs.method("Screen", "Screen:CharTyped(character, modifiers)", "Runs before the native screen handles typed text. Minecraft 26.1 does not provide a modifier mask for CharacterEvent, so modifiers is zero there. Return true to consume it; return false or nil to let Minecraft handle it normally.", "Typed character string and modifier mask", "boolean", "client:Client", null);
        docs.method("Screen", "Screen:MouseClicked(mouseX, mouseY, button, doubleClick)", "Runs before the native screen handles a mouse click. doubleClick is false on Minecraft 1.20.1 and 1.21.1, which do not provide that flag. Return true to consume it; return false or nil to let Minecraft handle it normally.", "Pointer coordinates, mouse button index, and double-click flag", "boolean", "client:Client", null);
        docs.method("Screen", "Screen:MouseReleased(mouseX, mouseY, button)", "Runs before the native screen handles a mouse release. Return true to consume it; return false or nil to let Minecraft handle it normally.", "Pointer coordinates and mouse button index", "boolean", "client:Client", null);
        docs.method("Screen", "Screen:MouseDragged(mouseX, mouseY, button, dragX, dragY)", "Runs before the native screen handles a mouse drag. Return true to consume it; return false or nil to let Minecraft handle it normally.", "Pointer coordinates, pressed button, and drag deltas", "boolean", "client:Client", null);
        docs.method("Screen", "Screen:MouseMoved(mouseX, mouseY)", "Runs when the pointer moves over the native screen, before Minecraft forwards the movement to widgets. Return values are ignored.", "Pointer coordinates", "void", "client:Client", null);
        docs.method("Screen", "Screen:MouseScrolled(mouseX, mouseY, scrollX, scrollY)", "Runs before the native screen handles scrolling. On Minecraft 1.20.1, the single vertical delta is passed as scrollY and scrollX is zero. Return true to consume it; return false or nil to keep normal scrolling.", "Pointer coordinates and horizontal and vertical scroll amounts", "boolean", "client:Client", null);
        docs.method("Screen", "Screen:OnClose()", "Runs when Minecraft closes this screen, unless its owner is being unloaded.", "No arguments", "void", "client:Client", null);
        docs.method("Screen", "Screen:Close()", "Closes this screen if it is active.", "No arguments", "boolean", "client:Client", null);
        docs.method("NativeScreen", "NativeScreen:Close()", "Closes this screen if it is the active Minecraft screen.", "No arguments", "boolean", "client:Client", null);
        docs.clazz("ChatLine", "MLuaChatLine", "A live chat message while Minecraft is adding it to the chat HUD.", "classes.ui/chat-line.html", "Instance");
        docs.contribution(MLuaDocBuilder.Contribution.builder(id(), "ui:extend-gui-classes")
            .section("UI")
            .title("Extend the GUI class tree")
            .description("Register a UI class that inherits a built-in constructor, then create instances through the registered class tree.")
            .order(10)
            .step("Docs", "Register a subclass",
                "Declare the class in a registry script. Guard it because Frame is registered on clients only; on a dedicated server the registry script should skip this client-only class.",
                "if Minecraft.Classes.Frame then\n    Minecraft:RegisterClass(\"HorrorPanel\", \"Frame\", \"A panel used by the horror HUD.\")\n    Minecraft:RegisterClassFunction(\"HorrorPanel\", \"Pulse\", function(self)\n        self.PulseCount = (self.PulseCount or 0) + 1\n    end, \"Counts how many times this panel has pulsed.\")\nend")
            .step("Docs", "Create and attach the live instance",
                "In a client script, instantiate the registered class through UIObject, then parent it under a screen returned by the Gui service. The instance keeps the HorrorPanel class identity, inherits Frame behavior, and exposes the method registered on HorrorPanel.",
                "local Gui = Minecraft:GetService(\"Gui\")\nlocal screen = Gui:CreateScreen(\"HorrorHUD\")\n\nlocal panel = UIObject.HorrorPanel.new(\"PulsePanel\")\npanel.Size = UDim2.new(0, 240, 0, 80)\npanel.Parent = screen\npanel:Pulse()\nprint(panel:IsA(\"HorrorPanel\"), panel:IsA(\"Frame\"), panel.PulseCount)")
            .example("Add a child label", "Subclasses can use the same parenting and class lookup APIs as built-in UI instances.",
                "local Gui = Minecraft:GetService(\"Gui\")\nlocal screen = Gui:CreateScreen(\"HorrorHUD\")\nlocal panel = UIObject.HorrorPanel.new(\"PulsePanel\")\npanel.Parent = screen\n\nlocal label = UIObject.TextLabel.new(\"Warning\")\nlabel.Text = \"Something is behind you\"\nlabel.Parent = panel\n\nprint(label:GetFullName())")
            .build());
        docs.contribution(MLuaDocBuilder.Contribution.builder(id(), "ui:inspect-native-controls")
            .section("UI")
            .title("Inspect and change native controls")
            .description("Find Minecraft's own screen widgets and read or change their supported bean properties through live NativeControl instances.")
            .order(20)
            .step("Docs", "Select native widgets",
                "Gui:GetNativeControls accepts a Java widget class, its superclass, or a readable property selector. Each result stays attached to the current widget.",
                "local Gui = Minecraft:GetService(\"Gui\")\nlocal buttons = Gui:GetNativeControls({ Class = \"Button\" })\nprint(#buttons)")
            .step("Docs", "Inspect and set supported properties",
                "GetProperties reports which discovered Java bean properties support direct reads and writes. Property access preserves the native widget instance.",
                "for _, control in ipairs(buttons) do\n    local properties = control:GetProperties()\n    if properties.Message and properties.Message.Writable then\n        control.Message = \"Start the story\"\n    end\nend")
            .example("Inspect a native screen", "Use the generic property API for widget values that are not among NativeControl's built-in Position, Size, Visible, or Text properties.",
                "local Gui = Minecraft:GetService(\"Gui\")\nlocal control = Gui:GetNativeControl({ Class = \"Button\", Index = 1 })\nif control then\n    for name, property in pairs(control:GetProperties()) do\n        print(name, property.Readable, property.Writable, property.Type)\n    end\nend")
            .build());
    }
}
