package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.event.GhostSyncEvents;
import com.breakinblocks.graveless.net.GravelessNetworking.ClaimRequestPayload;
import com.breakinblocks.graveless.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class WardTests {
    private WardTests() {
    }

    static void register(TestRegistrar tests) {
        tests.add("ward_stays_off_when_disabled", WardTests::staysOffWhenDisabled);
        tests.addIsolated("ward_guards_the_owner_near_their_grave", 400, WardTests::guardsOwnerNearGrave);
        tests.addIsolated("ward_clears_when_leaving_range", 600, WardTests::clearsWhenLeavingRange);
    }

    private static void staysOffWhenDisabled(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.join(helper);
        owner.give(0, new ItemStack(Items.DIAMOND, 1));
        owner.simulateDeath();
        owner.moveToRecord(owner.newestRecord());
        int start = owner.tickCount();

        helper.startSequence()
                .thenWaitUntil(() -> Check.isTrue(helper, owner.tickCount() >= start + 45,
                        "waiting for the player to tick past two Spirit Ward checks"))
                .thenExecute(() -> Check.isFalse(helper,
                        owner.player().hasEffect(ModEffects.SPIRIT_WARD.holder()),
                        "Spirit Ward was granted while protection is disabled"))
                .thenSucceed();
    }

    private static void guardsOwnerNearGrave(GameTestHelper helper) {
        TestCleanup cleanup = TestCleanup.attach(helper);
        cleanup.config(GravelessConfig.SERVER.protectionEnabled, true);
        cleanup.config(GravelessConfig.SERVER.protectionRange, 32);

        TestPlayer owner = TestPlayer.join(helper);
        TestPlayer bystander = TestPlayer.join(helper);
        owner.give(0, new ItemStack(Items.DIAMOND, 1));
        owner.simulateDeath();
        DeathRecord record = owner.newestRecord();
        owner.moveToRecord(record);
        bystander.moveToRecord(record);

        helper.startSequence()
                .thenWaitUntil(() -> Check.isTrue(helper,
                        owner.player().hasEffect(ModEffects.SPIRIT_WARD.holder()),
                        "Spirit Ward was not granted near the owner's grave"))
                .thenExecute(() -> {
                    Check.isTrue(helper, owner.player().hasEffect(MobEffects.INVISIBILITY),
                            "Invisibility was not granted with Spirit Ward");
                    Check.isTrue(helper, owner.player().hasEffect(MobEffects.NIGHT_VISION),
                            "Night Vision was not granted with Spirit Ward");
                    Check.isFalse(helper, bystander.player().hasEffect(ModEffects.SPIRIT_WARD.holder()),
                            "a bystander was warded by someone else's grave");
                    claimAndLinger(helper, owner, record);
                })
                .thenSucceed();
    }

    private static void claimAndLinger(GameTestHelper helper, TestPlayer owner, DeathRecord record) {
        int wardBefore = owner.player().getEffect(ModEffects.SPIRIT_WARD.holder()).getDuration();
        GhostSyncEvents.handleClaimRequest(new ClaimRequestPayload(record.id()), owner.context());
        Check.isTrue(helper, owner.records().isEmpty(), "grave was not claimed");

        int linger = GravelessConfig.SERVER.protectionLinger.get() * 20;
        MobEffectInstance effect = owner.player().getEffect(ModEffects.SPIRIT_WARD.holder());
        if (linger > 0) {
            Check.notNull(helper, effect, "Spirit Ward should linger briefly after a claim");
            Check.isTrue(helper, effect.getDuration() <= linger,
                    "Spirit Ward lingered for " + effect.getDuration() + " ticks, expected at most " + linger);
            Check.isTrue(helper, effect.getDuration() < wardBefore,
                    "claiming should cut the ward short, still " + effect.getDuration() + " ticks");
        } else {
            Check.isNull(helper, effect, "Spirit Ward should end immediately when linger is zero");
        }
    }

    private static void clearsWhenLeavingRange(GameTestHelper helper) {
        TestCleanup cleanup = TestCleanup.attach(helper);
        cleanup.config(GravelessConfig.SERVER.protectionEnabled, true);
        cleanup.config(GravelessConfig.SERVER.protectionRange, 32);

        TestPlayer owner = TestPlayer.join(helper);
        owner.give(0, new ItemStack(Items.DIAMOND, 1));
        owner.simulateDeath();
        DeathRecord record = owner.newestRecord();
        BlockPos anchor = owner.anchorOf(record);
        owner.moveToRecord(record);

        helper.startSequence()
                .thenWaitUntil(() -> Check.isTrue(helper,
                        owner.player().hasEffect(ModEffects.SPIRIT_WARD.holder()),
                        "Spirit Ward was not granted near the owner's grave"))
                .thenExecute(() -> owner.moveToAbsolute(anchor.getX() + 120.0, anchor.getY(), anchor.getZ()))
                .thenWaitUntil(() -> Check.isFalse(helper,
                        owner.player().hasEffect(ModEffects.SPIRIT_WARD.holder()),
                        "Spirit Ward survived walking out of protection_range"))
                .thenExecute(() -> {
                    Check.isFalse(helper, owner.player().hasEffect(MobEffects.INVISIBILITY),
                            "Invisibility survived walking out of protection_range");
                    Check.isFalse(helper, owner.player().hasEffect(MobEffects.NIGHT_VISION),
                            "Night Vision survived walking out of protection_range");
                    owner.moveToRecord(record);
                })
                .thenWaitUntil(() -> Check.isTrue(helper,
                        owner.player().hasEffect(ModEffects.SPIRIT_WARD.holder()),
                        "Spirit Ward was not re-granted after returning to the grave"))
                .thenSucceed();
    }
}
