package dev.mlua.modules.title;

import dev.mlua.modules.title.Service.MLuaTitleService;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import java.util.List;

public class TitleModule implements MLuaModule {
    @Override public String id() { return "title"; }
    @Override public int priority() { return 12; }
    @Override public List<ServiceEntry> services() {
        ServiceEntry.Builder title = ServiceEntry.builder("Title", MLuaTitleService::new)
            .description("Service for sending titles, subtitles, and action bars.");
        title.function("SetTitle", "Displays a title to a player.")
            .function("SetSubtitle", "Displays a subtitle to a player.")
            .function("SetActionBar", "Displays a short action bar message to a player.")
            .function("ClearTitle", "Clears the current title and subtitle.")
            .function("ResetTitle", "Resets title display timing to the default.")
            .function("SetTitleAnimation", "Sets title fade-in, display, and fade-out timing.");
        return List.of(title.build());
    }
    @Override public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Title", "MLuaTitleService", "Service for sending titles, subtitles, and action bars.", "services/title.html");
    }
}
