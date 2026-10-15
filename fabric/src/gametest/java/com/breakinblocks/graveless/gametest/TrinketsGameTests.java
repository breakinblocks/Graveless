package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.data.GraveProfile;
import com.breakinblocks.graveless.data.GraveStore;
import com.breakinblocks.graveless.integration.trinkets.TrinketsInventoryHook;
import com.breakinblocks.graveless.restore.RestoreEngine;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

public class TrinketsGameTests implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void trinketsRealDeathRestoresOriginalSlot(GameTestHelper helper) {
        ServerPlayer owner = TestPlayers.join(helper);
        TrinketInventory ring = ring(helper, owner);
        ring.setItem(0, new ItemStack(Items.DIAMOND, 2));
        owner.hurt(owner.damageSources().fellOutOfWorld(), Float.MAX_VALUE);

        helper.assertTrue(ring.getItem(0).isEmpty(), "the trinket slot was not emptied on death");
        List<ItemEntity> dropped =
                owner.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(owner.blockPosition()).inflate(8));
        helper.assertTrue(dropped.isEmpty(), "trinkets were dropped on the ground: " + dropped);

        GraveStore store = GraveStore.get(owner.server);
        GraveProfile profile = store.profile(owner.getUUID());
        helper.assertFalse(profile.records().isEmpty(), "death did not create a grave");
        DeathRecord grave = profile.records().getLast();
        helper.assertTrue(grave.entries().size() == 1, "grave should hold one entry, has " + grave.entries());
        CapturedEntry entry = grave.entries().getFirst();
        helper.assertTrue(TrinketsInventoryHook.ID.equals(entry.handler()), "entry handler was " + entry.handler());
        helper.assertTrue("hand/ring".equals(entry.context()), "entry slot type was " + entry.context());
        helper.assertTrue(entry.slot() == 0, "entry slot index was " + entry.slot());

        ServerPlayer recipient = TestPlayers.join(helper);
        TrinketInventory recipientRing = ring(helper, recipient);
        RestoreEngine.Result result = RestoreEngine.claim(recipient, profile, grave, store);
        helper.assertTrue(result.recordRemoved(), "claim left items in the grave");
        ItemStack restored = recipientRing.getItem(0);
        helper.assertTrue(restored.is(Items.DIAMOND) && restored.getCount() == 2, "trinket slot holds " + restored);
        helper.assertTrue(
                recipient.getInventory().countItem(Items.DIAMOND) == 0,
                "restored trinket also went into the main inventory");
        TestPlayers.leave(owner);
        TestPlayers.leave(recipient);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void trinketsBlockedSlotFallsBackToInventory(GameTestHelper helper) {
        ServerPlayer owner = TestPlayers.join(helper);
        ring(helper, owner).setItem(0, new ItemStack(Items.DIAMOND));
        owner.hurt(owner.damageSources().fellOutOfWorld(), Float.MAX_VALUE);

        GraveStore store = GraveStore.get(owner.server);
        GraveProfile profile = store.profile(owner.getUUID());
        helper.assertFalse(profile.records().isEmpty(), "death did not create a grave");
        DeathRecord grave = profile.records().getLast();

        ServerPlayer recipient = TestPlayers.join(helper);
        TrinketInventory recipientRing = ring(helper, recipient);
        recipientRing.setItem(0, new ItemStack(Items.GOLD_INGOT));
        RestoreEngine.claim(recipient, profile, grave, store);
        helper.assertTrue(recipientRing.getItem(0).is(Items.GOLD_INGOT), "existing trinket was replaced");
        helper.assertTrue(
                recipient.getInventory().countItem(Items.DIAMOND) == 1,
                "trinket with no free slot should go to the main inventory");
        helper.assertTrue(profile.records().isEmpty(), "claim left items in the grave");
        TestPlayers.leave(owner);
        TestPlayers.leave(recipient);
        helper.succeed();
    }

    private static TrinketInventory ring(GameTestHelper helper, ServerPlayer player) {
        TrinketInventory inventory = TrinketsApi.getTrinketComponent(player)
                .map(component ->
                        component.getInventory().getOrDefault("hand", Map.of()).get("ring"))
                .orElse(null);
        helper.assertTrue(inventory != null, "player has no hand/ring trinket slot");
        return inventory;
    }
}
