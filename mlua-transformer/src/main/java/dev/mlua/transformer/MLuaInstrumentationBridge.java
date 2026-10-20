package dev.mlua.transformer;

import java.lang.instrument.Instrumentation;

public final class MLuaInstrumentationBridge {
   private static volatile Instrumentation instrumentation;

   private MLuaInstrumentationBridge() {
   }

   public static void setInstrumentation(Instrumentation inst) {
      instrumentation = inst;
   }

   public static Instrumentation getInstrumentation() {
      return instrumentation;
   }
}
