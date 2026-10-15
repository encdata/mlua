package dev.mlua.minecraft.animation;

import dev.mlua.common.enums.EasingDirection;
import dev.mlua.common.enums.EasingStyle;
import dev.mlua.math.MLuaCFrame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public final class MLuaAnimation {
    private final List<Keyframe> keyframes;
    private final double length;

    public MLuaAnimation(List<Keyframe> keyframes) {
        if (keyframes == null || keyframes.isEmpty()) {
            throw new IllegalArgumentException("Animation must have at least one keyframe");
        }
        this.keyframes = new ArrayList<>(keyframes);
        this.keyframes.sort((a, b) -> Double.compare(a.time, b.time));
        for (int index = 1; index < this.keyframes.size(); index++) {
            if (this.keyframes.get(index - 1).time == this.keyframes.get(index).time) {
                throw new IllegalArgumentException("Animation keyframe times must be unique");
            }
        }
        this.length = this.keyframes.get(this.keyframes.size() - 1).time;
    }

    public List<Keyframe> getKeyframes() {
        return Collections.unmodifiableList(keyframes);
    }

    public double getLength() {
        return length;
    }

    public Map<String, MLuaCFrame> sample(double time) {
        if (keyframes.size() == 1) {
            return new HashMap<>(keyframes.get(0).data);
        }

        time = Math.max(0, Math.min(time, length));

        Keyframe prev = null;
        Keyframe next = null;

        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe a = keyframes.get(i);
            Keyframe b = keyframes.get(i + 1);
            if (time >= a.time && time <= b.time) {
                prev = a;
                next = b;
                break;
            }
        }

        if (prev == null || next == null) {
            if (time <= keyframes.get(0).time) {
                return new HashMap<>(keyframes.get(0).data);
            }
            return new HashMap<>(keyframes.get(keyframes.size() - 1).data);
        }

        if (prev.time == next.time) {
            return new HashMap<>(next.data);
        }

        double rawT = (time - prev.time) / (next.time - prev.time);
        double easedT = MLuaEasingHelper.apply(rawT, next.easingStyle, next.easingDirection);

        Map<String, MLuaCFrame> result = new HashMap<>();

        for (Map.Entry<String, MLuaCFrame> entry : prev.data.entrySet()) {
            MLuaCFrame from = entry.getValue();
            MLuaCFrame to = next.data.get(entry.getKey());
            if (to != null) {
                result.put(entry.getKey(), from.lerp(to, easedT));
            } else {
                result.put(entry.getKey(), from);
            }
        }

        for (Map.Entry<String, MLuaCFrame> entry : next.data.entrySet()) {
            if (!result.containsKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return result;
    }

    public static MLuaAnimation fromLuaTable(LuaTable table) {
        TreeMap<Integer, LuaTable> indexedFrames = new TreeMap<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = table.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (!key.isnumber()) {
                throw new LuaError("[MLua] Animation keyframes must be a dense array indexed from one.");
            }
            double numericIndex = key.todouble();
            if (!Double.isFinite(numericIndex) || numericIndex < 1 || numericIndex != Math.rint(numericIndex)
                    || numericIndex > Integer.MAX_VALUE) {
                throw new LuaError("[MLua] Animation keyframe indexes must be positive whole numbers.");
            }
            int frameIndex = (int) numericIndex;
            if (!entry.arg(2).istable()) {
                throw new LuaError("[MLua] Animation keyframe " + frameIndex + " must be a table.");
            }
            indexedFrames.put(frameIndex, entry.arg(2).checktable());
        }

        if (indexedFrames.isEmpty()) {
            throw new LuaError("[MLua] Animation keyframes table must contain at least one keyframe.");
        }
        List<Keyframe> frames = new ArrayList<>(indexedFrames.size());
        int expectedIndex = 1;
        for (Map.Entry<Integer, LuaTable> entry : indexedFrames.entrySet()) {
            if (entry.getKey() != expectedIndex) {
                throw new LuaError("[MLua] Animation keyframes must be a dense array; missing index " + expectedIndex + ".");
            }
            frames.add(keyframeFromLua(entry.getValue()));
            expectedIndex++;
        }

        try {
            return new MLuaAnimation(frames);
        } catch (IllegalArgumentException failure) {
            throw new LuaError("[MLua] " + failure.getMessage());
        }
    }

    private static Keyframe keyframeFromLua(LuaTable table) {
        LuaValue timeValue = table.get("Time");
        if (!timeValue.isnumber()) {
            throw new LuaError("[MLua] Each keyframe Time must be a finite, non-negative number.");
        }
        double time = timeValue.todouble();
        if (!Double.isFinite(time) || time < 0.0) {
            throw new LuaError("[MLua] Each keyframe Time must be a finite, non-negative number.");
        }

        LuaTable dataTable = table.get("Data").checktable();
        Map<String, MLuaCFrame> data = new HashMap<>();

        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = dataTable.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            if (!key.isstring() || key.tojstring().isBlank()) {
                throw new LuaError("[MLua] Animation pose bone names must be non-empty strings.");
            }
            String boneName = key.tojstring();
            MLuaCFrame cframe = MLuaCFrame.fromLua(next.arg(2));
            data.put(boneName, cframe);
        }

        EasingStyle easingStyle = EasingStyle.Linear;
        LuaValue styleVal = table.get("EasingStyle");
        if (!styleVal.isnil()) {
            if (!styleVal.isstring()) throw new LuaError("[MLua] EasingStyle must be a string.");
            try {
                easingStyle = EasingStyle.valueOf(styleVal.tojstring());
            } catch (IllegalArgumentException e) {
                throw new LuaError("[MLua] Unknown EasingStyle: " + styleVal.tojstring());
            }
        }

        EasingDirection easingDirection = EasingDirection.In;
        LuaValue dirVal = table.get("EasingDirection");
        if (!dirVal.isnil()) {
            if (!dirVal.isstring()) throw new LuaError("[MLua] EasingDirection must be a string.");
            try {
                easingDirection = EasingDirection.valueOf(dirVal.tojstring());
            } catch (IllegalArgumentException e) {
                throw new LuaError("[MLua] Unknown EasingDirection: " + dirVal.tojstring());
            }
        }

        return new Keyframe(time, data, easingStyle, easingDirection);
    }

    public static final class Keyframe {
        public final double time;
        public final Map<String, MLuaCFrame> data;
        public final EasingStyle easingStyle;
        public final EasingDirection easingDirection;

        public Keyframe(double time, Map<String, MLuaCFrame> data, EasingStyle easingStyle, EasingDirection easingDirection) {
            this.time = time;
            this.data = Collections.unmodifiableMap(new HashMap<>(data));
            this.easingStyle = easingStyle;
            this.easingDirection = easingDirection;
        }

        public Keyframe(double time, Map<String, MLuaCFrame> data) {
            this(time, data, EasingStyle.Linear, EasingDirection.In);
        }
    }
}
