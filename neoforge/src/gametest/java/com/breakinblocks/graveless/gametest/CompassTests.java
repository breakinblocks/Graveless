package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.event.DeathCaptureEvents;
import com.breakinblocks.graveless.registry.ModItems;
import com.breakinblocks.graveless.restore.RestoreEngine;
import com.breakinblocks.graveless.util.SpiritCompassManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;

public final class CompassTests {
    private CompassTests() {
    }

    static void register(TestRegistrar tests) {
        tests.add("compass_arrives_with_the_first_grave", CompassTests::arrivesWithFirstGrave);
        tests.add("compass_is_never_duplicated", CompassTests::isNeverDuplicated);
        tests.add("compass_leaves_with_the_last_grave", CompassTests::leavesWithLastGrave);
        tests.add("compass_follows_the_attuned_grave", CompassTests::followsAttunedGrave);
        tests.add("compass_retargets_when_its_grave_is_gone", CompassTests::retargetsWhenGraveIsGone);
    }

    private static void arrivesWithFirstGrave(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        SpiritCompassManager.giveIfMissing(player.player());
        Check.equal(helper, 0, player.countOf(ModItems.SPIRIT_COMPASS.get()),
                "a player with no graves should not be handed a compass");

        player.give(0, new ItemStack(Items.DIAMOND, 1));
        player.simulateDeath();
        DeathCaptureEvents.onRespawn(player.player());

        Check.equal(helper, 1, player.countOf(ModItems.SPIRIT_COMPASS.get()), "compasses after the first grave");
        LodestoneTracker tracker = player.compassTarget();
        Check.notNull(helper, tracker, "compass lodestone tracker");
        Check.isTrue(helper, tracker.target().isPresent(), "compass has no target");
        helper.succeed();
    }

    private static void isNeverDuplicated(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 1));
        player.simulateDeath();

        for (int i = 0; i < 4; i++) {
            DeathCaptureEvents.onRespawn(player.player());
            DeathCaptureEvents.onLogin(player.player());
        }

        Check.equal(helper, 1, player.countOf(ModItems.SPIRIT_COMPASS.get()),
                "repeated respawns and logins should never stack up compasses");
        helper.succeed();
    }

    private static void leavesWithLastGrave(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 1));
        player.simulateDeath();
        player.give(0, new ItemStack(Items.EMERALD, 1));
        player.simulateDeath();
        DeathCaptureEvents.onRespawn(player.player());
        Check.equal(helper, 1, player.countOf(ModItems.SPIRIT_COMPASS.get()), "compass while two graves stand");

        RestoreEngine.claim(player.player(), player.profile(), player.records().getFirst(), player.store());
        SpiritCompassManager.refresh(player.player());
        Check.equal(helper, 1, player.countOf(ModItems.SPIRIT_COMPASS.get()),
                "compass should stay while a grave remains");

        RestoreEngine.claim(player.player(), player.profile(), player.records().getFirst(), player.store());
        SpiritCompassManager.refresh(player.player());
        Check.isTrue(helper, player.records().isEmpty(), "both graves should be claimed");
        Check.equal(helper, 0, player.countOf(ModItems.SPIRIT_COMPASS.get()),
                "compass should vanish with the last grave");
        helper.succeed();
    }

    private static void followsAttunedGrave(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 1));
        player.simulateDeath();
        DeathRecord first = player.newestRecord();

        player.moveToAbsolute(player.player().getX() + 40.0, player.player().getY(), player.player().getZ());
        player.give(0, new ItemStack(Items.EMERALD, 1));
        player.simulateDeath();
        DeathRecord second = player.newestRecord();
        DeathCaptureEvents.onRespawn(player.player());

        Check.equal(helper, targetOf(player, second), currentTarget(helper, player),
                "an unattuned compass should point at the newest grave");

        player.profile().setTrackedRecordId(first.id());
        SpiritCompassManager.refresh(player.player());
        Check.equal(helper, targetOf(player, first), currentTarget(helper, player),
                "an attuned compass should point at the chosen grave");
        helper.succeed();
    }

    private static void retargetsWhenGraveIsGone(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 1));
        player.simulateDeath();
        DeathRecord first = player.newestRecord();

        player.moveToAbsolute(player.player().getX() + 40.0, player.player().getY(), player.player().getZ());
        player.give(0, new ItemStack(Items.EMERALD, 1));
        player.simulateDeath();
        DeathRecord second = player.newestRecord();

        DeathCaptureEvents.onRespawn(player.player());
        player.profile().setTrackedRecordId(first.id());
        SpiritCompassManager.refresh(player.player());

        player.profile().records().remove(first);
        SpiritCompassManager.refresh(player.player());

        Check.isNull(helper, player.profile().trackedRecordId(), "stale compass attunement");
        Check.equal(helper, targetOf(player, second), currentTarget(helper, player),
                "compass should fall back to the newest remaining grave");
        helper.succeed();
    }

    private static GlobalPos targetOf(TestPlayer player, DeathRecord record) {
        BlockPos anchor = player.anchorOf(record);
        return GlobalPos.of(record.pos().dimension(), anchor);
    }

    private static GlobalPos currentTarget(GameTestHelper helper, TestPlayer player) {
        LodestoneTracker tracker = player.compassTarget();
        Check.notNull(helper, tracker, "compass lodestone tracker");
        Check.isTrue(helper, tracker.target().isPresent(), "compass has no target");
        return tracker.target().orElseThrow();
    }
}
