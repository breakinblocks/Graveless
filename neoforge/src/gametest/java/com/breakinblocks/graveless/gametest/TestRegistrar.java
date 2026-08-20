package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.Graveless;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class TestRegistrar {
    private static final String EMPTY_STRUCTURE = Graveless.MOD_ID + ":empty";
    private static final int DEFAULT_TIMEOUT = 200;

    private final List<TestFunction> functions = new ArrayList<>();

    TestRegistrar() {
    }

    public List<TestFunction> functions() {
        return functions;
    }

    public void add(String name, Consumer<GameTestHelper> body) {
        add(name, DEFAULT_TIMEOUT, body);
    }

    public void add(String name, int maxTicks, Consumer<GameTestHelper> body) {
        register(name, Graveless.MOD_ID + ".shared", maxTicks, body);
    }

    public void addIsolated(String name, Consumer<GameTestHelper> body) {
        addIsolated(name, DEFAULT_TIMEOUT, body);
    }

    public void addIsolated(String name, int maxTicks, Consumer<GameTestHelper> body) {
        register(name, Graveless.MOD_ID + ".solo_" + name, maxTicks, body);
    }

    private void register(String name, String batch, int maxTicks, Consumer<GameTestHelper> body) {
        functions.add(new TestFunction(batch, Graveless.MOD_ID + "." + name, EMPTY_STRUCTURE,
                maxTicks, 0L, true, body));
    }
}
