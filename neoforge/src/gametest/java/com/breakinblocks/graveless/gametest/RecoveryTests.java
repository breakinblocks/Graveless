package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.capture.InventoryHook;
import com.breakinblocks.graveless.capture.InventoryHooks;
import com.breakinblocks.graveless.capture.VanillaInventoryHook;
import com.breakinblocks.graveless.client.GhostClientManager;
import com.breakinblocks.graveless.client.GhostInteraction;
import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.event.DeathCaptureEvents;
import com.breakinblocks.graveless.event.GhostSyncEvents;
import com.breakinblocks.graveless.event.GraveMenuHandlers;
import com.breakinblocks.graveless.event.SpiritWardEvents;
import com.breakinblocks.graveless.net.GravelessNetworking.ClaimRequestPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveDetailPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveDetailRequestPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveExtractPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveActionPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveListPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveOpenPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GhostAddPayload;
import com.breakinblocks.graveless.net.GravelessNetworking.GhostRemovePayload;
import com.breakinblocks.graveless.registry.ModEffects;
import com.breakinblocks.graveless.registry.ModItems;
import com.breakinblocks.graveless.restore.RestoreEngine;
import com.breakinblocks.graveless.util.SpiritCompassManager;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.List;

public final class RecoveryTests {
    private RecoveryTests() {
    }

    static void register(TestRegistrar tests) {
        tests.add("recovery_full_inventory_compass", RecoveryTests::compass);
        tests.add("recovery_preserve_unblocked_slots", RecoveryTests::slots);
        tests.add("recovery_trusted_player_details", RecoveryTests::details);
        tests.add("recovery_extract_partial_insert_conservation", RecoveryTests::extract);
        tests.addIsolated("recovery_hook_failure_preserves_pending", RecoveryTests::hookFailure);
        tests.addIsolated("recovery_partial_hook_failure_does_not_duplicate", RecoveryTests::partialHookFailure);
        tests.add("recovery_compass_retargets_when_inventory_is_full", RecoveryTests::remainingGraveCompass);
        tests.add("recovery_trusted_claim_preserves_both_compasses", RecoveryTests::trustedClaim);
        tests.add("recovery_ghost_reset_removes_then_resends", RecoveryTests::ghostReset);
        tests.addIsolated("recovery_targeting_respects_line_of_sight_setting", RecoveryTests::targeting);
        tests.addIsolated("recovery_ward_linger", RecoveryTests::linger);
        tests.addIsolated("recovery_ward_preserves_potions", RecoveryTests::potions);
        tests.addIsolated("recovery_ward_preserves_later_potions", RecoveryTests::laterPotions);
        tests.addIsolated("recovery_ward_linger_expires", RecoveryTests::lingerExpires);
        tests.addIsolated("recovery_ward_zero_linger", RecoveryTests::zeroLinger);
        tests.addIsolated("recovery_browser_extract_lingers", h -> browserLinger(h, false));
        tests.addIsolated("recovery_browser_xp_lingers", h -> browserLinger(h, true));
    }

    private static void compass(GameTestHelper h) {
        for (int compassSlot : new int[]{0, 17, Inventory.SLOT_OFFHAND}) {
            TestPlayer p = TestPlayer.join(h);
            List<ItemStack> original = fillDistinctInventory(p);
            p.simulateDeath();
            int captured = p.newestRecord().itemCount();
            DeathCaptureEvents.onRespawn(p.player());
            ItemStack compass = p.itemAt(0);
            p.give(0, ItemStack.EMPTY);
            p.give(compassSlot, compass);
            RestoreEngine.Result r = RestoreEngine.claim(p.player(), p.profile(), p.newestRecord(), p.store());
            Check.equal(h, 0, r.remaining(), "all stacks should return with compass in slot " + compassSlot);
            Check.equal(h, captured, r.restored(), "restored count");
            for (int slot = 0; slot < original.size(); slot++) {
                Check.isTrue(h, ItemStack.matches(original.get(slot), p.itemAt(slot)),
                        "original item and components in slot " + slot);
            }
            Check.equal(h, 0, p.countOf(ModItems.SPIRIT_COMPASS.get()), "last grave removes compass");
        }
        h.succeed();
    }

    private static List<ItemStack> fillDistinctInventory(TestPlayer p) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Slot " + slot));
            p.give(slot, stack);
        }
        p.give(36, new ItemStack(Items.IRON_BOOTS));
        p.give(37, new ItemStack(Items.IRON_LEGGINGS));
        p.give(38, new ItemStack(Items.IRON_CHESTPLATE));
        p.give(39, new ItemStack(Items.IRON_HELMET));
        p.give(Inventory.SLOT_OFFHAND, new ItemStack(Items.SHIELD));
        List<ItemStack> original = new ArrayList<>();
        for (int slot = 0; slot < p.player().getInventory().getContainerSize(); slot++) {
            original.add(p.itemAt(slot).copy());
        }
        return original;
    }

    private static void remainingGraveCompass(GameTestHelper h) {
        TestPlayer p = TestPlayer.join(h);
        p.give(0, new ItemStack(Items.EMERALD));
        p.simulateDeath();
        DeathRecord older = p.newestRecord();
        fillDistinctInventory(p);
        p.simulateDeath();
        DeathCaptureEvents.onRespawn(p.player());
        p.profile().setTrackedRecordId(p.newestRecord().id());
        RestoreEngine.Result result = RestoreEngine.claim(p.player(), p.profile(), p.newestRecord(), p.store());
        Check.equal(h, 0, result.remaining(), "second grave is fully restored");
        Check.equal(h, 1, p.records().size(), "older grave remains");
        List<ItemEntity> compasses = p.player().level().getEntitiesOfClass(ItemEntity.class,
                p.player().getBoundingBox().inflate(4), e -> e.getItem().is(ModItems.SPIRIT_COMPASS.get()));
        Check.equal(h, 1, compasses.size(), "only the displaced compass is dropped");
        Check.equal(h, older.pos().dimension(), compasses.getFirst().getItem()
                .get(DataComponents.LODESTONE_TRACKER).target().orElseThrow().dimension(), "dropped compass dimension");
        Check.equal(h, p.anchorOf(older), compasses.getFirst().getItem()
                .get(DataComponents.LODESTONE_TRACKER).target().orElseThrow().pos(), "dropped compass target");
        RestoreEngine.claim(p.player(), p.profile(), older, p.store());
        Check.equal(h, 1, older.itemCount(), "ordinary overflow remains in grave");
        Check.equal(h, 0, p.countOf(ModItems.SPIRIT_COMPASS.get()), "retry does not issue a new compass");
        h.succeed();
    }

    private static void trustedClaim(GameTestHelper h) {
        TestPlayer owner = TestPlayer.join(h);
        TestPlayer friend = TestPlayer.join(h);
        owner.give(0, new ItemStack(Items.DIAMOND_SWORD));
        owner.simulateDeath();
        DeathCaptureEvents.onRespawn(owner.player());
        friend.give(0, new ItemStack(Items.EMERALD));
        friend.simulateDeath();
        DeathCaptureEvents.onRespawn(friend.player());
        owner.profile().allowed().add(friend.id());
        friend.moveToRecord(owner.newestRecord());
        GhostSyncEvents.handleClaimRequest(new ClaimRequestPayload(owner.newestRecord().id()), friend.context());
        Check.isTrue(h, friend.itemAt(0).is(Items.DIAMOND_SWORD), "friend gets original slot");
        Check.equal(h, 1, friend.countOf(ModItems.SPIRIT_COMPASS.get()), "friend keeps compass for own grave");
        Check.equal(h, friend.anchorOf(friend.newestRecord()), friend.compassTarget().target().orElseThrow().pos(), "friend compass target");
        Check.equal(h, 0, owner.countOf(ModItems.SPIRIT_COMPASS.get()), "owner's last grave removes owner's compass");
        h.succeed();
    }

    private static void slots(GameTestHelper h) {
        TestPlayer p = TestPlayer.join(h);
        p.give(0, new ItemStack(Items.DIAMOND_SWORD));
        p.give(1, new ItemStack(Items.DIAMOND_PICKAXE));
        p.simulateDeath();
        p.give(0, new ItemStack(Items.STONE, 64));
        RestoreEngine.claim(p.player(), p.profile(), p.newestRecord(), p.store());
        Check.isTrue(h, p.itemAt(1).is(Items.DIAMOND_PICKAXE), "unblocked original slot 1 must keep the pickaxe");
        h.succeed();
    }

    private static void details(GameTestHelper h) {
        TestPlayer owner = TestPlayer.join(h);
        TestPlayer friend = TestPlayer.join(h);
        owner.give(0, new ItemStack(Items.DIAMOND));
        owner.simulateDeath();
        owner.profile().allowed().add(friend.id());
        friend.moveToRecord(owner.newestRecord());
        friend.clearOutbound();
        GraveMenuHandlers.handleOpenRequest(new GraveOpenPayload(owner.newestRecord().id()), friend.context());
        Check.equal(h, 1, friend.outbound(GraveListPayload.class).size(), "trusted player opens browser");
        GraveMenuHandlers.handleDetailRequest(new GraveDetailRequestPayload(owner.id(), owner.newestRecord().id()), friend.context());
        Check.equal(h, 1, friend.outbound(GraveDetailPayload.class).size(), "trusted player receives item details");
        friend.clearOutbound();
        friend.moveToAbsolute(friend.player().getX() + 100, friend.player().getY(), friend.player().getZ());
        GraveMenuHandlers.handleDetailRequest(new GraveDetailRequestPayload(owner.id(), owner.newestRecord().id()), friend.context());
        Check.isTrue(h, friend.outbound(GraveDetailPayload.class).isEmpty(), "trusted details are refused out of range");
        friend.moveToRecord(owner.newestRecord());
        owner.profile().allowed().clear();
        GraveMenuHandlers.handleDetailRequest(new GraveDetailRequestPayload(owner.id(), owner.newestRecord().id()), friend.context());
        Check.isTrue(h, friend.outbound(GraveDetailPayload.class).isEmpty(), "revoked access cannot load details");
        h.succeed();
    }

    private static void extract(GameTestHelper h) {
        TestPlayer p = TestPlayer.join(h);
        p.give(0, new ItemStack(Items.DIAMOND, 4));
        p.simulateDeath();
        DeathRecord record = p.newestRecord();
        p.moveToRecord(record);
        p.fillInventory(new ItemStack(Items.STONE, 64));
        p.give(0, new ItemStack(Items.DIAMOND, 63));
        GraveMenuHandlers.handleExtract(new GraveExtractPayload(p.id(), record.id(), 0), p.context());
        int dropped = p.player().level().getEntitiesOfClass(ItemEntity.class, p.player().getBoundingBox().inflate(4)).stream()
                .filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum();
        Check.equal(h, 67, p.countOf(Items.DIAMOND) + record.itemCount() + dropped, "all extracted diamonds must survive partial insertion");
        h.succeed();
    }

    private static void hookFailure(GameTestHelper h) {
        TestPlayer p = TestPlayer.join(h);
        p.give(0, new ItemStack(Items.DIAMOND));
        p.simulateDeath();
        DeathRecord record = p.newestRecord();
        installFailingHook(h, false);
        record.entries().addFirst(new CapturedEntry(VanillaInventoryHook.ID, "", 1, new ItemStack(Items.EMERALD)));
        RestoreEngine.Result result = RestoreEngine.claim(p.player(), p.profile(), record, p.store());
        Check.equal(h, 2, record.itemCount() + p.countOf(Items.DIAMOND) + p.countOf(Items.EMERALD), "hook failure must preserve all pending items");
        Check.equal(h, 1, result.restored(), "unaffected entries still restore");
        Check.equal(h, 1, result.remaining(), "failed entry stays in the grave");
        h.succeed();
    }

    private static InventoryHook installFailingHook(GameTestHelper h, boolean partial) {
        InventoryHook original = InventoryHooks.byId(VanillaInventoryHook.ID);
        TestCleanup.attach(h).onFinish(() -> InventoryHooks.register(original));
        InventoryHooks.register(new InventoryHook() {
            public String id() { return original.id(); }
            public List<CapturedEntry> capture(ServerPlayer player, DamageSource source) {
                return original.capture(player, source);
            }
            public ItemStack restore(ServerPlayer player, CapturedEntry entry) {
                if (!entry.stack().is(Items.EMERALD)) {
                    return original.restore(player, entry);
                }
                if (partial) {
                    player.getInventory().setItem(entry.slot(), entry.stack().copyWithCount(2));
                    entry.stack().shrink(2);
                }
                throw new IllegalStateException("Injected restore failure");
            }
        });
        return original;
    }

    private static void partialHookFailure(GameTestHelper h) {
        TestPlayer p = TestPlayer.join(h);
        p.give(0, new ItemStack(Items.DIAMOND));
        p.give(1, new ItemStack(Items.EMERALD, 4));
        p.simulateDeath();
        DeathRecord record = p.newestRecord();
        InventoryHook original = installFailingHook(h, true);
        RestoreEngine.Result first = RestoreEngine.claim(p.player(), p.profile(), record, p.store());
        Check.equal(h, 3, first.restored(), "successful transfers before failure are accounted for");
        Check.equal(h, 2, record.itemCount(), "only the untransferred remainder stays");
        InventoryHooks.register(original);
        RestoreEngine.Result second = RestoreEngine.claim(p.player(), p.profile(), record, p.store());
        Check.equal(h, 2, second.restored(), "retry restores only the remainder");
        Check.equal(h, 4, p.countOf(Items.EMERALD), "retry does not duplicate transferred items");
        Check.equal(h, 1, p.countOf(Items.DIAMOND), "earlier successful entry is not replayed");
        h.succeed();
    }

    private static void ghostReset(GameTestHelper h) {
        TestPlayer p = TestPlayer.join(h);
        p.give(0, new ItemStack(Items.DIAMOND));
        p.simulateDeath();
        p.moveToRecord(p.newestRecord());
        p.player().tickCount = 20 - Math.floorMod(p.player().getId(), 20);
        p.clearOutbound();
        GhostSyncEvents.onPlayerTick(p.player());
        Check.equal(h, 1, p.outbound(GhostAddPayload.class).size(), "initial ghost is synced");
        p.clearOutbound();
        GhostSyncEvents.reset(p.player());
        Check.equal(h, 1, p.outbound(GhostRemovePayload.class).size(), "world reset removes old client ghosts");
        Check.equal(h, p.newestRecord().id(), p.outbound(GhostRemovePayload.class).getFirst().recordId(), "correct ghost removed");
        GhostSyncEvents.onPlayerTick(p.player());
        Check.equal(h, 1, p.outbound(GhostAddPayload.class).size(), "next sync resends current ghosts");
        h.succeed();
    }

    private static void targeting(GameTestHelper h) {
        TestCleanup cleanup = TestCleanup.attach(h);
        cleanup.config(GravelessConfig.SERVER.requireLineOfSight, false);
        cleanup.onFinish(GhostClientManager::clear);
        TestPlayer p = TestPlayer.join(h);
        p.player().setYRot(-90);
        p.player().setYHeadRot(-90);
        p.player().setXRot(0);
        BlockPos pos = p.player().blockPosition().offset(6, 0, 0);
        var ghost = new GhostClientManager.ClientGhost(java.util.UUID.randomUUID(), p.id(), p.name(), pos, 1);
        GhostClientManager.add(ghost);
        var hitPos = p.player().getEyePosition().add(2, 0, 0);
        var wall = new BlockHitResult(hitPos, Direction.WEST, BlockPos.containing(hitPos), false);
        Check.equal(h, ghost, GhostInteraction.findTarget(p.player(), wall), "disabled line of sight permits wall targeting");
        Check.isNull(h, GhostInteraction.findTarget(p.player(), new EntityHitResult(p.player(), hitPos)),
                "ghost targeting does not steal an entity interaction");
        GravelessConfig.SERVER.requireLineOfSight.set(true);
        Check.isNull(h, GhostInteraction.findTarget(p.player(), wall), "enabled line of sight blocks wall targeting");
        h.succeed();
    }

    private static TestPlayer warded(GameTestHelper h) {
        TestCleanup c = TestCleanup.attach(h);
        c.config(GravelessConfig.SERVER.protectionEnabled, true);
        c.config(GravelessConfig.SERVER.protectionRange, 32);
        c.config(GravelessConfig.SERVER.protectionLinger, 5);
        TestPlayer p = TestPlayer.join(h);
        p.give(0, new ItemStack(Items.DIAMOND));
        p.simulateDeath();
        p.moveToRecord(p.newestRecord());
        p.player().tickCount = 20 - Math.floorMod(p.player().getId(), 20);
        return p;
    }

    private static void linger(GameTestHelper h) {
        TestPlayer p = warded(h);
        SpiritWardEvents.onPlayerTick(p.player());
        GhostSyncEvents.handleClaimRequest(new ClaimRequestPayload(p.newestRecord().id()), p.context());
        Check.isTrue(h, p.player().hasEffect(ModEffects.SPIRIT_WARD.holder()), "linger begins after claim");
        p.player().tickCount += 20;
        SpiritWardEvents.onPlayerTick(p.player());
        Check.isTrue(h, p.player().hasEffect(ModEffects.SPIRIT_WARD.holder()), "configured five-second linger must survive next ward check");
        h.succeed();
    }

    private static void potions(GameTestHelper h) {
        TestPlayer p = warded(h);
        p.player().addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 10000), null);
        SpiritWardEvents.onPlayerTick(p.player());
        p.moveToAbsolute(p.player().getX() + 100, p.player().getY(), p.player().getZ());
        SpiritWardEvents.onPlayerTick(p.player());
        Check.isTrue(h, p.player().hasEffect(MobEffects.NIGHT_VISION), "pre-existing night vision potion must survive ward cleanup");
        h.succeed();
    }

    private static void laterPotions(GameTestHelper h) {
        TestPlayer p = warded(h);
        SpiritWardEvents.onPlayerTick(p.player());
        p.player().addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 10000), null);
        p.player().addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 10000), null);
        SpiritWardEvents.beginWearOff(p.player());
        SpiritWardEvents.onLogout(p.player());
        Check.equal(h, 10000, p.player().getEffect(MobEffects.NIGHT_VISION).getDuration(), "later night vision survives cleanup");
        Check.equal(h, 10000, p.player().getEffect(MobEffects.INVISIBILITY).getDuration(), "later invisibility survives cleanup");
        h.succeed();
    }

    private static void lingerExpires(GameTestHelper h) {
        TestPlayer p = warded(h);
        SpiritWardEvents.onPlayerTick(p.player());
        GhostSyncEvents.handleClaimRequest(new ClaimRequestPayload(p.newestRecord().id()), p.context());
        h.startSequence()
                .thenIdle(40)
                .thenExecute(() -> Check.isTrue(h, p.player().hasEffect(ModEffects.SPIRIT_WARD.holder()), "five-second linger survives two seconds"))
                .thenWaitUntil(() -> Check.isFalse(h, p.player().hasEffect(ModEffects.SPIRIT_WARD.holder()), "linger expires"))
                .thenExecute(() -> {
                    SpiritWardEvents.onPlayerTick(p.player());
                    Check.isFalse(h, p.player().hasEffect(MobEffects.NIGHT_VISION), "owned night vision ends with ward");
                    Check.isFalse(h, p.player().hasEffect(MobEffects.INVISIBILITY), "owned invisibility ends with ward");
                })
                .thenSucceed();
    }

    private static void zeroLinger(GameTestHelper h) {
        TestPlayer p = warded(h);
        GravelessConfig.SERVER.protectionLinger.set(0);
        SpiritWardEvents.onPlayerTick(p.player());
        GhostSyncEvents.handleClaimRequest(new ClaimRequestPayload(p.newestRecord().id()), p.context());
        Check.isFalse(h, p.player().hasEffect(ModEffects.SPIRIT_WARD.holder()), "zero linger ends immediately");
        Check.isFalse(h, p.player().hasEffect(MobEffects.NIGHT_VISION), "zero linger removes owned night vision");
        h.succeed();
    }

    private static void browserLinger(GameTestHelper h, boolean xpOnly) {
        TestPlayer p = warded(h);
        DeathRecord grave = p.newestRecord();
        SpiritWardEvents.onPlayerTick(p.player());
        if (xpOnly) {
            grave.entries().clear();
            grave.setXp(10);
            GraveMenuHandlers.handleAction(new GraveActionPayload(p.id(), grave.id(),
                    GraveActionPayload.ACTION_CLAIM_XP), p.context());
        } else {
            GraveMenuHandlers.handleExtract(new GraveExtractPayload(p.id(), grave.id(), 0), p.context());
        }
        Check.isTrue(h, p.records().isEmpty(), "browser action empties the grave");
        p.player().tickCount += 20;
        SpiritWardEvents.onPlayerTick(p.player());
        Check.isTrue(h, p.player().hasEffect(ModEffects.SPIRIT_WARD.holder()), "browser recovery grants configured linger");
        h.succeed();
    }
}
