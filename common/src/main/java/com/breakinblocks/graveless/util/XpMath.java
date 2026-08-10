package com.breakinblocks.graveless.util;

public final class XpMath {
    private XpMath() {
    }

    public static int totalPoints(int level, float progress) {
        return pointsAtLevel(level) + Math.round(progress * xpForLevelUp(level));
    }

    private static int pointsAtLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        }
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    private static int xpForLevelUp(int level) {
        if (level <= 15) {
            return 2 * level + 7;
        }
        if (level <= 30) {
            return 5 * level - 38;
        }
        return 9 * level - 158;
    }
}
