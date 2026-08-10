package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.restore.RestoreEngine;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class RestoreTests {
    private RestoreTests() {
    }

    static void register(TestRegistrar tests) {
        tests.add("restore_returns_items_to_original_slots", RestoreTests::returnsItemsToOriginalSlots);
        tests.add("restore_leaves_leftovers_when_inventory_is_full", RestoreTests::leavesLeftoversWhenFull);
        tests.add("restore_merges_into_a_matching_stack", RestoreTests::mergesIntoMatchingStack);
        tests.add("restore_returns_stored_xp", RestoreTests::returnsStoredXp);
        tests.add("restore_keeps_the_grave_until_it_is_empty", RestoreTests::keepsGraveUntilEmpty);
    }

    private static void returnsItemsToOriginalSlots(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND_SWORD));
        player.give(17, new ItemStack(Items.BREAD, 12));
        player.give(38, new ItemStack(Items.IRON_CHESTPLATE));
        player.give(Inventory.SLOT_OFFHAND, new ItemStack(Items.SHIELD));
        player.simulateDeath();

        DeathRecord record = player.newestRecord();
        RestoreEngine.Result result = RestoreEngine.claim(player.player(), player.profile(), record, player.store());

        Check.equal(helper, 15, result.restored(), "restored item count");
        Check.equal(helper, 0, result.remaining(), "items left in the grave");
        Check.isTrue(helper, result.recordRemoved(), "an emptied grave is removed");
        Check.isTrue(helper, player.records().isEmpty(), "profile still lists the claimed grave");
        Check.isTrue(helper, player.itemAt(0).is(Items.DIAMOND_SWORD), "hotbar slot 0");
        Check.isTrue(helper, player.itemAt(17).is(Items.BREAD), "main inventory slot 17");
        Check.equal(helper, 12, player.itemAt(17).getCount(), "main inventory slot 17 count");
        Check.isTrue(helper, player.itemAt(38).is(Items.IRON_CHESTPLATE), "armour slot 38");
        Check.isTrue(helper, player.itemAt(Inventory.SLOT_OFFHAND).is(Items.SHIELD), "offhand slot");
        helper.succeed();
    }

    private static void leavesLeftoversWhenFull(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 3));
        player.give(1, new ItemStack(Items.EMERALD, 2));
        player.simulateDeath();

        DeathRecord record = player.newestRecord();
        player.fillInventory(new ItemStack(Items.STONE, 64));

        RestoreEngine.Result result = RestoreEngine.claim(player.player(), player.profile(), record, player.store());
        Check.equal(helper, 0, result.restored(), "nothing fits into a full inventory");
        Check.equal(helper, 5, result.remaining(), "items still waiting in the grave");
        Check.isFalse(helper, result.recordRemoved(), "a partly claimed grave stays");
        Check.equal(helper, 1, player.records().size(), "grave count after a partial claim");
        Check.equal(helper, 5, record.itemCount(), "grave item count after a partial claim");

        player.give(4, ItemStack.EMPTY);
        RestoreEngine.Result second = RestoreEngine.claim(player.player(), player.profile(), record, player.store());
        Check.equal(helper, 3, second.restored(), "the freed slot took the first stack");
        Check.equal(helper, 2, second.remaining(), "the rest is still waiting");
        Check.isFalse(helper, second.recordRemoved(), "grave survives a second partial claim");

        player.give(6, ItemStack.EMPTY);
        RestoreEngine.Result third = RestoreEngine.claim(player.player(), player.profile(), record, player.store());
        Check.equal(helper, 2, third.restored(), "the last stack came back");
        Check.isTrue(helper, third.recordRemoved(), "grave removed once it is empty");
        Check.isTrue(helper, player.records().isEmpty(), "profile is clear once everything is recovered");
        helper.succeed();
    }

    private static void mergesIntoMatchingStack(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.BREAD, 20));
        player.simulateDeath();

        player.give(0, new ItemStack(Items.BREAD, 50));
        DeathRecord record = player.newestRecord();
        RestoreEngine.Result result = RestoreEngine.claim(player.player(), player.profile(), record, player.store());

        Check.equal(helper, 20, result.restored(), "restored item count when merging");
        Check.isTrue(helper, result.recordRemoved(), "grave removed after a merging claim");
        Check.equal(helper, 64, player.itemAt(0).getCount(), "slot 0 filled to the stack limit");
        Check.equal(helper, 70, player.countOf(Items.BREAD), "total bread after merging");
        helper.succeed();
    }

    private static void returnsStoredXp(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 1));
        player.player().giveExperiencePoints(900);
        int granted = player.storedXp();
        player.simulateDeath();

        DeathRecord record = player.newestRecord();
        int stored = record.xp();
        Check.equal(helper, granted, stored, "grave xp matches what the player had");

        RestoreEngine.Result result = RestoreEngine.claim(player.player(), player.profile(), record, player.store());
        Check.equal(helper, stored, result.xpRestored(), "reported xp restored");
        Check.equal(helper, 0, record.xp(), "grave xp is drained after a claim");
        Check.isTrue(helper, Math.abs(player.storedXp() - stored) <= 2,
                "player xp after a claim, saw " + player.storedXp() + " expected " + stored);
        helper.succeed();
    }

    private static void keepsGraveUntilEmpty(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 1));
        player.player().giveExperiencePoints(200);
        player.simulateDeath();

        DeathRecord record = player.newestRecord();
        player.fillInventory(new ItemStack(Items.STONE, 64));

        RestoreEngine.Result result = RestoreEngine.claim(player.player(), player.profile(), record, player.store());
        Check.isTrue(helper, result.xpRestored() > 0, "xp comes back even on a partial claim");
        Check.isFalse(helper, result.recordRemoved(), "grave stays while items remain");
        Check.equal(helper, 1, record.itemCount(), "grave still holds the item that did not fit");
        Check.equal(helper, 0, record.xp(), "grave xp is drained even on a partial claim");
        helper.succeed();
    }
}
