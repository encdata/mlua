package dev.mlua.core.engine;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Installs the Luau-compatible standard-library extensions used by every script environment. */
final class MLuaLuauStandardLibraries {
   private static final Random SHARED_RNG = new Random();

   private MLuaLuauStandardLibraries() { }

   static void install(LuaTable globals) {
      installTableLibrary(globals.get("table").checktable());
      installStringLibrary(globals.get("string").checktable());
      globals.set("Random", randomLibrary());
      globals.set("DateTime", dateTimeLibrary());
      installBit32(globals);
   }

   private static void installTableLibrary(LuaTable table) {
      table.set("clone", new OneArgFunction() {
         public LuaValue call(LuaValue tbl) {
            LuaTable source = tbl.checktable();
            LuaTable dest = new LuaTable();
            LuaValue metatable = source.getmetatable();
            LuaValue key = LuaValue.NIL;

            while(true) {
               Varargs next = source.next(key);
               key = next.arg1();
               if (key.isnil()) {
                  if (metatable != null) dest.setmetatable(metatable);
                  return dest;
               }

               dest.rawset(key, next.arg(2));
            }
         }
      });
      table.set("freeze", new OneArgFunction() {
         public LuaValue call(LuaValue value) {
            return value.checktable().freeze();
         }
      });
      table.set("isfrozen", new OneArgFunction() {
         public LuaValue call(LuaValue value) {
            return LuaValue.valueOf(value.checktable().isfrozen());
         }
      });
      table.set("clear", new OneArgFunction() {
         public LuaValue call(LuaValue tbl) {
            LuaTable t = tbl.checktable();
            if (t.isfrozen()) throw new LuaError("attempt to modify a frozen table");
            List<LuaValue> keys = new ArrayList<>();
            LuaValue key = LuaValue.NIL;

            while(true) {
               Varargs next = t.next(key);
               key = next.arg1();
               if (key.isnil()) {
                  for (LuaValue entry : keys) t.rawset(entry, LuaValue.NIL);
                  return LuaValue.NONE;
               }
               keys.add(key);
            }
         }
      });
      table.set("find", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaTable t = args.checktable(1);
            LuaValue value = args.checkvalue(2);

            for(int i = Math.max(1, args.optint(3, 1)); !t.rawget(i).isnil(); ++i) {
               if (t.rawget(i).raweq(value)) {
                  return LuaValue.valueOf(i);
               }
            }

            return LuaValue.NIL;
         }
      });
      table.set("create", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            int n = args.checkint(1);
            if (n < 0) {
               LuaValue.argerror(1, "count must be non-negative");
            }

            LuaTable t = new LuaTable(n, 0);
            LuaValue value = args.arg(2);
            if (!value.isnil()) {
               for(int i = 1; i <= n; ++i) {
                  t.rawset(i, value);
               }
            }

            return t;
         }
      });
      table.set("pack", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaTable result = new LuaTable();
            int n = args.narg();

            for(int i = 1; i <= n; ++i) {
               result.set(i, args.arg(i));
            }

            result.set("n", n);
            return result;
         }
      });
      table.set("move", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaTable a1 = args.checktable(1);
            int f = args.checkint(2);
            int e = args.checkint(3);
            int t = args.checkint(4);
            LuaTable a2 = args.arg(5).istable() ? args.checktable(5) : a1;
            int i;
            if (a1 == a2 && t > f) {
               for(i = e; i >= f; --i) {
                  a2.set(t + (i - f), a1.get(i));
               }
            } else {
               for(i = f; i <= e; ++i) {
                  a2.set(t + (i - f), a1.get(i));
               }
            }

            return a2;
         }
      });
   }

   private static void installStringLibrary(LuaTable str) {
      str.set("split", new TwoArgFunction() {
         public LuaValue call(LuaValue text, LuaValue separator) {
            String s = text.checkjstring();
            String sep = separator.checkjstring();
            LuaTable result = new LuaTable();
            int index = 1;
            if (sep.isEmpty()) {
               for (int offset = 0; offset < s.length();) {
                  int codePoint = s.codePointAt(offset);
                  result.set(index++, LuaValue.valueOf(new String(Character.toChars(codePoint))));
                  offset += Character.charCount(codePoint);
               }
               return result;
            }

            int start;
            int pos;
            for(start = 0; (pos = s.indexOf(sep, start)) != -1; start = pos + sep.length()) {
               result.set(index++, LuaValue.valueOf(s.substring(start, pos)));
            }

            result.set(index, LuaValue.valueOf(s.substring(start)));
            return result;
         }
      });
      str.set("trim", new OneArgFunction() {
         public LuaValue call(LuaValue text) {
            return LuaValue.valueOf(text.checkjstring().trim());
         }
      });
      str.set("startswith", new TwoArgFunction() {
         public LuaValue call(LuaValue text, LuaValue prefix) {
            return LuaValue.valueOf(text.checkjstring().startsWith(prefix.checkjstring()));
         }
      });
      str.set("endswith", new TwoArgFunction() {
         public LuaValue call(LuaValue text, LuaValue suffix) {
            return LuaValue.valueOf(text.checkjstring().endsWith(suffix.checkjstring()));
         }
      });
   }

   private static LuaTable randomLibrary() {
      LuaTable lib = new LuaTable();
      lib.set("new", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            long seed = args.isnumber(1) ? args.checklong(1) : SHARED_RNG.nextLong();
            final Random rng = new Random(seed);
            LuaTable inst = new LuaTable();
            inst.set("NextNumber", new VarArgFunction() {
               public Varargs invoke(Varargs inner) {
                  double min = inner.optdouble(2, 0.0);
                  double max = inner.optdouble(3, 1.0);
                  return LuaValue.valueOf(min + (max - min) * rng.nextDouble());
               }
            });
            inst.set("NextInteger", new VarArgFunction() {
               public Varargs invoke(Varargs inner) {
                  int min = inner.checkint(2);
                  int max = inner.checkint(3);
                  if (min > max) throw new LuaError("Random.NextInteger minimum must not exceed maximum");
                  return LuaValue.valueOf((int)rng.nextLong(min, (long)max + 1));
               }
            });
            return inst;
         }
      });
      return lib;
   }

   private static LuaTable dateTimeLibrary() {
      LuaTable lib = new LuaTable();
      lib.set("now", new ZeroArgFunction() {
         public LuaValue call() {
            return dateTimeTable(Instant.now());
         }
      });
      lib.set("fromUnixTimestamp", new OneArgFunction() {
         public LuaValue call(LuaValue seconds) {
            return dateTimeTable(Instant.ofEpochSecond(seconds.checklong()));
         }
      });
      lib.set("fromUnixTimestampMillis", new OneArgFunction() {
         public LuaValue call(LuaValue millis) {
            return dateTimeTable(Instant.ofEpochMilli(millis.checklong()));
         }
      });
      return lib;
   }

   private static LuaTable dateTimeTable(Instant instant) {
      LuaTable t = new LuaTable();
      t.set("UnixTimestamp", (double)instant.getEpochSecond());
      t.set("UnixTimestampMillis", (double)instant.toEpochMilli());
      ZonedDateTime zdt = instant.atZone(ZoneId.systemDefault());
      t.set("Year", zdt.getYear());
      t.set("Month", zdt.getMonthValue());
      t.set("Day", zdt.getDayOfMonth());
      t.set("Hour", zdt.getHour());
      t.set("Minute", zdt.getMinute());
      t.set("Second", zdt.getSecond());
      t.set("DayOfWeek", zdt.getDayOfWeek().getValue());
      t.set("DayOfYear", zdt.getDayOfYear());
      t.set("ToISO", zdt.toString());
      return t;
   }

   private static void installBit32(LuaTable globals) {
      LuaTable bit32 = new LuaTable();
      bit32.set("band", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            int result = args.checkint(1);

            for(int i = 2; i <= args.narg(); ++i) {
               result &= args.checkint(i);
            }

            return LuaValue.valueOf(result);
         }
      });
      bit32.set("bor", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            int result = args.checkint(1);

            for(int i = 2; i <= args.narg(); ++i) {
               result |= args.checkint(i);
            }

            return LuaValue.valueOf(result);
         }
      });
      bit32.set("bxor", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            int result = args.checkint(1);

            for(int i = 2; i <= args.narg(); ++i) {
               result ^= args.checkint(i);
            }

            return LuaValue.valueOf(result);
         }
      });
      bit32.set("bnot", new OneArgFunction() {
         public LuaValue call(LuaValue x) {
            return LuaValue.valueOf(~x.checkint());
         }
      });
      bit32.set("btest", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            int result = args.checkint(1);

            for(int i = 2; i <= args.narg(); ++i) {
               result &= args.checkint(i);
            }

            return LuaValue.valueOf(result != 0);
         }
      });
      bit32.set("lshift", new TwoArgFunction() {
         public LuaValue call(LuaValue x, LuaValue disp) {
            int xi = x.checkint();
            int d = disp.checkint();
            if (d > -31 && d < 31) {
               return d < 0 ? LuaValue.valueOf(xi >>> -d) : LuaValue.valueOf(xi << d);
            } else {
               return LuaValue.valueOf(0);
            }
         }
      });
      bit32.set("rshift", new TwoArgFunction() {
         public LuaValue call(LuaValue x, LuaValue disp) {
            int xi = x.checkint();
            int d = disp.checkint();
            if (d > -31 && d < 31) {
               return d < 0 ? LuaValue.valueOf(xi << -d) : LuaValue.valueOf(xi >>> d);
            } else {
               return LuaValue.valueOf(0);
            }
         }
      });
      bit32.set("arshift", new TwoArgFunction() {
         public LuaValue call(LuaValue x, LuaValue disp) {
            int xi = x.checkint();
            int d = disp.checkint();
            if (d > -31 && d < 31) {
               return d < 0 ? LuaValue.valueOf(xi << -d) : LuaValue.valueOf(xi >> d);
            } else {
               return LuaValue.valueOf(xi < 0 ? -1 : 0);
            }
         }
      });
      bit32.set("lrotate", new TwoArgFunction() {
         public LuaValue call(LuaValue x, LuaValue disp) {
            int xi = x.checkint();
            int d = disp.checkint() & 31;
            return LuaValue.valueOf(xi << d | xi >>> 32 - d);
         }
      });
      bit32.set("rrotate", new TwoArgFunction() {
         public LuaValue call(LuaValue x, LuaValue disp) {
            int xi = x.checkint();
            int d = disp.checkint() & 31;
            return LuaValue.valueOf(xi >>> d | xi << 32 - d);
         }
      });
      bit32.set("extract", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            int n = args.checkint(1);
            int field = args.checkint(2);
            int width = args.optint(3, 1);
            if (width > 0 && field >= 0 && field < 32) {
               int w = Math.min(width, 32 - field);
               int mask = w == 32 ? -1 : (1 << w) - 1;
               return LuaValue.valueOf(n >>> field & mask);
            } else {
               return LuaValue.valueOf(0);
            }
         }
      });
      bit32.set("replace", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            int n = args.checkint(1);
            int v = args.checkint(2);
            int field = args.checkint(3);
            int width = args.optint(4, 1);
            if (width > 0 && field >= 0 && field < 32) {
               int w = Math.min(width, 32 - field);
               int mask = w == 32 ? -1 : (1 << w) - 1 << field;
               return LuaValue.valueOf(n & ~mask | v << field & mask);
            } else {
               return LuaValue.valueOf(n);
            }
         }
      });
      bit32.set("countlz", new OneArgFunction() {
         public LuaValue call(LuaValue x) {
            return LuaValue.valueOf(Integer.numberOfLeadingZeros(x.checkint()));
         }
      });
      bit32.set("countrz", new OneArgFunction() {
         public LuaValue call(LuaValue x) {
            return LuaValue.valueOf(Integer.numberOfTrailingZeros(x.checkint()));
         }
      });
      globals.set("bit32", bit32);
   }

}
