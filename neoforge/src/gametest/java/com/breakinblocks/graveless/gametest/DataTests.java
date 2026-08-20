package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.capture.VanillaInventoryHook;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.data.GraveProfile;
import com.breakinblocks.graveless.event.GhostSyncEvents;
import com.breakinblocks.graveless.util.LenientCodecs;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DataTests {
    private DataTests() {
    }

    static void register(TestRegistrar tests) {
        tests.add("data_sanitize_splits_oversized_stack", DataTests::splitsOversizedStack);
        tests.add("data_sanitize_splits_unstackable_pile", DataTests::splitsUnstackablePile);
        tests.add("data_sanitize_drops_empty_stacks", DataTests::dropsEmptyStacks);
        tests.add("data_sanitize_caps_runaway_stacks", DataTests::capsRunawayStacks);
        tests.add("data_death_record_codec_round_trip", DataTests::deathRecordRoundTrip);
        tests.add("data_grave_profile_codec_round_trip", DataTests::graveProfileRoundTrip);
        tests.add("data_lenient_codec_skips_broken_entries", DataTests::lenientCodecSkipsBrokenEntries);
        tests.add("data_ghost_anchor_clamps_to_world", DataTests::ghostAnchorClampsToWorld);
    }

    private static RegistryOps<Tag> ops(GameTestHelper helper) {
        return helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }

    private static void splitsOversizedStack(GameTestHelper helper) {
        CapturedEntry entry = new CapturedEntry(VanillaInventoryHook.ID, "", 5,
                new ItemStack(Items.COBBLESTONE, 300));
        List<CapturedEntry> out = CapturedEntry.sanitize(List.of(entry));

        Check.equal(helper, 5, out.size(), "split entry count");
        Check.equal(helper, 300, totalCount(out), "total item count after splitting");
        Check.equal(helper, VanillaInventoryHook.ID, out.getFirst().handler(), "first split keeps its handler");
        Check.equal(helper, 5, out.getFirst().slot(), "first split keeps its slot");
        for (int i = 1; i < out.size(); i++) {
            Check.equal(helper, CapturedEntry.LOOSE_HANDLER, out.get(i).handler(),
                    "overflow split " + i + " handler");
        }
        for (CapturedEntry split : out) {
            Check.isTrue(helper, split.stack().getCount() <= 64,
                    "split stack of " + split.stack().getCount() + " exceeds the vanilla stack limit");
        }
        helper.succeed();
    }

    private static void splitsUnstackablePile(GameTestHelper helper) {
        CapturedEntry entry = new CapturedEntry(VanillaInventoryHook.ID, "", 2,
                new ItemStack(Items.DIAMOND_PICKAXE, 5));
        List<CapturedEntry> out = CapturedEntry.sanitize(List.of(entry));

        Check.equal(helper, 5, out.size(), "unstackable split count");
        for (CapturedEntry split : out) {
            Check.equal(helper, 1, split.stack().getCount(), "unstackable split stack size");
        }
        helper.succeed();
    }

    private static void dropsEmptyStacks(GameTestHelper helper) {
        List<CapturedEntry> out = CapturedEntry.sanitize(List.of(
                CapturedEntry.loose(ItemStack.EMPTY),
                CapturedEntry.loose(new ItemStack(Items.APPLE, 3)),
                CapturedEntry.loose(new ItemStack(Items.APPLE, 0))));

        Check.equal(helper, 1, out.size(), "empty stacks dropped");
        Check.equal(helper, 3, out.getFirst().stack().getCount(), "surviving stack count");
        helper.succeed();
    }

    private static void capsRunawayStacks(GameTestHelper helper) {
        List<CapturedEntry> out = CapturedEntry.sanitize(List.of(
                CapturedEntry.loose(new ItemStack(Items.COBBLESTONE, 64 * 40 + 100))));

        Check.equal(helper, 40, out.size(), "runaway stack split cap");
        Check.equal(helper, 64 * 40, totalCount(out), "items kept from a runaway stack");
        helper.succeed();
    }

    private static void deathRecordRoundTrip(GameTestHelper helper) {
        RegistryOps<Tag> ops = ops(helper);
        int[] terrain = {1, 2, 3, 4, 5};
        DeathRecord original = new DeathRecord(
                UUID.randomUUID(),
                GlobalPos.of(Level.OVERWORLD, new BlockPos(11, -20, 33)),
                4321L,
                987654321L,
                "gl_test was slain by a codec",
                77,
                List.of(new CapturedEntry(VanillaInventoryHook.ID, "", 4, new ItemStack(Items.DIAMOND, 7)),
                        new CapturedEntry("curios", "ring", 1, new ItemStack(Items.GOLD_INGOT, 2)),
                        CapturedEntry.loose(new ItemStack(Items.APPLE, 5))),
                terrain);

        Tag encoded = DeathRecord.CODEC.encodeStart(ops, original).getOrThrow();
        DeathRecord decoded = DeathRecord.CODEC.parse(ops, encoded).getOrThrow();

        Check.equal(helper, original.id(), decoded.id(), "record id");
        Check.equal(helper, original.pos(), decoded.pos(), "record position");
        Check.equal(helper, (int) original.gameTime(), (int) decoded.gameTime(), "record game time");
        Check.equal(helper, original.epochMillis(), decoded.epochMillis(), "record timestamp");
        Check.equal(helper, original.cause(), decoded.cause(), "record cause");
        Check.equal(helper, original.xp(), decoded.xp(), "record xp");
        Check.equal(helper, original.itemCount(), decoded.itemCount(), "record item count");
        Check.equal(helper, original.entries().size(), decoded.entries().size(), "record entry count");
        Check.equal(helper, terrain.length, decoded.terrain().length, "terrain snapshot length");
        for (int i = 0; i < terrain.length; i++) {
            Check.equal(helper, terrain[i], decoded.terrain()[i], "terrain value " + i);
        }
        CapturedEntry curio = decoded.entries().get(1);
        Check.equal(helper, "curios", curio.handler(), "curio entry handler");
        Check.equal(helper, "ring", curio.context(), "curio entry context");
        Check.equal(helper, 1, curio.slot(), "curio entry slot");
        helper.succeed();
    }

    private static void graveProfileRoundTrip(GameTestHelper helper) {
        RegistryOps<Tag> ops = ops(helper);
        UUID tracked = UUID.randomUUID();
        UUID allowed = UUID.randomUUID();
        DeathRecord record = new DeathRecord(tracked,
                GlobalPos.of(Level.NETHER, new BlockPos(1, 2, 3)), 10L, 20L, "test", 5,
                List.of(CapturedEntry.loose(new ItemStack(Items.STONE, 8))));
        GraveProfile original = new GraveProfile(false, List.of(allowed), List.of(record), Optional.of(tracked));

        Tag encoded = GraveProfile.CODEC.encodeStart(ops, original).getOrThrow();
        GraveProfile decoded = GraveProfile.CODEC.parse(ops, encoded).getOrThrow();

        Check.isFalse(helper, decoded.isEnabled(), "profile opt-out survives a round trip");
        Check.equal(helper, 1, decoded.allowed().size(), "allow list size");
        Check.isTrue(helper, decoded.allowed().contains(allowed), "allow list entry");
        Check.equal(helper, tracked, decoded.trackedRecordId(), "tracked record id");
        Check.equal(helper, 1, decoded.records().size(), "record count");
        Check.notNull(helper, decoded.findRecord(tracked), "tracked record is findable");
        helper.succeed();
    }

    private static void lenientCodecSkipsBrokenEntries(GameTestHelper helper) {
        RegistryOps<Tag> ops = ops(helper);
        Codec<List<CapturedEntry>> codec = LenientCodecs.lenientList(CapturedEntry.CODEC, "test entry");

        ListTag list = new ListTag();
        list.add(CapturedEntry.CODEC.encodeStart(ops,
                CapturedEntry.loose(new ItemStack(Items.DIAMOND, 2))).getOrThrow());
        CompoundTag broken = new CompoundTag();
        broken.putString("handler", VanillaInventoryHook.ID);
        broken.putInt("slot", 3);
        broken.putString("stack", "definitely not an item stack");
        list.add(broken);
        list.add(CapturedEntry.CODEC.encodeStart(ops,
                CapturedEntry.loose(new ItemStack(Items.EMERALD, 4))).getOrThrow());

        List<CapturedEntry> decoded = codec.parse(ops, list).getOrThrow();

        Check.equal(helper, 2, decoded.size(), "readable entries survive a broken neighbour");
        Check.equal(helper, 6, totalCount(decoded), "surviving item count");
        helper.succeed();
    }

    private static void ghostAnchorClampsToWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos surface = new BlockPos(0, 64, 0);
        Check.equal(helper, surface, GhostSyncEvents.anchor(level, surface), "anchor for a normal death");

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        BlockPos voidPos = new BlockPos(0, minY - 40, 0);
        int expectedVoid = Math.min(minY + 48, maxY - 2);
        Check.equal(helper, expectedVoid, GhostSyncEvents.anchor(level, voidPos).getY(),
                "void death anchors above the world floor");

        BlockPos ceiling = new BlockPos(0, maxY + 10, 0);
        Check.equal(helper, maxY - 2, GhostSyncEvents.anchor(level, ceiling).getY(),
                "death above the build limit anchors below the ceiling");
        helper.succeed();
    }

    private static int totalCount(List<CapturedEntry> entries) {
        int total = 0;
        for (CapturedEntry entry : entries) {
            total += entry.stack().getCount();
        }
        return total;
    }
}
