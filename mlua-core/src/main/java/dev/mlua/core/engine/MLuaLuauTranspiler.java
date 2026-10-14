package dev.mlua.core.engine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class MLuaLuauTranspiler {
   public String transpile(String source) {
      List<Seg> segs = this.tokenize(source);

      for(int i = 0; i < segs.size(); ++i) {
         Seg s = (Seg)segs.get(i);
         if (s.type == MLuaLuauTranspiler.SegType.CODE) {
            s.text = this.transform(s.text);
         } else if (s.type == MLuaLuauTranspiler.SegType.INTERP_STRING) {
            s.text = this.interp(s.text);
         }
      }

      StringBuilder out = new StringBuilder();
      Iterator var7 = segs.iterator();

      while(var7.hasNext()) {
         Seg s = (Seg)var7.next();
         out.append(s.text);
      }

      return out.toString();
   }

   private List<Seg> tokenize(String src) {
      List<Seg> segs = new ArrayList();
      int i = 0;
      int n = src.length();

      while(true) {
         while(true) {
            while(i < n) {
               char c = src.charAt(i);
               int eq;
               int j;
               int end;
               String close;
               if (c == '-' && i + 1 < n && src.charAt(i + 1) == '-') {
                  if (i + 2 < n && (src.charAt(i + 2) == '[' || src.charAt(i + 2) == '=')) {
                     eq = 0;
                     j = i + 2;
                     if (src.charAt(j) == '=') {
                        while(j < n && src.charAt(j) == '=') {
                           ++eq;
                           ++j;
                        }
                     }

                     if (j < n && src.charAt(j) == '[') {
                        close = "]" + "=".repeat(eq) + "]";
                        end = src.indexOf(close, j + 1);
                        if (end < 0) {
                           end = n;
                        } else {
                           end += close.length();
                        }

                        segs.add(new Seg(MLuaLuauTranspiler.SegType.COMMENT_BLOCK, src.substring(i, end)));
                        i = end;
                        continue;
                     }
                  }

                  eq = src.indexOf(10, i + 2);
                  if (eq < 0) {
                     eq = n;
                  }

                  segs.add(new Seg(MLuaLuauTranspiler.SegType.COMMENT_LINE, src.substring(i, eq)));
                  i = eq;
               } else if (c != '"' && c != '\'') {
                  if (c == '`') {
                     for(eq = i + 1; eq < n && src.charAt(eq) != '`'; ++eq) {
                        if (src.charAt(eq) == '\\') {
                           ++eq;
                        }
                     }

                     if (eq < n) {
                        ++eq;
                     }

                     segs.add(new Seg(MLuaLuauTranspiler.SegType.INTERP_STRING, src.substring(i, eq)));
                     i = eq;
                  } else {
                     if (c == '[' && i + 1 < n) {
                        eq = 0;
                        j = i + 1;
                        if (src.charAt(j) == '=') {
                           while(j < n && src.charAt(j) == '=') {
                              ++eq;
                              ++j;
                           }
                        }

                        if (j < n && src.charAt(j) == '[') {
                           close = "]" + "=".repeat(eq) + "]";
                           end = src.indexOf(close, j + 1);
                           if (end < 0) {
                              end = n;
                           } else {
                              end += close.length();
                           }

                           segs.add(new Seg(MLuaLuauTranspiler.SegType.LONG_STRING, src.substring(i, end)));
                           i = end;
                           continue;
                        }
                     }

                     StringBuilder cb;
                     for(cb = new StringBuilder(); i < n; ++i) {
                        char cc = src.charAt(i);
                        if (cc == '"' || cc == '\'' || cc == '`' || cc == '-' && i + 1 < n && src.charAt(i + 1) == '-') {
                           break;
                        }

                        if (cc == '[' && i + 1 < n) {
                           int nx = i + 1;
                           if (src.charAt(nx) == '[' || src.charAt(nx) == '=') {
                              break;
                           }
                        }

                        cb.append(cc);
                     }

                     if (cb.length() > 0) {
                        segs.add(new Seg(MLuaLuauTranspiler.SegType.CODE, cb.toString()));
                     }
                  }
               } else {
                  for(eq = i + 1; eq < n && src.charAt(eq) != c; ++eq) {
                     if (src.charAt(eq) == '\\') {
                        ++eq;
                     }
                  }

                  if (eq < n) {
                     ++eq;
                  }

                  segs.add(new Seg(MLuaLuauTranspiler.SegType.STRING, src.substring(i, eq)));
                  i = eq;
               }
            }

            return segs;
         }
      }
   }

   private String transform(String code) {
      StringBuilder out = new StringBuilder();
      int i = 0;
      int n = code.length();
      while (i < n) {
         int ifStart = this.findIfExpr(code, i);
         if (ifStart >= 0) {
            int exprEnd = this.findIfExprEnd(code, ifStart);
            String rewritten = exprEnd < 0 ? null : this.rewriteIfExpression(code.substring(ifStart, exprEnd));
            if (rewritten != null) {
               out.append(code, i, ifStart).append(this.transform(rewritten));
               i = exprEnd;
               continue;
            }
         }

         if (code.charAt(i) == ':' && i + 1 < n && code.charAt(i + 1) == ':' && i + 2 < n && code.charAt(i + 2) != ':') {
            int typeStart = this.skipWs(code, i + 2);
            if (typeStart < n && (Character.isJavaIdentifierStart(code.charAt(typeStart)) || code.charAt(typeStart) == '(' || code.charAt(typeStart) == '{')) {
               i = this.readType(code, typeStart, n);
               continue;
            }
         }

         int typeColon = this.findTypeColon(code, i);
         if (typeColon >= 0) {
            out.append(code, i, typeColon);
            i = this.skipTypeAnnotation(code, typeColon);
            continue;
         }

         int after;
         if (code.startsWith("export", i) && this.isKw(code, i, "export")) {
            after = this.skipWs(code, i + 6);
            if (after < n && this.isKw(code, after, "type")) {
               int lineEnd = code.indexOf('\n', after);
               i = lineEnd < 0 ? n : lineEnd;
               continue;
            }
         }

         if (this.isKw(code, i, "type")) {
            after = this.skipWs(code, i + 4);
            if (after >= n || code.charAt(after) != '=') {
               int lineEnd = code.indexOf('\n', i + 4);
               i = lineEnd < 0 ? n : lineEnd;
               continue;
            }
         }

         if (this.isKw(code, i, "function")) {
            int nameStart = this.skipWs(code, i + 8);
            out.append(code, i, nameStart);
            int genericStart;
            if (nameStart < n && Character.isJavaIdentifierStart(code.charAt(nameStart))) {
               int nameEnd = this.skipIdent(code, nameStart);
               out.append(code, nameStart, nameEnd);
               genericStart = this.skipWs(code, nameEnd);
            } else {
               genericStart = nameStart;
            }
            if (genericStart < n && code.charAt(genericStart) == '<') {
               int genericEnd = this.findMatching(code, genericStart, '<', '>');
               if (genericEnd >= 0) {
                  i = genericEnd + 1;
                  continue;
            }
            }
            i = genericStart;
            continue;
         }

         out.append(code.charAt(i++));
      }
      return out.toString();
   }

   private boolean isKw(String s, int pos, String kw) {
      if (pos + kw.length() > s.length()) {
         return false;
      } else if (!s.regionMatches(pos, kw, 0, kw.length())) {
         return false;
      } else if (pos > 0 && Character.isJavaIdentifierPart(s.charAt(pos - 1))) {
         return false;
      } else {
         int after = pos + kw.length();
         return after >= s.length() || !Character.isJavaIdentifierPart(s.charAt(after));
      }
   }

   private int skipWs(String s, int pos) {
      while(pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
         ++pos;
      }

      return pos;
   }

   private int skipIdent(String s, int pos) {
      while(pos < s.length() && (Character.isJavaIdentifierPart(s.charAt(pos)) || s.charAt(pos) == '.' || s.charAt(pos) == ':')) {
         ++pos;
      }

      return pos;
   }

   private int findMatching(String s, int from, char open, char close) {
      int depth = 1;

      for(int i = from + 1; i < s.length(); ++i) {
         if (s.charAt(i) == open) {
            ++depth;
         } else if (s.charAt(i) == close) {
            --depth;
            if (depth == 0) {
               return i;
            }
         }
      }

      return -1;
   }

   private int findIfExpr(String code, int from) {
      for(int i = from; i < code.length() - 2; ++i) {
         if (this.isKw(code, i, "if")) {
            int j;
            for(j = i - 1; j >= from && Character.isWhitespace(code.charAt(j)); --j) {
            }

            if (j >= from && code.charAt(j) == '=') {
               return i;
            }
         }
      }

      return -1;
   }

   private String extractVar(String code, int start, int eqEnd) {
      int j;
      for(j = eqEnd - 1; j >= start && Character.isWhitespace(code.charAt(j)); --j) {
      }

      if (j < start) {
         return "";
      } else if (!Character.isJavaIdentifierStart(code.charAt(j))) {
         return "";
      } else {
         int k;
         for(k = j; k >= start && (Character.isJavaIdentifierPart(code.charAt(k)) || code.charAt(k) == '.'); --k) {
         }

         return code.substring(k + 1, j + 1);
      }
   }

   private int findEndOfExpr(String code, int from) {
      int i = this.skipWs(code, from);
      if (i >= code.length()) {
         return code.length();
      } else if (code.charAt(i) != ';' && code.charAt(i) != ')' && code.charAt(i) != ',') {
         int end = code.indexOf(10, i);
         return end < 0 ? code.length() : end;
      } else {
         return i + 1;
      }
   }

   private int findIfExprEnd(String code, int ifPos) {
      int depthParen = 0;
      int depthBracket = 0;
      int depthBrace = 0;

      for(int i = ifPos; i < code.length(); ++i) {
         char c = code.charAt(i);
         if (c == '(') {
            ++depthParen;
         } else if (c == ')') {
            depthParen = Math.max(0, depthParen - 1);
         } else if (c == '[') {
            ++depthBracket;
         } else if (c == ']') {
            depthBracket = Math.max(0, depthBracket - 1);
         } else if (c == '{') {
            ++depthBrace;
         } else if (c == '}') {
            depthBrace = Math.max(0, depthBrace - 1);
         } else if (depthParen == 0 && depthBracket == 0 && depthBrace == 0 && this.isKw(code, i, "end")) {
            return i + 3;
         }
      }

      return -1;
   }

   private String rewriteIfExpression(String expr) {
      String body = this.rewriteIfExpressionBody(expr.trim());
      if (body == null) {
         return null;
      } else {
         return "(function() " + body + " end)()";
      }
   }

   private String rewriteIfExpressionBody(String expr) {
      if (!expr.startsWith("if")) {
         return null;
      } else {
         return this.rewriteIfExpressionTail(expr, true);
      }
   }

   private String rewriteIfExpressionTail(String expr, boolean firstBranch) {
      String keyword = firstBranch ? "if" : "elseif";
      if (!expr.startsWith(keyword)) {
         return null;
      } else {
         int thenPos = this.findTopLevelKeyword(expr, keyword.length(), "then");
         if (thenPos < 0) {
            return null;
         } else {
            String cond = expr.substring(keyword.length(), thenPos).trim();
            int branchStart = thenPos + 4;
            int elseifPos = this.findTopLevelKeyword(expr, branchStart, "elseif");
            int elsePos = this.findTopLevelKeyword(expr, branchStart, "else");
            int endPos = this.findTopLevelKeyword(expr, branchStart, "end");
            int nextPos = this.minPositive(elseifPos, elsePos, endPos);
            if (nextPos < 0) {
               return null;
            } else {
               String branchExpr = expr.substring(branchStart, nextPos).trim();
               if (elseifPos >= 0 && elseifPos == nextPos) {
                  String rest = this.rewriteIfExpressionTail(expr.substring(elseifPos).trim(), false);
                  return rest == null ? null : keyword + " " + cond + " then return " + branchExpr + " " + rest;
               } else if (elsePos >= 0 && elsePos == nextPos) {
                  int finalEnd = this.findTopLevelKeyword(expr, elsePos + 4, "end");
                  if (finalEnd < 0) {
                     return null;
                  } else {
                     String elseExpr = expr.substring(elsePos + 4, finalEnd).trim();
                     return keyword + " " + cond + " then return " + branchExpr + " else return " + elseExpr + " end";
                  }
               } else {
                  return null;
               }
            }
         }
      }
   }

   private int findTopLevelKeyword(String expr, int from, String keyword) {
      int depthParen = 0;
      int depthBracket = 0;
      int depthBrace = 0;

      for(int i = from; i < expr.length(); ++i) {
         char c = expr.charAt(i);
         if (c == '(') {
            ++depthParen;
         } else if (c == ')') {
            depthParen = Math.max(0, depthParen - 1);
         } else if (c == '[') {
            ++depthBracket;
         } else if (c == ']') {
            depthBracket = Math.max(0, depthBracket - 1);
         } else if (c == '{') {
            ++depthBrace;
         } else if (c == '}') {
            depthBrace = Math.max(0, depthBrace - 1);
         } else if (depthParen == 0 && depthBracket == 0 && depthBrace == 0 && this.isKw(expr, i, keyword)) {
            return i;
         }
      }

      return -1;
   }

   private int minPositive(int... values) {
      int result = -1;
      int[] var3 = values;
      int var4 = values.length;

      for(int var5 = 0; var5 < var4; ++var5) {
         int value = var3[var5];
         if (value >= 0 && (result < 0 || value < result)) {
            result = value;
         }
      }

      return result;
   }

   private int findTypeColon(String code, int from) {
      for(int i = from; i < code.length(); ++i) {
         if (code.charAt(i) == ':' && i + 1 < code.length()) {
            if (code.charAt(i + 1) == ':') {
               ++i;
            } else {
               int after = this.skipWs(code, i + 1);
               if (after < code.length() && Character.isJavaIdentifierStart(code.charAt(after))) {
                  int identEnd = this.skipIdent(code, after);
                  int afterIdent = this.skipWs(code, identEnd);
                  if (afterIdent >= code.length() || code.charAt(afterIdent) != '(') {
                     int before;
                     for(before = i - 1; before >= from && Character.isWhitespace(code.charAt(before)); --before) {
                     }

                     if (before >= from && (Character.isJavaIdentifierPart(code.charAt(before)) || code.charAt(before) == ')' || code.charAt(before) == ']')) {
                        return i;
                     }
                  }
               }
            }
         }
      }

      return -1;
   }

   private int skipTypeAnnotation(String code, int colonPos) {
      int i;
      for(i = colonPos + 1; i < code.length() && Character.isWhitespace(code.charAt(i)); ++i) {
      }

      return this.readType(code, i, code.length());
   }

   private int readType(String code, int start, int n) {
      int i = start;
      if (start >= n) {
         return start;
      } else {
         char c = code.charAt(start);
         int ws;
         int gt;
         if (c == '(') {
            ws = this.findMatching(code, start, '(', ')');
            if (ws >= 0) {
               i = ws + 1;
               gt = this.skipWs(code, i);
               if (gt + 1 < n && code.charAt(gt) == '-' && code.charAt(gt + 1) == '>') {
                  i = gt + 2;
                  i = this.skipWs(code, i);
                  i = this.readType(code, i, n);
                  return i;
               } else {
                  return i;
               }
            } else {
               while(i < n && code.charAt(i) != '\n') {
                  ++i;
               }

               return i;
            }
         } else if (c == '{') {
            ws = this.findMatching(code, start, '{', '}');
            if (ws >= 0) {
               return ws + 1;
            } else {
               while(i < n && code.charAt(i) != '\n') {
                  ++i;
               }

               return i;
            }
         } else if (!Character.isJavaIdentifierStart(c)) {
            return start;
         } else {
            while(i < n && (Character.isJavaIdentifierPart(code.charAt(i)) || code.charAt(i) == '.' || code.charAt(i) == '?' || code.charAt(i) == '|' || code.charAt(i) == '&')) {
               if (code.charAt(i) == '?' && i + 1 < n && !Character.isJavaIdentifierPart(code.charAt(i + 1))) {
                  ++i;
                  break;
               }

               ++i;
            }

            ws = this.skipWs(code, i);
            if (ws < n && code.charAt(ws) == '<') {
               gt = this.findMatching(code, ws, '<', '>');
               if (gt >= 0) {
                  i = gt + 1;
               }
            }

            return i;
         }
      }
   }

   private String interp(String raw) {
      String inner = raw.length() >= 2 ? raw.substring(1, raw.length() - 1) : "";
      List<String> parts = new ArrayList<>();
      StringBuilder literal = new StringBuilder();
      for (int i = 0; i < inner.length();) {
         char current = inner.charAt(i);
         if (current == '\\' && i + 1 < inner.length()) {
            char escaped = inner.charAt(i + 1);
            switch (escaped) {
               case 'n' -> literal.append('\n');
               case 'r' -> literal.append('\r');
               case 't' -> literal.append('\t');
               case '`', '{', '}', '\\' -> literal.append(escaped);
               default -> literal.append(escaped);
            }
            i += 2;
         } else if (current == '{') {
            int close = this.findInterpolationClose(inner, i);
            if (close < 0) {
               literal.append(current);
               ++i;
               continue;
            }
            if (literal.length() > 0) {
               parts.add(this.luaString(literal.toString()));
               literal.setLength(0);
            }
            String expression = inner.substring(i + 1, close).trim();
            if (!expression.isEmpty()) parts.add("tostring(" + this.transform(expression) + ")");
            else literal.append("{}");
            i = close + 1;
         } else {
            literal.append(current);
            ++i;
         }
      }
      if (literal.length() > 0) parts.add(this.luaString(literal.toString()));
      return parts.isEmpty() ? "\"\"" : String.join(" .. ", parts);
   }

   private int findInterpolationClose(String source, int open) {
      int depth = 1;
      char quote = 0;
      for (int i = open + 1; i < source.length(); ++i) {
         char current = source.charAt(i);
         if (quote != 0) {
            if (current == '\\') ++i;
            else if (current == quote) quote = 0;
            continue;
         }
         if (current == '\'' || current == '\"') {
            quote = current;
         } else if (current == '-' && i + 1 < source.length() && source.charAt(i + 1) == '-') {
            int lineEnd = source.indexOf('\n', i + 2);
            if (lineEnd < 0) return -1;
            i = lineEnd;
         } else if (current == '{') {
            ++depth;
         } else if (current == '}' && --depth == 0) {
            return i;
         }
      }
      return -1;
   }

   private String luaString(String value) {
      StringBuilder escaped = new StringBuilder(value.length() + 2).append('\"');
      for (int i = 0; i < value.length(); ++i) {
         char current = value.charAt(i);
         switch (current) {
            case '\\' -> escaped.append("\\\\");
            case '\"' -> escaped.append("\\\"");
            case '\n' -> escaped.append("\\n");
            case '\r' -> escaped.append("\\r");
            case '\t' -> escaped.append("\\t");
            default -> escaped.append(current);
         }
      }
      return escaped.append('\"').toString();
   }

   private static final class Seg {
      SegType type;
      String text;

      Seg(SegType t, String tx) {
         this.type = t;
         this.text = tx;
      }
   }

   private static enum SegType {
      CODE,
      STRING,
      LONG_STRING,
      COMMENT_LINE,
      COMMENT_BLOCK,
      INTERP_STRING;

      // $FF: synthetic method
      private static SegType[] $values() {
         return new SegType[]{CODE, STRING, LONG_STRING, COMMENT_LINE, COMMENT_BLOCK, INTERP_STRING};
      }
   }
}
