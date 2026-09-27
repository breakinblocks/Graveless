package com.breakinblocks.graveless.gametest;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class GravelessTestInstance extends GameTestInstance {
    private final String name;
    private final Consumer<GameTestHelper> body;

    GravelessTestInstance(
            TestData<Holder<TestEnvironmentDefinition<?>>> info, String name, Consumer<GameTestHelper> body) {
        super(info);
        this.name = name;
        this.body = body;
    }

    @Override
    public void run(GameTestHelper helper) {
        this.body.accept(helper);
    }

    @Override
    public MapCodec<? extends GameTestInstance> codec() {
        return MapCodec.unit(this);
    }

    @Override
    protected MutableComponent typeDescription() {
        return Component.literal("graveless:" + this.name);
    }
}
