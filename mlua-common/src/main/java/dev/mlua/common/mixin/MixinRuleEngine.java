package dev.mlua.common.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MixinRuleEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger("MLuaMixinEngine");
    private static final MixinRuleEngine INSTANCE = new MixinRuleEngine();
    private static volatile boolean earlyBootstrapDone = false;
    private static volatile Runnable retransformTask;

    private final List<MLuaMixinRule> rules = new ArrayList<>();
    private final Map<String, String> applicationFailures = new ConcurrentHashMap<>();

    private MixinRuleEngine() {
    }

    public static MixinRuleEngine get() {
        return INSTANCE;
    }

    public static boolean isEarlyBootstrapDone() {
        return earlyBootstrapDone;
    }

    public static void markEarlyBootstrapDone() {
        earlyBootstrapDone = true;
    }

    public static void setRetransformTask(Runnable task) {
        retransformTask = task;
    }

    public static void triggerRetransform() {
        Runnable task = retransformTask;
        if (task != null) {
            task.run();
        } else {
            LOGGER.info("[MLua] No retransform task registered — rules will apply to classes loaded after this point");
        }
    }

    public void addRule(MLuaMixinRule rule) {
        validateRule(rule);
        synchronized (this.rules) {
            for (MLuaMixinRule existing : this.rules) {
                if (existing.callbackId().equals(rule.callbackId())) {
                    if (!existing.equals(rule)) {
                        throw new IllegalStateException("Mixin callback ID '" + rule.callbackId()
                            + "' is already registered for a different target rule");
                    }
                    return;
                }
                rejectDuplicateOverwrite(existing, rule);
            }
            this.rules.add(rule);
        }
    }

    public void registerRule(MLuaMixinRule rule, LuaValue callback) {
        validateRule(rule);
        if (callback == null || !callback.isfunction()) {
            throw new IllegalArgumentException("Mixin callback must be a function");
        }
        synchronized (this.rules) {
            for (MLuaMixinRule existing : this.rules) {
                if (existing.callbackId().equals(rule.callbackId()) && !existing.equals(rule)) {
                    throw new IllegalStateException("Mixin callback ID '" + rule.callbackId()
                        + "' is already registered for a different target rule");
                }
                if (!existing.callbackId().equals(rule.callbackId())) rejectDuplicateOverwrite(existing, rule);
            }
            boolean alreadyRegistered = this.rules.stream()
                .anyMatch(existing -> existing.callbackId().equals(rule.callbackId()));
            MLuaMixinCallbackRegistry.register(rule.callbackId(), callback);
            if (!alreadyRegistered) this.rules.add(rule);
        }
    }

    public List<MLuaMixinRule> getRules() {
        synchronized (this.rules) {
            return List.copyOf(this.rules);
        }
    }

    public void registerCallback(String id, LuaValue callback) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Mixin callback ID must be non-empty");
        if (callback == null || !callback.isfunction()) throw new IllegalArgumentException("Mixin callback must be a function");
        synchronized (this.rules) {
            MLuaMixinCallbackRegistry.register(id, callback);
        }
    }

    public LuaValue getCallback(String id) {
        return MLuaMixinCallbackRegistry.get(id);
    }

    public Map<String, LuaValue> getCallbacks() {
        return MLuaMixinCallbackRegistry.snapshot();
    }

    public void recordApplicationFailure(String className, Throwable failure) {
        if (className == null || className.isBlank() || failure == null) return;
        String message = failure.getMessage();
        this.applicationFailures.put(MLuaMixinRule.normalizeClassName(className),
            message == null || message.isBlank() ? failure.getClass().getSimpleName() : message);
    }

    public void recordApplicationFailure(String className, List<MLuaMixinRule> failedRules, Throwable failure) {
        recordApplicationFailure(className, failure);
        if (failedRules != null) {
            failedRules.forEach(rule -> MLuaMixinCallbackRegistry.unregisterOriginal(rule.callbackId()));
        }
    }

    public void clearApplicationFailure(String className) {
        if (className != null) this.applicationFailures.remove(MLuaMixinRule.normalizeClassName(className));
    }

    public Map<String, String> getApplicationFailures() {
        return Map.copyOf(this.applicationFailures);
    }

    public int unregisterScope(String scope) {
        if (scope == null || scope.isBlank()) return 0;
        String prefix = scope + ":";
        int removed;
        synchronized (this.rules) {
            java.util.Set<String> affectedTargets = this.rules.stream()
                .filter(rule -> rule.callbackId().startsWith(prefix))
                .map(rule -> MLuaMixinRule.normalizeClassName(rule.targetClassName()))
                .collect(java.util.stream.Collectors.toSet());
            int before = this.rules.size();
            this.rules.removeIf(rule -> rule.callbackId().startsWith(prefix));
            removed = before - this.rules.size();
            for (String target : affectedTargets) {
                if (this.rules.stream().noneMatch(rule -> rule.targetsClass(target))) this.applicationFailures.remove(target);
            }
            MLuaMixinCallbackRegistry.unregisterScope(scope);
        }
        return removed;
    }

    public void clear() {
        synchronized (this.rules) {
            this.rules.clear();
            this.applicationFailures.clear();
            MLuaMixinCallbackRegistry.clear();
        }
    }

    private static void validateRule(MLuaMixinRule rule) {
        if (rule == null) throw new NullPointerException("rule");
        if (rule.targetClassName() == null || rule.targetClassName().isBlank()) {
            throw new IllegalArgumentException("Mixin target class must be non-empty");
        }
        if (rule.methodName() == null || rule.methodName().isBlank()) {
            throw new IllegalArgumentException("Mixin target method must be non-empty");
        }
        if (rule.type() == null) throw new IllegalArgumentException("Mixin type must be provided");
        if (rule.callbackId() == null || rule.callbackId().isBlank()) {
            throw new IllegalArgumentException("Mixin callback ID must be non-empty");
        }
        if (rule.type() == dev.mlua.common.enums.MixinType.INJECT && rule.at() == null) {
            throw new IllegalArgumentException("Inject mixin requires an injection point");
        }
        if ((rule.type() == dev.mlua.common.enums.MixinType.REDIRECT
                || rule.type() == dev.mlua.common.enums.MixinType.MODIFY_ARG)
                && (rule.redirectTarget() == null || rule.redirectTarget().isBlank())) {
            throw new IllegalArgumentException("Redirect and ModifyArg mixins require a target");
        }
        if (rule.type() == dev.mlua.common.enums.MixinType.MODIFY_ARG && rule.modifyIndex() < 0) {
            throw new IllegalArgumentException("ModifyArg index must be non-negative");
        }
    }

    private static void rejectDuplicateOverwrite(MLuaMixinRule existing, MLuaMixinRule candidate) {
        if (existing.type() != dev.mlua.common.enums.MixinType.OVERWRITE
            || candidate.type() != dev.mlua.common.enums.MixinType.OVERWRITE) return;
        if (existing.targetsClass(candidate.targetClassName())
            && java.util.Objects.equals(existing.methodName(), candidate.methodName())
            && java.util.Objects.equals(existing.methodDescriptor(), candidate.methodDescriptor())) {
            throw new IllegalStateException("Java method '" + candidate.targetClassName() + "#"
                + candidate.methodName() + (candidate.methodDescriptor() == null ? "" : candidate.methodDescriptor())
                + "' already has an overwrite mixin");
        }
    }
}
