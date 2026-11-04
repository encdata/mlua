package dev.mlua.transformer;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import dev.mlua.common.mixin.MLuaMixinMapping;
import dev.mlua.common.mixin.MLuaMixinRule;
import dev.mlua.common.mixin.MixinRuleEngine;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MLuaTransformer implements ITransformer<ClassNode> {
   private static final Logger LOGGER = LoggerFactory.getLogger("MLuaTransformer");
   private final Map<String, Map<String, String>> nameMapping;

   public MLuaTransformer() {
      this.nameMapping = loadNameMapping();
   }

   static Map<String, Map<String, String>> loadNameMapping() {
      return MLuaMixinMapping.load(MLuaTransformer.class.getClassLoader());
   }

   static String resolveMethodName(String className, String officialName, String descriptor,
                                   Map<String, Map<String, String>> mapping) {
      return MLuaMixinMapping.resolveMethodName(className, officialName, descriptor, mapping);
   }

   @Override
   public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
      List<MLuaMixinRule> matchingRules = List.of();
      try {
         String binaryName = context.getClassName();
         if (binaryName == null) {
            return input;
         }

         matchingRules = MixinRuleEngine.get().getRules().stream()
            .filter(rule -> rule.targetsClass(binaryName))
            .toList();

         if (matchingRules.isEmpty()) return input;

         LOGGER.info("[MLua] Transforming class '{}' with {} mixin rules", binaryName, matchingRules.size());
         ClassNode transformed = MLuaMixinClassWriter.applyToClassNodeCopy(input, binaryName, matchingRules, nameMapping);
         if (transformed != input) {
            LOGGER.info("[MLua] Class '{}' transformed successfully", binaryName);
         }
         MixinRuleEngine.get().clearApplicationFailure(binaryName);
         return transformed;
      } catch (Exception e) {
         LOGGER.error("[MLua] Transform error for class '{}'", context.getClassName(), e);
         MixinRuleEngine.get().recordApplicationFailure(context.getClassName(), matchingRules, e);
         return input;
      }
   }

   @Override
   public TransformerVoteResult castVote(ITransformerVotingContext context) {
      return TransformerVoteResult.YES;
   }

   @Override
   public Set<Target<ClassNode>> targets() {
      try {
         return MixinRuleEngine.get().getRules().stream()
            .map(rule -> MLuaMixinRule.normalizeClassName(rule.targetClassName()))
            .map(ITransformer.Target::<ClassNode>targetClass)
            .collect(Collectors.toSet());
      } catch (Exception e) {
         LOGGER.warn("[MLua] targets() failed: {}", e.getMessage());
         return Set.of();
      }
   }

   @Override
   public TargetType<ClassNode> getTargetType() {
      return TargetType.CLASS;
   }
}
