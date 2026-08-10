package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.Graveless;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.function.Consumer;

public final class TestRegistrar {
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int DEFAULT_TIMEOUT = 200;

    private final RegisterGameTestsEvent event;
    private final Holder<TestEnvironmentDefinition<?>> shared;

    TestRegistrar(RegisterGameTestsEvent event) {
        this.event = event;
        this.shared = event.registerEnvironment(Graveless.id("shared"));
    }

    public void add(String name, Consumer<GameTestHelper> body) {
        add(name, DEFAULT_TIMEOUT, body);
    }

    public void add(String name, int maxTicks, Consumer<GameTestHelper> body) {
        register(name, this.shared, maxTicks, body);
    }

    public void addIsolated(String name, Consumer<GameTestHelper> body) {
        addIsolated(name, DEFAULT_TIMEOUT, body);
    }

    public void addIsolated(String name, int maxTicks, Consumer<GameTestHelper> body) {
        register(name, this.event.registerEnvironment(Graveless.id("solo_" + name)), maxTicks, body);
    }

    private void register(String name, Holder<TestEnvironmentDefinition<?>> environment, int maxTicks,
                          Consumer<GameTestHelper> body) {
        TestData<Holder<TestEnvironmentDefinition<?>>> data =
                new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 0, true);
        this.event.registerTest(Graveless.id(name), new GravelessTestInstance(data, name, body));
    }
}
