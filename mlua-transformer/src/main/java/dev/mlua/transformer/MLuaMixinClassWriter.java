package dev.mlua.transformer;

import dev.mlua.common.enums.InjectionPoint;
import dev.mlua.common.mixin.MLuaMixinRule;
import dev.mlua.common.mixin.MLuaMixinCallbackRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MLuaMixinClassWriter {
   private static final Logger LOGGER = LoggerFactory.getLogger("MLuaTransformer");
   private static final String REGISTRY = "dev/mlua/common/mixin/MLuaMixinCallbackRegistry";
   private static final String BRIDGE = "dev/mlua/common/mixin/MLuaMixinBridge";

   private MLuaMixinClassWriter() {
   }

   public static boolean applyToClassNode(ClassNode classNode, String className, List<MLuaMixinRule> rules,
                                           Map<String, Map<String, String>> nameMapping) {
      boolean modified = false;
      for (MLuaMixinRule rule : rules) {
         if (applyRule(classNode, rule, nameMapping)) {
            modified = true;
         }
      }
      return modified;
   }

   /** Applies a rule batch to a copy so failures never leave a loader's input node partially modified. */
   public static ClassNode applyToClassNodeCopy(ClassNode original, String className, List<MLuaMixinRule> rules,
                                                 Map<String, Map<String, String>> nameMapping) {
      ClassNode copy = new ClassNode(Opcodes.ASM9);
      original.accept(copy);
      return applyToClassNode(copy, className, rules, nameMapping) ? copy : original;
   }

   public static byte[] apply(byte[] originalClassBytes, String className, List<MLuaMixinRule> rules) {
      return apply(originalClassBytes, className, rules, Map.of());
   }

   public static byte[] apply(byte[] originalClassBytes, String className, List<MLuaMixinRule> rules,
                               Map<String, Map<String, String>> nameMapping) {
      ClassReader reader = new ClassReader(originalClassBytes);
      ClassNode classNode = new ClassNode(Opcodes.ASM9);
      reader.accept(classNode, ClassReader.EXPAND_FRAMES);
      boolean modified = applyToClassNode(classNode, className, rules, nameMapping);
      if (!modified) {
         return null;
      }

      ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
      classNode.accept(writer);
      return writer.toByteArray();
   }

   private static boolean applyRule(ClassNode classNode, MLuaMixinRule rule) {
      return applyRule(classNode, rule, Map.of());
   }

   private static boolean applyRule(ClassNode classNode, MLuaMixinRule rule,
                                     Map<String, Map<String, String>> nameMapping) {
      String targetMethod = rule.methodName();
      String targetDesc = rule.methodDescriptor();
      String mappedMethod = targetMethod;
      if (!nameMapping.isEmpty()) {
         String srg = MLuaTransformer.resolveMethodName(
            rule.targetClassName(), targetMethod, targetDesc, nameMapping);
         if (srg != null) {
            mappedMethod = srg;
         }
      }
      final String resolvedMethod = mappedMethod;

      List<MethodNode> matches = classNode.methods.stream()
         .filter(method -> (method.name.equals(resolvedMethod) || method.name.equals(targetMethod))
            && (targetDesc == null || targetDesc.isEmpty() || method.desc.equals(targetDesc)))
         .toList();
      if (matches.isEmpty()) {
         String actualMethods = classNode.methods.stream()
            .map(method -> method.name + (targetDesc != null ? " " + method.desc : ""))
            .collect(Collectors.joining(", "));
         throw new IllegalStateException("[MLua] No matching method found for mixin rule: " + rule.targetClassName()
            + "#" + targetMethod + " (resolved: " + resolvedMethod + ", descriptor: "
            + (targetDesc != null ? targetDesc : "(any)") + ") — available methods in class: [" + actualMethods + "]");
      }
      if ((targetDesc == null || targetDesc.isEmpty()) && matches.size() > 1) {
         String candidates = matches.stream()
            .map(method -> method.name + method.desc)
            .collect(Collectors.joining(", "));
         throw new IllegalStateException("[MLua] Mixin target '" + rule.targetClassName() + "#"
            + targetMethod + "' is overloaded; set 'descriptor' to one of: " + candidates);
      }

      for (MethodNode method : matches) {
         if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            throw new IllegalStateException("Mixin target method '" + rule.targetClassName() + "#"
               + method.name + method.desc + "' has no bytecode body");
         }
         switch (rule.type()) {
            case INJECT -> injectAt(classNode, method, rule);
            case OVERWRITE -> overwrite(classNode, method, rule);
            case REDIRECT -> redirect(method, rule);
            case MODIFY_ARG -> modifyArg(method, rule);
         }
      }

      return true;
   }

   private static void injectAt(ClassNode classNode, MethodNode method, MLuaMixinRule rule) {
      if (hasBridgeCallback(method, rule.callbackId())) return;
      if (rule.at() == InjectionPoint.HEAD) {
         if (method.name.equals("<init>")) {
            injectAfterInitSuper(method, rule.callbackId());
         } else {
            injectHead(method, rule.callbackId());
         }
         if (hasBridgeCallback(method, rule.callbackId())) {
            LOGGER.info("[MLua] Injected HEAD callback into {}#{}", classNode.name, method.name);
         }
      } else if (rule.at() == InjectionPoint.RETURN) {
         injectBeforeReturns(method, rule.callbackId());
         if (hasBridgeCallback(method, rule.callbackId())) {
            LOGGER.info("[MLua] Injected RETURN callback into {}#{}", classNode.name, method.name);
         }
      } else if (rule.at() == InjectionPoint.TAIL) {
         injectBeforeTailReturn(method, rule.callbackId());
         if (hasBridgeCallback(method, rule.callbackId())) {
            LOGGER.info("[MLua] Injected TAIL callback into {}#{}", classNode.name, method.name);
         }
      } else if (rule.at() == InjectionPoint.INVOKE) {
         injectBeforeInvocation(method, rule);
         if (hasBridgeCallback(method, rule.callbackId())) {
            LOGGER.info("[MLua] Injected INVOKE callback into {}#{}", classNode.name, method.name);
         }
      }
      if (!hasBridgeCallback(method, rule.callbackId())) {
         throw new IllegalStateException("[MLua] Inject could not place callback at " + rule.at()
            + " in " + rule.targetClassName() + "#" + method.name + method.desc);
      }
   }

   private static void injectBeforeInvocation(MethodNode method, MLuaMixinRule rule) {
      if (hasBridgeCallback(method, rule.callbackId())) return;
      InvocationTarget target = InvocationTarget.parse(rule.redirectTarget());
      List<AbstractInsnNode> calls = new ArrayList<>();
      for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
         if (instruction instanceof MethodInsnNode invocation
               && target.owner().equals(invocation.owner)
               && target.name().equals(invocation.name)
               && target.descriptor().equals(invocation.desc)) {
            calls.add(instruction);
         }
      }
      for (AbstractInsnNode call : calls) {
         MethodInsnNode invocation = (MethodInsnNode) call;
         method.instructions.insertBefore(call, notifyInvocation(rule.callbackId(), method, invocation));
      }
   }

   private static InsnList notifyInvocation(String callbackId, MethodNode enclosing, MethodInsnNode invocation) {
      Type[] invokedTypes = Type.getArgumentTypes(invocation.desc);
      boolean isStatic = invocation.getOpcode() == Opcodes.INVOKESTATIC;
      int[] invokedLocals = new int[invokedTypes.length];
      for (int index = invokedTypes.length - 1; index >= 0; index--) {
         invokedLocals[index] = allocateLocal(enclosing, invokedTypes[index]);
      }
      int receiverLocal = isStatic ? -1 : allocateLocal(enclosing, Type.getObjectType(invocation.owner));
      InsnList injected = new InsnList();
      for (int index = invokedTypes.length - 1; index >= 0; index--) {
         injected.add(new VarInsnNode(storeOpcode(invokedTypes[index]), invokedLocals[index]));
      }
      if (!isStatic) injected.add(new VarInsnNode(Opcodes.ASTORE, receiverLocal));
      pushInvocationContext(injected, callbackId, enclosing, isStatic, receiverLocal, invokedTypes, invokedLocals);
      if (!isStatic) injected.add(new VarInsnNode(Opcodes.ALOAD, receiverLocal));
      for (int index = 0; index < invokedTypes.length; index++) {
         loadLocal(injected, invokedTypes[index], invokedLocals[index]);
      }
      return injected;
   }

   private static void pushInvocationContext(InsnList insns, String callbackId, MethodNode enclosing,
                                            boolean isStatic, int receiverLocal,
                                            Type[] invokedTypes, int[] invokedLocals) {
      Type[] enclosingTypes = Type.getArgumentTypes(enclosing.desc);
      int argumentCount = enclosingTypes.length + 1 + invokedTypes.length;
      insns.add(new LdcInsnNode(callbackId));
      pushSelf(insns, enclosing);
      pushInt(insns, argumentCount);
      insns.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
      int arrayIndex = 0;
      boolean enclosingStatic = (enclosing.access & Opcodes.ACC_STATIC) != 0;
      int localIndex = enclosingStatic ? 0 : 1;
      for (Type type : enclosingTypes) {
         insns.add(new InsnNode(Opcodes.DUP));
         pushInt(insns, arrayIndex++);
         loadLocal(insns, type, localIndex);
         boxValue(insns, type);
         insns.add(new InsnNode(Opcodes.AASTORE));
         localIndex += type.getSize();
      }
      insns.add(new InsnNode(Opcodes.DUP));
      pushInt(insns, arrayIndex++);
      if (isStatic) insns.add(new InsnNode(Opcodes.ACONST_NULL));
      else insns.add(new VarInsnNode(Opcodes.ALOAD, receiverLocal));
      insns.add(new InsnNode(Opcodes.AASTORE));
      for (int index = 0; index < invokedTypes.length; index++) {
         insns.add(new InsnNode(Opcodes.DUP));
         pushInt(insns, arrayIndex++);
         loadLocal(insns, invokedTypes[index], invokedLocals[index]);
         boxValue(insns, invokedTypes[index]);
         insns.add(new InsnNode(Opcodes.AASTORE));
      }
      insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "notify",
         "(Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)V", false));
   }

   private record InvocationTarget(String owner, String name, String descriptor) {
      private static InvocationTarget parse(String value) {
         if (value == null) throw new IllegalArgumentException("Invoke injection requires an exact invocation target");
         int ownerEnd = value.indexOf(';');
         int descriptorStart = value.indexOf('(', ownerEnd + 1);
         if (!value.startsWith("L") || ownerEnd < 2 || descriptorStart <= ownerEnd + 1) {
            throw new IllegalArgumentException("Invalid exact invocation target: " + value);
         }
         if ("<init>".equals(value.substring(ownerEnd + 1, descriptorStart))) {
            throw new IllegalArgumentException("Invoke injection cannot target a constructor invocation");
         }
         return new InvocationTarget(value.substring(1, ownerEnd), value.substring(ownerEnd + 1, descriptorStart), value.substring(descriptorStart));
      }
   }

   private static void injectAfterInitSuper(MethodNode method, String callbackId) {
      if (hasBridgeCallback(method, callbackId)) {
         return;
      }

      AbstractInsnNode insertAfter = null;
      for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
         if (insn.getOpcode() == Opcodes.INVOKESPECIAL
               && insn instanceof MethodInsnNode min
               && min.name.equals("<init>")) {
            insertAfter = insn.getNext();
            while (insertAfter != null && insertAfter.getOpcode() >= 0 && insertAfter.getOpcode() <= 20) {
               insertAfter = insertAfter.getNext();
            }
            break;
         }
      }

      if (insertAfter != null) {
         method.instructions.insert(insertAfter, notifyInvoke(callbackId, method, null, -1));
      }
   }

   private static void injectHead(MethodNode method, String callbackId) {
      if (hasBridgeCallback(method, callbackId)) {
         return;
      }

      Type returnType = Type.getReturnType(method.desc);
      if (returnType.getSort() == Type.VOID) {
         method.instructions.insert(headCancelableInvoke(callbackId, method));
      } else {
         method.instructions.insert(headOverrideInvoke(callbackId, method, returnType));
      }
   }

   private static void injectBeforeReturns(MethodNode method, String callbackId) {
      if (hasBridgeCallback(method, callbackId)) {
         return;
      }

      injectBeforeReturnInstructions(method, callbackId, returnInstructions(method));
   }

   private static void injectBeforeTailReturn(MethodNode method, String callbackId) {
      if (hasBridgeCallback(method, callbackId)) {
         return;
      }

      List<AbstractInsnNode> returns = returnInstructions(method);
      if (returns.isEmpty()) return;
      injectBeforeReturnInstructions(method, callbackId, List.of(returns.get(returns.size() - 1)));
   }

   private static void injectBeforeReturnInstructions(MethodNode method, String callbackId,
                                                       List<AbstractInsnNode> returns) {
      Type returnType = Type.getReturnType(method.desc);
      if (returnType.getSort() == Type.VOID) {
         for (AbstractInsnNode insn : returns) {
            method.instructions.insertBefore(insn, notifyInvoke(callbackId, method, null, -1));
         }
         return;
      }

      int returnLocal = allocateLocal(method, returnType);
      int overrideLocal = allocateLocal(method, Type.getType(Object.class));
      for (AbstractInsnNode insn : returns) {
         InsnList before = new InsnList();
         before.add(new VarInsnNode(storeOpcode(returnType), returnLocal));
         before.add(returnOverrideInvoke(callbackId, method, returnType, returnLocal, overrideLocal));
         before.add(new VarInsnNode(loadOpcode(returnType), returnLocal));
         method.instructions.insertBefore(insn, before);
      }
   }

   private static void overwrite(ClassNode classNode, MethodNode method, MLuaMixinRule rule) {
      String originalName = "__mlua$original$" + Integer.toHexString((rule.callbackId() + method.desc).hashCode());
      boolean alreadyCopied = classNode.methods.stream().anyMatch(candidate -> candidate.name.equals(originalName) && candidate.desc.equals(method.desc));
      if (!alreadyCopied) {
         MethodNode original = new MethodNode(Opcodes.ASM9, method.access, originalName, method.desc, method.signature,
            method.exceptions == null ? null : method.exceptions.toArray(String[]::new));
         method.accept(original);
         original.access = (original.access & ~(Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) | Opcodes.ACC_PRIVATE;
         classNode.methods.add(original);
      }
      MLuaMixinCallbackRegistry.registerOriginal(rule.callbackId(), classNode.name, originalName, method.desc);
      method.instructions.clear();
      method.tryCatchBlocks.clear();
      method.localVariables = null;

      InsnList body = overwriteInvoke(rule.callbackId(), method);
      method.instructions.add(body);

      LOGGER.info("[MLua] Applied Overwrite mixin: {}#{} callbackId={}",
         rule.targetClassName(), method.name, rule.callbackId());
   }

   private static void redirect(MethodNode method, MLuaMixinRule rule) {
      InvocationTarget target = parseInvocationTarget(rule.redirectTarget());
      String callbackId = rule.callbackId();
      int count = 0;
      int matchingCalls = 0;

      if (hasBridgeCallback(method, callbackId)) {
         return;
      }

      for (AbstractInsnNode insn : method.instructions) {
         if (insn instanceof MethodInsnNode min
               && isInterceptableInvocation(min)
               && matchesTarget(min, target)) {
            matchingCalls++;
            if ("<init>".equals(min.name)) {
               LOGGER.warn("[MLua] Redirect skips constructor call {}#{}{} because its receiver is uninitialized",
                  min.owner, min.name, min.desc);
               continue;
            }
            method.instructions.insertBefore(insn, redirectInvocation(callbackId, method, min));
            count++;
         }
      }

      if (count > 0) {
         LOGGER.info("[MLua] Applied Redirect in {}#{} for {}#{}{} ({} call(s))",
            rule.targetClassName(), method.name, target.owner(), target.method(), target.descriptor(), count);
      } else {
         throw new IllegalStateException("[MLua] Redirect target " + rule.redirectTarget()
            + (matchingCalls == 0 ? " did not match a call" : " matched only constructor calls that cannot be redirected")
            + " in " + rule.targetClassName() + "#" + method.name + method.desc);
      }
   }

   private static InsnList redirectInvocation(String callbackId, MethodNode method,
                                             MethodInsnNode invocation) {
      Type[] callArguments = Type.getArgumentTypes(invocation.desc);
      int[] argumentLocals = new int[callArguments.length];
      InsnList insns = new InsnList();
      for (int index = callArguments.length - 1; index >= 0; index--) {
         int local = allocateLocal(method, callArguments[index]);
         argumentLocals[index] = local;
         insns.add(new VarInsnNode(storeOpcode(callArguments[index]), local));
      }

      int receiverLocal = -1;
      if (invocation.getOpcode() != Opcodes.INVOKESTATIC) {
         receiverLocal = allocateLocal(method, Type.getType(Object.class));
         insns.add(new VarInsnNode(Opcodes.ASTORE, receiverLocal));
      }

      Type returnType = Type.getReturnType(invocation.desc);
      int resultLocal = allocateLocal(method, Type.getType(Object.class));
      insns.add(new LdcInsnNode(callbackId));
      pushClassLiteral(insns, returnType);
      pushSelf(insns, method);
      Type[] enclosingArguments = Type.getArgumentTypes(method.desc);
      boolean enclosingStatic = (method.access & Opcodes.ACC_STATIC) != 0;
      pushInt(insns, 1 + callArguments.length + enclosingArguments.length);
      insns.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));

      int packedIndex = 1;
      insns.add(new InsnNode(Opcodes.DUP));
      pushInt(insns, 0);
      if (receiverLocal >= 0) {
         insns.add(new VarInsnNode(Opcodes.ALOAD, receiverLocal));
      } else {
         insns.add(new InsnNode(Opcodes.ACONST_NULL));
      }
      insns.add(new InsnNode(Opcodes.AASTORE));
      for (int index = 0; index < callArguments.length; index++) {
         insns.add(new InsnNode(Opcodes.DUP));
         pushInt(insns, packedIndex++);
         loadLocal(insns, callArguments[index], argumentLocals[index]);
         boxValue(insns, callArguments[index]);
         insns.add(new InsnNode(Opcodes.AASTORE));
      }

      int enclosingLocal = enclosingStatic ? 0 : 1;
      for (Type enclosingArgument : enclosingArguments) {
         insns.add(new InsnNode(Opcodes.DUP));
         pushInt(insns, packedIndex++);
         loadLocal(insns, enclosingArgument, enclosingLocal);
         boxValue(insns, enclosingArgument);
         insns.add(new InsnNode(Opcodes.AASTORE));
         enclosingLocal += enclosingArgument.getSize();
      }

      insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "redirect",
         "(Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false));
      insns.add(new VarInsnNode(Opcodes.ASTORE, resultLocal));
      LabelNode callOriginal = new LabelNode();
      LabelNode complete = new LabelNode();
      insns.add(new VarInsnNode(Opcodes.ALOAD, resultLocal));
      insns.add(new FieldInsnNode(Opcodes.GETSTATIC, BRIDGE, "NO_RESULT", "Ljava/lang/Object;"));
      insns.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, callOriginal));
      if (returnType.getSort() == Type.VOID) {
         insns.add(new JumpInsnNode(Opcodes.GOTO, complete));
      } else {
         insns.add(new VarInsnNode(Opcodes.ALOAD, resultLocal));
         appendCastValue(insns, returnType);
         insns.add(new JumpInsnNode(Opcodes.GOTO, complete));
      }
      insns.add(callOriginal);
      if (receiverLocal >= 0) insns.add(new VarInsnNode(Opcodes.ALOAD, receiverLocal));
      for (int index = 0; index < callArguments.length; index++) {
         loadLocal(insns, callArguments[index], argumentLocals[index]);
      }
      insns.add(new MethodInsnNode(invocation.getOpcode(), invocation.owner, invocation.name,
         invocation.desc, invocation.itf));
      insns.add(complete);
      return insns;
   }

   private static void modifyArg(MethodNode method, MLuaMixinRule rule) {
      InvocationTarget target = parseInvocationTarget(rule.redirectTarget());
      String callbackId = rule.callbackId();
      int targetArgIndex = rule.modifyIndex();
      int count = 0;
      int matchingCalls = 0;
      int matchingArguments = 0;

      if (hasBridgeCallback(method, callbackId)) {
         return;
      }

      for (AbstractInsnNode insn : method.instructions) {
         if (insn instanceof MethodInsnNode min
               && isInterceptableInvocation(min)
               && matchesTarget(min, target)) {
            matchingCalls++;
            Type[] argTypes = Type.getArgumentTypes(min.desc);
            if (targetArgIndex >= 0 && targetArgIndex < argTypes.length) {
               matchingArguments++;
               if ("<init>".equals(min.name)) {
                  LOGGER.warn("[MLua] ModifyArg skips constructor call {}#{}{} because its receiver is uninitialized",
                     min.owner, min.name, min.desc);
                  continue;
               }
               method.instructions.insertBefore(insn, modifyArgumentInvoke(callbackId, method, min, targetArgIndex));
               count++;
            }
         }
      }

      if (count > 0) {
         LOGGER.info("[MLua] Applied ModifyArg in {}#{} for {}#{}{} argument {} ({} call(s))",
            rule.targetClassName(), method.name, target.owner(), target.method(), target.descriptor(), targetArgIndex, count);
      } else {
         String reason = matchingCalls == 0 ? "did not match a call"
            : matchingArguments == 0 ? "has no argument at index " + targetArgIndex
            : "matched only constructor calls that cannot be modified";
         throw new IllegalStateException("[MLua] ModifyArg target " + rule.redirectTarget() + " " + reason
            + " in " + rule.targetClassName() + "#" + method.name + method.desc);
      }
   }

   private static boolean matchesTarget(MethodInsnNode invocation, InvocationTarget target) {
      return target != null && invocation.owner.equals(target.owner())
         && invocation.name.equals(target.method()) && invocation.desc.equals(target.descriptor());
   }

   private static InvocationTarget parseInvocationTarget(String target) {
      if (target == null || !target.startsWith("L")) return null;
      int ownerEnd = target.indexOf(';');
      int descriptorStart = target.indexOf('(', ownerEnd + 1);
      if (ownerEnd <= 1 || descriptorStart <= ownerEnd + 1) return null;
      String descriptor = target.substring(descriptorStart);
      if (descriptor.indexOf(')') < 0 || descriptor.length() <= descriptor.indexOf(')') + 1) return null;
      return new InvocationTarget(target.substring(1, ownerEnd),
         target.substring(ownerEnd + 1, descriptorStart), descriptor);
   }

   private record InvocationTarget(String owner, String method, String descriptor) { }

   private static InsnList modifyArgumentInvoke(String callbackId, MethodNode method,
                                                MethodInsnNode invocation, int argumentIndex) {
      Type[] argumentTypes = Type.getArgumentTypes(invocation.desc);
      int[] argumentLocals = new int[argumentTypes.length];
      InsnList insns = new InsnList();
      for (int index = argumentTypes.length - 1; index >= 0; index--) {
         Type type = argumentTypes[index];
         int local = allocateLocal(method, type);
         argumentLocals[index] = local;
         insns.add(new VarInsnNode(storeOpcode(type), local));
      }

      int receiverLocal = -1;
      if (invocation.getOpcode() != Opcodes.INVOKESTATIC) {
         receiverLocal = allocateLocal(method, Type.getType(Object.class));
         insns.add(new VarInsnNode(Opcodes.ASTORE, receiverLocal));
      }

      Type targetType = argumentTypes[argumentIndex];
      int resultLocal = allocateLocal(method, Type.getType(Object.class));
      insns.add(new LdcInsnNode(callbackId));
      pushClassLiteral(insns, targetType);
      pushSelf(insns, method);
      pushInt(insns, Type.getArgumentTypes(method.desc).length + 1);
      insns.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
      insns.add(new InsnNode(Opcodes.DUP));
      pushInt(insns, 0);
      loadLocal(insns, targetType, argumentLocals[argumentIndex]);
      boxValue(insns, targetType);
      insns.add(new InsnNode(Opcodes.AASTORE));

      Type[] enclosingArguments = Type.getArgumentTypes(method.desc);
      boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
      int local = isStatic ? 0 : 1;
      for (int index = 0; index < enclosingArguments.length; index++) {
         insns.add(new InsnNode(Opcodes.DUP));
         pushInt(insns, index + 1);
         loadLocal(insns, enclosingArguments[index], local);
         boxValue(insns, enclosingArguments[index]);
         insns.add(new InsnNode(Opcodes.AASTORE));
         local += enclosingArguments[index].getSize();
      }

      insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "override",
         "(Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false));
      insns.add(new VarInsnNode(Opcodes.ASTORE, resultLocal));
      LabelNode keepOriginal = new LabelNode();
      LabelNode ready = new LabelNode();
      insns.add(new VarInsnNode(Opcodes.ALOAD, resultLocal));
      insns.add(new FieldInsnNode(Opcodes.GETSTATIC, BRIDGE, "NO_RESULT", "Ljava/lang/Object;"));
      insns.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, keepOriginal));
      insns.add(new VarInsnNode(Opcodes.ALOAD, resultLocal));
      appendCastValue(insns, targetType);
      insns.add(new JumpInsnNode(Opcodes.GOTO, ready));
      insns.add(keepOriginal);
      loadLocal(insns, targetType, argumentLocals[argumentIndex]);
      insns.add(ready);
      insns.add(new VarInsnNode(storeOpcode(targetType), argumentLocals[argumentIndex]));

      if (receiverLocal >= 0) insns.add(new VarInsnNode(Opcodes.ALOAD, receiverLocal));
      for (int index = 0; index < argumentTypes.length; index++) {
         loadLocal(insns, argumentTypes[index], argumentLocals[index]);
      }
      return insns;
   }

   private static void appendCastValue(InsnList insns, Type type) {
      switch (type.getSort()) {
         case Type.BOOLEAN -> unbox(insns, "java/lang/Boolean", "booleanValue", "()Z");
         case Type.BYTE -> unbox(insns, "java/lang/Byte", "byteValue", "()B");
         case Type.CHAR -> unbox(insns, "java/lang/Character", "charValue", "()C");
         case Type.SHORT -> unbox(insns, "java/lang/Short", "shortValue", "()S");
         case Type.INT -> unbox(insns, "java/lang/Integer", "intValue", "()I");
         case Type.LONG -> unbox(insns, "java/lang/Long", "longValue", "()J");
         case Type.FLOAT -> unbox(insns, "java/lang/Float", "floatValue", "()F");
         case Type.DOUBLE -> unbox(insns, "java/lang/Double", "doubleValue", "()D");
         case Type.ARRAY -> insns.add(new TypeInsnNode(Opcodes.CHECKCAST, type.getDescriptor()));
         case Type.OBJECT -> insns.add(new TypeInsnNode(Opcodes.CHECKCAST, type.getInternalName()));
         default -> throw new IllegalArgumentException("Unsupported ModifyArg type: " + type);
      }
   }

   private static void pushClassLiteral(InsnList insns, Type type) {
      String wrapper = switch (type.getSort()) {
         case Type.VOID -> "java/lang/Void";
         case Type.BOOLEAN -> "java/lang/Boolean";
         case Type.BYTE -> "java/lang/Byte";
         case Type.CHAR -> "java/lang/Character";
         case Type.SHORT -> "java/lang/Short";
         case Type.INT -> "java/lang/Integer";
         case Type.LONG -> "java/lang/Long";
         case Type.FLOAT -> "java/lang/Float";
         case Type.DOUBLE -> "java/lang/Double";
         default -> null;
      };
      if (wrapper == null) insns.add(new LdcInsnNode(type));
      else insns.add(new FieldInsnNode(Opcodes.GETSTATIC, wrapper, "TYPE", "Ljava/lang/Class;"));
   }

   private static void unbox(InsnList insns, String owner, String method, String descriptor) {
      insns.add(new TypeInsnNode(Opcodes.CHECKCAST, owner));
      insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, method, descriptor, false));
   }

   private static InsnList headCancelableInvoke(String callbackId, MethodNode method) {
      InsnList insns = new InsnList();
      pushInvocationPrefix(insns, callbackId, method);
      insns.add(new MethodInsnNode(
         Opcodes.INVOKESTATIC, BRIDGE, "allow",
         "(Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)Z", false));
      LabelNode continueLabel = new LabelNode();
      insns.add(new JumpInsnNode(Opcodes.IFNE, continueLabel));
      insns.add(returnInsn(method.desc));
      insns.add(continueLabel);
      return insns;
   }

   private static InsnList headOverrideInvoke(String callbackId, MethodNode method, Type returnType) {
      int overrideLocal = allocateLocal(method, Type.getType(Object.class));
      InsnList insns = new InsnList();
      pushOverrideInvocation(insns, callbackId, method, returnType, null, -1);
      insns.add(new VarInsnNode(Opcodes.ASTORE, overrideLocal));
      LabelNode continueLabel = new LabelNode();
      insns.add(new VarInsnNode(Opcodes.ALOAD, overrideLocal));
      insns.add(new FieldInsnNode(Opcodes.GETSTATIC, BRIDGE, "NO_RESULT", "Ljava/lang/Object;"));
      insns.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, continueLabel));
      insns.add(new VarInsnNode(Opcodes.ALOAD, overrideLocal));
      appendCastAndReturn(insns, returnType);
      insns.add(continueLabel);
      return insns;
   }

   private static InsnList returnOverrideInvoke(String callbackId, MethodNode method, Type returnType, int returnLocal, int overrideLocal) {
      InsnList insns = new InsnList();
      pushOverrideInvocation(insns, callbackId, method, returnType, returnType, returnLocal);
      insns.add(new VarInsnNode(Opcodes.ASTORE, overrideLocal));
      LabelNode continueLabel = new LabelNode();
      insns.add(new VarInsnNode(Opcodes.ALOAD, overrideLocal));
      insns.add(new FieldInsnNode(Opcodes.GETSTATIC, BRIDGE, "NO_RESULT", "Ljava/lang/Object;"));
      insns.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, continueLabel));
      insns.add(new VarInsnNode(Opcodes.ALOAD, overrideLocal));
      appendCastAndReturn(insns, returnType);
      insns.add(continueLabel);
      return insns;
   }

   private static InsnList notifyInvoke(String callbackId, MethodNode method, Type leadingType, int leadingLocal) {
      InsnList insns = new InsnList();
      pushInvocationPrefix(insns, callbackId, method, leadingType, leadingLocal);
      insns.add(new MethodInsnNode(
         Opcodes.INVOKESTATIC, BRIDGE, "notify",
         "(Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)V", false));
      return insns;
   }

   private static void pushOverrideInvocation(InsnList insns, String callbackId, MethodNode method, Type returnType, Type leadingType, int leadingLocal) {
      insns.add(new LdcInsnNode(callbackId));
      insns.add(new LdcInsnNode(returnType));
      pushSelf(insns, method);
      pushPackedArgs(insns, method, leadingType, leadingLocal);
      insns.add(new MethodInsnNode(
         Opcodes.INVOKESTATIC, BRIDGE, "override",
         "(Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false));
   }

   private static void pushInvocationPrefix(InsnList insns, String callbackId, MethodNode method) {
      pushInvocationPrefix(insns, callbackId, method, null, -1);
   }

   private static void pushInvocationPrefix(InsnList insns, String callbackId, MethodNode method, Type leadingType, int leadingLocal) {
      insns.add(new LdcInsnNode(callbackId));
      pushSelf(insns, method);
      pushPackedArgs(insns, method, leadingType, leadingLocal);
   }

   private static void pushPackedArgs(InsnList insns, MethodNode method, Type leadingType, int leadingLocal) {
      Type[] argTypes = Type.getArgumentTypes(method.desc);
      int extra = leadingType != null ? 1 : 0;
      pushInt(insns, argTypes.length + extra);
      insns.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));

      int arrayIndex = 0;
      if (leadingType != null) {
         insns.add(new InsnNode(Opcodes.DUP));
         pushInt(insns, arrayIndex++);
         loadLocal(insns, leadingType, leadingLocal);
         boxValue(insns, leadingType);
         insns.add(new InsnNode(Opcodes.AASTORE));
      }

      boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
      int localVarIndex = isStatic ? 0 : 1;
      for (Type argType : argTypes) {
         insns.add(new InsnNode(Opcodes.DUP));
         pushInt(insns, arrayIndex++);
         loadLocal(insns, argType, localVarIndex);
         boxValue(insns, argType);
         insns.add(new InsnNode(Opcodes.AASTORE));
         localVarIndex += argType.getSize();
      }
   }

   private static void pushSelf(InsnList insns, MethodNode method) {
      if ((method.access & Opcodes.ACC_STATIC) != 0) {
         insns.add(new InsnNode(Opcodes.ACONST_NULL));
      } else {
         insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
      }
   }

   private static void pushInt(InsnList insns, int value) {
      if (value >= -1 && value <= 5) {
         insns.add(new InsnNode(Opcodes.ICONST_0 + value));
      } else if (value >= -128 && value <= 127) {
         insns.add(new IntInsnNode(Opcodes.BIPUSH, value));
      } else if (value >= -32768 && value <= 32767) {
         insns.add(new IntInsnNode(Opcodes.SIPUSH, value));
      } else {
         insns.add(new LdcInsnNode(value));
      }
   }

   private static void loadLocal(InsnList insns, Type type, int varIndex) {
      insns.add(new VarInsnNode(loadOpcode(type), varIndex));
   }

   private static int loadOpcode(Type type) {
      return switch (type.getSort()) {
         case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> Opcodes.ILOAD;
         case Type.LONG -> Opcodes.LLOAD;
         case Type.FLOAT -> Opcodes.FLOAD;
         case Type.DOUBLE -> Opcodes.DLOAD;
         default -> Opcodes.ALOAD;
      };
   }

   private static int storeOpcode(Type type) {
      return switch (type.getSort()) {
         case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> Opcodes.ISTORE;
         case Type.LONG -> Opcodes.LSTORE;
         case Type.FLOAT -> Opcodes.FSTORE;
         case Type.DOUBLE -> Opcodes.DSTORE;
         default -> Opcodes.ASTORE;
      };
   }

   private static void boxValue(InsnList insns, Type type) {
      switch (type.getSort()) {
         case Type.BOOLEAN ->
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
         case Type.BYTE ->
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false));
         case Type.CHAR ->
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false));
         case Type.SHORT ->
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false));
         case Type.INT ->
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
         case Type.LONG ->
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
         case Type.FLOAT ->
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
         case Type.DOUBLE ->
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
         default -> {
         }
      }
   }

   private static void appendCastAndReturn(InsnList insns, Type returnType) {
      switch (returnType.getSort()) {
         case Type.BOOLEAN -> {
            insns.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"));
            insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
            insns.add(new InsnNode(Opcodes.IRETURN));
         }
         case Type.BYTE -> {
            insns.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Byte"));
            insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false));
            insns.add(new InsnNode(Opcodes.IRETURN));
         }
         case Type.CHAR -> {
            insns.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Character"));
            insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false));
            insns.add(new InsnNode(Opcodes.IRETURN));
         }
         case Type.SHORT -> {
            insns.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Short"));
            insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false));
            insns.add(new InsnNode(Opcodes.IRETURN));
         }
         case Type.INT -> {
            insns.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Integer"));
            insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false));
            insns.add(new InsnNode(Opcodes.IRETURN));
         }
         case Type.LONG -> {
            insns.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Long"));
            insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false));
            insns.add(new InsnNode(Opcodes.LRETURN));
         }
         case Type.FLOAT -> {
            insns.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Float"));
            insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false));
            insns.add(new InsnNode(Opcodes.FRETURN));
         }
         case Type.DOUBLE -> {
            insns.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Double"));
            insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false));
            insns.add(new InsnNode(Opcodes.DRETURN));
         }
         default -> {
            insns.add(new TypeInsnNode(Opcodes.CHECKCAST, returnType.getInternalName()));
            insns.add(new InsnNode(Opcodes.ARETURN));
         }
      }
   }

   private static int allocateLocal(MethodNode method, Type type) {
      int local = method.maxLocals;
      method.maxLocals += type.getSize();
      return local;
   }

   private static boolean isInterceptableInvocation(MethodInsnNode min) {
      int opcode = min.getOpcode();
      return opcode == Opcodes.INVOKEVIRTUAL
         || opcode == Opcodes.INVOKESTATIC
         || opcode == Opcodes.INVOKESPECIAL
         || opcode == Opcodes.INVOKEINTERFACE;
   }

   private static InsnList overwriteInvoke(String callbackId, MethodNode method) {
      Type returnType = Type.getReturnType(method.desc);
      InsnList insns = registerInvokeArgs(callbackId, method);

      switch (returnType.getSort()) {
         case Type.VOID -> {
            insns.add(new MethodInsnNode(
               Opcodes.INVOKESTATIC, REGISTRY, "invokeArgs",
               "(Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)V", false));
            insns.add(new InsnNode(Opcodes.RETURN));
         }
         case Type.BOOLEAN -> {
            insns.add(new MethodInsnNode(
               Opcodes.INVOKESTATIC, REGISTRY, "invokeBoolean",
               "(Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)Z", false));
            insns.add(new InsnNode(Opcodes.IRETURN));
         }
         case Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> {
            insns.add(new MethodInsnNode(
               Opcodes.INVOKESTATIC, REGISTRY, "invokeInt",
               "(Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)I", false));
            insns.add(new InsnNode(Opcodes.IRETURN));
         }
         case Type.LONG -> {
            insns.add(new MethodInsnNode(
               Opcodes.INVOKESTATIC, REGISTRY, "invokeLong",
               "(Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)J", false));
            insns.add(new InsnNode(Opcodes.LRETURN));
         }
         case Type.FLOAT -> {
            insns.add(new MethodInsnNode(
               Opcodes.INVOKESTATIC, REGISTRY, "invokeFloat",
               "(Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)F", false));
            insns.add(new InsnNode(Opcodes.FRETURN));
         }
         case Type.DOUBLE -> {
            insns.add(new MethodInsnNode(
               Opcodes.INVOKESTATIC, REGISTRY, "invokeDouble",
               "(Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)D", false));
            insns.add(new InsnNode(Opcodes.DRETURN));
         }
         default -> {
            insns.add(new LdcInsnNode(callbackId));
            pushClassLiteral(insns, returnType);
            pushSelf(insns, method);
            pushPackedArgs(insns, method, null, -1);
            insns.add(new MethodInsnNode(
               Opcodes.INVOKESTATIC, REGISTRY, "invokeObject",
               "(Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false));
            if (returnType.getSort() == Type.ARRAY) {
               insns.add(new TypeInsnNode(Opcodes.CHECKCAST, returnType.getDescriptor()));
            } else {
               insns.add(new TypeInsnNode(Opcodes.CHECKCAST, returnType.getInternalName()));
            }
            insns.add(new InsnNode(Opcodes.ARETURN));
         }
      }

      return insns;
   }

   private static InsnList registerInvokeArgs(String callbackId, MethodNode method) {
      InsnList insns = new InsnList();
      insns.add(new LdcInsnNode(callbackId));
      pushSelf(insns, method);
      pushPackedArgs(insns, method, null, -1);
      return insns;
   }

   private static boolean hasBridgeCallback(MethodNode method, String callbackId) {
      for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
         if (!(insn instanceof LdcInsnNode ldc) || !callbackId.equals(ldc.cst)) {
            continue;
         }

         for (AbstractInsnNode cursor = insn.getNext(); cursor != null; cursor = cursor.getNext()) {
            if (cursor instanceof MethodInsnNode min
               && min.getOpcode() == Opcodes.INVOKESTATIC
               && (BRIDGE.equals(min.owner) || REGISTRY.equals(min.owner))) {
               return true;
            }

            if (cursor instanceof LdcInsnNode otherLdc
               && otherLdc.cst instanceof String
               && !callbackId.equals(otherLdc.cst)) {
               break;
            }
         }
      }
      return false;
   }

   private static List<AbstractInsnNode> returnInstructions(MethodNode method) {
      List<AbstractInsnNode> returns = new ArrayList<>();
      for (AbstractInsnNode insn : method.instructions) {
         int opcode = insn.getOpcode();
         if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
            returns.add(insn);
         }
      }
      return returns;
   }

   private static AbstractInsnNode returnInsn(String desc) {
      String ret = desc.substring(desc.indexOf(')') + 1);
      return switch (ret) {
         case "V" -> new InsnNode(Opcodes.RETURN);
         case "Z", "B", "C", "S", "I" -> new InsnNode(Opcodes.IRETURN);
         case "J" -> new InsnNode(Opcodes.LRETURN);
         case "F" -> new InsnNode(Opcodes.FRETURN);
         case "D" -> new InsnNode(Opcodes.DRETURN);
         default -> new InsnNode(Opcodes.ARETURN);
      };
   }

}
