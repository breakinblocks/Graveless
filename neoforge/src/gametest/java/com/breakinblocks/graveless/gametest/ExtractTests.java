package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.event.GraveMenuHandlers;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveActionPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveExtractPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveListPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveOpenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

public final class ExtractTests {
    private ExtractTests() {
    }

    static void register(TestRegistrar tests) {
        tests.add("open_request_focuses_the_clicked_grave", ExtractTests::openFocusesClickedGrave);
        tests.add("open_request_is_refused_for_strangers", ExtractTests::openRefusedForStrangers);
        tests.add("open_request_is_refused_from_far_away", ExtractTests::openRefusedFromFarAway);
        tests.add("owner_extracts_a_single_stack_at_their_grave", ExtractTests::ownerExtractsSingleStack);
        tests.add("owner_extract_is_refused_from_far_away", ExtractTests::ownerExtractRefusedFromFarAway);
        tests.add("allowed_player_extracts_at_the_grave", ExtractTests::allowedPlayerExtracts);
        tests.add("allowed_player_cannot_delete_a_grave", ExtractTests::allowedPlayerCannotDelete);
        tests.add("xp_claim_drains_xp_and_leaves_items", ExtractTests::xpClaimLeavesItems);
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

    private static void sendFarAway(TestPlayer player, DeathRecord record) {
        BlockPos anchor = player.anchorOf(record);
        player.moveToAbsolute(anchor.getX() + 120.0, anchor.getY(), anchor.getZ());
    }

    private static void openFocusesClickedGrave(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        DeathRecord record = graveAt(owner, new ItemStack(Items.DIAMOND, 4));
        owner.clearOutbound();

        GraveMenuHandlers.handleOpenRequest(new GraveOpenPayload(record.id()), owner.context());

        List<GraveListPayload> lists = owner.outbound(GraveListPayload.class);
        Check.equal(helper, 1, lists.size(), "grave lists sent after an open request");
        Check.equal(helper, owner.id(), lists.getFirst().ownerId(), "grave list owner");
        Check.equal(helper, Optional.of(record.id()), lists.getFirst().focusId(), "focused grave");
        helper.succeed();
    }

    private static void openRefusedForStrangers(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        TestPlayer stranger = TestPlayer.join(helper);
        DeathRecord record = graveAt(owner, new ItemStack(Items.DIAMOND, 4));
        stranger.moveToRecord(record);
        stranger.clearOutbound();

        GraveMenuHandlers.handleOpenRequest(new GraveOpenPayload(record.id()), stranger.context());

        Check.isTrue(helper, stranger.outbound(GraveListPayload.class).isEmpty(),
                "a stranger opened someone else's grave");
        helper.succeed();
    }

    private static void openRefusedFromFarAway(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        DeathRecord record = graveAt(owner, new ItemStack(Items.DIAMOND, 4));
        sendFarAway(owner, record);
        owner.clearOutbound();

        GraveMenuHandlers.handleOpenRequest(new GraveOpenPayload(record.id()), owner.context());

        Check.isTrue(helper, owner.outbound(GraveListPayload.class).isEmpty(),
                "a grave was opened from beyond claim_range");
        helper.succeed();
    }

    private static void ownerExtractsSingleStack(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        DeathRecord record = graveAt(owner,
                new ItemStack(Items.DIAMOND, 4), new ItemStack(Items.EMERALD, 2));

        GraveMenuHandlers.handleExtract(
                new GraveExtractPayload(owner.id(), record.id(), 0), owner.context());

        Check.equal(helper, 1, record.entries().size(), "entries left after the owner took one stack");
        Check.equal(helper, 4, owner.countOf(Items.DIAMOND), "diamonds handed to the owner");
        Check.equal(helper, 0, owner.countOf(Items.EMERALD), "the untouched stack stayed in the grave");
        Check.equal(helper, 1, owner.records().size(), "the grave survives a partial extract");
        helper.succeed();
    }

    private static void ownerExtractRefusedFromFarAway(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        DeathRecord record = graveAt(owner, new ItemStack(Items.DIAMOND, 4));
        sendFarAway(owner, record);

        GraveMenuHandlers.handleExtract(
                new GraveExtractPayload(owner.id(), record.id(), 0), owner.context());

        Check.equal(helper, 1, record.entries().size(), "a grave was emptied from beyond claim_range");
        Check.equal(helper, 0, owner.countItems(), "items were handed out from beyond claim_range");
        helper.succeed();
    }

    private static void allowedPlayerExtracts(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        TestPlayer friend = TestPlayer.join(helper);
        DeathRecord record = graveAt(owner, new ItemStack(Items.DIAMOND, 4));
        friend.moveToRecord(record);

        GraveMenuHandlers.handleExtract(
                new GraveExtractPayload(owner.id(), record.id(), 0), friend.context());
        Check.equal(helper, 0, friend.countItems(), "a player without access extracted from the grave");

        owner.profile().allowed().add(friend.id());
        GraveMenuHandlers.handleExtract(
                new GraveExtractPayload(owner.id(), record.id(), 0), friend.context());
        Check.equal(helper, 4, friend.countOf(Items.DIAMOND), "an allowed player could not extract");
        Check.isTrue(helper, owner.records().isEmpty(), "the emptied grave should be gone");
        helper.succeed();
    }

    private static void allowedPlayerCannotDelete(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        TestPlayer friend = TestPlayer.join(helper);
        DeathRecord record = graveAt(owner, new ItemStack(Items.DIAMOND, 4));
        friend.moveToRecord(record);
        owner.profile().allowed().add(friend.id());

        GraveMenuHandlers.handleAction(
                new GraveActionPayload(owner.id(), record.id(), GraveActionPayload.ACTION_DELETE),
                friend.context());

        Check.equal(helper, 1, owner.records().size(), "an allowed player deleted someone else's grave");
        helper.succeed();
    }

    private static void xpClaimLeavesItems(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        owner.player().giveExperiencePoints(900);
        int granted = owner.storedXp();
        DeathRecord record = graveAt(owner, new ItemStack(Items.DIAMOND, 4));
        Check.equal(helper, granted, record.xp(), "grave xp matches what the player had");

        GraveMenuHandlers.handleAction(
                new GraveActionPayload(owner.id(), record.id(), GraveActionPayload.ACTION_CLAIM_XP),
                owner.context());

        Check.equal(helper, 0, record.xp(), "grave xp after a claim");
        Check.isTrue(helper, Math.abs(owner.storedXp() - granted) <= 2,
                "player xp after an xp claim, saw " + owner.storedXp() + " expected " + granted);
        Check.equal(helper, 1, record.entries().size(), "an xp claim took items out of the grave");
        Check.equal(helper, 1, owner.records().size(), "the grave should survive an xp-only claim");
        helper.succeed();
    }
}
