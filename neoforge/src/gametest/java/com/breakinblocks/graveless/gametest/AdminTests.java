package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.event.GraveMenuHandlers;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveActionPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveDetailPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveDetailRequestPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveExtractPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveListPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveViewRequestPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.ProfileActionPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AdminTests {
    private AdminTests() {
    }

    static void register(TestRegistrar tests) {
        tests.add("admin_extract_pulls_a_stack_from_a_grave", AdminTests::extractPullsStack);
        tests.add("admin_extract_removes_an_emptied_grave", AdminTests::extractRemovesEmptiedGrave);
        tests.add("admin_extract_is_refused_for_regular_players", AdminTests::extractRefusedForRegularPlayers);
        tests.add("admin_view_request_is_refused_for_regular_players", AdminTests::viewRefusedForRegularPlayers);
        tests.add("admin_restores_a_grave_to_its_owner", AdminTests::restoresGraveToOwner);
        tests.add("grave_detail_is_refused_for_other_players", AdminTests::detailRefusedForOtherPlayers);
        tests.add("grave_delete_is_refused_for_other_players", AdminTests::deleteRefusedForOtherPlayers);
        tests.add("profile_actions_manage_the_allow_list", AdminTests::profileActionsManageAllowList);
    }

    private static DeathRecord graveWith(TestPlayer owner, ItemStack... stacks) {
        for (int i = 0; i < stacks.length; i++) {
            owner.give(i, stacks[i]);
        }
        owner.simulateDeath();
        return owner.newestRecord();
    }

    private static void extractPullsStack(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        TestPlayer admin = TestPlayer.join(helper);
        admin.op();
        DeathRecord record = graveWith(owner,
                new ItemStack(Items.DIAMOND, 4), new ItemStack(Items.EMERALD, 2));

        GraveMenuHandlers.handleExtract(
                new GraveExtractPayload(owner.id(), record.id(), 0), admin.context());

        Check.equal(helper, 1, record.entries().size(), "entries left in the grave after an extract");
        Check.equal(helper, 2, record.itemCount(), "grave item count after an extract");
        Check.equal(helper, 4, admin.countOf(Items.DIAMOND), "diamonds handed to the admin");
        Check.equal(helper, 1, owner.records().size(), "the grave itself survives a partial extract");
        helper.succeed();
    }

    private static void extractRemovesEmptiedGrave(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        TestPlayer admin = TestPlayer.join(helper);
        admin.op();
        DeathRecord record = graveWith(owner, new ItemStack(Items.DIAMOND, 1));

        GraveMenuHandlers.handleExtract(
                new GraveExtractPayload(owner.id(), record.id(), 0), admin.context());

        Check.isTrue(helper, owner.records().isEmpty(), "extracting the last item should remove the grave");
        Check.equal(helper, 1, admin.countOf(Items.DIAMOND), "the extracted item reached the admin");
        helper.succeed();
    }

    private static void extractRefusedForRegularPlayers(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        TestPlayer stranger = TestPlayer.join(helper);
        DeathRecord record = graveWith(owner, new ItemStack(Items.DIAMOND, 4));

        GraveMenuHandlers.handleExtract(
                new GraveExtractPayload(owner.id(), record.id(), 0), stranger.context());

        Check.equal(helper, 1, record.entries().size(), "a regular player extracted from someone else's grave");
        Check.equal(helper, 0, stranger.countItems(), "a regular player received extracted items");
        helper.succeed();
    }

    private static void viewRefusedForRegularPlayers(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        TestPlayer stranger = TestPlayer.join(helper);
        TestPlayer admin = TestPlayer.join(helper);
        admin.op();
        graveWith(owner, new ItemStack(Items.DIAMOND, 4));
        stranger.clearOutbound();
        admin.clearOutbound();

        GraveMenuHandlers.handleViewRequest(new GraveViewRequestPayload(owner.id()), stranger.context());
        Check.isTrue(helper, stranger.outbound(GraveListPayload.class).isEmpty(),
                "a regular player was sent another player's grave list");

        GraveMenuHandlers.handleViewRequest(new GraveViewRequestPayload(owner.id()), admin.context());
        List<GraveListPayload> lists = admin.outbound(GraveListPayload.class);
        Check.equal(helper, 1, lists.size(), "grave lists sent to the admin");
        Check.equal(helper, owner.id(), lists.getFirst().ownerId(), "grave list owner");
        Check.isTrue(helper, lists.getFirst().admin(), "grave list admin flag");
        Check.equal(helper, 1, lists.getFirst().graves().size(), "graves in the admin view");
        helper.succeed();
    }

    private static void restoresGraveToOwner(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        TestPlayer admin = TestPlayer.join(helper);
        admin.op();
        DeathRecord record = graveWith(owner, new ItemStack(Items.DIAMOND, 4));

        GraveMenuHandlers.handleAction(
                new GraveActionPayload(owner.id(), record.id(), GraveActionPayload.ACTION_RESTORE),
                admin.context());

        Check.isTrue(helper, owner.records().isEmpty(), "the grave should be gone after an admin restore");
        Check.equal(helper, 4, owner.countOf(Items.DIAMOND), "the owner received the grave contents");
        Check.equal(helper, 0, admin.countOf(Items.DIAMOND), "the admin kept the grave contents");
        helper.succeed();
    }

    private static void detailRefusedForOtherPlayers(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        TestPlayer stranger = TestPlayer.join(helper);
        DeathRecord record = graveWith(owner, new ItemStack(Items.DIAMOND, 4));
        stranger.clearOutbound();
        owner.clearOutbound();

        GraveMenuHandlers.handleDetailRequest(
                new GraveDetailRequestPayload(owner.id(), record.id()), stranger.context());
        Check.isTrue(helper, stranger.outbound(GraveDetailPayload.class).isEmpty(),
                "grave contents leaked to a player who does not own them");

        GraveMenuHandlers.handleDetailRequest(
                new GraveDetailRequestPayload(owner.id(), record.id()), owner.context());
        List<GraveDetailPayload> details = owner.outbound(GraveDetailPayload.class);
        Check.equal(helper, 1, details.size(), "grave details sent to the owner");
        Check.equal(helper, 1, details.getFirst().items().size(), "items in the grave detail");
        int terrain = GraveMenuHandlers.TERRAIN_SIZE * GraveMenuHandlers.TERRAIN_SIZE
                * GraveMenuHandlers.TERRAIN_HEIGHT;
        Check.equal(helper, terrain, details.getFirst().terrain().size(), "terrain payload size");
        helper.succeed();
    }

    private static void deleteRefusedForOtherPlayers(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        TestPlayer stranger = TestPlayer.join(helper);
        DeathRecord record = graveWith(owner, new ItemStack(Items.DIAMOND, 4));

        GraveMenuHandlers.handleAction(
                new GraveActionPayload(owner.id(), record.id(), GraveActionPayload.ACTION_DELETE),
                stranger.context());
        Check.equal(helper, 1, owner.records().size(), "a stranger deleted someone else's grave");

        GraveMenuHandlers.handleAction(
                new GraveActionPayload(owner.id(), record.id(), GraveActionPayload.ACTION_DELETE),
                owner.context());
        Check.isTrue(helper, owner.records().isEmpty(), "the owner could not delete their own grave");
        helper.succeed();
    }

    private static void profileActionsManageAllowList(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        TestPlayer friend = TestPlayer.join(helper);

        act(owner, ProfileActionPayload.ACTION_TOGGLE_ENABLED, Optional.empty());
        Check.isFalse(helper, owner.profile().isEnabled(), "toggling should switch graves off");
        act(owner, ProfileActionPayload.ACTION_TOGGLE_ENABLED, Optional.empty());
        Check.isTrue(helper, owner.profile().isEnabled(), "toggling again should switch graves back on");

        act(owner, ProfileActionPayload.ACTION_ALLOW, Optional.of(friend.id()));
        Check.isTrue(helper, owner.profile().allowed().contains(friend.id()), "allow action");

        act(owner, ProfileActionPayload.ACTION_ALLOW, Optional.of(owner.id()));
        Check.isFalse(helper, owner.profile().allowed().contains(owner.id()),
                "a player should never be added to their own allow list");

        act(owner, ProfileActionPayload.ACTION_DENY, Optional.of(friend.id()));
        Check.isFalse(helper, owner.profile().allowed().contains(friend.id()), "deny action");

        act(owner, ProfileActionPayload.ACTION_ALLOW, Optional.of(friend.id()));
        act(owner, ProfileActionPayload.ACTION_CLEAR, Optional.empty());
        Check.isTrue(helper, owner.profile().allowed().isEmpty(), "clear action");
        helper.succeed();
    }

    private static void act(TestPlayer player, int action, Optional<UUID> target) {
        GraveMenuHandlers.handleProfileAction(new ProfileActionPayload(action, target), player.context());
    }
}
