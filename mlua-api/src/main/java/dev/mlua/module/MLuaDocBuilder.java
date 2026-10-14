package dev.mlua.module;

import java.util.ArrayList;
import java.util.List;

/** Collects generated API references and addon guide contributions for one documentation build. */
public final class MLuaDocBuilder {
    private final List<DocEntry> entries = new ArrayList<>();
    private final List<MethodDoc> methods = new ArrayList();
    private final List<SignatureDoc> signatures = new ArrayList<>();
    private final List<ServiceFunctionDoc> serviceFunctions = new ArrayList<>();
    private final List<GuideStep> guideSteps = new ArrayList<>();
    private final List<MemberDoc> members = new ArrayList<>();
    private final List<TypeDoc> types = new ArrayList<>();
    private final List<FieldDoc> fields = new ArrayList<>();
    private final List<SectionDoc> sections = new ArrayList<>();
    private final List<Contribution> contributions = new ArrayList<>();

    public void register(Registration registration) {
        if (registration == null) throw new NullPointerException("registration");
        Object[] value = registration.values();
        switch (registration.kind()) {
            case SERVICE -> service((String) value[0], (String) value[1], (String) value[2], (String) value[3]);
            case CLASS -> clazz((String) value[0], (String) value[1], (String) value[2], (String) value[3], (String) value[4]);
            case ENUM -> enumDoc((String) value[0], (String) value[1], (String) value[2], (String[][]) value[3]);
            case DATA_TYPE -> dataType((String) value[0], (String) value[1], (String) value[2]);
            case LIBRARY -> library((String) value[0], (String) value[1], (String) value[2]);
            case METHOD -> method((String) value[0], (String) value[1], (String) value[2], (String) value[3], (String) value[4], (String) value[5], (String) value[6]);
            case SIGNATURE -> signature((String) value[0], (String) value[1], (String) value[2]);
            case SERVICE_FUNCTION -> serviceFunction((String) value[0], (String) value[1], (String) value[2], (String) value[3]);
            case PROPERTY -> property((String) value[0], (String) value[1], (String) value[2], (String) value[3], (String) value[4]);
            case EVENT -> event((String) value[0], (String) value[1], (String) value[2], (String) value[3], (String) value[4]);
            case TYPE -> type((String) value[0], (String) value[1], (String) value[2]);
            case FIELD -> field((String) value[0], (String) value[1], (String) value[2], (String) value[3]);
            case GUIDE_STEP -> guideStep((String) value[0], (String) value[1], (String) value[2], (String) value[3]);
            case CONTRIBUTION -> contribution((Contribution) value[0]);
            case SECTION -> section((String) value[0], (String) value[1], (String) value[2], (String) value[3], (Integer) value[4]);
        }
    }

    public enum RegistrationKind { SERVICE, CLASS, ENUM, DATA_TYPE, LIBRARY, METHOD, SIGNATURE,
        SERVICE_FUNCTION, PROPERTY, EVENT, TYPE, FIELD, GUIDE_STEP, CONTRIBUTION, SECTION }

    public record Registration(RegistrationKind kind, Object[] values) {
        public Registration {
            if (kind == null) throw new NullPointerException("kind");
            values = values.clone();
        }
        public Object[] values() { return values.clone(); }
        public static Registration service(String name, String className, String description, String path) { return of(RegistrationKind.SERVICE, name, className, description, path); }
        public static Registration clazz(String name, String className, String description, String path, String parent) { return of(RegistrationKind.CLASS, name, className, description, path, parent); }
        public static Registration enumDoc(String name, String className, String description, String[][] constants) { return of(RegistrationKind.ENUM, name, className, description, constants); }
        public static Registration dataType(String name, String description, String path) { return of(RegistrationKind.DATA_TYPE, name, description, path); }
        public static Registration library(String name, String description, String path) { return of(RegistrationKind.LIBRARY, name, description, path); }
        public static Registration method(String receiver, String signature, String description, String parameters, String returns, String side, String example) { return of(RegistrationKind.METHOD, receiver, signature, description, parameters, returns, side, example); }
        public static Registration signature(String receiver, String signature, String returns) { return of(RegistrationKind.SIGNATURE, receiver, signature, returns); }
        public static Registration serviceFunction(String service, String className, String name, String description) { return of(RegistrationKind.SERVICE_FUNCTION, service, className, name, description); }
        public static Registration property(String receiver, String signature, String type, String description, String side) { return of(RegistrationKind.PROPERTY, receiver, signature, type, description, side); }
        public static Registration event(String receiver, String signature, String arguments, String description, String side) { return of(RegistrationKind.EVENT, receiver, signature, arguments, description, side); }
        public static Registration type(String name, String parent, String description) { return of(RegistrationKind.TYPE, name, parent, description); }
        public static Registration field(String typeName, String name, String valueType, String description) { return of(RegistrationKind.FIELD, typeName, name, valueType, description); }
        public static Registration guideStep(String receiver, String title, String explanation, String example) { return of(RegistrationKind.GUIDE_STEP, receiver, title, explanation, example); }
        public static Registration contribution(Contribution contribution) { return of(RegistrationKind.CONTRIBUTION, contribution); }
        public static Registration section(String receiver, String kind, String title, String description, int order) { return of(RegistrationKind.SECTION, receiver, kind, title, description, order); }
        private static Registration of(RegistrationKind kind, Object... values) { return new Registration(kind, values); }
    }

    public void service(String name, String className, String desc, String filePath) {
        this.entries.add(new DocEntry(DocEntryType.SERVICE, name, className, desc, filePath));
    }

    public void clazz(String name, String className, String desc, String filePath, String parentClass) {
        this.entries.add(new DocEntry(DocEntryType.CLASS, name, className, desc, filePath, parentClass));
    }

    public void enumDoc(String name, String className, String desc, String[][] constants) {
        this.entries.add(new DocEntry(DocEntryType.ENUM, name, className, desc, constants));
    }

    public void dataType(String name, String desc, String filePath) {
        this.entries.add(new DocEntry(DocEntryType.DATA_TYPE, name, name, desc, filePath));
    }

    /** Registers a Lua global library reference page, discovered by the docs sidebar and link index. */
    public void library(String name, String desc, String filePath) {
        this.entries.add(new DocEntry(DocEntryType.LIBRARY, name, name, desc, filePath));
    }

    public List<DocEntry> entries() {
        return List.copyOf(this.entries);
    }

    /** Adds a precise Lua-facing method reference to the generated API docs. */
    public void method(String receiver, String signature, String description,
                       String parameters, String returns, String side, String example) {
        if (receiver == null || receiver.isBlank()) throw new IllegalArgumentException("Method receiver must be non-empty");
        if (signature == null || signature.isBlank()) throw new IllegalArgumentException("Method signature must be non-empty");
        this.methods.add(new MethodDoc(receiver, signature, description, parameters, returns, side, example));
    }

    public List<MethodDoc> methods() {
        return List.copyOf(this.methods);
    }

    /** Registers typed parameters or a return type for an existing Lua-facing method. */
    public void signature(String receiver, String signature, String returns) {
        if (receiver == null || receiver.isBlank()) throw new IllegalArgumentException("Signature receiver must be non-empty");
        if (signature == null || signature.isBlank()) throw new IllegalArgumentException("Method signature must be non-empty");
        if (this.signatures.stream().anyMatch(item -> item.receiver().equals(receiver)
            && item.signature().equals(signature))) {
            throw new IllegalArgumentException("Lua API signature is already declared: " + signature);
        }
        this.signatures.add(new SignatureDoc(receiver, signature, returns));
    }

    public List<SignatureDoc> signatures() { return List.copyOf(this.signatures); }

    /** Adds a method from a declared service contract to its generated reference page. */
    public void serviceFunction(String service, String className, String name, String description) {
        if (service == null || service.isBlank()) throw new IllegalArgumentException("Service name must be non-empty");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Service function name must be non-empty");
        ServiceFunctionDoc doc = new ServiceFunctionDoc(service, className, name, description);
        if (this.serviceFunctions.contains(doc)) throw new IllegalArgumentException("Service function is already documented: " + service + "." + name);
        this.serviceFunctions.add(doc);
    }

    public List<ServiceFunctionDoc> serviceFunctions() { return List.copyOf(this.serviceFunctions); }

    /** Registers a documented property on a service or live class instance. */
    public void property(String receiver, String signature, String type, String description, String side) {
        this.member(receiver, signature, MemberKind.PROPERTY, type, description, side);
    }

    /** Registers a documented signal or event on a service or live class instance. */
    public void event(String receiver, String signature, String arguments, String description, String side) {
        this.member(receiver, signature, MemberKind.EVENT, arguments, description, side);
    }

    private void member(String receiver, String signature, MemberKind kind, String type,
                        String description, String side) {
        if (receiver == null || receiver.isBlank()) throw new IllegalArgumentException("Member receiver must be non-empty");
        if (signature == null || signature.isBlank()) throw new IllegalArgumentException("Member signature must be non-empty");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("Member type or event arguments must be non-empty");
        this.members.add(new MemberDoc(receiver, signature, kind, type, description, side));
    }

    public List<MemberDoc> members() {
        return List.copyOf(this.members);
    }

    /** Registers a Lua table type contributed by a module for generated API declarations. */
    public void type(String name, String parent, String description) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Type name must be non-empty");
        if (this.types.stream().anyMatch(type -> type.name().equals(name))) {
            throw new IllegalArgumentException("Lua API type is already declared: " + name);
        }
        this.types.add(new TypeDoc(name, parent, description));
    }

    /** Registers a field on a module-contributed Lua table type. */
    public void field(String typeName, String name, String valueType, String description) {
        if (typeName == null || typeName.isBlank()) throw new IllegalArgumentException("Field type name must be non-empty");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Field name must be non-empty");
        if (valueType == null || valueType.isBlank()) throw new IllegalArgumentException("Field value type must be non-empty");
        if (this.fields.stream().anyMatch(field -> field.typeName().equals(typeName) && field.name().equals(name))) {
            throw new IllegalArgumentException("Lua API field is already declared: " + typeName + "." + name);
        }
        this.fields.add(new FieldDoc(typeName, name, valueType, description));
    }

    public List<TypeDoc> types() { return List.copyOf(this.types); }

    public List<FieldDoc> fields() { return List.copyOf(this.fields); }

    public void section(String receiver, String kind, String title, String description, int order) {
        if (receiver == null || receiver.isBlank()) throw new IllegalArgumentException("Documentation section receiver must be non-empty");
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("Documentation section kind must be non-empty");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Documentation section title must be non-empty");
        SectionDoc section = new SectionDoc(receiver, kind, title, description, order);
        if (this.sections.stream().anyMatch(existing -> existing.receiver().equals(receiver) && existing.kind().equalsIgnoreCase(kind))) {
            throw new IllegalArgumentException("Documentation section is already registered: " + receiver + "." + kind);
        }
        this.sections.add(section);
    }

    public List<SectionDoc> sections() {
        return this.sections.stream().sorted(java.util.Comparator.comparing(SectionDoc::receiver)
            .thenComparingInt(SectionDoc::order).thenComparing(SectionDoc::kind)).toList();
    }

    /** Descriptions are optional while authoring, but omissions are reported during doc generation. */
    public List<String> warnings() {
        List<String> warnings = new ArrayList<>();
        for (DocEntry entry : entries) if (blank(entry.desc())) warnings.add(entry.type() + " " + entry.name() + " has no description");
        for (MethodDoc method : methods) if (blank(method.description())) warnings.add("Method " + method.receiver() + " " + method.signature() + " has no description");
        for (ServiceFunctionDoc function : serviceFunctions) if (blank(function.description())) warnings.add("Function " + function.service() + "." + function.name() + " has no description");
        for (MemberDoc member : members) if (blank(member.description())) warnings.add(member.kind() + " " + member.receiver() + " " + member.signature() + " has no description");
        for (FieldDoc field : fields) if (blank(field.description())) warnings.add("Field " + field.typeName() + "." + field.name() + " has no description");
        for (TypeDoc type : types) if (blank(type.description())) warnings.add("Type " + type.name() + " has no description");
        for (GuideStep step : guideSteps) if (blank(step.explanation())) warnings.add("Guide step " + step.receiver() + ": " + step.title() + " has no description");
        for (Contribution contribution : contributions) {
            if (blank(contribution.description())) warnings.add("Guide " + contribution.id() + " has no description");
            for (Example example : contribution.examples()) if (blank(example.description())) warnings.add("Example " + example.title() + " has no description");
            for (Template template : contribution.templates()) if (blank(template.description())) warnings.add("Template " + template.filename() + " has no description");
        }
        return List.copyOf(warnings);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public void contribution(Contribution contribution) {
        if (contribution == null) throw new NullPointerException("contribution");
        if (this.contributions.stream().anyMatch(existing -> existing.id().equals(contribution.id()))) {
            throw new IllegalArgumentException("Duplicate documentation id: " + contribution.id());
        }
        this.contributions.add(contribution);
    }

    public List<Contribution> contributions() {
        return this.contributions.stream()
            .sorted(java.util.Comparator.comparing((Contribution contribution) -> sectionKey(contribution.section()))
                .thenComparingInt(Contribution::order).thenComparing(Contribution::id))
            .toList();
    }

    /** Returns a stable key for grouping guide sections regardless of spaces or punctuation. */
    public static String sectionKey(String section) {
        if (section == null) return "";
        return section.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    /** Getting Started accepts the common display-name and slug spellings. */
    public static boolean isGettingStartedSection(String section) {
        return "gettingstarted".equals(sectionKey(section));
    }

    /** Adds an ordered, teach-by-example step to a service or class reference page. */
    public void guideStep(String receiver, String title, String explanation, String example) {
        if (receiver == null || receiver.isBlank()) throw new IllegalArgumentException("Guide receiver must be non-empty");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Guide step title must be non-empty");
        if (explanation == null || explanation.isBlank()) throw new IllegalArgumentException("Guide step explanation must be non-empty");
        if (example == null || example.isBlank()) throw new IllegalArgumentException("Guide step example must be non-empty");
        this.guideSteps.add(new GuideStep(receiver, title, explanation, example));
    }

    public List<GuideStep> guideSteps() {
        return List.copyOf(this.guideSteps);
    }

    public enum DocEntryType {
        SERVICE,
        CLASS,
        ENUM,
        DATA_TYPE,
        LIBRARY
    }

    public static record DocEntry(DocEntryType type, String name, String className, String desc, String filePath, String parentClass, String[][] constants) {
        public DocEntry(DocEntryType type, String name, String className, String desc, String filePath) {
            this(type, name, className, desc, filePath, null, null);
        }

        public DocEntry(DocEntryType type, String name, String className, String desc, String filePath, String parentClass) {
            this(type, name, className, desc, filePath, parentClass, null);
        }

        public DocEntry(DocEntryType type, String name, String className, String desc, String[][] constants) {
            this(type, name, className, desc, null, null, constants);
        }
    }

    public record MethodDoc(String receiver, String signature, String description,
                            String parameters, String returns, String side, String example) {
    }

    public record SignatureDoc(String receiver, String signature, String returns) { }

    public record ServiceFunctionDoc(String service, String className, String name, String description) { }

    public record GuideStep(String receiver, String title, String explanation, String example) {
        public GuideStep {
            if (receiver == null || receiver.isBlank()) throw new IllegalArgumentException("Guide step receiver must be non-empty");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("Guide step title must be non-empty");
            if (example == null || example.isBlank()) throw new IllegalArgumentException("Guide step example must be non-empty");
        }
    }

    public enum MemberKind { PROPERTY, EVENT }

    public record MemberDoc(String receiver, String signature, MemberKind kind,
                            String type, String description, String side) {
    }

    public record TypeDoc(String name, String parent, String description) { }

    public record FieldDoc(String typeName, String name, String valueType, String description) { }

    public record SectionDoc(String receiver, String kind, String title, String description, int order) { }

    public record Contribution(String id, String section, String title, String description,
                               String owner, int order, List<GuideStep> steps, List<Example> examples,
                               List<Template> templates) {
        /** Starts an addon-owned documentation contribution with an owner-qualified id. */
        public static Builder builder(String owner, String id) {
            return new Builder(owner, id);
        }

        public Contribution(String id, String section, String title, String description,
                            String owner, int order, List<GuideStep> steps, List<Example> examples) {
            this(id, section, title, description, owner, order, steps, examples, List.of());
        }

        public Contribution {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Documentation id must be non-empty");
            if (section == null || section.isBlank()) throw new IllegalArgumentException("Documentation section must be non-empty");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("Documentation title must be non-empty");
            if (owner == null || owner.isBlank()) throw new IllegalArgumentException("Documentation owner must be non-empty");
            if (!id.startsWith(owner + ":")) {
                throw new IllegalArgumentException("Documentation id must use its owner's namespace: " + owner + ":");
            }
            List<GuideStep> stepCopy = steps == null ? List.of() : new ArrayList<>(steps);
            List<Example> exampleCopy = examples == null ? List.of() : new ArrayList<>(examples);
            List<Template> templateCopy = templates == null ? List.of() : new ArrayList<>(templates);
            if (stepCopy.isEmpty() && exampleCopy.isEmpty() && templateCopy.isEmpty()) {
                throw new IllegalArgumentException("Documentation contribution must include at least one step, example, or template");
            }
            if (stepCopy.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Documentation steps cannot contain null entries");
            }
            if (exampleCopy.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Documentation examples cannot contain null entries");
            }
            if (templateCopy.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Documentation templates cannot contain null entries");
            }
            steps = List.copyOf(stepCopy);
            examples = List.copyOf(exampleCopy);
            templates = List.copyOf(templateCopy);
        }

        /** Collects one addon guide before publishing its immutable contribution. */
        public static final class Builder {
            private final String owner;
            private final String id;
            private String section;
            private String title;
            private String description;
            private int order;
            private final List<GuideStep> steps = new ArrayList<>();
            private final List<Example> examples = new ArrayList<>();
            private final List<Template> templates = new ArrayList<>();

            private Builder(String owner, String id) {
                this.owner = owner;
                this.id = id;
            }

            public Builder section(String section) {
                this.section = section;
                return this;
            }

            public Builder title(String title) {
                this.title = title;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public Builder order(int order) {
                this.order = order;
                return this;
            }

            public Builder step(String receiver, String title, String explanation, String example) {
                this.steps.add(new GuideStep(receiver, title, explanation, example));
                return this;
            }

            public Builder example(String title, String description, String code) {
                this.examples.add(new Example(title, description, code));
                return this;
            }

            public Builder template(String title, String description, String filename, String code) {
                this.templates.add(new Template(title, description, filename, code));
                return this;
            }

            public Contribution build() {
                return new Contribution(this.id, this.section, this.title, this.description, this.owner,
                    this.order, this.steps, this.examples, this.templates);
            }
        }
    }

    public record Example(String title, String description, String code) {
        public Example {
            if (title == null || title.isBlank()) throw new IllegalArgumentException("Example title must be non-empty");
            if (code == null || code.isBlank()) throw new IllegalArgumentException("Example code must be non-empty");
        }
    }

    /** A reusable script file shown in the generated guide and available for readers to copy. */
    public record Template(String title, String description, String filename, String code) {
        public Template {
            if (title == null || title.isBlank()) throw new IllegalArgumentException("Template title must be non-empty");
            if (filename == null || filename.isBlank()) throw new IllegalArgumentException("Template filename must be non-empty");
            if (code == null || code.isBlank()) throw new IllegalArgumentException("Template code must be non-empty");
        }
    }
}
