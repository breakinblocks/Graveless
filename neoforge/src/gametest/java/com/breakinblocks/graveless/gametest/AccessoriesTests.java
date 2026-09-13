package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.integration.accessories.AccessoriesInventoryHook;
import com.breakinblocks.graveless.restore.RestoreEngine;
import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.DropRule;
import io.wispforest.accessories.api.events.OnDropCallback;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AccessoriesTests {
    private static final Map<UUID, DropRule> DROP_OVERRIDES = new HashMap<>();
    private static final Map<UUID, Integer> DROP_CALLS = new HashMap<>();
    private AccessoriesTests() {
    }

    static void register(TestRegistrar tests) {
        if (!Graveless.isModLoaded("accessories")) {
            return;
        }
        tests.add("accessories_capture_and_restore_round_trip", AccessoriesTests::captureAndRestoreRoundTrip);
        tests.add("accessories_restore_falls_back_to_inventory", AccessoriesTests::restoreFallsBackToInventory);
        tests.add("accessories_original_slots_precede_fallback", AccessoriesTests::originalSlotsFirst);
        tests.addIsolated("accessories_respects_keep_accessory_inventory", AccessoriesTests::keepAccessoryInventory);
        tests.add("accessories_respects_drop_callback_keep", h -> dropOverride(h, DropRule.KEEP));
        tests.add("accessories_respects_drop_callback_destroy", h -> dropOverride(h, DropRule.DESTROY));
        tests.add("accessories_explicit_drop_overrides_vanishing", h -> dropOverride(h, DropRule.DROP));
        tests.add("accessories_restore_preserves_oversized_remainder", AccessoriesTests::oversizedRemainder);
        OnDropCallback.EVENT.register((rule, stack, slot, source) -> {
            UUID id = slot.entity().getUUID();
            if (DROP_OVERRIDES.containsKey(id)) {
                DROP_CALLS.merge(id, 1, Integer::sum);
                return DROP_OVERRIDES.get(id);
            }
            return rule;
        });
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

        player.killForReal();

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

        player.killForReal();
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

    private static void originalSlotsFirst(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        AccessoriesContainer container = anyContainer(helper, player);
        container.addTransientModifier(new AttributeModifier(Graveless.id("test_slots"), 2,
                AttributeModifier.Operation.ADD_VALUE));
        container.update();
        Check.isTrue(helper, container.getSize() >= 3, "test has three accessory slots");
        player.give(0, new ItemStack(Items.STICK));
        player.simulateDeath();
        DeathRecord grave = player.newestRecord();
        grave.entries().clear();
        String context = container.getSlotName() + "#cosmetic";
        grave.entries().add(new CapturedEntry(AccessoriesInventoryHook.ID, context, 0, new ItemStack(Items.DIAMOND)));
        grave.entries().add(new CapturedEntry(AccessoriesInventoryHook.ID, context, 1, new ItemStack(Items.EMERALD)));
        container.getCosmeticAccessories().setItem(0, new ItemStack(Items.GOLD_INGOT));
        RestoreEngine.claim(player.player(), player.profile(), grave, player.store());
        Check.isTrue(helper, container.getCosmeticAccessories().getItem(0).is(Items.GOLD_INGOT), "existing item preserved");
        Check.isTrue(helper, container.getCosmeticAccessories().getItem(1).is(Items.EMERALD), "free original slot preserved");
        Check.isTrue(helper, container.getCosmeticAccessories().getItem(2).is(Items.DIAMOND), "blocked item restored afterward");
        Check.isTrue(helper, grave.isEmpty(), "both accessories restored");
        helper.succeed();
    }

    private static void keepAccessoryInventory(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        AccessoriesContainer container = anyContainer(helper, player);
        TestCleanup.attach(helper).gameRule(helper.getLevel(), Accessories.RULE_KEEP_ACCESSORY_INVENTORY, true);
        container.getCosmeticAccessories().setItem(0, new ItemStack(Items.EMERALD));
        player.give(0, new ItemStack(Items.DIAMOND));
        player.killForReal();
        Check.isTrue(helper, container.getCosmeticAccessories().getItem(0).is(Items.EMERALD), "accessory gamerule keeps item equipped");
        Check.equal(helper, 1, player.newestRecord().itemCount(), "ordinary inventory still captured");
        Check.isTrue(helper, player.newestRecord().entries().getFirst().stack().is(Items.DIAMOND), "grave contains ordinary item only");
        helper.succeed();
    }

    private static void dropOverride(GameTestHelper helper, DropRule rule) {
        TestPlayer player = TestPlayer.join(helper);
        AccessoriesContainer container = anyContainer(helper, player);
        UUID id = player.id();
        DROP_OVERRIDES.put(id, rule);
        TestCleanup.attach(helper).onFinish(() -> {
            DROP_OVERRIDES.remove(id);
            DROP_CALLS.remove(id);
        });
        ItemStack accessory = new ItemStack(Items.DIAMOND_HELMET);
        accessory.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.VANISHING_CURSE), 1);
        container.getCosmeticAccessories().setItem(0, accessory);
        player.killForReal();
        Check.equal(helper, 1, DROP_CALLS.getOrDefault(id, 0).intValue(), "Accessories drop callback runs once");
        if (rule == DropRule.DROP) {
            Check.equal(helper, 1, player.newestRecord().itemCount(), "explicit drop preserves even a vanishing accessory");
            Check.equal(helper, AccessoriesInventoryHook.ID, player.newestRecord().entries().getFirst().handler(), "slot metadata retained");
        } else {
            Check.isTrue(helper, player.records().isEmpty(), "kept or destroyed item does not enter a grave");
        }
        Check.equal(helper, rule == DropRule.KEEP, !container.getCosmeticAccessories().getItem(0).isEmpty(), "slot follows resolved drop rule");
        helper.succeed();
    }

    private static void oversizedRemainder(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        AccessoriesContainer container = anyContainer(helper, player);
        player.give(0, new ItemStack(Items.DIAMOND, 64));
        player.simulateDeath();
        DeathRecord grave = player.newestRecord();
        grave.entries().clear();
        grave.entries().add(new CapturedEntry(AccessoriesInventoryHook.ID, container.getSlotName() + "#cosmetic",
                0, new ItemStack(Items.DIAMOND, 64)));
        RestoreEngine.claim(player.player(), player.profile(), grave, player.store());
        int equipped = 0;
        for (int slot = 0; slot < container.getSize(); slot++) {
            ItemStack stack = container.getCosmeticAccessories().getItem(slot);
            equipped += stack.getCount();
            Check.isTrue(helper, stack.getCount() <= container.getCosmeticAccessories().getMaxStackSize(stack), "accessory slot limit honored");
        }
        Check.equal(helper, 64, equipped + player.countOf(Items.DIAMOND) + grave.itemCount(), "oversized restoration conserves items");
        helper.succeed();
    }
}
