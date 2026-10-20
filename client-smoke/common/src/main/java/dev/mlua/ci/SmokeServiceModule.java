package dev.mlua.ci;

import dev.mlua.module.MLuaModule;
import java.util.List;

/** Java service provider contributed by the loader template mod in CI. */
public final class SmokeServiceModule implements MLuaModule {
    @Override
    public String id() {
        return "ci-java-service";
    }

    @Override
    public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("CiSmokeService", SmokeService::new)
            .className("CiSmokeService")
            .reflective(SmokeService.class)
            .build());
    }
}
