package dev.mlua.mcapi.core;

public interface MCChatFormatting {
    char getChar();
    String getName();

    MCChatFormatting BLACK = of('0', "black");
    MCChatFormatting DARK_BLUE = of('1', "dark_blue");
    MCChatFormatting DARK_GREEN = of('2', "dark_green");
    MCChatFormatting DARK_AQUA = of('3', "dark_aqua");
    MCChatFormatting DARK_RED = of('4', "dark_red");
    MCChatFormatting DARK_PURPLE = of('5', "dark_purple");
    MCChatFormatting GOLD = of('6', "gold");
    MCChatFormatting GRAY = of('7', "gray");
    MCChatFormatting DARK_GRAY = of('8', "dark_gray");
    MCChatFormatting BLUE = of('9', "blue");
    MCChatFormatting GREEN = of('a', "green");
    MCChatFormatting AQUA = of('b', "aqua");
    MCChatFormatting RED = of('c', "red");
    MCChatFormatting LIGHT_PURPLE = of('d', "light_purple");
    MCChatFormatting YELLOW = of('e', "yellow");
    MCChatFormatting WHITE = of('f', "white");
    MCChatFormatting OBFUSCATED = of('k', "obfuscated");
    MCChatFormatting BOLD = of('l', "bold");
    MCChatFormatting STRIKETHROUGH = of('m', "strikethrough");
    MCChatFormatting UNDERLINE = of('n', "underline");
    MCChatFormatting ITALIC = of('o', "italic");
    MCChatFormatting RESET = of('r', "reset");

    private static MCChatFormatting of(char code, String name) {
        return new MCChatFormatting() {
            @Override public char getChar() { return code; }
            @Override public String getName() { return name; }
            @Override public String toString() { return "MCChatFormatting." + name.toUpperCase(); }
        };
    }
}
