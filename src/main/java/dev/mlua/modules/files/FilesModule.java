package dev.mlua.modules.files;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.modules.files.Service.MLuaFilesService;
import java.util.List;

public class FilesModule implements MLuaModule {
    @Override
    public String id() {
        return "files";
    }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder files = ServiceEntry.builder("Files", MLuaFilesService::new)
            .reflective(MLuaFilesService.class);
        return List.of(files.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.signature("Files", "Files:ReadText(path: string)", null);
        docs.signature("Files", "Files:WriteText(path: string, content: string)", null);

        docs.service("Files", "MLuaFilesService", "Provides file read/write operations in the MLua data directory.",
                "services/files.html");
    }
}
