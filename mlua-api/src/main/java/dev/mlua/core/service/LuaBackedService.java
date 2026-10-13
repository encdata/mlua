package dev.mlua.core.service;

import org.luaj.vm2.LuaTable;

public interface LuaBackedService {
   LuaTable toLua();
}
