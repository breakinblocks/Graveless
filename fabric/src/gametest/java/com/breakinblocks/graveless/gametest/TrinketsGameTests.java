package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.data.GraveProfile;
import com.breakinblocks.graveless.data.GraveStore;
import com.breakinblocks.graveless.integration.trinkets.TrinketsInventoryHook;
import com.breakinblocks.graveless.restore.RestoreEngine;
import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketsApi;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

public class TrinketsGameTests {
    private static final String RING = "hand/ring";

    @GameTest
    public void trinketsRealDeathRestoresOriginalSlot(GameTestHelper helper) {
        ServerPlayer owner = TestPlayers.join(helper);
        TrinketInventory ring = ring(helper, owner);
        ring.setItem(0, new ItemStack(Items.DIAMOND, 2));
        owner.hurtServer(owner.level(), owner.damageSources().fellOutOfWorld(), Float.MAX_VALUE);

        check(helper, ring.getItem(0).isEmpty(), "the trinket slot was not emptied on death");
        List<ItemEntity> dropped =
                owner.level().getEntitiesOfClass(ItemEntity.class, new AABB(owner.blockPosition()).inflate(2));
        check(helper, dropped.isEmpty(), "trinkets were dropped on the ground: " + dropped);

        GraveStore store = GraveStore.get(owner.level().getServer());
        GraveProfile profile = store.profile(owner.getUUID());
        check(helper, !profile.records().isEmpty(), "death did not create a grave");
        DeathRecord grave = profile.records().getLast();
        check(helper, grave.entries().size() == 1, "grave should hold one entry, has " + grave.entries());
        CapturedEntry entry = grave.entries().getFirst();
        check(helper, TrinketsInventoryHook.ID.equals(entry.handler()), "entry handler was " + entry.handler());
        check(helper, entry.slot() == 0, "entry slot index was " + entry.slot());

        ServerPlayer recipient = TestPlayers.join(helper);
        TrinketInventory recipientRing = ring(helper, recipient);
        RestoreEngine.Result result = RestoreEngine.claim(recipient, profile, grave, store);
        check(helper, result.recordRemoved(), "claim left items in the grave");
        ItemStack restored = recipientRing.getItem(0);
        check(helper, restored.is(Items.DIAMOND) && restored.getCount() == 2, "trinket slot holds " + restored);
        check(
                helper,
                recipient.getInventory().countItem(Items.DIAMOND) == 0,
                "restored trinket also went into the main inventory");
        TestPlayers.leave(owner);
        TestPlayers.leave(recipient);
        helper.succeed();
    }

    @GameTest
    public void trinketsBlockedSlotFallsBackToInventory(GameTestHelper helper) {
        ServerPlayer owner = TestPlayers.join(helper);
        ring(helper, owner).setItem(0, new ItemStack(Items.DIAMOND));
        owner.hurtServer(owner.level(), owner.damageSources().fellOutOfWorld(), Float.MAX_VALUE);

        GraveStore store = GraveStore.get(owner.level().getServer());
        GraveProfile profile = store.profile(owner.getUUID());
        check(helper, !profile.records().isEmpty(), "death did not create a grave");
        DeathRecord grave = profile.records().getLast();

        ServerPlayer recipient = TestPlayers.join(helper);
        TrinketInventory recipientRing = ring(helper, recipient);
        recipientRing.setItem(0, new ItemStack(Items.GOLD_INGOT));
        RestoreEngine.claim(recipient, profile, grave, store);
        check(helper, recipientRing.getItem(0).is(Items.GOLD_INGOT), "existing trinket was replaced");
        check(
                helper,
                recipient.getInventory().countItem(Items.DIAMOND) == 1,
                "trinket with no free slot should go to the main inventory");
        check(helper, profile.records().isEmpty(), "claim left items in the grave");
        TestPlayers.leave(owner);
        TestPlayers.leave(recipient);
        helper.succeed();
    }

    private static TrinketInventory ring(GameTestHelper helper, ServerPlayer player) {
        TrinketInventory inventory = TrinketsApi.getAttachment(player).getInventory(RING);
        check(helper, inventory != null, "player has no hand/ring trinket slot");
        return inventory;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, Component.literal(message));
    }
}
