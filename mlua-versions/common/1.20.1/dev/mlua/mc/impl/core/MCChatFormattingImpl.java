package dev.mlua.mc.impl.core;

import dev.mlua.mcapi.core.MCChatFormatting;
import net.minecraft.ChatFormatting;
import java.util.EnumMap;

public final class MCChatFormattingImpl implements MCChatFormatting {
    private static final EnumMap<ChatFormatting, MCChatFormattingImpl> CACHE = new EnumMap<>(ChatFormatting.class);

    public static final MCChatFormattingImpl BLACK = new MCChatFormattingImpl(ChatFormatting.BLACK);
    public static final MCChatFormattingImpl DARK_BLUE = new MCChatFormattingImpl(ChatFormatting.DARK_BLUE);
    public static final MCChatFormattingImpl DARK_GREEN = new MCChatFormattingImpl(ChatFormatting.DARK_GREEN);
    public static final MCChatFormattingImpl DARK_AQUA = new MCChatFormattingImpl(ChatFormatting.DARK_AQUA);
    public static final MCChatFormattingImpl DARK_RED = new MCChatFormattingImpl(ChatFormatting.DARK_RED);
    public static final MCChatFormattingImpl DARK_PURPLE = new MCChatFormattingImpl(ChatFormatting.DARK_PURPLE);
    public static final MCChatFormattingImpl GOLD = new MCChatFormattingImpl(ChatFormatting.GOLD);
    public static final MCChatFormattingImpl GRAY = new MCChatFormattingImpl(ChatFormatting.GRAY);
    public static final MCChatFormattingImpl DARK_GRAY = new MCChatFormattingImpl(ChatFormatting.DARK_GRAY);
    public static final MCChatFormattingImpl BLUE = new MCChatFormattingImpl(ChatFormatting.BLUE);
    public static final MCChatFormattingImpl GREEN = new MCChatFormattingImpl(ChatFormatting.GREEN);
    public static final MCChatFormattingImpl AQUA = new MCChatFormattingImpl(ChatFormatting.AQUA);
    public static final MCChatFormattingImpl RED = new MCChatFormattingImpl(ChatFormatting.RED);
    public static final MCChatFormattingImpl LIGHT_PURPLE = new MCChatFormattingImpl(ChatFormatting.LIGHT_PURPLE);
    public static final MCChatFormattingImpl YELLOW = new MCChatFormattingImpl(ChatFormatting.YELLOW);
    public static final MCChatFormattingImpl WHITE = new MCChatFormattingImpl(ChatFormatting.WHITE);
    public static final MCChatFormattingImpl OBFUSCATED = new MCChatFormattingImpl(ChatFormatting.OBFUSCATED);
    public static final MCChatFormattingImpl BOLD = new MCChatFormattingImpl(ChatFormatting.BOLD);
    public static final MCChatFormattingImpl STRIKETHROUGH = new MCChatFormattingImpl(ChatFormatting.STRIKETHROUGH);
    public static final MCChatFormattingImpl UNDERLINE = new MCChatFormattingImpl(ChatFormatting.UNDERLINE);
    public static final MCChatFormattingImpl ITALIC = new MCChatFormattingImpl(ChatFormatting.ITALIC);
    public static final MCChatFormattingImpl RESET = new MCChatFormattingImpl(ChatFormatting.RESET);

    private final ChatFormatting inner;

    private MCChatFormattingImpl(ChatFormatting inner) {
        this.inner = inner;
        CACHE.put(inner, this);
    }

    public static MCChatFormattingImpl fromReal(ChatFormatting inner) {
        if (inner == null) return null;
        return CACHE.computeIfAbsent(inner, MCChatFormattingImpl::new);
    }

    @Override
    public char getChar() {
        return inner.getChar();
    }

    @Override
    public String getName() {
        return inner.getName();
    }

    @Override
    public String toString() {
        return "MCChatFormatting." + inner.getName().toUpperCase();
    }
}
