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
   private static final String STYLE = "*{box-sizing:border-box}body{margin:0;background:#121215;color:#e7e9ee;font:15px/1.65 system-ui,-apple-system,'Segoe UI',sans-serif}a{color:#82b6ff;text-decoration:none}a:hover{color:#b6d4ff}button,input{font:inherit}.shell{display:grid;grid-template-columns:290px minmax(0,1fr);height:100vh}.side{background:#15161a;border-right:1px solid #292b32;display:flex;flex-direction:column;min-height:0}.brand{display:flex;align-items:center;gap:12px;padding:20px;border-bottom:1px solid #292b32;font-size:18px;font-weight:700;color:#fff}.brand img{width:38px;height:38px;object-fit:contain;border-radius:9px}.search{margin:14px 12px 8px;padding:10px 12px;border:1px solid #30323a;border-radius:8px;background:#1d1f24;color:#eee;outline:none}.search:focus{border-color:#5596ec}.nav{overflow:auto;padding:8px 12px 20px}.nav a{display:block;padding:7px 10px;border-radius:6px;color:#cbd0da;font-size:13px}.nav a:hover,.nav a.active{background:#242832;color:#fff}.nav .group{padding:14px 10px 4px;color:#8d95a3;font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.08em}.main{overflow:auto;min-width:0}.top{position:sticky;top:0;z-index:2;padding:14px 32px;background:#121215eF;border-bottom:1px solid #292b32;color:#969eac;font-size:13px;backdrop-filter:blur(10px)}.viewport{max-width:1120px;margin:auto;padding:42px 48px 100px}.page[hidden]{display:none}.eyebrow{color:#85b5fa;font-size:12px;font-weight:700;letter-spacing:.08em;text-transform:uppercase}h1{margin:5px 0 8px;color:#fff;font-size:36px;line-height:1.2;letter-spacing:-.03em}h2{margin:36px 0 6px;color:#fff;font-size:21px}h3{margin:0 0 8px;color:#fff;font-size:16px}.lead{max-width:760px;margin:0 0 24px;color:#b5bbc6;font-size:17px}.section-note{max-width:800px;color:#9da5b2;margin:0 0 15px}.cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,330px),1fr));gap:12px}.card{min-width:0;padding:18px 19px;border:1px solid #30323a;border-radius:10px;background:linear-gradient(145deg,#1d1f24,#191a1e);box-shadow:0 5px 20px #0002}.card:hover{border-color:#4a5568}.card .kind{display:block;margin-bottom:7px;color:#8d98a9;font-size:10px;font-weight:800;letter-spacing:.1em;text-transform:uppercase}.card .signature{display:block;margin-bottom:9px;color:#a8ceff;font:600 14px/1.5 ui-monospace,SFMono-Regular,Consolas,monospace;overflow-wrap:anywhere}.card p{margin:5px 0;color:#b7bdc8;font-size:13px}.card .meta{color:#8992a0;font-size:12px}.member-warning{color:#ddb874;font-size:12px}.code{margin:12px 0 0;padding:13px 14px;border:1px solid #292d35;border-radius:7px;background:#111216;overflow:auto;color:#e2e7ef;white-space:pre-wrap;font:12px/1.6 ui-monospace,SFMono-Regular,Consolas,monospace}.callout{margin:18px 0;padding:13px 16px;border-left:3px solid #5794e8;border-radius:5px;background:#1b2636;color:#c7d9f2}.empty{padding:18px;border:1px dashed #363943;border-radius:9px;color:#8992a0}.api-inventory{margin-top:24px}.api-inventory article{margin:10px 0}.api-inventory h2{margin-top:28px}.api-inventory input{padding:8px;border-radius:6px;border:1px solid #343741;background:#1d1f24;color:#eee}@media(max-width:800px){.shell{grid-template-columns:1fr;grid-template-rows:auto 1fr;height:auto;min-height:100vh}.side{max-height:42vh}.main{overflow:visible}.viewport{padding:28px 18px 72px}.top{padding:12px 18px}h1{font-size:30px}}";
   private static final String SYNTAX_STYLE = ".syntax-keyword{color:#82aaff;font-weight:600}.syntax-string{color:#c3e88d}.syntax-number{color:#f78c6c}.syntax-comment{color:#68737d;font-style:italic}.syntax-global{color:#ffcb6b}.code .syntax-keyword,.code .syntax-string,.code .syntax-number,.code .syntax-comment,.code .syntax-global{font:inherit}";

   private MLuaDocumentationSite() { }

   static void write(Path output, MLuaDocBuilder builder, String inventory, String iconDataUri) throws IOException {
      List<Page> pages = buildPages(builder, inventory);
      MLuaDocumentation.replaceTypeRoutes(typeRoutes(pages));
      String active = pages.stream().filter(page -> page.route().equals("/home")).findFirst().orElse(pages.get(0)).route();
      StringBuilder html = new StringBuilder(32_768);
      html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><meta name=\"color-scheme\" content=\"dark\"><title>MLua Documentation</title><style>")
         .append(STYLE).append(SYNTAX_STYLE).append("</style></head><body><div class=\"shell\"><aside class=\"side\"><a class=\"brand\" href=\"#/home\"><img src=\"")
         .append(iconDataUri).append("\" alt=\"MLua icon\"><span>MLua Documentation</span></a><input id=\"doc-search\" class=\"search\" type=\"search\" placeholder=\"Search documentation\" aria-label=\"Search documentation\"><nav id=\"doc-nav\" class=\"nav\">");
      appendNavigation(html, pages);
      html.append("</nav></aside><main class=\"main\"><header id=\"crumb\" class=\"top\">MLua / Documentation</header><div id=\"doc-pages\" class=\"viewport\">");
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
      pages.add(new Page("/api", "API Reference", "API", "", apiOverview(entries)));
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
      StringBuilder body = new StringBuilder();
      List<MLuaDocBuilder.MethodDoc> methods = builder.methods().stream().filter(item -> item.receiver().equals(entry.name())).toList();
      List<MLuaDocBuilder.SignatureDoc> signatureOnly = builder.signatures().stream()
         .filter(item -> item.receiver().equals(entry.name()))
         .filter(item -> methods.stream().noneMatch(method -> method.signature().equals(item.signature())))
         .toList();
      if (!methods.isEmpty() || !signatureOnly.isEmpty()) {
         body.append(sectionStart(builder, entry.name(), "methods", "Methods"));
         for (MLuaDocBuilder.MethodDoc method : methods) body.append(apiCard("METHOD", method.signature(), method.description(), method.parameters(), method.returns(), method.side(), method.example()));
         for (MLuaDocBuilder.SignatureDoc signature : signatureOnly) {
            body.append(apiCard("METHOD", signature.signature(), "", null, signature.returns(), null, null));
         }
         body.append("</div>");
      }
      List<MLuaDocBuilder.ServiceFunctionDoc> serviceFunctions = builder.serviceFunctions().stream().filter(item -> item.service().equals(entry.name())).toList();
      if (!serviceFunctions.isEmpty()) {
         body.append(sectionStart(builder, entry.name(), "functions", "Functions"));
         for (MLuaDocBuilder.ServiceFunctionDoc function : serviceFunctions) {
            String signature = builder.signatures().stream().filter(item -> (item.receiver().equals(entry.name()) || item.receiver().equals(function.className())) && item.signature().contains(function.name() + "(")).map(MLuaDocBuilder.SignatureDoc::signature).findFirst().orElse(entry.name() + ":" + function.name() + "()");
            body.append(apiCard("FUNCTION", signature, function.description(), null, null, null, null));
         }
         body.append("</div>");
      }
      List<MLuaDocBuilder.MemberDoc> members = builder.members().stream().filter(item -> item.receiver().equals(entry.name())).toList();
      for (MLuaDocBuilder.MemberKind kind : MLuaDocBuilder.MemberKind.values()) {
         List<MLuaDocBuilder.MemberDoc> selected = members.stream().filter(item -> item.kind() == kind).toList();
         if (selected.isEmpty()) continue;
         String sectionKind = kind == MLuaDocBuilder.MemberKind.PROPERTY ? "properties" : "events";
         String fallbackTitle = kind == MLuaDocBuilder.MemberKind.PROPERTY ? "Properties" : "Events";
         body.append(sectionStart(builder, entry.name(), sectionKind, fallbackTitle));
         for (MLuaDocBuilder.MemberDoc member : selected) {
            body.append(apiCard(kind == MLuaDocBuilder.MemberKind.PROPERTY ? "PROPERTY" : "EVENT",
               member.signature(), member.description(), null, member.type(), member.side(), null));
         }
         body.append("</div>");
      }
      List<MLuaDocBuilder.FieldDoc> fields = builder.fields().stream().filter(item -> item.typeName().equals(entry.name())).toList();
      if (!fields.isEmpty()) {
         body.append(sectionStart(builder, entry.name(), "fields", "Fields"));
         for (MLuaDocBuilder.FieldDoc field : fields) body.append(apiCard("FIELD", field.name(), field.description(), null, field.valueType(), null, null));
         body.append("</div>");
      }
      List<MLuaDocBuilder.GuideStep> steps = builder.guideSteps().stream().filter(item -> item.receiver().equals(entry.name())).toList();
      if (!steps.isEmpty()) {
         body.append(sectionStart(builder, entry.name(), "examples", "Examples"));
         for (MLuaDocBuilder.GuideStep step : steps) body.append("<section class=\"card\"><h3>").append(MLuaDocumentation.escape(step.title())).append("</h3><p>").append(MLuaDocumentation.escape(step.explanation())).append("</p>").append(code(step.example())).append("</section>");
         body.append("</div>");
      }
      if (body.length() == 0) body.append("<div class=\"empty\">No registered members for this entry.</div>");
      return body.toString();
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
         body.append("<h2>").append(MLuaDocumentation.escape(contribution.title())).append("</h2><p class=\"section-note\">").append(MLuaDocumentation.escape(contribution.description())).append("</p><div class=\"cards\">");
         for (MLuaDocBuilder.GuideStep step : contribution.steps()) body.append("<section class=\"card\"><span class=\"kind\">STEP · ").append(MLuaDocumentation.escape(step.receiver())).append("</span><h3>").append(MLuaDocumentation.escape(step.title())).append("</h3><p>").append(MLuaDocumentation.escape(step.explanation())).append("</p>").append(code(step.example())).append("</section>");
         for (MLuaDocBuilder.Example example : contribution.examples()) body.append("<section class=\"card\"><span class=\"kind\">EXAMPLE</span><h3>").append(MLuaDocumentation.escape(example.title())).append("</h3><p>").append(MLuaDocumentation.escape(example.description())).append("</p>").append(code(example.code())).append("</section>");
         for (MLuaDocBuilder.Template template : contribution.templates()) body.append("<section class=\"card\"><span class=\"kind\">TEMPLATE · ").append(MLuaDocumentation.escape(template.filename())).append("</span><h3>").append(MLuaDocumentation.escape(template.title())).append("</h3><p>").append(MLuaDocumentation.escape(template.description())).append("</p>").append(code(template.code())).append("</section>");
         body.append("</div>");
      }
      return body.toString();
   }

   private static String sectionStart(MLuaDocBuilder builder, String receiver, String kind, String fallbackTitle) {
      MLuaDocBuilder.SectionDoc section = builder.sections().stream()
         .filter(item -> item.receiver().equals(receiver) && item.kind().equalsIgnoreCase(kind))
         .findFirst().orElse(null);
      String title = section == null ? fallbackTitle : section.title();
      String description = section == null ? null : section.description();
      StringBuilder html = new StringBuilder("<h2>").append(MLuaDocumentation.escape(title)).append("</h2>");
      if (description != null && !description.isBlank()) {
         html.append("<p class=\"section-note\">").append(MLuaDocumentation.escape(description)).append("</p>");
      }
      return html.append("<div class=\"cards\">").toString();
   }

   private static String apiCard(String kind, String signature, String description, String parameters,
                                 String returns, String side, String example) {
      StringBuilder card = new StringBuilder("<article class=\"card\"><span class=\"kind\">")
         .append(MLuaDocumentation.escape(kind)).append(side == null || side.isBlank() ? "" : " · " + MLuaDocumentation.escape(side))
         .append("</span><code class=\"signature\">").append(linkTypes(signature)).append("</code>");
      if (description != null && !description.isBlank()) card.append("<p>").append(MLuaDocumentation.escape(description)).append("</p>");
      if (parameters != null && !parameters.isBlank()) card.append("<p class=\"meta\"><strong>Parameters:</strong> ").append(MLuaDocumentation.escape(parameters)).append("</p>");
      if (returns != null && !returns.isBlank()) card.append("<p class=\"meta\"><strong>Returns:</strong> ").append(MLuaDocumentation.linkType(returns)).append("</p>");
      if (example != null && !example.isBlank()) card.append(code(example));
      return card.append("</article>").toString();
   }

   private static String linkTypes(String text) {
      String escaped = MLuaDocumentation.escape(text == null ? "" : text);
      for (String type : List.of("Vector2", "Vector3", "CFrame", "Color3", "UDim", "UDim2", "Player", "Entity", "Block", "World", "Chunk", "ItemStack", "Inventory")) {
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
         if (page.route().startsWith("/api/types/") || page.route().startsWith("/api/classes/")) {
            routes.put(page.title(), "#" + page.route());
         }
      }
      return routes;
   }

   private static void appendNavigation(StringBuilder html, List<Page> pages) {
      String category = "";
      for (Page page : pages) {
         if (!page.category().equals(category)) {
            category = page.category();
            html.append("<div class=\"group\">").append(MLuaDocumentation.escape(category)).append("</div>");
         }
         html.append("<a data-route=\"").append(MLuaDocumentation.escape(page.route())).append("\" data-title=\"")
            .append(MLuaDocumentation.escape((page.title() + " " + page.description()).toLowerCase())).append("\" href=\"#")
            .append(MLuaDocumentation.escape(page.route())).append("\">").append(MLuaDocumentation.escape(page.title())).append("</a>");
      }
   }

   private static Map<String, List<MLuaDocBuilder.Contribution>> contributionSections(List<MLuaDocBuilder.Contribution> contributions) {
      Map<String, List<MLuaDocBuilder.Contribution>> sections = new java.util.TreeMap<>();
      for (MLuaDocBuilder.Contribution contribution : contributions) {
         sections.computeIfAbsent(MLuaDocBuilder.sectionKey(contribution.section()), ignored -> new ArrayList<>()).add(contribution);
      }
      return sections;
   }

   private static final String SCRIPT = """
      (function(){
        var pages=[...document.querySelectorAll('.page')],links=[...document.querySelectorAll('#doc-nav a')],crumb=document.getElementById('crumb'),search=document.getElementById('doc-search');
        function route(){var value=decodeURIComponent(location.hash.slice(1)||'/home');if(!value.startsWith('/'))value='/'+value;var page=pages.find(function(p){return p.dataset.route===value})||pages[0];pages.forEach(function(p){p.hidden=p!==page});links.forEach(function(a){a.classList.toggle('active',a.dataset.route===page.dataset.route)});crumb.textContent='MLua / '+page.dataset.route.split('/').filter(Boolean).join(' / ');document.title=page.querySelector('h1').textContent+' - MLua';}
        function escapeHtml(text){return text.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
        function highlight(code){
          var source=code.textContent;
          var pattern=/(--[^\\n]*|"(?:\\\\.|[^"\\\\])*"|'(?:\\\\.|[^'\\\\])*'|\\b(?:and|break|do|else|elseif|end|false|for|function|if|in|local|nil|not|or|repeat|return|then|true|until|while|goto)\\b|\\b\\d+(?:\\.\\d+)?\\b|\\b(?:Minecraft|Enum|Require|Signal|Vector2|Vector3|CFrame|Color3|UDim|UDim2|task|wait|print|warn|pairs|ipairs|pcall|xpcall|typeof)\\b)/g;
          code.innerHTML=source.replace(pattern,function(token){var kind=token.startsWith('--')?'comment':token[0]==='"'||token[0]==="'"?'string':/^\\d/.test(token)?'number':/^(and|break|do|else|elseif|end|false|for|function|if|in|local|nil|not|or|repeat|return|then|true|until|while|goto)$/.test(token)?'keyword':'global';return '<span class="syntax-'+kind+'">'+escapeHtml(token)+'</span>';});
        }
        document.querySelectorAll('pre.code code').forEach(highlight);
        window.addEventListener('hashchange',route);
        search.addEventListener('input',function(){var q=search.value.toLowerCase();links.forEach(function(a){var page=pages.find(function(p){return p.dataset.route===a.dataset.route});var text=a.dataset.title+' '+(page?page.textContent.toLowerCase():'');a.hidden=!text.includes(q);});});
        route();
      })();
      """;

   private record Page(String route, String title, String category, String description, String body) { }
}
