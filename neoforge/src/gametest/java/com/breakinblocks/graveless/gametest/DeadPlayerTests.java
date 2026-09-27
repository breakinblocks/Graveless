package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.event.GhostSyncEvents;
import com.breakinblocks.graveless.event.GraveMenuHandlers;
import com.breakinblocks.graveless.net.GravelessNetworking.ClaimRequestPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveActionPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveExtractPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class DeadPlayerTests {
    private DeadPlayerTests() {}

    static void register(TestRegistrar tests) {
        tests.add("dead_player_claim_keeps_the_grave", DeadPlayerTests::claimKeepsTheGrave);
        tests.add("dead_player_extract_keeps_the_stack", DeadPlayerTests::extractKeepsTheStack);
        tests.add("dead_player_xp_claim_keeps_the_xp", DeadPlayerTests::xpClaimKeepsTheXp);
        tests.add("dead_admin_extract_keeps_the_stack", DeadPlayerTests::adminExtractKeepsTheStack);
        tests.add("admin_restore_waits_for_a_dead_owner", DeadPlayerTests::adminRestoreWaits);
        tests.add("command_restore_waits_for_a_dead_player", DeadPlayerTests::commandRestoreWaits);
    }

    private static DeathRecord graveAt(TestPlayer owner, ItemStack... stacks) {
        for (int i = 0; i < stacks.length; i++) {
            owner.give(i, stacks[i]);
        }
        owner.simulateDeath();
        DeathRecord record = owner.newestRecord();
        owner.moveToRecord(record);
        return record;
    }

    private static void afterDeath(GameTestHelper helper, TestPlayer victim, Runnable body) {
        helper.startSequence()
                .thenExecute(victim::killForReal)
                .thenIdle(2)
                .thenExecute(() -> {
                    Check.isTrue(helper, victim.player().isDeadOrDying(), victim.name() + " should be dead");
                    body.run();
                })
                .thenSucceed();
    }

    private static void claim(TestPlayer actor, DeathRecord record) {
        GhostSyncEvents.handleClaimRequest(new ClaimRequestPayload(record.id()), actor.context());
    }

    private static void claimKeepsTheGrave(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        DeathRecord record = graveAt(owner, new ItemStack(Items.DIAMOND, 4));

        afterDeath(helper, owner, () -> {
            claim(owner, record);
            owner.respawn();

            Check.equal(helper, 1, owner.records().size(), "graves after a claim that arrived after death");
            Check.equal(
                    helper, 4, owner.newestRecord().itemCount(), "grave items after a claim that arrived after death");

            owner.moveToRecord(record);
            claim(owner, record);
            Check.equal(helper, 4, owner.countOf(Items.DIAMOND), "diamonds claimed after respawning");
            Check.isTrue(helper, owner.records().isEmpty(), "the grave should be gone after the respawned claim");
        });
    }

    private static void extractKeepsTheStack(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        DeathRecord record = graveAt(owner, new ItemStack(Items.DIAMOND, 4), new ItemStack(Items.EMERALD, 2));

        afterDeath(helper, owner, () -> {
            GraveMenuHandlers.handleExtract(new GraveExtractPayload(owner.id(), record.id(), 0), owner.context());
            Check.equal(helper, 6, record.itemCount(), "grave items after an extract that arrived after death");
            Check.equal(helper, 0, owner.countItems(), "items handed to a dead player");
        });
    }

    private static void xpClaimKeepsTheXp(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        owner.player().giveExperiencePoints(900);
        DeathRecord record = graveAt(owner, new ItemStack(Items.DIAMOND, 4));
        int stored = record.xp();

        afterDeath(helper, owner, () -> {
            GraveMenuHandlers.handleAction(
                    new GraveActionPayload(owner.id(), record.id(), GraveActionPayload.ACTION_CLAIM_XP),
                    owner.context());
            Check.equal(helper, stored, record.xp(), "grave xp after an xp claim that arrived after death");
        });
    }

    private static void adminExtractKeepsTheStack(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        TestPlayer admin = TestPlayer.join(helper);
        admin.op();
        DeathRecord record = graveAt(owner, new ItemStack(Items.DIAMOND, 4));

        afterDeath(helper, admin, () -> {
            GraveMenuHandlers.handleExtract(new GraveExtractPayload(owner.id(), record.id(), 0), admin.context());
            Check.equal(helper, 1, owner.records().size(), "graves after a dead admin's extract");
            Check.equal(helper, 4, record.itemCount(), "grave items after a dead admin's extract");
        });
    }

    private static void adminRestoreWaits(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        TestPlayer admin = TestPlayer.join(helper);
        admin.op();
        DeathRecord record = graveAt(owner, new ItemStack(Items.DIAMOND, 4));

        afterDeath(helper, owner, () -> {
            GraveMenuHandlers.handleAction(
                    new GraveActionPayload(owner.id(), record.id(), GraveActionPayload.ACTION_RESTORE),
                    admin.context());
            Check.equal(helper, 1, owner.records().size(), "graves after restoring to a dead owner");
            Check.equal(helper, 4, record.itemCount(), "grave items after restoring to a dead owner");
        });
    }

    private static void commandRestoreWaits(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        DeathRecord record = graveAt(owner, new ItemStack(Items.DIAMOND, 4));

        afterDeath(helper, owner, () -> {
            Check.equal(
                    helper,
                    0,
                    CommandTests.run(helper, "graveless restore " + owner.name()),
                    "items restored to a dead player");
            Check.equal(helper, 1, owner.records().size(), "graves after a command restore to a dead player");
            Check.equal(helper, 4, record.itemCount(), "grave items after a command restore to a dead player");
        });
    }
}
