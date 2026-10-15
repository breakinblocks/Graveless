package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.capture.InventoryHooks;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.integration.armorcosmetic.ArmorCosmeticHook;
import com.breakinblocks.graveless.restore.RestoreEngine;
import com.skd.armorcosmetic.api.CosArmorAPI;
import com.skd.armorcosmetic.api.inventory.CAStacksBase;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ArmorCosmeticTests {
    private ArmorCosmeticTests() {}

    static void register(TestRegistrar tests) {
        tests.add("armor_cosmetic_real_death_restores_cosmetic_slots", ArmorCosmeticTests::realDeath);
        tests.add("armor_cosmetic_occupied_slot_falls_back_to_inventory", ArmorCosmeticTests::occupiedSlot);
    }

    private static CAStacksBase slots(GameTestHelper helper, TestPlayer player) {
        Check.notNull(helper, InventoryHooks.byId(ArmorCosmeticHook.ID), "Armor Cosmetic restore hook is registered");
        return CosArmorAPI.getCAStacks(player.id());
    }

    private static DeathRecord dieWithCosmetics(GameTestHelper helper, TestPlayer owner) {
        CAStacksBase stacks = slots(helper, owner);
        stacks.setStackInSlot(0, new ItemStack(Items.IRON_BOOTS));
        stacks.setStackInSlot(3, new ItemStack(Items.DIAMOND_HELMET));
        owner.killForReal();
        Check.isTrue(helper, stacks.getStackInSlot(0).isEmpty(), "cosmetic boots leave the slot on death");
        Check.isTrue(helper, stacks.getStackInSlot(3).isEmpty(), "cosmetic helmet leaves the slot on death");
        DeathRecord grave = owner.newestRecord();
        Check.notNull(helper, grave, "real death creates a grave for cosmetic armor");
        Check.equal(helper, 2, grave.itemCount(), "both cosmetic pieces are captured");
        Check.isTrue(
                helper,
                grave.entries().stream().allMatch(entry -> entry.handler().equals(ArmorCosmeticHook.ID)),
                "cosmetic slot indices are kept in the grave");
        return grave;
    }

    private static void realDeath(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        DeathRecord grave = dieWithCosmetics(helper, owner);
        TestPlayer recipient = TestPlayer.join(helper);
        CAStacksBase recipientSlots = slots(helper, recipient);
        RestoreEngine.Result result = RestoreEngine.claim(recipient.player(), owner.profile(), grave, owner.store());
        Check.equal(helper, 2, result.restored(), "both cosmetic pieces restore");
        Check.isTrue(helper, recipientSlots.getStackInSlot(0).is(Items.IRON_BOOTS), "boots return to their slot");
        Check.isTrue(helper, recipientSlots.getStackInSlot(3).is(Items.DIAMOND_HELMET), "helmet returns to its slot");
        Check.equal(helper, 0, recipient.countItems(), "restored cosmetics stay out of main inventory");
        helper.succeed();
    }

    private static void occupiedSlot(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        DeathRecord grave = dieWithCosmetics(helper, owner);
        TestPlayer recipient = TestPlayer.join(helper);
        CAStacksBase recipientSlots = slots(helper, recipient);
        recipientSlots.setStackInSlot(3, new ItemStack(Items.GOLDEN_HELMET));
        RestoreEngine.claim(recipient.player(), owner.profile(), grave, owner.store());
        Check.isTrue(helper, recipientSlots.getStackInSlot(3).is(Items.GOLDEN_HELMET), "existing cosmetic is kept");
        Check.isTrue(helper, recipientSlots.getStackInSlot(0).is(Items.IRON_BOOTS), "free slot still restores");
        Check.equal(helper, 1, recipient.countOf(Items.DIAMOND_HELMET), "blocked cosmetic goes to main inventory");
        Check.isTrue(helper, owner.records().isEmpty(), "grave is emptied");
        helper.succeed();
    }
}
