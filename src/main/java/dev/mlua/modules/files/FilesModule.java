package dev.mlua.modules.files;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.modules.files.service.MLuaFilesService;
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
        docs.register(MLuaDocBuilder.Registration.signature("Files", "Files:ReadText(path: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Files", "Files:WriteText(path: string, content: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Files", "Files:AppendText(path: string, content: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Files", "Files:Exists(path: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Files", "Files:IsDirectory(path: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Files", "Files:CreateDirectory(path: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Files", "Files:List(path?: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Files", "Files:Delete(path: string)", null));

        docs.register(MLuaDocBuilder.Registration.service("Files", "MLuaFilesService", "Provides owner-scoped file operations in the MLua data directory.",
                "services/files.html"));
    }
}
