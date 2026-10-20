package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaLogger;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Version-neutral state machine for scripted entity AI definitions. */
public final class MLuaScriptedEntityAI {
    public static final int MAX_TRANSITIONS_PER_REQUEST = 16;

    private String state;
    private int ticksInState;
    private boolean transitioning;
    private String queuedState;
    private boolean transitionLimitReported;

    public String state() {
        return this.state;
    }

    public int ticksInState() {
        return this.ticksInState;
    }

    public void tickState() {
        if (this.state != null) this.ticksInState++;
    }

    public void clearState() {
        this.state = null;
        this.ticksInState = 0;
    }

    public void restore(String state, int ticksInState) {
        this.state = state;
        this.ticksInState = Math.max(0, ticksInState);
    }

    public void resetTransitionLimit() {
        this.transitionLimitReported = false;
    }

    public void tick(LuaValue definition, CallbackHost callbacks) {
        if (definition.isfunction()) {
            callbacks.invokeDefinition(definition, callbacks.entity());
            return;
        }
        if (!definition.istable()) {
            throw new LuaError("[MLua] Entity AI must be a function or a state table.");
        }

        LuaTable config = definition.checktable();
        LuaTable states = config.get("states").checktable();
        if (this.state == null || !isState(states.get(this.state))) {
            this.transition(config, config.get("initial").checkjstring(), definition, callbacks);
        }

        LuaValue stateDefinition = states.get(this.state);
        LuaValue update = stateDefinition.isfunction() ? stateDefinition : stateDefinition.get("update");
        if (update.isnil()) return;
        if (!update.isfunction()) {
            throw new LuaError("[MLua] AI state '" + this.state + "' update must be a function.");
        }

        String stateAtStart = this.state;
        LuaTable context = this.context(callbacks);
        LuaValue result = this.invokeStateUpdate(definition, update, callbacks.entity(), context,
            stateAtStart, callbacks);
        if (result.isnil() || !stateAtStart.equals(this.state)) return;
        if (!result.isstring() || result.tojstring().isBlank()) {
            this.disable(callbacks, "aiState:" + this.state + ":update",
                new LuaError("[MLua] AI state update must return nil or a non-empty declared state name."));
            return;
        }

        String nextState = result.tojstring();
        if (nextState.length() > MLuaBehaviorRegistry.MAX_BEHAVIOR_NAME_LENGTH
            || !isState(states.get(nextState))) {
            this.disable(callbacks, "aiState:" + this.state + ":update",
                new LuaError("[MLua] AI state update returned undeclared state '" + nextState + "'."));
            return;
        }
        if (!nextState.equals(this.state)) this.transition(config, nextState, definition, callbacks);
    }

    public void setState(LuaTable config, String state, LuaValue definition, CallbackHost callbacks) {
        LuaTable states = config.get("states").checktable();
        if (!isState(states.get(state))) throw new LuaError("[MLua] Unknown scripted AI state: " + state);
        callbacks.clearFailures(state);
        this.transition(config, state, definition, callbacks);
    }

    private LuaValue invokeStateUpdate(LuaValue definition, LuaValue callback, LuaValue entity,
            LuaTable context, String state, CallbackHost callbacks) {
        String event = "aiState:" + state + ":update";
        if (!callbacks.shouldInvoke(event, callback)) return LuaValue.NIL;
        try {
            return callbacks.invokeState(definition, callback, entity, context).arg1();
        } catch (Exception failure) {
            this.disable(callbacks, event, failure);
            return LuaValue.NIL;
        }
    }

    private void transition(LuaTable config, String nextState, LuaValue definition, CallbackHost callbacks) {
        LuaTable states = config.get("states").checktable();
        if (!isState(states.get(nextState))) throw new LuaError("[MLua] Unknown scripted AI state: " + nextState);
        if (this.transitioning) {
            this.queuedState = nextState;
            return;
        }

        this.transitioning = true;
        String requestedState = nextState;
        int transitions = 0;
        try {
            while (requestedState != null) {
                if (++transitions > MAX_TRANSITIONS_PER_REQUEST) {
                    this.queuedState = null;
                    if (!this.transitionLimitReported) {
                        this.transitionLimitReported = true;
                        MLuaLogger.LOGGER.warn("MLua scripted entity '{}' exceeded the limit of {} queued AI state transitions; remaining transition requests were discarded",
                            callbacks.behaviorKey(), MAX_TRANSITIONS_PER_REQUEST);
                    }
                    break;
                }
                this.applyTransition(states, requestedState, definition, callbacks);
                requestedState = this.queuedState;
                this.queuedState = null;
            }
        } finally {
            this.queuedState = null;
            this.transitioning = false;
        }
    }

    private void applyTransition(LuaTable states, String nextState, LuaValue definition, CallbackHost callbacks) {
        LuaValue next = states.get(nextState);
        if (!isState(next)) throw new LuaError("[MLua] Unknown scripted AI state: " + nextState);

        LuaValue entity = callbacks.entity();
        if (this.state != null) {
            String previousState = this.state;
            LuaValue previous = states.get(previousState);
            LuaValue exit = previous.istable() ? previous.get("onExit") : LuaValue.NIL;
            if (exit.isfunction()) {
                this.invokeTransitionCallback(definition, exit, entity, this.context(callbacks), previousState,
                    "onExit", callbacks);
            }
        }

        this.state = nextState;
        this.ticksInState = 0;
        LuaValue enter = next.istable() ? next.get("onEnter") : LuaValue.NIL;
        if (enter.isfunction()) {
            this.invokeTransitionCallback(definition, enter, entity, this.context(callbacks), nextState,
                "onEnter", callbacks);
        }
    }

    private void invokeTransitionCallback(LuaValue definition, LuaValue callback, LuaValue entity,
            LuaTable context, String state, String hook, CallbackHost callbacks) {
        String event = "aiState:" + state + ":" + hook;
        if (!callbacks.shouldInvoke(event, callback)) return;
        try {
            callbacks.invokeState(definition, callback, entity, context);
        } catch (Exception failure) {
            this.disable(callbacks, event, failure);
        }
    }

    private LuaTable context(CallbackHost callbacks) {
        LuaTable context = new LuaTable();
        context.set("State", this.state == null ? LuaValue.NIL : LuaValue.valueOf(this.state));
        context.set("TicksInState", this.ticksInState);
        context.set("Target", callbacks.target());
        return context;
    }

    private void disable(CallbackHost callbacks, String event, Exception failure) {
        callbacks.disable(event, failure);
    }

    private static boolean isState(LuaValue value) {
        return value.istable() || value.isfunction();
    }

    public interface CallbackHost {
        LuaValue entity();

        LuaValue target();

        String behaviorKey();

        Varargs invokeDefinition(LuaValue callback, LuaValue... arguments);

        Varargs invokeState(LuaValue definition, LuaValue callback, LuaValue... arguments);

        boolean shouldInvoke(String event, LuaValue callback);

        void disable(String event, Exception failure);

        void clearFailures(String state);
    }
}
