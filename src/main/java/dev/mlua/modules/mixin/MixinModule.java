package dev.mlua.modules.mixin;

import dev.mlua.module.MLuaDocBuilder;

import dev.mlua.common.mixin.MLuaMixinLuaApi;
import dev.mlua.modules.mixin.runtime.MLuaMixinService;
import dev.mlua.module.MLuaModule;
import java.util.List;

public class MixinModule implements MLuaModule {
    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.signature("Mixin", "Mixin:Inject(targetClass: string, method: string, options: MixinOptions)", "Use options.at = Enum.MixinAt.Head, Return, Tail, or Invoke. For Invoke, set options.target to the exact invocation descriptor; the callback then receives invocation receiver and arguments after the enclosing method arguments. Set options.descriptor when the target class has overloaded methods.");
        docs.signature("Mixin", "Mixin:Target(targetClass: string):Inject(method: string, options: MixinOptions)", "Bind a Java target class once, then declare its method mixins with Inject, Overwrite, Redirect, or ModifyArg. Use Enum.MixinAt for the injection point; Invoke requires an exact options.target descriptor.");
        docs.signature("Mixin", "Mixin:Target(targetClass: string):Overwrite(method: string, options: table)", "Set options.descriptor when the target class has multiple methods with this name.");
        docs.signature("Mixin", "Mixin:Target(targetClass: string):Redirect(method: string, options: table)", "Set options.target to the exact invocation descriptor to replace.");
        docs.signature("Mixin", "Mixin:Target(targetClass: string):ModifyArg(method: string, options: table)", "Set options.target and the zero-based options.index for the invocation argument to change.");
        docs.signature("Mixin", "Mixin:Overwrite(targetClass: string, method: string, options: table)", "Set options.descriptor when the target class has multiple methods with this name.");
        docs.signature("Mixin", "Mixin:ModifyArg(targetClass: string, method: string, options: table)", "Index is zero-based; use descriptor when an overloaded method is ambiguous.");
        docs.signature("Mixin", "Mixin:Redirect(targetClass: string, method: string, options: table)", "Target is an exact invocation descriptor; use descriptor when an overloaded enclosing method is ambiguous.");
        docs.type("MixinOptions", null, "Options shared by method mixin registrations.");
        docs.field("MixinOptions", "callback", "function", "Lua callback invoked by the transformed Java method.");
        docs.field("MixinOptions", "at", "Enum.MixinAt?", "Injection location for Inject. Defaults to Head.");
        docs.field("MixinOptions", "target", "string?", "Exact invocation descriptor required by INVOKE, Redirect, or ModifyArg.");
        docs.field("MixinOptions", "descriptor", "string?", "JVM method descriptor used to disambiguate overloads.");
        docs.field("MixinOptions", "index", "number?", "Zero-based invocation argument index used by ModifyArg.");
    }

    @Override
    public String id() { return "mixin"; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder mixin = ServiceEntry.builder("Mixin", MLuaMixinService::new)
            .description("Registers method mixins from mixin and registry scripts. Provide a JVM descriptor when loader mappings make an overloaded method ambiguous.");
        MLuaMixinLuaApi.functionDescriptions().forEach(mixin::function);
        return List.of(mixin.build());
    }
}
