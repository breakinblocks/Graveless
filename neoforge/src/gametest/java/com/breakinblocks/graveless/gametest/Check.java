package com.breakinblocks.graveless.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Objects;

public final class Check {
    private Check() {
    }

    public static void isTrue(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    public static void isFalse(GameTestHelper helper, boolean condition, String message) {
        isTrue(helper, !condition, message);
    }

    public static void equal(GameTestHelper helper, int expected, int actual, String what) {
        if (expected != actual) {
            helper.fail(what + ": expected " + expected + " but was " + actual);
        }
    }

    public static void equal(GameTestHelper helper, Object expected, Object actual, String what) {
        if (!Objects.equals(expected, actual)) {
            helper.fail(what + ": expected " + expected + " but was " + actual);
        }
    }

    public static void notNull(GameTestHelper helper, Object value, String what) {
        if (value == null) {
            helper.fail(what + " was null");
        }
    }

    public static void isNull(GameTestHelper helper, Object value, String what) {
        if (value != null) {
            helper.fail(what + " should have been null but was " + value);
        }
    }
}
