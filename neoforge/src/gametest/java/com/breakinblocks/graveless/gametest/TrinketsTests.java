package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.capture.InventoryHooks;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.integration.trinkets.TrinketsInventoryHook;
import com.breakinblocks.graveless.restore.RestoreEngine;
import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketsApi;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

public final class TrinketsTests {
    private static final String RING = "hand/ring";

    private TrinketsTests() {}

    static void register(TestRegistrar tests) {
        tests.add("trinkets_real_death_restores_original_slot", TrinketsTests::realDeath);
        tests.add("trinkets_blocked_slot_falls_back_to_inventory", TrinketsTests::blockedSlot);
    }

    private static TrinketInventory ring(GameTestHelper helper, TestPlayer player) {
        Check.notNull(helper, InventoryHooks.byId(TrinketsInventoryHook.ID), "Trinkets restore hook is registered");
        TrinketAttachment attachment = TrinketsApi.getAttachment(player.player());
        Check.notNull(helper, attachment, "Trinkets attachment is available");
        TrinketInventory inventory = attachment.getInventory(RING);
        Check.notNull(helper, inventory, "player has a hand/ring trinket slot");
        return inventory;
    }

    private static void realDeath(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        TrinketInventory ring = ring(helper, owner);
        ring.setItem(0, new ItemStack(Items.DIAMOND, 2));
        owner.killForReal();

        Check.isTrue(helper, ring.getItem(0).isEmpty(), "trinket slot is emptied on death");
        var dropped = owner.player()
                .level()
                .getEntitiesOfClass(ItemEntity.class, new AABB(owner.player().blockPosition()).inflate(2));
        Check.isTrue(helper, dropped.isEmpty(), "trinkets were dropped on the ground: " + dropped);
        DeathRecord grave = owner.newestRecord();
        Check.notNull(helper, grave, "real death creates a grave for Trinkets drops");
        Check.equal(helper, 1, grave.entries().size(), "grave holds one entry");
        CapturedEntry entry = grave.entries().getFirst();
        Check.equal(helper, TrinketsInventoryHook.ID, entry.handler(), "entry handler");
        Check.equal(helper, 0, entry.slot(), "entry slot index");

        TestPlayer recipient = TestPlayer.join(helper);
        TrinketInventory recipientRing = ring(helper, recipient);
        RestoreEngine.Result result = RestoreEngine.claim(recipient.player(), owner.profile(), grave, owner.store());
        Check.isTrue(helper, result.recordRemoved(), "claim empties the grave");
        ItemStack restored = recipientRing.getItem(0);
        Check.isTrue(
                helper, restored.is(Items.DIAMOND) && restored.getCount() == 2, "trinket slot restored: " + restored);
        Check.equal(helper, 0, recipient.countItems(), "restored trinket stays out of main inventory");
        helper.succeed();
    }

    private static void blockedSlot(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        ring(helper, owner).setItem(0, new ItemStack(Items.DIAMOND));
        owner.killForReal();
        DeathRecord grave = owner.newestRecord();
        Check.notNull(helper, grave, "real death creates a grave for Trinkets drops");

        TestPlayer recipient = TestPlayer.join(helper);
        TrinketInventory recipientRing = ring(helper, recipient);
        recipientRing.setItem(0, new ItemStack(Items.GOLD_INGOT));
        RestoreEngine.claim(recipient.player(), owner.profile(), grave, owner.store());
        Check.isTrue(helper, recipientRing.getItem(0).is(Items.GOLD_INGOT), "existing trinket is preserved");
        Check.equal(helper, 1, recipient.countOf(Items.DIAMOND), "trinket with no valid slot goes to main inventory");
        Check.isTrue(helper, owner.records().isEmpty(), "claim empties the grave");
        helper.succeed();
    }
}
