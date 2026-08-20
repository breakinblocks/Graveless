package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.integration.accessories.AccessoriesInventoryHook;
import com.breakinblocks.graveless.restore.RestoreEngine;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

public final class AccessoriesTests {
    private AccessoriesTests() {
    }

    static void register(TestRegistrar tests) {
        if (!Graveless.isModLoaded("accessories")) {
            return;
        }
        tests.add("accessories_capture_and_restore_round_trip", AccessoriesTests::captureAndRestoreRoundTrip);
        tests.add("accessories_restore_falls_back_to_inventory", AccessoriesTests::restoreFallsBackToInventory);
    }

    private static AccessoriesContainer anyContainer(GameTestHelper helper, TestPlayer player) {
        AccessoriesCapability capability = AccessoriesCapability.get(player.player());
        Check.notNull(helper, capability, "accessories capability on a server player");
        Map<String, AccessoriesContainer> containers = capability.getContainers();
        Check.isFalse(helper, containers.isEmpty(), "accessories should provide at least one slot container");
        return containers.values().iterator().next();
    }

    private static void captureAndRestoreRoundTrip(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        AccessoriesContainer container = anyContainer(helper, player);
        String type = container.getSlotName();
        container.getCosmeticAccessories().setItem(0, new ItemStack(Items.GOLDEN_APPLE));
        container.markChanged();

        player.simulateDeath();

        DeathRecord record = player.newestRecord();
        Check.notNull(helper, record, "a death with only an accessory still leaves a grave");
        Check.equal(helper, 1, record.entries().size(), "captured accessory entry count");
        CapturedEntry entry = record.entries().getFirst();
        Check.equal(helper, AccessoriesInventoryHook.ID, entry.handler(), "accessory entry handler");
        Check.equal(helper, type + "#cosmetic", entry.context(), "accessory entry context");
        Check.equal(helper, 0, entry.slot(), "accessory entry slot");
        Check.isTrue(helper, container.getCosmeticAccessories().getItem(0).isEmpty(),
                "the accessory should leave the slot on death");

        RestoreEngine.claim(player.player(), player.profile(), record, player.store());
        Check.isTrue(helper, player.records().isEmpty(), "grave should be claimed");
        ItemStack restored = container.getCosmeticAccessories().getItem(0);
        Check.isTrue(helper, restored.is(Items.GOLDEN_APPLE), "the accessory should return to its slot");
        Check.equal(helper, 1, restored.getCount(), "restored accessory count");
        Check.equal(helper, 0, player.countItems(), "nothing should spill into the main inventory");
        helper.succeed();
    }

    private static void restoreFallsBackToInventory(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        AccessoriesContainer container = anyContainer(helper, player);
        container.getCosmeticAccessories().setItem(0, new ItemStack(Items.EMERALD));
        container.markChanged();

        player.simulateDeath();
        DeathRecord record = player.newestRecord();
        Check.notNull(helper, record, "grave after an accessory death");

        for (int slot = 0; slot < container.getCosmeticAccessories().getContainerSize(); slot++) {
            container.getCosmeticAccessories().setItem(slot, new ItemStack(Items.STICK));
        }
        container.markChanged();

        RestoreEngine.claim(player.player(), player.profile(), record, player.store());
        Check.isTrue(helper, player.records().isEmpty(), "grave should be claimed");
        Check.isTrue(helper, container.getCosmeticAccessories().getItem(0).is(Items.STICK),
                "the occupying item should keep its slot");
        Check.equal(helper, 1, player.countOf(Items.EMERALD),
                "the blocked accessory should go to the main inventory");
        helper.succeed();
    }
}
