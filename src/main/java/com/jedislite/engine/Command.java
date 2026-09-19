package com.jedislite.engine;

import com.jedislite.resp.RespValue;

import java.util.List;

@FunctionalInterface
public interface Command {

    RespValue execute(List<RespValue> args, Database db);
}

