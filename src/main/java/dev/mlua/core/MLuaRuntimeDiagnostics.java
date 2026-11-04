package dev.mlua.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Bounded runtime diagnostics waiting for a client or server delivery point. */
final class MLuaRuntimeDiagnostics {
   private static final int MAX_MESSAGES = 8;

   private final Object lock = new Object();
   private final Deque<String> pendingServer = new ArrayDeque<>();
   private final Deque<String> pendingClient = new ArrayDeque<>();
   private final Deque<String> recentServer = new ArrayDeque<>();
   private final Deque<String> recentClient = new ArrayDeque<>();

   String recordFailure(String summary, Throwable throwable) {
      String detail = formatThrowableMessage(throwable);
      String message = "[MLua] " + summary + (detail.isBlank() ? "" : ": " + detail);
      synchronized (this.lock) {
         push(this.pendingServer, message);
         push(this.pendingClient, message);
         push(this.recentServer, message);
         push(this.recentClient, message);
      }
      return message;
   }

   void recordClientMessage(String message) {
      synchronized (this.lock) {
         push(this.pendingClient, message);
      }
   }

   List<String> recentServerMessages() {
      synchronized (this.lock) {
         return new ArrayList<>(this.recentServer);
      }
   }

   List<String> drainServerMessages() {
      return drain(this.pendingServer);
   }

   List<String> drainClientMessages() {
      return drain(this.pendingClient);
   }

   private List<String> drain(Deque<String> queue) {
      synchronized (this.lock) {
         List<String> messages = new ArrayList<>(queue);
         queue.clear();
         return messages;
      }
   }

   private static void push(Deque<String> queue, String message) {
      if (!queue.isEmpty() && message.equals(queue.peekLast())) return;
      queue.addLast(message);
      while (queue.size() > MAX_MESSAGES) queue.removeFirst();
   }

   private static String formatThrowableMessage(Throwable throwable) {
      if (throwable == null) return "";
      String message = throwable.getMessage();
      if (message == null || message.isBlank()) return throwable.getClass().getSimpleName();
      return message.replace('\n', ' ').replace('\r', ' ').trim();
   }
}
