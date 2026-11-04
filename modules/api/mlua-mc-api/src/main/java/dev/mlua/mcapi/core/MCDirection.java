package dev.mlua.mcapi.core;

public interface MCDirection {
    String getName();
    MCDirection getOpposite();

    MCDirection NORTH = new MCDirection() {
        @Override public String getName() { return "north"; }
        @Override public MCDirection getOpposite() { return SOUTH; }
        @Override public String toString() { return "MCDirection.NORTH"; }
    };
    MCDirection SOUTH = new MCDirection() {
        @Override public String getName() { return "south"; }
        @Override public MCDirection getOpposite() { return NORTH; }
        @Override public String toString() { return "MCDirection.SOUTH"; }
    };
    MCDirection EAST = new MCDirection() {
        @Override public String getName() { return "east"; }
        @Override public MCDirection getOpposite() { return WEST; }
        @Override public String toString() { return "MCDirection.EAST"; }
    };
    MCDirection WEST = new MCDirection() {
        @Override public String getName() { return "west"; }
        @Override public MCDirection getOpposite() { return EAST; }
        @Override public String toString() { return "MCDirection.WEST"; }
    };
    MCDirection UP = new MCDirection() {
        @Override public String getName() { return "up"; }
        @Override public MCDirection getOpposite() { return DOWN; }
        @Override public String toString() { return "MCDirection.UP"; }
    };
    MCDirection DOWN = new MCDirection() {
        @Override public String getName() { return "down"; }
        @Override public MCDirection getOpposite() { return UP; }
        @Override public String toString() { return "MCDirection.DOWN"; }
    };
}
