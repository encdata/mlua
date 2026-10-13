package dev.mlua.modules.enums;

import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.common.enums.EasingDirection;
import dev.mlua.common.enums.EasingStyle;
import dev.mlua.minecraft.binding.MLuaKeyCatalog;
import dev.mlua.minecraft.binding.MLuaMinecraftEnums;
import dev.mlua.module.MLuaModule;
import dev.mlua.modules.enums.MLuaEnumValue;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public class EnumsModule implements MLuaModule {
    @Override
    public String id() { return "enums"; }

    @Override
    public int priority() { return -100; }

    @Override
    public void contributeGlobals(Globals globals) {
        globals.set("Enum", enumTable());
    }

    /** Builds the same enum root exposed to scripts for documentation discovery. */
    public static LuaTable enumTable() {
        LuaTable enumTable = new LuaTable();
        enumTable.rawset("__mlua_doc_category", LuaValue.valueOf("Enums"));
        enumTable.set("GameMode", MLuaMinecraftEnums.gameModeTable());
        enumTable.set("Difficulty", MLuaMinecraftEnums.difficultyTable());
        enumTable.set("Weather", MLuaMinecraftEnums.weatherTable());
        enumTable.set("Direction", MLuaMinecraftEnums.directionTable());
        enumTable.set("Hand", MLuaMinecraftEnums.handTable());
        enumTable.set("SoundCategory", MLuaMinecraftEnums.soundCategoryTable());
        enumTable.set("EquipmentSlot", MLuaMinecraftEnums.equipmentSlotTable());
        enumTable.set("CoreGuiType", MLuaMinecraftEnums.coreGuiTypeTable());
        enumTable.set("ZIndexBehavior", MLuaMinecraftEnums.zIndexBehaviorTable());
        enumTable.set("ResampleMode", MLuaMinecraftEnums.resampleModeTable());
        enumTable.set("ScaleType", MLuaMinecraftEnums.scaleTypeTable());
        enumTable.set("TextXAlignment", MLuaMinecraftEnums.textXAlignmentTable());
        enumTable.set("TextYAlignment", MLuaMinecraftEnums.textYAlignmentTable());
        enumTable.set("Keys", MLuaKeyCatalog.keysTable());
        enumTable.set("AnimationPriority", MLuaEnumValue.enumConstants(AnimationPriority.class, null));
        enumTable.set("EasingStyle", MLuaEnumValue.enumConstants(EasingStyle.class, null));
        enumTable.set("EasingDirection", MLuaEnumValue.enumConstants(EasingDirection.class, null));

        LuaTable cameraMode = new LuaTable();
        cameraMode.set("Custom", "Custom");
        cameraMode.set("Scriptable", "Scriptable");
        cameraMode.set("FirstPerson", "FirstPerson");
        cameraMode.set("SecondPerson", "SecondPerson");
        cameraMode.set("ThirdPerson", "ThirdPerson");
        enumTable.set("CameraMode", cameraMode);

        LuaTable viewmode = new LuaTable();
        viewmode.set("FirstPerson", "FirstPerson");
        viewmode.set("SecondPerson", "SecondPerson");
        viewmode.set("ThirdPerson", "ThirdPerson");
        enumTable.set("Viewmode", viewmode);
        enumTable.set("ViewMode", viewmode);

        return enumTable;
    }
}
