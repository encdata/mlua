package dev.mlua.modules.title;

import dev.mlua.modules.title.Service.MLuaTitleService;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import java.util.List;

public class TitleModule implements MLuaModule {
    @Override public String id() { return "title"; }
    @Override public int priority() { return 12; }
    @Override public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("Title", MLuaTitleService::new)
            .reflective(MLuaTitleService.class)
            .build());
    }
    @Override public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Title", "MLuaTitleService", "Service for sending titles, subtitles, and action bars.", "services/title.html");
    }
}
