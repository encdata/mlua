package dev.mlua.core;

import dev.mlua.module.MLuaDocBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MLuaDocumentationSite {
   private static final String STYLE = """
      *{box-sizing:border-box}body{margin:0;background:#121215;color:#e7e9ee;font:15px/1.65 system-ui,-apple-system,'Segoe UI',sans-serif}a{color:#82b6ff;text-decoration:none}a:hover{color:#b6d4ff}button,input{font:inherit}
      .shell{display:grid;grid-template-columns:300px minmax(0,1fr);height:100vh}.side{position:sticky;top:0;height:100vh;background:#15161a;border-right:1px solid #292b32;display:flex;flex-direction:column;min-height:0;z-index:5}
      .brand{display:flex;align-items:center;gap:12px;padding:19px 20px;border-bottom:1px solid #292b32;font-size:17px;font-weight:700;color:#fff;flex:none}.brand img{width:38px;height:38px;object-fit:contain;border-radius:9px}
      .search-wrap{position:relative;margin:14px 12px 8px;flex:none}.search{width:100%;padding:10px 48px 10px 12px;border:1px solid #30323a;border-radius:8px;background:#1d1f24;color:#eee;outline:none}.search:focus{border-color:#5596ec;box-shadow:0 0 0 3px #5596ec22}.search-hint{position:absolute;right:9px;top:8px;padding:1px 5px;border:1px solid #383b44;border-radius:4px;color:#8992a0;font:11px/1.6 ui-monospace,SFMono-Regular,Consolas,monospace;pointer-events:none}
      .nav{overflow:auto;overscroll-behavior:contain;padding:7px 10px 20px;scrollbar-color:#363943 transparent;scrollbar-width:thin}.nav-group{margin:3px 0}.nav-group summary{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:9px 10px;border-radius:6px;color:#8d95a3;cursor:pointer;list-style:none;font-size:11px;font-weight:750;text-transform:uppercase;letter-spacing:.08em;user-select:none}.nav-group summary::-webkit-details-marker{display:none}.nav-group summary:after{content:'›';font-size:17px;line-height:12px;transform:rotate(0);transition:transform .15s ease}.nav-group[open] summary:after{transform:rotate(90deg)}.nav-group summary:hover,.nav-group summary:focus-visible{background:#1e2025;color:#cbd0da;outline:none}.nav-count{margin-left:auto;color:#626b79;font:11px ui-monospace,SFMono-Regular,Consolas,monospace}.nav a{display:block;margin:1px 0;padding:7px 10px 7px 13px;border-left:2px solid transparent;border-radius:0 6px 6px 0;color:#b7bdc8;font-size:13px;line-height:1.45;overflow-wrap:anywhere}.nav a:hover{background:#202228;color:#fff}.nav a.active{border-left-color:#70a9fa;background:#242832;color:#fff;font-weight:600}.nav a:focus-visible{outline:2px solid #70a9fa;outline-offset:1px}.nav a[hidden],.nav-group[hidden],.nav-empty[hidden],.nav-backdrop{display:none}.nav-empty{margin:12px 8px;padding:14px;border:1px dashed #363943;border-radius:8px;color:#929aa8;font-size:13px}.side-footer{padding:10px 18px;border-top:1px solid #292b32;color:#69717e;font-size:11px;flex:none}
      .main{overflow:auto;min-width:0}.top{position:sticky;top:0;z-index:2;display:flex;align-items:center;gap:12px;padding:14px 32px;background:#121215ef;border-bottom:1px solid #292b32;color:#969eac;font-size:13px;backdrop-filter:blur(10px)}.menu-toggle{display:none;align-items:center;justify-content:center;width:34px;height:34px;border:1px solid #343741;border-radius:7px;background:#1d1f24;color:#dce2ec;cursor:pointer}.viewport{max-width:1120px;margin:auto;padding:42px 48px 100px}.page[hidden]{display:none}.eyebrow{color:#85b5fa;font-size:12px;font-weight:700;letter-spacing:.08em;text-transform:uppercase}h1{margin:5px 0 8px;color:#fff;font-size:36px;line-height:1.2;letter-spacing:-.03em}h2{margin:36px 0 6px;color:#fff;font-size:21px}h3{margin:0 0 8px;color:#fff;font-size:16px}.lead{max-width:760px;margin:0 0 24px;color:#b5bbc6;font-size:17px}.section-note{max-width:800px;color:#9da5b2;margin:0 0 15px}.cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,330px),1fr));gap:12px}.card{min-width:0;padding:18px 19px;border:1px solid #30323a;border-radius:10px;background:linear-gradient(145deg,#1d1f24,#191a1e);box-shadow:0 5px 20px #0002}.card:hover{border-color:#4a5568}.card .kind{display:block;margin-bottom:7px;color:#8d98a9;font-size:10px;font-weight:800;letter-spacing:.1em;text-transform:uppercase}.card .signature{display:block;margin-bottom:9px;color:#a8ceff;font:600 14px/1.5 ui-monospace,SFMono-Regular,Consolas,monospace;overflow-wrap:anywhere}.card p{margin:5px 0;color:#b7bdc8;font-size:13px}.card .meta{color:#8992a0;font-size:12px}.member-warning{color:#ddb874;font-size:12px}.code{margin:12px 0 0;padding:13px 14px;border:1px solid #292d35;border-radius:7px;background:#111216;overflow:auto;color:#e2e7ef;white-space:pre-wrap;font:12px/1.6 ui-monospace,SFMono-Regular,Consolas,monospace}.callout{margin:18px 0;padding:13px 16px;border-left:3px solid #5794e8;border-radius:5px;background:#1b2636;color:#c7d9f2}.empty{padding:18px;border:1px dashed #363943;border-radius:9px;color:#8992a0}.api-inventory{margin-top:24px}.api-inventory article{margin:10px 0}.api-inventory h2{margin-top:28px}.api-inventory input{padding:8px;border-radius:6px;border:1px solid #343741;background:#1d1f24;color:#eee}
      @media(max-width:800px){.shell{display:block;height:100vh}.side{position:fixed;inset:0 auto 0 0;width:min(340px,88vw);height:100dvh;transform:translateX(-102%);transition:transform .2s ease;box-shadow:12px 0 36px #0008}.shell.nav-open .side{transform:translateX(0)}.nav-backdrop{display:none;position:fixed;inset:0;width:100%;height:100%;padding:0;border:0;background:#0009;z-index:4}.shell.nav-open .nav-backdrop{display:block}.main{height:100vh;overflow:auto}.menu-toggle{display:inline-flex;flex:none}.top{padding:10px 16px}.viewport{padding:28px 18px 72px}h1{font-size:30px}}
      @media(prefers-reduced-motion:reduce){*,*:before,*:after{scroll-behavior:auto!important;transition:none!important}}
      """;
   private static final String GUIDE_STYLE = ".guide-steps{display:grid;gap:12px;max-width:900px;counter-reset:guide}.guide-step{position:relative;padding:19px 20px 19px 64px;border:1px solid #30323a;border-radius:10px;background:#1a1c21;counter-increment:guide}.guide-step:before{content:counter(guide);position:absolute;left:19px;top:19px;display:grid;place-items:center;width:28px;height:28px;border:1px solid #3b4e68;border-radius:50%;background:#202c3b;color:#a8ceff;font-size:13px;font-weight:700}.guide-step h3{margin:0 0 5px}.guide-step p{margin:4px 0;color:#b7bdc8;font-size:14px}.guide-assets{margin-top:20px}.guide-assets .cards{align-items:start}.guide-steps+.guide-assets{margin-top:28px}@media(max-width:520px){.guide-step{padding:17px 14px 17px 52px}.guide-step:before{left:13px;top:17px}}";
   private static final String SYNTAX_STYLE = ".syntax-keyword{color:#82aaff;font-weight:600}.syntax-string{color:#c3e88d}.syntax-number{color:#f78c6c}.syntax-comment{color:#68737d;font-style:italic}.syntax-global{color:#ffcb6b}.code .syntax-keyword,.code .syntax-string,.code .syntax-number,.code .syntax-comment,.code .syntax-global{font:inherit}";

   private MLuaDocumentationSite() { }

   static void write(Path output, MLuaDocBuilder builder, String inventory, String iconDataUri) throws IOException {
      List<Page> pages = buildPages(builder, inventory);
      MLuaDocumentation.replaceTypeRoutes(typeRoutes(pages));
      StringBuilder html = new StringBuilder(32_768);
      html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><meta name=\"color-scheme\" content=\"dark\"><title>MLua Documentation</title><style>")
         .append(STYLE).append(GUIDE_STYLE).append(SYNTAX_STYLE).append("</style></head><body><div id=\"doc-shell\" class=\"shell\"><aside id=\"doc-sidebar\" class=\"side\"><a class=\"brand\" href=\"#/home\"><img src=\"")
         .append(iconDataUri).append("\" alt=\"MLua icon\"><span>MLua Documentation</span></a><div class=\"search-wrap\"><input id=\"doc-search\" class=\"search\" type=\"search\" placeholder=\"Search pages and API\" aria-label=\"Search pages and API\" autocomplete=\"off\"><span class=\"search-hint\" aria-hidden=\"true\">/</span></div><nav id=\"doc-nav\" class=\"nav\" aria-label=\"Documentation pages\">");
      appendNavigation(html, pages);
      html.append("<p id=\"nav-empty\" class=\"nav-empty\" hidden>No pages match that search.</p></nav><footer class=\"side-footer\">Offline API reference</footer></aside><button id=\"nav-backdrop\" class=\"nav-backdrop\" type=\"button\" aria-label=\"Close documentation navigation\"></button><main class=\"main\"><header id=\"crumb\" class=\"top\"><button id=\"menu-toggle\" class=\"menu-toggle\" type=\"button\" aria-label=\"Open documentation navigation\" aria-controls=\"doc-sidebar\" aria-expanded=\"false\">☰</button><span id=\"breadcrumb\">MLua / Documentation</span></header><div id=\"doc-pages\" class=\"viewport\">");
      for (Page page : pages) {
         html.append("<article class=\"page\" data-route=\"").append(MLuaDocumentation.escape(page.route()))
            .append("\" data-search=\"").append(MLuaDocumentation.escape((page.title() + " " + page.description()).toLowerCase()))
            .append("\" hidden><div class=\"eyebrow\">").append(MLuaDocumentation.escape(page.category()))
            .append("</div><h1>").append(MLuaDocumentation.escape(page.title())).append("</h1>");
         if (page.description() != null && !page.description().isBlank()) {
            html.append("<p class=\"lead\">").append(MLuaDocumentation.escape(page.description())).append("</p>");
         }
         html.append(page.body()).append("</article>");
      }
      html.append("</div></main></div><script>").append(SCRIPT).append("</script></body></html>");
      Files.createDirectories(output.getParent());
      Files.writeString(output, html, StandardCharsets.UTF_8);
   }

   private static List<Page> buildPages(MLuaDocBuilder builder, String inventory) {
      List<Page> pages = new ArrayList<>();
      List<MLuaDocBuilder.DocEntry> entries = builder.entries();
      Map<String, String> routes = new LinkedHashMap<>();
      for (MLuaDocBuilder.DocEntry entry : entries) routes.put(entry.name(), "#" + entryRoute(entry));
      MLuaDocumentation.replaceTypeRoutes(routes);
      pages.add(new Page("/home", "Documentation", "Home", "", overview(entries, builder.contributions())));
      pages.add(new Page("/api", "API Reference", "API Reference", "", apiOverview(entries)));
      MLuaDocBuilder.Contribution start = builder.contributions().stream()
         .filter(item -> MLuaDocBuilder.isGettingStartedSection(item.section())).findFirst().orElse(null);
      pages.add(new Page("/guides/getting-started", start == null ? "Getting Started" : start.title(), "Guides",
         start == null ? "" : start.description(), gettingStarted(builder)));
      for (MLuaDocBuilder.DocEntry entry : entries) {
         String route = entryRoute(entry);
         if (route == null) continue;
         pages.add(new Page(route, entry.name(), category(entry.type()), entry.desc(), entryContent(entry, builder)));
      }
      for (Map.Entry<String, List<MLuaDocBuilder.Contribution>> section : contributionSections(builder.contributions()).entrySet()) {
         if (MLuaDocBuilder.isGettingStartedSection(section.getValue().get(0).section())) continue;
         MLuaDocBuilder.Contribution first = section.getValue().get(0);
         pages.add(new Page("/guides/" + MLuaDocumentation.slug(section.getKey()), first.section(), "Guides",
            first.description(), guideContent(section.getValue())));
      }
      if (inventory != null && !inventory.isBlank()) {
         pages.add(new Page("/api/live", "Registered Lua Tables", "API Reference",
            "", "<div class=\"api-inventory\">" + inventory + "</div>"));
      }
      pages.sort(Comparator.comparingInt(page -> page.route().equals("/home") ? -1 : page.route().equals("/api") ? 0 : 1));
      return List.copyOf(pages);
   }

   private static String overview(List<MLuaDocBuilder.DocEntry> entries,
                                  List<MLuaDocBuilder.Contribution> contributions) {
      StringBuilder body = new StringBuilder("<h2>API</h2><div class=\"cards\">");
      for (MLuaDocBuilder.DocEntry entry : entries) {
         String route = entryRoute(entry);
         if (route != null) body.append(card(category(entry.type()), entry.name(), entry.desc(), route));
      }
      MLuaDocBuilder.Contribution start = contributions.stream()
         .filter(item -> MLuaDocBuilder.isGettingStartedSection(item.section())).findFirst().orElse(null);
      body.append("</div><h2>Guides</h2><div class=\"cards\">")
         .append(card("GUIDE", start == null ? "Getting Started" : start.title(),
            start == null ? "" : start.description(), "/guides/getting-started"));
      for (Map.Entry<String, List<MLuaDocBuilder.Contribution>> section : contributionSections(contributions).entrySet()) {
         if (MLuaDocBuilder.isGettingStartedSection(section.getValue().get(0).section())) continue;
         MLuaDocBuilder.Contribution first = section.getValue().get(0);
         body.append(card("GUIDE", first.section(), first.description(), "/guides/" + MLuaDocumentation.slug(section.getKey())));
      }
      return body.append("</div>").toString();
   }

   private static String apiOverview(List<MLuaDocBuilder.DocEntry> entries) {
      Map<String, List<MLuaDocBuilder.DocEntry>> groups = new LinkedHashMap<>();
      for (MLuaDocBuilder.DocEntry entry : entries) groups.computeIfAbsent(category(entry.type()), ignored -> new ArrayList<>()).add(entry);
      StringBuilder body = new StringBuilder();
      groups.forEach((name, values) -> {
         body.append("<h2>").append(MLuaDocumentation.escape(name)).append("</h2><div class=\"cards\">");
         for (MLuaDocBuilder.DocEntry entry : values) {
            String route = entryRoute(entry);
            if (route != null) body.append(card(name, entry.name(), entry.desc(), route));
         }
         body.append("</div>");
      });
      return body.toString();
   }

   private static String entryContent(MLuaDocBuilder.DocEntry entry, MLuaDocBuilder builder) {
      List<SectionChunk> sections = new ArrayList<>();
      List<MLuaDocBuilder.MethodDoc> methods = builder.methods().stream().filter(item -> item.receiver().equals(entry.name())).toList();
      List<MLuaDocBuilder.SignatureDoc> signatureOnly = builder.signatures().stream()
         .filter(item -> item.receiver().equals(entry.name()))
         .filter(item -> methods.stream().noneMatch(method -> method.signature().equals(item.signature())))
         .toList();
      if (!methods.isEmpty() || !signatureOnly.isEmpty()) {
         StringBuilder cards = new StringBuilder();
         for (MLuaDocBuilder.MethodDoc method : methods) cards.append(apiCard("METHOD", method.signature(), method.description(), method.parameters(), method.returns(), method.side(), method.example()));
         for (MLuaDocBuilder.SignatureDoc signature : signatureOnly) {
            cards.append(apiCard("METHOD", signature.signature(), "", null, signature.returns(), null, null));
         }
         sections.add(section(builder, entry.name(), "methods", "Methods", 0, cards));
      }
      List<MLuaDocBuilder.ServiceFunctionDoc> serviceFunctions = builder.serviceFunctions().stream().filter(item -> item.service().equals(entry.name())).toList();
      List<MLuaDocBuilder.ServiceFunctionDoc> remainingFunctions = serviceFunctions.stream()
         .filter(function -> methods.stream().noneMatch(method -> method.signature().contains(function.name() + "(")))
         .toList();
      if (!remainingFunctions.isEmpty()) {
         StringBuilder cards = new StringBuilder();
         for (MLuaDocBuilder.ServiceFunctionDoc function : remainingFunctions) {
            MLuaDocBuilder.SignatureDoc metadata = builder.signatures().stream()
               .filter(item -> (item.receiver().equals(entry.name()) || item.receiver().equals(function.className()))
                  && item.signature().contains(function.name() + "("))
               .findFirst().orElse(null);
            String signature = metadata == null ? entry.name() + ":" + function.name() + "()" : metadata.signature();
            cards.append(apiCard("FUNCTION", signature, function.description(), null,
               metadata == null ? null : metadata.returns(), null, null));
         }
         sections.add(section(builder, entry.name(), "functions", "Functions", 1, cards));
      }
      List<MLuaDocBuilder.MemberDoc> members = builder.members().stream().filter(item -> item.receiver().equals(entry.name())).toList();
      for (MLuaDocBuilder.MemberKind kind : MLuaDocBuilder.MemberKind.values()) {
         List<MLuaDocBuilder.MemberDoc> selected = members.stream().filter(item -> item.kind() == kind).toList();
         if (selected.isEmpty()) continue;
         String sectionKind = kind == MLuaDocBuilder.MemberKind.PROPERTY ? "properties" : "events";
         String fallbackTitle = kind == MLuaDocBuilder.MemberKind.PROPERTY ? "Properties" : "Events";
         StringBuilder cards = new StringBuilder();
         for (MLuaDocBuilder.MemberDoc member : selected) {
            cards.append(apiCard(kind == MLuaDocBuilder.MemberKind.PROPERTY ? "PROPERTY" : "EVENT",
               member.signature(), member.description(), null, member.type(), member.side(), null));
         }
         sections.add(section(builder, entry.name(), sectionKind, fallbackTitle,
            kind == MLuaDocBuilder.MemberKind.PROPERTY ? 2 : 3, cards));
      }
      List<MLuaDocBuilder.FieldDoc> fields = builder.fields().stream().filter(item -> item.typeName().equals(entry.name())).toList();
      if (!fields.isEmpty()) {
         StringBuilder cards = new StringBuilder();
         for (MLuaDocBuilder.FieldDoc field : fields) cards.append(apiCard("FIELD", field.name(), field.description(), null, field.valueType(), null, null));
         sections.add(section(builder, entry.name(), "fields", "Fields", 4, cards));
      }
      List<MLuaDocBuilder.GuideStep> steps = builder.guideSteps().stream().filter(item -> item.receiver().equals(entry.name())).toList();
      if (!steps.isEmpty()) {
         StringBuilder cards = new StringBuilder();
         for (MLuaDocBuilder.GuideStep step : steps) cards.append("<section class=\"card\"><h3>").append(MLuaDocumentation.escape(step.title())).append("</h3><p>").append(MLuaDocumentation.escape(step.explanation())).append("</p>").append(code(step.example())).append("</section>");
         sections.add(section(builder, entry.name(), "examples", "Examples", 5, cards));
      }
      sections.sort(Comparator.comparingInt(SectionChunk::order));
      StringBuilder body = new StringBuilder();
      if (entry.parentClass() != null && !entry.parentClass().isBlank()) {
         String route = MLuaDocumentation.typeHref(entry.parentClass());
         body.append("<p class=\"meta\">Inherits from: ");
         if (route == null) body.append(MLuaDocumentation.escape(entry.parentClass()));
         else body.append("<a href=\"").append(MLuaDocumentation.escape(route)).append("\">")
            .append(MLuaDocumentation.escape(entry.parentClass())).append("</a>");
         body.append("</p>");
      }
      sections.forEach(section -> body.append(section.html()));
      if (body.length() == 0) body.append("<div class=\"empty\">No registered members for this entry.</div>");
      return body.toString();
   }

   private static SectionChunk section(MLuaDocBuilder builder, String receiver, String kind,
                                       String fallbackTitle, int fallbackOrder, StringBuilder cards) {
      MLuaDocBuilder.SectionDoc registration = builder.sections().stream()
         .filter(item -> item.receiver().equals(receiver) && item.kind().equalsIgnoreCase(kind))
         .findFirst().orElse(null);
      String title = registration == null ? fallbackTitle : registration.title();
      String description = registration == null ? null : registration.description();
      int order = registration == null ? fallbackOrder : registration.order();
      StringBuilder html = new StringBuilder("<h2>").append(MLuaDocumentation.escape(title)).append("</h2>");
      if (description != null && !description.isBlank()) {
         html.append("<p class=\"section-note\">").append(MLuaDocumentation.escape(description)).append("</p>");
      }
      html.append("<div class=\"cards\">").append(cards).append("</div>");
      return new SectionChunk(order, html.toString());
   }

   private static String gettingStarted(MLuaDocBuilder builder) {
      StringBuilder body = new StringBuilder();
      List<MLuaDocBuilder.Contribution> gettingStarted = builder.contributions().stream()
         .filter(item -> MLuaDocBuilder.isGettingStartedSection(item.section())).toList();
      body.append(guideContent(gettingStarted));
      return body.toString();
   }

   private static String guideContent(List<MLuaDocBuilder.Contribution> contributions) {
      StringBuilder body = new StringBuilder();
      for (MLuaDocBuilder.Contribution contribution : contributions) {
         body.append("<section class=\"guide\"><h2>").append(MLuaDocumentation.escape(contribution.title())).append("</h2><p class=\"section-note\">").append(MLuaDocumentation.escape(contribution.description())).append("</p>");
         if (!contribution.steps().isEmpty()) {
            body.append("<div class=\"guide-steps\">");
            for (MLuaDocBuilder.GuideStep step : contribution.steps()) body.append("<article class=\"guide-step\"><span class=\"kind\">").append(MLuaDocumentation.escape(step.receiver())).append("</span><h3>").append(MLuaDocumentation.escape(step.title())).append("</h3><p>").append(MLuaDocumentation.escape(step.explanation())).append("</p>").append(code(step.example())).append("</article>");
            body.append("</div>");
         }
         if (!contribution.examples().isEmpty() || !contribution.templates().isEmpty()) {
            body.append("<div class=\"guide-assets\"><div class=\"cards\">");
            for (MLuaDocBuilder.Example example : contribution.examples()) body.append("<section class=\"card\"><span class=\"kind\">EXAMPLE</span><h3>").append(MLuaDocumentation.escape(example.title())).append("</h3><p>").append(MLuaDocumentation.escape(example.description())).append("</p>").append(code(example.code())).append("</section>");
            for (MLuaDocBuilder.Template template : contribution.templates()) body.append("<section class=\"card\"><span class=\"kind\">TEMPLATE · ").append(MLuaDocumentation.escape(template.filename())).append("</span><h3>").append(MLuaDocumentation.escape(template.title())).append("</h3><p>").append(MLuaDocumentation.escape(template.description())).append("</p>").append(code(template.code())).append("</section>");
            body.append("</div></div>");
         }
         body.append("</section>");
      }
      return body.toString();
   }

   private static String apiCard(String kind, String signature, String description, String parameters,
                                 String returns, String side, String example) {
      StringBuilder card = new StringBuilder("<article class=\"card\"><span class=\"kind\">")
         .append(MLuaDocumentation.escape(kind)).append(side == null || side.isBlank() ? "" : " · " + MLuaDocumentation.escape(side))
         .append("</span><code class=\"signature\">").append(linkTypes(signature)).append("</code>");
      if (description != null && !description.isBlank()) card.append("<p>").append(MLuaDocumentation.escape(description)).append("</p>");
      if (parameters != null && !parameters.isBlank()) card.append("<p class=\"meta\"><strong>Parameters:</strong> ").append(MLuaDocumentation.escape(parameters)).append("</p>");
      if (returns != null && !returns.isBlank()) {
         String label = switch (kind) {
            case "PROPERTY", "FIELD" -> "Type:";
            case "EVENT" -> "Arguments:";
            default -> "Returns:";
         };
         card.append("<p class=\"meta\"><strong>").append(label).append("</strong> ")
            .append(kind.equals("EVENT") ? linkTypes(returns) : MLuaDocumentation.linkType(returns)).append("</p>");
      }
      if (example != null && !example.isBlank()) card.append(code(example));
      return card.append("</article>").toString();
   }

   private static String linkTypes(String text) {
      String escaped = MLuaDocumentation.escape(text == null ? "" : text);
      java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>(MLuaDocumentation.typeNames());
      names.addAll(List.of("Vector2", "Vector3", "CFrame", "Color3", "UDim", "UDim2", "Player", "Entity", "Block", "World", "Chunk", "ItemStack", "Inventory"));
      for (String type : names) {
         String route = MLuaDocumentation.typeHref(type);
         if (route != null) escaped = escaped.replaceAll("\\b" + type + "\\b", "<a href=\"" + route + "\">" + type + "</a>");
      }
      return escaped;
   }

   private static String card(String kind, String title, String description, String route) {
      return "<a class=\"card\" href=\"#" + MLuaDocumentation.escape(route) + "\"><span class=\"kind\">" + MLuaDocumentation.escape(kind) + "</span><h3>" + MLuaDocumentation.escape(title) + "</h3><p>" + MLuaDocumentation.escape(description == null ? "" : description) + "</p></a>";
   }

   private static String code(String source) { return "<pre class=\"code\"><code>" + MLuaDocumentation.escape(source) + "</code></pre>"; }

   private static String entryRoute(MLuaDocBuilder.DocEntry entry) {
      String kind = switch (entry.type()) {
         case SERVICE -> "services";
         case CLASS -> "classes";
         case ENUM -> "enums";
         case DATA_TYPE -> "types";
         case LIBRARY -> "globals";
      };
      return "/api/" + kind + "/" + MLuaDocumentation.slug(entry.name());
   }

   private static String category(MLuaDocBuilder.DocEntryType type) {
      return switch (type) {
         case SERVICE -> "Services";
         case CLASS -> "Classes";
         case ENUM -> "Enums";
         case DATA_TYPE -> "Types";
         case LIBRARY -> "Globals";
      };
   }

   private static Map<String, String> typeRoutes(List<Page> pages) {
      Map<String, String> routes = new LinkedHashMap<>();
      for (Page page : pages) {
         if (page.route().startsWith("/api/types/") || page.route().startsWith("/api/classes/")
             || page.route().startsWith("/api/services/") || page.route().startsWith("/api/enums/")) {
            routes.put(page.title(), "#" + page.route());
         }
      }
      return routes;
   }

   private static void appendNavigation(StringBuilder html, List<Page> pages) {
      Map<String, List<Page>> groups = new LinkedHashMap<>();
      for (Page page : pages) groups.computeIfAbsent(page.category(), ignored -> new ArrayList<>()).add(page);
      for (Map.Entry<String, List<Page>> group : groups.entrySet()) {
         html.append("<details class=\"nav-group\" data-category=\"").append(MLuaDocumentation.escape(group.getKey()))
            .append("\"><summary><span>").append(MLuaDocumentation.escape(group.getKey())).append("</span><span class=\"nav-count\">")
            .append(group.getValue().size()).append("</span></summary>");
         for (Page page : group.getValue()) {
            String searchText = (page.title() + " " + page.description() + " " + page.category()).toLowerCase();
            html.append("<a data-route=\"").append(MLuaDocumentation.escape(page.route())).append("\" data-title=\"")
               .append(MLuaDocumentation.escape(searchText)).append("\" href=\"#")
               .append(MLuaDocumentation.escape(page.route())).append("\">").append(MLuaDocumentation.escape(page.title())).append("</a>");
         }
         html.append("</details>");
      }
   }

   private static Map<String, List<MLuaDocBuilder.Contribution>> contributionSections(List<MLuaDocBuilder.Contribution> contributions) {
      Map<String, List<MLuaDocBuilder.Contribution>> sections = new java.util.TreeMap<>();
      for (MLuaDocBuilder.Contribution contribution : contributions) {
         sections.computeIfAbsent(MLuaDocBuilder.sectionKey(contribution.section()), ignored -> new ArrayList<>()).add(contribution);
      }
      sections.values().forEach(items -> items.sort(Comparator.comparingInt(MLuaDocBuilder.Contribution::order)
         .thenComparing(MLuaDocBuilder.Contribution::title).thenComparing(MLuaDocBuilder.Contribution::id)));
      return sections;
   }

   private static final String SCRIPT = """
      (function(){
        var shell=document.getElementById('doc-shell'),pages=[...document.querySelectorAll('.page')],links=[...document.querySelectorAll('#doc-nav a')],groups=[...document.querySelectorAll('.nav-group')],crumb=document.getElementById('breadcrumb'),search=document.getElementById('doc-search'),empty=document.getElementById('nav-empty'),menu=document.getElementById('menu-toggle'),backdrop=document.getElementById('nav-backdrop'),pageSearch=new Map(pages.map(function(page){return [page.dataset.route,(page.dataset.search+' '+page.textContent).toLowerCase()]}));
        function closeMenu(){shell.classList.remove('nav-open');menu.setAttribute('aria-expanded','false');menu.setAttribute('aria-label','Open documentation navigation');}
        function route(){var value;try{value=decodeURIComponent(location.hash.slice(1)||'/home')}catch(error){value='/home'}if(!value.startsWith('/'))value='/'+value;var page=pages.find(function(p){return p.dataset.route===value})||pages[0];pages.forEach(function(p){p.hidden=p!==page});links.forEach(function(a){var active=a.dataset.route===page.dataset.route;a.classList.toggle('active',active);a.setAttribute('aria-current',active?'page':'false');if(active){var group=a.closest('.nav-group');if(group)group.open=true;a.scrollIntoView({block:'nearest'});}});crumb.textContent='MLua / '+page.dataset.route.split('/').filter(Boolean).join(' / ');document.title=page.querySelector('h1').textContent+' - MLua';closeMenu();}
        function escapeHtml(text){return text.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
        function highlight(code){
          var source=code.textContent;
          var pattern=/(--[^\\n]*|"(?:\\\\.|[^"\\\\])*"|'(?:\\\\.|[^'\\\\])*'|\\b(?:and|break|do|else|elseif|end|false|for|function|if|in|local|nil|not|or|repeat|return|then|true|until|while|goto)\\b|\\b\\d+(?:\\.\\d+)?\\b|\\b(?:Minecraft|Enum|Require|Signal|Vector2|Vector3|CFrame|Color3|UDim|UDim2|task|wait|print|warn|pairs|ipairs|pcall|xpcall|typeof)\\b)/g;
          code.innerHTML=source.replace(pattern,function(token){var kind=token.startsWith('--')?'comment':token[0]==='"'||token[0]==="'"?'string':/^\\d/.test(token)?'number':/^(and|break|do|else|elseif|end|false|for|function|if|in|local|nil|not|or|repeat|return|then|true|until|while|goto)$/.test(token)?'keyword':'global';return '<span class="syntax-'+kind+'">'+escapeHtml(token)+'</span>';});
        }
        document.querySelectorAll('pre.code code').forEach(highlight);
        window.addEventListener('hashchange',route);
        function filterNavigation(){var q=search.value.trim().toLowerCase(),visible=0;links.forEach(function(a){var match=!q||(a.dataset.title+' '+(pageSearch.get(a.dataset.route)||'')).includes(q);a.hidden=!match;if(match){visible++;if(q)a.closest('.nav-group').open=true;}});groups.forEach(function(group){var hasVisible=group.querySelector('a:not([hidden])')!==null;group.hidden=!hasVisible;if(!q)group.open=group.dataset.defaultOpen==='true';});empty.hidden=visible!==0;}
        groups.forEach(function(group){var category=group.dataset.category;group.dataset.defaultOpen=['Home','API Reference','Guides'].includes(category)?'true':'false';});
        search.addEventListener('input',filterNavigation);
        menu.addEventListener('click',function(){var open=!shell.classList.contains('nav-open');shell.classList.toggle('nav-open',open);menu.setAttribute('aria-expanded',String(open));menu.setAttribute('aria-label',open?'Close documentation navigation':'Open documentation navigation');if(open)search.focus();});
        backdrop.addEventListener('click',closeMenu);
        document.addEventListener('keydown',function(event){var target=event.target,editing=target&&(target.isContentEditable||/^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName));if(event.key==='/'&&!editing){event.preventDefault();search.focus();}else if(event.key==='Escape'){if(document.activeElement===search&&search.value){search.value='';filterNavigation();}else{closeMenu();search.blur();}}});
        filterNavigation();
        route();
      })();
      """;

   private record Page(String route, String title, String category, String description, String body) { }
   private record SectionChunk(int order, String html) { }
}
