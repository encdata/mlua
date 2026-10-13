package dev.mlua.modules.mixin;

import dev.mlua.module.MLuaDocBuilder;

import dev.mlua.modules.mixin.runtime.MLuaMixinService;
import dev.mlua.module.MLuaModule;
import java.util.List;

public class MixinModule implements MLuaModule {
    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.signature("Mixin", "Mixin:Inject(targetClass: string, method: string, options: table)", "Set options.descriptor when the target class has multiple methods with this name.");
        docs.signature("Mixin", "Mixin:Target(targetClass: string):Inject(method: string, options: table)", "Bind a Java target class once, then declare its method mixins with Inject, Overwrite, Redirect, or ModifyArg. Set options.descriptor when the class has overloaded methods with this name.");
        docs.signature("Mixin", "Mixin:Target(targetClass: string):Overwrite(method: string, options: table)", "Set options.descriptor when the target class has multiple methods with this name.");
        docs.signature("Mixin", "Mixin:Target(targetClass: string):Redirect(method: string, options: table)", "Set options.target to the exact invocation descriptor to replace.");
        docs.signature("Mixin", "Mixin:Target(targetClass: string):ModifyArg(method: string, options: table)", "Set options.target and the zero-based options.index for the invocation argument to change.");
        docs.signature("Mixin", "Mixin:Overwrite(targetClass: string, method: string, options: table)", "Set options.descriptor when the target class has multiple methods with this name.");
        docs.signature("Mixin", "Mixin:ModifyArg(targetClass: string, method: string, options: table)", "Index is zero-based; use descriptor when an overloaded method is ambiguous.");
        docs.signature("Mixin", "Mixin:Redirect(targetClass: string, method: string, options: table)", "Target is an exact invocation descriptor; use descriptor when an overloaded enclosing method is ambiguous.");
    }

    @Override
    public String id() { return "mixin"; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder mixin = ServiceEntry.builder("Mixin", MLuaMixinService::new)
            .description("Registers method mixins from mixin and registry scripts. Provide a JVM descriptor when loader mappings make an overloaded method ambiguous.");
        mixin.function("CallOriginal", "Calls the original method from the matching overwrite callback.")
            .function("Target", "Binds a Java target class and returns target-scoped Inject, Overwrite, Redirect, and ModifyArg methods.")
            .function("GetRules", "Returns mixin rules registered by the current script.")
            .function("GetFailures", "Returns target classes whose registered mixin rules could not be applied.")
            .function("Inject", "Injects a callback at the HEAD or RETURN of a target method.")
            .function("Overwrite", "Replaces a target method with a Lua callback.")
            .function("Redirect", "Replaces an exact invocation selected by target = 'Lowner;method(descriptor)returnType'; nil preserves the original call.")
            .function("ModifyArg", "Changes a zero-based argument at the exact invocation selected by target = 'Lowner;method(descriptor)returnType'.");
        return List.of(mixin.build());
    }
}
