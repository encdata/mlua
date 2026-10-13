package dev.mlua.agent;

import com.sun.tools.attach.VirtualMachine;

public final class MLuaAgentAttacher {
   private MLuaAgentAttacher() {
   }

   public static void main(String[] args) throws Exception {
      if (args.length < 2) {
         throw new IllegalArgumentException("Expected <pid> <agent-jar>");
      }

      VirtualMachine vm = VirtualMachine.attach(args[0]);
      try {
         vm.loadAgent(args[1]);
      } finally {
         vm.detach();
      }
   }
}
