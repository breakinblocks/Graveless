package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.Graveless;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

public final class TestCleanup implements GameTestListener {
    private final List<Runnable> actions = new ArrayList<>();
    private boolean done;

    private TestCleanup() {
    }

    public static TestCleanup attach(GameTestHelper helper) {
        TestCleanup cleanup = new TestCleanup();
        helper.testInfo.addListener(cleanup);
        return cleanup;
    }

    public <T> void config(ModConfigSpec.ConfigValue<T> value, T temporary) {
        T original = value.get();
        value.set(temporary);
        this.actions.add(() -> value.set(original));
    }

    public <T> void gameRule(ServerLevel level, GameRule<T> rule, T temporary) {
        GameRules rules = level.getGameRules();
        T original = rules.get(rule);
        rules.set(rule, temporary, level.getServer());
        this.actions.add(() -> rules.set(rule, original, level.getServer()));
    }

    public void onFinish(Runnable action) {
        this.actions.add(action);
    }

    private void restore() {
        if (this.done) {
            return;
        }
        this.done = true;
        for (int i = this.actions.size() - 1; i >= 0; i--) {
            try {
                this.actions.get(i).run();
            } catch (Exception e) {
                Graveless.LOGGER.error("Game test cleanup action failed", e);
            }
        }
    }

    @Override
    public void testStructureLoaded(GameTestInfo testInfo) {
    }

    @Override
    public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
        restore();
    }

    @Override
    public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
        restore();
    }

    @Override
    public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
    }
}
