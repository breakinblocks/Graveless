package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.event.GhostSyncEvents;
import com.breakinblocks.graveless.net.GravelessNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public final class GhostTests {
    private GhostTests() {
    }

    static void register(TestRegistrar tests) {
        tests.add("ghost_claim_requires_access", GhostTests::claimRequiresAccess);
        tests.add("ghost_claim_enforces_claim_range", GhostTests::claimEnforcesRange);
        tests.add("ghost_claim_requires_the_same_dimension", GhostTests::claimRequiresSameDimension);
        tests.add("ghost_claim_ignores_unknown_records", GhostTests::claimIgnoresUnknownRecords);
        tests.add("ghost_sync_reaches_owner_and_allowed_only", GhostTests::syncReachesOwnerAndAllowedOnly);
        tests.add("ghost_removal_is_broadcast_on_claim", GhostTests::removalIsBroadcastOnClaim);
    }

    private static void claim(TestPlayer actor, DeathRecord record) {
        GhostSyncEvents.handleClaimRequest(
                new GravelessNetworking.ClaimRequestPayload(record.id()), actor.context());
    }

    private static void claimRequiresAccess(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        TestPlayer stranger = TestPlayer.join(helper);
        owner.give(0, new ItemStack(Items.DIAMOND, 4));
        owner.simulateDeath();
        DeathRecord record = owner.newestRecord();
        owner.moveToRecord(record);
        stranger.moveToRecord(record);

        claim(stranger, record);
        Check.equal(helper, 1, owner.records().size(), "grave survives a claim by a stranger");
        Check.equal(helper, 0, stranger.countItems(), "stranger received items from a grave");

        owner.profile().allowed().add(stranger.id());
        claim(stranger, record);
        Check.isTrue(helper, owner.records().isEmpty(), "an allowed player could not claim the grave");
        Check.equal(helper, 4, stranger.countItems(), "allowed player received the grave contents");
        helper.succeed();
    }

    private static void claimEnforcesRange(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        owner.give(0, new ItemStack(Items.DIAMOND, 2));
        owner.simulateDeath();
        DeathRecord record = owner.newestRecord();
        BlockPos anchor = owner.anchorOf(record);

        owner.moveToAbsolute(anchor.getX() + 120.0, anchor.getY(), anchor.getZ());
        claim(owner, record);
        Check.equal(helper, 1, owner.records().size(), "grave claimed from beyond claim_range");
        Check.equal(helper, 0, owner.countItems(), "items handed out from beyond claim_range");

        owner.moveToRecord(record);
        claim(owner, record);
        Check.isTrue(helper, owner.records().isEmpty(), "grave could not be claimed from inside claim_range");
        Check.equal(helper, 2, owner.countItems(), "items returned from inside claim_range");
        helper.succeed();
    }

    private static void claimRequiresSameDimension(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        DeathRecord elsewhere = new DeathRecord(UUID.randomUUID(),
                GlobalPos.of(Level.NETHER, owner.player().blockPosition()),
                helper.getLevel().getGameTime(), System.currentTimeMillis(), "test", 0,
                List.of(CapturedEntry.loose(new ItemStack(Items.DIAMOND, 3))));
        owner.profile().records().add(elsewhere);
        owner.store().setDirty();

        claim(owner, elsewhere);

        Check.equal(helper, 1, owner.records().size(), "grave in another dimension was claimed remotely");
        Check.equal(helper, 0, owner.countItems(), "items handed out across dimensions");
        helper.succeed();
    }

    private static void claimIgnoresUnknownRecords(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        GhostSyncEvents.handleClaimRequest(
                new GravelessNetworking.ClaimRequestPayload(UUID.randomUUID()), owner.context());
        Check.equal(helper, 0, owner.countItems(), "an unknown record id produced items");
        helper.succeed();
    }

    private static void syncReachesOwnerAndAllowedOnly(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        TestPlayer stranger = TestPlayer.join(helper);
        owner.give(0, new ItemStack(Items.DIAMOND, 1));
        owner.simulateDeath();
        DeathRecord record = owner.newestRecord();
        owner.moveToRecord(record);
        stranger.moveToRecord(record);
        owner.clearOutbound();
        stranger.clearOutbound();

        helper.startSequence()
                .thenWaitUntil(() -> Check.isTrue(helper, hasGhost(owner, record),
                        "owner was not told about their own ghost"))
                .thenExecute(() -> {
                    Check.isFalse(helper, hasGhost(stranger, record),
                            "a stranger was told about someone else's ghost");
                    owner.profile().allowed().add(stranger.id());
                    stranger.clearOutbound();
                })
                .thenWaitUntil(() -> Check.isTrue(helper, hasGhost(stranger, record),
                        "an allowed player was not told about the ghost"))
                .thenSucceed();
    }

    private static void removalIsBroadcastOnClaim(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        owner.give(0, new ItemStack(Items.DIAMOND, 1));
        owner.simulateDeath();
        DeathRecord record = owner.newestRecord();
        owner.moveToRecord(record);

        helper.startSequence()
                .thenWaitUntil(() -> Check.isTrue(helper, hasGhost(owner, record),
                        "ghost was never synced to the owner"))
                .thenExecute(() -> {
                    owner.clearOutbound();
                    claim(owner, record);
                    boolean claimed = owner.records().isEmpty();
                    List<GravelessNetworking.GhostRemovePayload> removals =
                            owner.outbound(GravelessNetworking.GhostRemovePayload.class);
                    boolean removed = removals.stream().anyMatch(p -> p.recordId().equals(record.id()));
                    Check.isTrue(helper, claimed && removed,
                            "claiming a grave should remove the ghost for the owner (claimed=" + claimed
                                    + ", ghost removals seen=" + removals.size() + ")");
                })
                .thenSucceed();
    }

    private static boolean hasGhost(TestPlayer player, DeathRecord record) {
        return player.outbound(GravelessNetworking.GhostAddPayload.class).stream()
                .anyMatch(payload -> payload.recordId().equals(record.id()));
    }
}
