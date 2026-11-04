package dev.mlua.modules.javabridge.service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.luaj.vm2.LuaError;

/** Selects a Java overload using MLua's typed conversion scores and specificity rules. */
final class MLuaJavaOverloadResolver {
    private final MLuaJavaService service;

    MLuaJavaOverloadResolver(MLuaJavaService service) {
        this.service = service;
    }

    Constructor<?> resolveConstructor(Class<?> clazz, Object[] rawArgs) {
        return this.resolveBestExecutable(this.service.reflection.constructors(clazz), rawArgs);
    }

    Method resolveMethod(List<Method> candidates, Object[] rawArgs) {
        return this.resolveBestExecutable(candidates, rawArgs);
    }

    private <T extends Executable> T resolveBestExecutable(List<T> candidates, Object[] rawArgs) {
        int bestScore = Integer.MAX_VALUE;
        List<T> bestMatches = new ArrayList<>();
        for (T candidate : candidates) {
            int score = this.compatibilityScore(candidate, rawArgs);
            if (score < 0 || score > bestScore) continue;
            if (score < bestScore) {
                bestScore = score;
                bestMatches.clear();
            }
            bestMatches.add(candidate);
        }
        if (bestMatches.isEmpty()) {
            if (this.hasScalarContainerArgument(candidates, rawArgs)) {
                throw new LuaError("[MLua] Java array and Iterable parameters require a dense Lua array table, Java array, or Java collection; a scalar value is not wrapped automatically");
            }
            return null;
        }
        if (bestMatches.size() == 1) return bestMatches.get(0);

        for (T candidate : bestMatches) {
            boolean mostSpecific = true;
            for (T other : bestMatches) {
                if (candidate != other && !this.isPreferredExecutable(candidate, other, rawArgs)) {
                    mostSpecific = false;
                    break;
                }
            }
            if (mostSpecific) return candidate;
        }
        List<String> descriptions = new ArrayList<>(bestMatches.size());
        for (T candidate : bestMatches) descriptions.add(this.executableDescription(candidate));
        descriptions.sort(String::compareTo);
        throw new LuaError("[MLua] Ambiguous Java call for arguments " + this.service.describeArgs(rawArgs)
            + ". Matching signatures: " + descriptions);
    }

    private boolean hasScalarContainerArgument(List<? extends Executable> candidates, Object[] rawArgs) {
        for (Executable candidate : candidates) {
            Class<?>[] parameterTypes = candidate.getParameterTypes();
            if (candidate.isVarArgs() || parameterTypes.length != rawArgs.length) continue;
            for (int index = 0; index < parameterTypes.length; index++) {
                Class<?> parameterType = parameterTypes[index];
                Object value = rawArgs[index];
                if ((parameterType.isArray() || Iterable.class.isAssignableFrom(parameterType))
                    && value != null && !this.service.isDeferredLuaTable(value)
                    && !(value instanceof Iterable<?>) && !value.getClass().isArray()) {
                    return true;
                }
            }
        }
        return false;
    }

    private String executableDescription(Executable executable) {
        if (executable instanceof Method method) return this.service.methodSignature(method);
        Constructor<?> constructor = (Constructor<?>) executable;
        StringBuilder description = new StringBuilder(constructor.getDeclaringClass().getName()).append('(');
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) description.append(", ");
            description.append(parameterTypes[i].getName());
        }
        return description.append(')').toString();
    }

    private int compatibilityScore(Executable executable, Object[] rawArgs) {
        Class<?>[] parameterTypes = executable.getParameterTypes();
        Type[] genericTypes = executable.getGenericParameterTypes();
        if (executable.isVarArgs() && rawArgs.length >= parameterTypes.length - 1) {
            return this.compatibilityScoreVarArgs(parameterTypes, genericTypes, rawArgs);
        }
        if (parameterTypes.length != rawArgs.length) return -1;

        int score = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            int parameterScore = this.service.coercionScore(rawArgs[i], parameterTypes[i], genericTypes[i]);
            if (parameterScore < 0) {
                return -1;
            }
            score += parameterScore;
        }
        return score;
    }

    private int compatibilityScoreVarArgs(Class<?>[] parameterTypes, Type[] genericTypes, Object[] rawArgs) {
        int fixedCount = parameterTypes.length - 1;
        int score = 1;
        for (int i = 0; i < fixedCount; i++) {
            int parameterScore = this.service.coercionScore(rawArgs[i], parameterTypes[i], genericTypes[i]);
            if (parameterScore < 0) {
                return -1;
            }
            score += parameterScore;
        }

        Class<?> componentType = parameterTypes[fixedCount].getComponentType();
        if (this.service.hasPackedVarargs(parameterTypes, rawArgs)) {
            int packedScore = this.service.coercionScore(rawArgs[fixedCount], parameterTypes[fixedCount], genericTypes[fixedCount]);
            return packedScore < 0 ? -1 : score + packedScore;
        }

        Type componentGenericType = genericTypes[fixedCount] instanceof java.lang.reflect.GenericArrayType genericArray
            ? genericArray.getGenericComponentType() : componentType;
        for (int i = fixedCount; i < rawArgs.length; i++) {
            int parameterScore = this.service.coercionScore(rawArgs[i], componentType, componentGenericType);
            if (parameterScore < 0) {
                return -1;
            }
            score += parameterScore;
        }
        return score;
    }

    private boolean isPreferredExecutable(Executable candidate, Executable currentBest, Object[] rawArgs) {
        if (currentBest == null) {
            return true;
        }
        if (candidate.isVarArgs() != currentBest.isVarArgs()) {
            return !candidate.isVarArgs();
        }
        int specificity = this.compareSpecificity(candidate.getParameterTypes(), currentBest.getParameterTypes(), rawArgs);
        if (specificity != 0) {
            return specificity < 0;
        }
        return currentBest.getDeclaringClass() != candidate.getDeclaringClass()
            && currentBest.getDeclaringClass().isAssignableFrom(candidate.getDeclaringClass());
    }

    private int compareSpecificity(Class<?>[] left, Class<?>[] right, Object[] rawArgs) {
        boolean leftBetter = false;
        boolean rightBetter = false;
        int len = Math.min(left.length, right.length);
        for (int i = 0; i < len; i++) {
            Class<?> leftType = this.service.boxType(left[i]);
            Class<?> rightType = this.service.boxType(right[i]);
            Object rawArg = i < rawArgs.length ? rawArgs[i] : null;

            if (rawArg != null) {
                Class<?> rawType = rawArg.getClass();
                boolean leftExact = leftType == rawType;
                boolean rightExact = rightType == rawType;
                if (leftExact != rightExact) {
                    if (leftExact) {
                        leftBetter = true;
                    } else {
                        rightBetter = true;
                    }
                    continue;
                }
            }

            if (leftType != rightType) {
                if (leftType.isAssignableFrom(rightType)) {
                    rightBetter = true;
                } else if (rightType.isAssignableFrom(leftType)) {
                    leftBetter = true;
                }
            }
        }
        if (leftBetter == rightBetter) {
            return 0;
        }
        return leftBetter ? -1 : 1;
    }
}
