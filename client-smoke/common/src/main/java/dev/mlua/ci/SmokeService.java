package dev.mlua.ci;

import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.service.MLuaService;

@MLuaDescription("CI probe for Java contributed MLua services and service classes.")
public final class SmokeService extends MLuaReflectiveService {
    public SmokeService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "CiSmokeService";
    }

    @MLuaDescription("Returns the current script side and records that the Java service was called.")
    public String GetSide() {
        String side = this.context.side().name();
        System.setProperty("mlua.ci.service." + side, "called");
        return side;
    }

    @MLuaDescription("Marks the native screen lifecycle smoke check as complete.")
    public String MarkNativeScreenLifecycleComplete() {
        System.setProperty("mlua.ci.native_screen_lifecycle", "complete");
        return "complete";
    }
}
