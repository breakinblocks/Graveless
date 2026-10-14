package com.breakinblocks.graveless.util;

import net.minecraft.server.level.ServerPlayer;

public final class XpMath {
    private static final long MAX_POINTS = Long.MAX_VALUE / 2;

    private XpMath() {}

    public static long totalPoints(int level, float progress) {
        if (level < 0) {
            return 0L;
        }
        return Math.min(pointsAtLevel(level), MAX_POINTS) + Math.round((double) progress * xpForLevelUp(level));
    }

    public static void give(ServerPlayer player, long points) {
        if (points <= 0) {
            return;
        }
        long current = Math.min(totalPoints(player.experienceLevel, player.experienceProgress), MAX_POINTS);
        long total = current + Math.min(points, MAX_POINTS - current);
        int level = levelForPoints(total);
        player.setExperienceLevels(level);
        player.setExperiencePoints((int) Math.min(Integer.MAX_VALUE, total - pointsAtLevel(level)));
        player.totalExperience = (int) Math.min(Integer.MAX_VALUE, (long) player.totalExperience + points);
    }

    private static int levelForPoints(long points) {
        double discriminant = 162.5 * 162.5 - 18.0 * (2220.0 - points);
        int level = discriminant <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, (162.5 + Math.sqrt(discriminant)) / 9.0);
        while (level > 0 && pointsAtLevel(level) > points) {
            level--;
        }
        while (level < Integer.MAX_VALUE && pointsAtLevel(level + 1) <= points) {
            level++;
        }
        return level;
    }

    private static long pointsAtLevel(int level) {
        long l = level;
        if (l <= 16) {
            return l * l + 6 * l;
        }
        if (l <= 31) {
            return (5 * l * l - 81 * l + 720) / 2;
        }
        return (long) (4.5 * l * l - 162.5 * l + 2220);
    }

    private static long xpForLevelUp(int level) {
        long l = level;
        if (l <= 15) {
            return 2 * l + 7;
        }
        if (l <= 30) {
            return 5 * l - 38;
        }
        return 9 * l - 158;
    }
}
