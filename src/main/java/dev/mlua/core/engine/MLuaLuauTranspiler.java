package dev.mlua.core.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

public final class MLuaLuauTranspiler {
   public String transpile(String source) {
      List<Seg> segs = this.tokenize(source);

      for(int i = 0; i < segs.size(); ++i) {
         Seg s = (Seg)segs.get(i);
         if (s.type == MLuaLuauTranspiler.SegType.CODE) {
            s.text = this.transform(s.text);
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
      Deque<Integer> loopStack = new ArrayDeque();
      int loopIdGen = 0;

      while(true) {
         while(true) {
            while(i < n) {
               int ca;
               int ie;
               int ta;
               int nameEnd;
               if (this.isKw(code, i, "for")) {
                  ca = this.skipIdents(code, i + 3);
                  ie = code.indexOf(61, ca);
                  ta = code.indexOf("in", ca);
                  boolean isNumeric = ie > 0 && (ta < 0 || ie < ta);
                  if (isNumeric) {
                     ++loopIdGen;
                     loopStack.push(loopIdGen);
                     out.append(code, i, ca);
                     nameEnd = ca;

                     while(true) {
                        while(nameEnd < n && code.charAt(nameEnd) != '=') {
                           if (code.charAt(nameEnd) == ':') {
                              ++nameEnd;

                              while(nameEnd < n && Character.isWhitespace(code.charAt(nameEnd))) {
                                 ++nameEnd;
                              }

                              while(nameEnd < n && (Character.isJavaIdentifierPart(code.charAt(nameEnd)) || code.charAt(nameEnd) == '.')) {
                                 ++nameEnd;
                              }
                           } else {
                              out.append(code.charAt(nameEnd));
                              ++nameEnd;
                           }
                        }

                        out.append(code, nameEnd, n);
                        i = n;
                        break;
                     }
                  } else {
                     ++loopIdGen;
                     loopStack.push(loopIdGen);
                     out.append(code, i, ca);
                     nameEnd = ca;

                     while(nameEnd < n && !code.substring(nameEnd).startsWith("in")) {
                        if (code.charAt(nameEnd) == ':' && nameEnd + 1 < n) {
                           nameEnd = this.skipTypeAnnotation(code, nameEnd);
                        } else {
                           out.append(code.charAt(nameEnd));
                           ++nameEnd;
                        }
                     }

                     out.append(code, nameEnd, n);
                     i = n;
                  }
               } else if (this.isKw(code, i, "while")) {
                  ++loopIdGen;
                  loopStack.push(loopIdGen);
                  out.append(code, i, i + 5);
                  i += 5;
               } else if (this.isKw(code, i, "repeat")) {
                  ++loopIdGen;
                  loopStack.push(loopIdGen);
                  out.append(code, i, i + 6);
                  i += 6;
               } else if (this.isKw(code, i, "end")) {
                  if (!loopStack.isEmpty()) {
                     ca = (Integer)loopStack.pop();
                     out.append("::__mlc_").append(ca).append("::");
                  }

                  out.append("end");
                  i += 3;
               } else if (this.isKw(code, i, "until")) {
                  if (!loopStack.isEmpty()) {
                     ca = (Integer)loopStack.pop();
                     out.append("::__mlc_").append(ca).append("::").append('\n');
                  }

                  out.append(code, i, i + 5);
                  i += 5;
               } else if (this.isKw(code, i, "continue")) {
                  if (!loopStack.isEmpty()) {
                     out.append("goto __mlc_").append(loopStack.peek());
                  } else {
                     out.append("-- continue (no loop context)");
                  }

                  i += 8;
               } else {
                  ca = this.findCompound(code, i);
                  int after;
                  if (ca >= 0) {
                     String lhs = this.extractLHS(code, ca);
                     char var10000 = code.charAt(ca);
                     String op = "" + var10000;
                     after = ca + 1;
                     if (code.charAt(ca) == '.' && after < n && code.charAt(after) == '.') {
                        op = "..";
                        ++after;
                     }

                     ++after;
                     out.append(lhs).append(" = ").append(lhs).append(' ').append(op).append(' ');

                     for(i = after; i < n && Character.isWhitespace(code.charAt(i)); ++i) {
                        out.append(code.charAt(i));
                     }

                     out.append(code, i, n);
                     i = n;
                  } else {
                     ie = this.findIfExpr(code, i);
                     int afterName;
                     int gt;
                     if (ie >= 0) {
                        for(ta = ie - 1; ta > i && Character.isWhitespace(code.charAt(ta - 1)); --ta) {
                        }

                        String prefix = code.substring(i, ie);
                        int exprEnd = this.findIfExprEnd(code, ie);
                        if (exprEnd < 0) {
                           out.append(code.charAt(i));
                           ++i;
                        } else {
                           String rewritten = this.rewriteIfExpression(code.substring(ie, exprEnd));
                           if (rewritten == null) {
                              out.append(code.charAt(i));
                              ++i;
                           } else {
                              out.append(prefix).append(rewritten);
                              i = exprEnd;
                           }
                        }
                     } else {
                        if (code.charAt(i) == ':' && i + 1 < n && code.charAt(i + 1) == ':' && i + 2 < n && code.charAt(i + 2) != ':') {
                           ta = this.skipWs(code, i + 2);
                           if (ta < n && (Character.isJavaIdentifierStart(code.charAt(ta)) || code.charAt(ta) == '(' || code.charAt(ta) == '{')) {
                              after = this.readType(code, ta, n);
                              i = after;
                              continue;
                           }
                        }

                        ta = this.findTypeColon(code, i);
                        if (ta >= 0) {
                           out.append(code, i, ta);
                           i = this.skipTypeAnnotation(code, ta);
                        } else {
                           if (code.startsWith("export", i) && this.isKw(code, i, "export")) {
                              after = this.skipWs(code, i + 6);
                              if (after < n && code.startsWith("type", after) && this.isKw(code, after, "type")) {
                                 nameEnd = code.indexOf(10, after);
                                 if (nameEnd < 0) {
                                    nameEnd = n;
                                 }

                                 i = nameEnd;
                                 continue;
                              }
                           }

                           if (code.startsWith("type", i) && this.isKw(code, i, "type")) {
                              after = this.skipWs(code, i + 4);
                              if (after >= n || code.charAt(after) != '=') {
                                 nameEnd = code.indexOf(10, i + 4);
                                 if (nameEnd < 0) {
                                    nameEnd = n;
                                 }

                                 i = nameEnd;
                                 continue;
                              }
                           }

                           if (code.startsWith("function", i) && this.isKw(code, i, "function")) {
                              after = this.skipWs(code, i + 8);
                              out.append(code, i, after);
                              if (after < n && Character.isJavaIdentifierStart(code.charAt(after))) {
                                 nameEnd = this.skipIdent(code, after);
                                 out.append(code, after, nameEnd);
                                 afterName = this.skipWs(code, nameEnd);
                                 if (afterName < n && code.charAt(afterName) == '<') {
                                    gt = this.findMatching(code, afterName, '<', '>');
                                    if (gt >= 0) {
                                       i = gt + 1;
                                       continue;
                                    }
                                 }

                                 i = afterName;
                              } else {
                                 i = after;
                              }
                           } else {
                              out.append(code.charAt(i));
                              ++i;
                           }
                        }
                     }
                  }
               }
            }

            return out.toString();
         }
      }
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

   private int skipIdents(String s, int pos) {
      while(true) {
         if (pos < s.length()) {
            char c = s.charAt(pos);
            if (Character.isJavaIdentifierPart(c) || c == '.' || c == ',' || c == ' ') {
               ++pos;
               continue;
            }
         }

         return pos;
      }
   }

   private int findKw(String s, int from, String kw) {
      for(int i = from; i <= s.length() - kw.length(); ++i) {
         if (this.isKw(s, i, kw)) {
            return i;
         }
      }

      return -1;
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

   private int findCompound(String code, int from) {
      for(int i = from; i < code.length(); ++i) {
         char c = code.charAt(i);
         if (c == '.' && i + 2 < code.length() && code.charAt(i + 1) == '.' && code.charAt(i + 2) == '=' && this.hasValidLHS(code, i)) {
            return i;
         }

         if ("+-*/%^".indexOf(c) >= 0 && i + 1 < code.length() && code.charAt(i + 1) == '=') {
            if (c == '/' && i + 2 < code.length() && code.charAt(i + 2) == '/') {
               continue;
            }

            if (this.hasValidLHS(code, i)) {
               return i;
            }
         }

         if (c == '/' && i + 2 < code.length() && code.charAt(i + 1) == '/' && code.charAt(i + 2) == '=' && this.hasValidLHS(code, i)) {
            return i;
         }
      }

      return -1;
   }

   private boolean hasValidLHS(String code, int opPos) {
      int j;
      for(j = opPos - 1; j >= 0 && Character.isWhitespace(code.charAt(j)); --j) {
      }

      if (j < 0) {
         return false;
      } else {
         char c = code.charAt(j);
         return Character.isJavaIdentifierStart(c) || c == ')' || c == ']' || c == '}';
      }
   }

   private String extractLHS(String code, int opPos) {
      int end;
      for(end = opPos - 1; end >= 0 && Character.isWhitespace(code.charAt(end)); --end) {
      }

      if (end < 0) {
         return "";
      } else {
         int start;
         for(start = end; start >= 0; --start) {
            char c = code.charAt(start);
            if (Character.isJavaIdentifierPart(c) || c == '.' || c == ':') {
               continue;
            }

            if (c == ')' || c == ']' || c == '}') {
               start = this.findMatchingOpenBackward(code, start) - 1;
               if (start < -1) {
                  return code.substring(0, end + 1);
               }
               continue;
            }

            break;
         }

         return code.substring(start + 1, end + 1);
      }
   }

   private int findMatchingOpenBackward(String code, int closePos) {
      Deque<Character> stack = new ArrayDeque<>();

      for(int i = closePos; i >= 0; --i) {
         char c = code.charAt(i);
         if (c == ')' || c == ']' || c == '}') {
            stack.push(c);
         } else if ((c == '(' || c == '[' || c == '{') && !stack.isEmpty() && this.matchesBracket(c, (Character)stack.peek())) {
            stack.pop();
            if (stack.isEmpty()) {
               return i;
            }
         }
      }

      return -1;
   }

   private boolean matchesBracket(char open, char close) {
      return open == '(' && close == ')' || open == '[' && close == ']' || open == '{' && close == '}';
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
      String inner = raw.substring(1, raw.length() - 1);
      StringBuilder out = new StringBuilder();
      boolean first = true;
      int i = 0;
      int n = inner.length();

      while(true) {
         while(i < n) {
            if (inner.charAt(i) == '{') {
               int depth = 1;

               int j;
               for(j = i + 1; j < n && depth > 0; ++j) {
                  if (inner.charAt(j) == '{') {
                     ++depth;
                  } else if (inner.charAt(j) == '}') {
                     --depth;
                  }
               }

               String expr = inner.substring(i + 1, j - 1);
               if (first) {
                  out.append('"');
                  first = false;
               } else {
                  out.append(" .. ");
               }

               out.append(expr);
               i = j;
            } else if (inner.charAt(i) == '\\' && i + 1 < n) {
               if (first) {
                  out.append('"');
                  first = false;
               }

               out.append('\\');
               ++i;
               if ("nt\"\\`".indexOf(inner.charAt(i)) >= 0) {
                  out.append(inner.charAt(i));
               } else {
                  out.append(inner.charAt(i));
               }

               ++i;
            } else {
               if (first) {
                  out.append('"');
                  first = false;
               }

               if (inner.charAt(i) == '"') {
                  out.append("\\\"");
               } else {
                  out.append(inner.charAt(i));
               }

               ++i;
            }
         }

         if (first) {
            out.append("\"\"");
         } else {
            out.append('"');
         }

         return out.toString();
      }
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
