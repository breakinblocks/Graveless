package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.capture.InventoryHooks;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.integration.curios.CuriosInventoryHook;
import com.breakinblocks.graveless.registry.ModDataComponents;
import com.breakinblocks.graveless.restore.RestoreEngine;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.common.DropRule;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;
import top.theillusivec4.curios.config.CuriosConfig;
import top.theillusivec4.curios.impl.CuriosRegistry;

public final class CuriosTests {
    private CuriosTests() {}

    static void register(TestRegistrar tests) {
        tests.add("curios_real_death_restores_equipped_and_cosmetic_slots", CuriosTests::realDeath);
        tests.add("curios_original_slots_precede_alternative_slots", CuriosTests::originalSlotsFirst);
        tests.addIsolated("curios_kept_on_death_lose_slot_marker", CuriosTests::keptCuriosLoseMarker);
    }

    private static CurioStacksHandler slots(GameTestHelper helper, TestPlayer player) {
        var inventory = CuriosApi.getCuriosInventoryOrNull(player.player());
        Check.notNull(helper, inventory, "Curios inventory is available");
        Check.notNull(helper, InventoryHooks.byId(CuriosInventoryHook.ID), "Curios restore hook is registered");
        var stacks = new CurioStacksHandler(
                player.player().getData(CuriosRegistry.INVENTORY.get()),
                "ring",
                3,
                true,
                true,
                true,
                DropRule.ALWAYS_DROP);
        inventory.setCurios(Map.of("ring", stacks));
        return stacks;
    }

    private static void realDeath(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        var stacks = slots(helper, owner);
        stacks.getStacks().setStackInSlot(0, new ItemStack(Items.DIAMOND));
        stacks.getCosmeticStacks().setStackInSlot(1, new ItemStack(Items.EMERALD));
        owner.killForReal();
        DeathRecord grave = owner.newestRecord();
        Check.notNull(helper, grave, "real death creates a grave for Curios drops");
        Check.equal(helper, 2, grave.itemCount(), "both Curios inventories are captured");
        Check.isTrue(
                helper,
                grave.entries().stream().allMatch(entry -> entry.handler().equals(CuriosInventoryHook.ID)),
                "Curios slot metadata survives the death-drop event");
        TestPlayer recipient = TestPlayer.join(helper);
        var recipientSlots = slots(helper, recipient);
        RestoreEngine.Result result = RestoreEngine.claim(recipient.player(), owner.profile(), grave, owner.store());
        Check.equal(helper, 2, result.restored(), "both Curios items restore");
        Check.isTrue(helper, recipientSlots.getStacks().getStackInSlot(0).is(Items.DIAMOND), "equipped slot restored");
        Check.isTrue(
                helper,
                recipientSlots.getCosmeticStacks().getStackInSlot(1).is(Items.EMERALD),
                "cosmetic slot restored");
        Check.equal(helper, 0, recipient.countItems(), "restored Curios stay out of main inventory");
        helper.succeed();
    }

    private static void keptCuriosLoseMarker(GameTestHelper helper) {
        TestCleanup cleanup = TestCleanup.attach(helper);
        cleanup.config(CuriosConfig.SERVER.keepCurios, CuriosConfig.KeepCurios.ON);
        TestPlayer owner = TestPlayer.join(helper);
        var stacks = slots(helper, owner);
        stacks.getStacks().setStackInSlot(0, new ItemStack(Items.DIAMOND));
        stacks.getCosmeticStacks().setStackInSlot(1, new ItemStack(Items.EMERALD));
        owner.killForReal();
        ItemStack kept = stacks.getStacks().getStackInSlot(0);
        ItemStack keptCosmetic = stacks.getCosmeticStacks().getStackInSlot(1);
        Check.isTrue(helper, kept.is(Items.DIAMOND), "kept Curio stays equipped");
        Check.isTrue(helper, keptCosmetic.is(Items.EMERALD), "kept cosmetic Curio stays equipped");
        Check.isTrue(helper, !kept.has(ModDataComponents.CURIO_SLOT.get()), "kept Curio loses the slot marker");
        Check.isTrue(
                helper,
                !keptCosmetic.has(ModDataComponents.CURIO_SLOT.get()),
                "kept cosmetic Curio loses the slot marker");
        helper.succeed();
    }

    private static void originalSlotsFirst(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND));
        player.simulateDeath();
        DeathRecord grave = player.newestRecord();
        grave.entries().clear();
        grave.entries()
                .add(new CapturedEntry(CuriosInventoryHook.ID, "ring#cosmetic", 0, new ItemStack(Items.DIAMOND)));
        grave.entries()
                .add(new CapturedEntry(CuriosInventoryHook.ID, "ring#cosmetic", 1, new ItemStack(Items.EMERALD)));
        var stacks = slots(helper, player).getCosmeticStacks();
        stacks.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT));
        RestoreEngine.claim(player.player(), player.profile(), grave, player.store());
        Check.isTrue(helper, stacks.getStackInSlot(0).is(Items.GOLD_INGOT), "existing Curio is preserved");
        Check.isTrue(helper, stacks.getStackInSlot(1).is(Items.EMERALD), "free original Curio slot is preserved");
        Check.isTrue(helper, stacks.getStackInSlot(2).is(Items.DIAMOND), "blocked Curio uses an alternative afterward");
        Check.isTrue(helper, player.records().isEmpty(), "Curios restoration empties grave");
        helper.succeed();
    }
}
