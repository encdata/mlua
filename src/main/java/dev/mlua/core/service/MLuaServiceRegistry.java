package dev.mlua.core.service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import dev.mlua.module.MLuaModule;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

public final class MLuaServiceRegistry {
   /**
    * Readers only need a stable view while a module batch is being installed or removed.
    * Publishing both indexes together prevents documentation/class lookup from observing a
    * half-updated registry, and keeps the hot GetService path free of registry locking.
    */
   private volatile Snapshot snapshot = Snapshot.empty();

   @Deprecated(forRemoval = false)
   public synchronized void register(String name, Function<MLuaService.Context, MLuaService> factory) {
      throw new IllegalArgumentException("Service '" + name + "' needs a description; register it with the described overload");
   }

   @Deprecated(forRemoval = false)
   public synchronized void register(String name, Side side, Function<MLuaService.Context, MLuaService> factory) {
      throw new IllegalArgumentException("Service '" + name + "' needs a description; register it with the described overload");
   }

   public synchronized void register(String name, Side side, Function<MLuaService.Context, MLuaService> factory,
                                     String description, Map<String, String> functionDescriptions) {
      this.registerBatch(side, List.of(new Registration(name, factory, description, functionDescriptions)));
   }

   public synchronized void register(String name, Function<MLuaService.Context, MLuaService> factory,
                                     String description, Map<String, String> functionDescriptions) {
      this.register(name, Side.BOTH, factory, description, functionDescriptions);
   }

   public synchronized void register(String name, String className, Side side,
                                     Function<MLuaService.Context, MLuaService> factory,
                                     String description, Map<String, String> functionDescriptions) {
      this.registerBatch(side, List.of(new Registration(name, className, factory, description, functionDescriptions)));
   }

   public synchronized void register(String name, String className,
                                     Function<MLuaService.Context, MLuaService> factory,
                                     String description, Map<String, String> functionDescriptions) {
      this.register(name, className, Side.BOTH, factory, description, functionDescriptions);
   }

   public synchronized void validateBatch(Side side, List<Registration> registrations) {
      this.stageBatch(side, registrations);
   }

   public synchronized void registerBatch(Side side, List<Registration> registrations) {
      StagedRegistrations staged = this.stageBatch(side, registrations);
      this.snapshot = Snapshot.of(staged.registrations(), staged.classNames());
   }

   private StagedRegistrations stageBatch(Side side, List<Registration> registrations) {
      Objects.requireNonNull(side, "side");
      List<Registration> additions = List.copyOf(Objects.requireNonNull(registrations, "registrations"));
      Map<String, Map<Side, Registration>> staged = new LinkedHashMap<>();
      Snapshot current = this.snapshot;
      Map<String, String> stagedClassNames = new LinkedHashMap<>(current.classNames());
      current.registrations().forEach((name, bySide) -> staged.put(name, new EnumMap<>(bySide)));
      for (Registration registration : additions) {
         requireServiceName(registration.name());
         validateClassName(stagedClassNames, registration);
         Map<Side, Registration> bySide = staged.computeIfAbsent(
            registration.name(), ignored -> new EnumMap<>(Side.class));
         validateAddition(bySide, registration, side);
         if (bySide.putIfAbsent(side, registration) != null) {
            throw new IllegalStateException("MLua service '" + registration.name() + "' is already registered for " + side);
         }
         recordClassName(stagedClassNames, registration);
      }
      return new StagedRegistrations(staged, stagedClassNames);
   }

   public synchronized void unregisterBatch(Side side, List<Registration> registrations) {
      Objects.requireNonNull(side, "side");
      List<Registration> removals = List.copyOf(Objects.requireNonNull(registrations, "registrations"));
      Snapshot current = this.snapshot;
      Map<String, Map<Side, Registration>> staged = new LinkedHashMap<>();
      current.registrations().forEach((name, bySide) -> staged.put(name, new EnumMap<>(bySide)));
      Map<String, String> stagedClassNames = new LinkedHashMap<>(current.classNames());
      for (Registration registration : removals) {
         Map<Side, Registration> bySide = staged.get(registration.name());
         if (bySide != null && bySide.get(side) == registration) {
            bySide.remove(side);
            if (bySide.isEmpty()) {
               staged.remove(registration.name());
               stagedClassNames.remove(registration.name());
            }
         }
      }
      this.snapshot = Snapshot.of(staged, stagedClassNames);
   }

   public MLuaService create(String name, MLuaContext context) {
      Objects.requireNonNull(context, "context");
      Snapshot current = this.snapshot;
      Registration registration;
      Side registeredSide;
      Map<Side, Registration> bySide = current.registrations().get(name);
      if (bySide == null) throw new IllegalArgumentException("[MLua] Unknown Minecraft service: " + name);
      registration = bySide.get(context.side());
      if (registration != null) {
         registeredSide = context.side();
      } else {
         registration = bySide.get(Side.BOTH);
         registeredSide = Side.BOTH;
      }
      if (registration == null) {
         throw new IllegalArgumentException("[MLua] Minecraft service '" + name
            + "' is not available on " + context.side() + " scripts");
      }

      MLuaService service;
      try {
         service = registration.factory().apply(new MLuaService.Context(context));
      } catch (RuntimeException failure) {
         throw new IllegalStateException("[MLua] Factory for service '" + name + "' failed for mod '"
            + context.ownerModId() + "' on " + context.side() + " during " + context.phase(), failure);
      }
      if (service == null) {
         throw new IllegalStateException("[MLua] Factory for service '" + name + "' returned null for mod '"
            + context.ownerModId() + "' on " + context.side() + " during " + context.phase());
      }
      String actualName;
      try {
         actualName = service.name();
      } catch (RuntimeException failure) {
         throw new IllegalStateException("[MLua] Service '" + name + "' from mod '"
            + context.ownerModId() + "' failed while reporting its name on " + context.side()
            + " scripts during " + context.phase(), failure);
      }
      if (!name.equals(actualName)) {
         throw new IllegalStateException("[MLua] Factory registered as service '" + name + "' for mod '"
            + context.ownerModId() + "' on " + context.side() + " scripts during " + context.phase()
            + " returned service named '" + actualName + "'");
      }
      return new MLuaDescribedService(service, registration.description(),
         registration.functionDescriptions(), true, registeredSide);
   }

   public Set<String> names() {
      return this.snapshot.names();
   }

   public boolean isRegistered(String name) {
      return name != null && this.snapshot.registrations().containsKey(name);
   }

   public String explicitClassName(String name) {
      return this.snapshot.classNames().get(name);
   }

   public synchronized void assignClassName(String name, String className) {
      requireServiceName(name);
      if (className == null || className.isBlank()) {
         throw new IllegalArgumentException("MLua service class name must be non-empty");
      }
      Snapshot current = this.snapshot;
      if (!current.registrations().containsKey(name)) {
         throw new IllegalStateException("Cannot assign a class name to unregistered MLua service '" + name + "'");
      }
      String existing = current.classNames().get(name);
      if (existing != null && !existing.equals(className)) {
         throw new IllegalStateException("MLua service '" + name + "' is already assigned to class '" + existing + "'");
      }
      if (existing == null) {
         Map<String, String> classNames = new LinkedHashMap<>(current.classNames());
         classNames.put(name, className);
         this.snapshot = current.withClassNames(classNames);
      }
   }

   public synchronized void clearClassName(String name, String expectedClassName) {
      if (name != null && expectedClassName != null) {
         Snapshot current = this.snapshot;
         if (expectedClassName.equals(current.classNames().get(name))) {
            Map<String, String> classNames = new LinkedHashMap<>(current.classNames());
            classNames.remove(name);
            this.snapshot = current.withClassNames(classNames);
         }
      }
   }

   public Set<String> availableNames(Side side) {
      if (side == null || side == Side.BOTH) {
         throw new IllegalArgumentException("A concrete client or server side is required");
      }
      return this.snapshot.availableNames().get(side);
   }

   public boolean isAvailable(String name, Side side) {
      if (name == null || side == null || side == Side.BOTH) return false;
      Map<Side, Registration> bySide = this.snapshot.registrations().get(name);
      return bySide != null && (bySide.containsKey(side) || bySide.containsKey(Side.BOTH));
   }

   public ServiceMetadata metadata(String name) {
      Snapshot current = this.snapshot;
      Map<Side, Registration> bySide = current.registrations().get(name);
      if (bySide == null || bySide.isEmpty()) return null;
      Registration registration = bySide.values().iterator().next();
      return new ServiceMetadata(current.classNames().getOrDefault(name, registration.className()),
         registration.description(), registration.functionDescriptions());
   }

   private static void validateClassName(Map<String, String> classNames, Registration registration) {
      String className = registration.className();
      if (className == null) return;
      String existing = classNames.get(registration.name());
      if (existing != null && !existing.equals(className)) {
         throw new IllegalStateException("MLua service '" + registration.name()
            + "' is already assigned to Lua class '" + existing + "'");
      }
   }

   private static void recordClassName(Map<String, String> classNames, Registration registration) {
      if (registration.className() != null) classNames.putIfAbsent(registration.name(), registration.className());
   }

   private static void validateAddition(Map<Side, Registration> bySide, Registration registration, Side side) {
      String name = registration.name();
      if ((side == Side.BOTH && !bySide.isEmpty()) || (side != Side.BOTH && bySide.containsKey(Side.BOTH))) {
         throw new IllegalStateException("MLua service '" + name + "' cannot mix BOTH and side-specific registrations");
      }
      for (Registration existing : bySide.values()) {
         if (!Objects.equals(existing.className(), registration.className())
            || !Objects.equals(existing.description(), registration.description())
            || !existing.functionDescriptions().equals(registration.functionDescriptions())) {
            throw new IllegalStateException("Side-specific MLua service '" + name
               + "' registrations must expose the same class name, class description, and function descriptions");
         }
      }
   }

   private static void requireServiceName(String name) {
      if (name == null || name.isBlank()) throw new IllegalArgumentException("MLua service name must be non-empty");
   }

   public record Registration(String name, String className, Function<MLuaService.Context, MLuaService> factory,
                              String description, Map<String, String> functionDescriptions) {
      @Deprecated(forRemoval = false)
      public Registration(String name, Function<MLuaService.Context, MLuaService> factory) {
         this(name, null, factory, null, Map.of());
      }

      @Deprecated(forRemoval = false)
      public Registration(String name, Function<MLuaService.Context, MLuaService> factory,
                          String description) {
         this(name, null, factory, description, Map.of());
      }

      public Registration(String name, Function<MLuaService.Context, MLuaService> factory,
                          String description, Map<String, String> functionDescriptions) {
         this(name, null, factory, description, functionDescriptions);
      }

      @Deprecated(forRemoval = false)
      public Registration(String name, String className, Function<MLuaService.Context, MLuaService> factory,
                          String description) {
         this(name, className, factory, description, Map.of());
      }

      @Deprecated(forRemoval = false)
      public Registration(String name, String className, Function<MLuaService.Context, MLuaService> factory) {
         this(name, className, factory, null, Map.of());
      }

      public Registration {
         MLuaModule.ServiceEntry declaration = new MLuaModule.ServiceEntry(
            name, className, factory, description, functionDescriptions);
         name = declaration.name();
         className = declaration.className();
         factory = declaration.factory();
         description = declaration.description();
         functionDescriptions = declaration.functionDescriptions();
      }
   }

   public record ServiceMetadata(String className, String description, Map<String, String> functionDescriptions) {
      public ServiceMetadata(String description, Map<String, String> functionDescriptions) {
         this(null, description, functionDescriptions);
      }

      public ServiceMetadata {
         if (className != null && className.isBlank()) throw new IllegalArgumentException("Service class name must be non-empty");
         functionDescriptions = Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(functionDescriptions, "functionDescriptions")));
      }
   }

   private record Snapshot(Map<String, Map<Side, Registration>> registrations,
                           Map<String, String> classNames, Set<String> names,
                           Map<Side, Set<String>> availableNames) {
      private static Snapshot empty() {
         return new Snapshot(Map.of(), Map.of(), Set.of(), Map.of(
            Side.CLIENT, Set.of(), Side.SERVER, Set.of()));
      }

      private Snapshot withClassNames(Map<String, String> classNames) {
         return new Snapshot(this.registrations,
            Collections.unmodifiableMap(new LinkedHashMap<>(classNames)), this.names, this.availableNames);
      }

      private static Snapshot of(Map<String, Map<Side, Registration>> registrations,
                                 Map<String, String> classNames) {
         Map<String, Map<Side, Registration>> immutableRegistrations = new LinkedHashMap<>();
         registrations.forEach((name, bySide) -> immutableRegistrations.put(name,
            Collections.unmodifiableMap(new EnumMap<>(bySide))));
         Map<String, String> immutableClassNames = Collections.unmodifiableMap(new LinkedHashMap<>(classNames));
         Set<String> names = Collections.unmodifiableSet(new TreeSet<>(immutableRegistrations.keySet()));
         Map<Side, Set<String>> available = new EnumMap<>(Side.class);
         for (Side side : List.of(Side.CLIENT, Side.SERVER)) {
            Set<String> sideNames = new TreeSet<>();
            immutableRegistrations.forEach((name, bySide) -> {
               if (bySide.containsKey(side) || bySide.containsKey(Side.BOTH)) sideNames.add(name);
            });
            available.put(side, Collections.unmodifiableSet(sideNames));
         }
         return new Snapshot(Collections.unmodifiableMap(immutableRegistrations), immutableClassNames, names,
            Collections.unmodifiableMap(available));
      }
   }

   private record StagedRegistrations(Map<String, Map<Side, Registration>> registrations,
                                      Map<String, String> classNames) {
   }
}
