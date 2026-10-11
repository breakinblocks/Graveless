package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.capture.VanillaInventoryHook;
import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.event.DeathCaptureEvents;
import com.breakinblocks.graveless.event.GraveMenuHandlers;
import com.breakinblocks.graveless.registry.ModItems;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class CaptureTests {
    private static final Component SOULBOUND_MARKER = Component.literal("graveless soulbound test");
    private static final List<ItemStack> KEPT_BY_SOULBOUND = new ArrayList<>();
    private static final Map<UUID, ServerPlayer> MAGNET_KILLERS = new HashMap<>();

    private CaptureTests() {}

    static void register(TestRegistrar tests) {
        NeoForge.EVENT_BUS.addListener(LivingDropsEvent.class, CaptureTests::keepSoulboundDrops);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDropsEvent.class, CaptureTests::magnetDrops);
        tests.add("capture_stores_every_slot_region", CaptureTests::storesEverySlotRegion);
        tests.add("capture_takes_and_zeroes_xp", CaptureTests::takesAndZeroesXp);
        tests.add("capture_destroys_vanishing_curse_items", CaptureTests::destroysVanishingCurseItems);
        tests.add("capture_never_stores_the_spirit_compass", CaptureTests::neverStoresSpiritCompass);
        tests.add("capture_ignores_a_death_with_nothing_to_keep", CaptureTests::ignoresEmptyDeath);
        tests.add("capture_respects_player_opt_out", CaptureTests::respectsPlayerOptOut);
        tests.add("capture_skips_spectators", CaptureTests::skipsSpectators);
        tests.add("capture_absorbs_foreign_drops", CaptureTests::absorbsForeignDrops);
        tests.add("capture_snapshots_terrain", CaptureTests::snapshotsTerrain);
        tests.add("capture_records_the_death_cause", CaptureTests::recordsDeathCause);
        tests.add("capture_xp_math_matches_vanilla", CaptureTests::xpMathMatchesVanilla);
        tests.add("capture_real_death_leaves_nothing_behind", CaptureTests::realDeathLeavesNothingBehind);
        tests.add("capture_real_death_in_water_keeps_the_grave", CaptureTests::realDeathInWaterKeepsTheGrave);
        tests.add("capture_interrupted_drops_are_saved_on_respawn", CaptureTests::interruptedDropsSavedOnRespawn);
        tests.add("capture_interrupted_drops_are_saved_on_logout", CaptureTests::interruptedDropsSavedOnLogout);
        tests.add("capture_leaves_drops_kept_by_other_mods", CaptureTests::leavesDropsKeptByOtherMods);
        tests.add("capture_empties_the_drops_it_takes", CaptureTests::emptiesTheDropsItTakes);
        tests.add("capture_skips_drops_a_magnet_already_took", CaptureTests::skipsDropsAMagnetAlreadyTook);
        tests.add("capture_real_death_runs_before_lowest_magnets", CaptureTests::realDeathRunsBeforeLowestMagnets);
        tests.add("capture_interrupted_drops_are_saved_at_end_of_tick", CaptureTests::interruptedDropsSavedAtEndOfTick);
        tests.add("capture_real_death_leaves_soulbound_items_alone", CaptureTests::realDeathLeavesSoulboundItemsAlone);

        tests.addIsolated("capture_respects_keep_inventory", CaptureTests::respectsKeepInventory);
        tests.addIsolated("capture_respects_master_switch", CaptureTests::respectsMasterSwitch);
        tests.addIsolated("capture_enforces_record_cap", CaptureTests::enforcesRecordCap);
    }

    private static void storesEverySlotRegion(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND_SWORD));
        player.give(17, new ItemStack(Items.BREAD, 12));
        player.give(38, new ItemStack(Items.IRON_CHESTPLATE));
        player.give(Inventory.SLOT_OFFHAND, new ItemStack(Items.SHIELD));

        player.simulateDeath();

        Check.equal(helper, 1, player.records().size(), "grave count after a death");
        DeathRecord record = player.newestRecord();
        Check.equal(helper, 4, record.entries().size(), "captured entry count");
        Check.equal(helper, 15, record.itemCount(), "captured item count");
        Check.equal(helper, 0, player.countItems(), "items left in the inventory");

        Set<Integer> slots = new HashSet<>();
        for (CapturedEntry entry : record.entries()) {
            slots.add(entry.slot());
        }
        Check.isTrue(
                helper,
                slots.containsAll(Set.of(0, 17, 38, Inventory.SLOT_OFFHAND)),
                "captured slots " + slots + " should cover hotbar, main, armour and offhand");
        helper.succeed();
    }

    private static void takesAndZeroesXp(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.player().giveExperiencePoints(1000);
        int before = player.storedXp();
        Check.isTrue(helper, before > 900, "test setup granted xp, saw " + before);

        player.simulateDeath();

        DeathRecord record = player.newestRecord();
        Check.notNull(helper, record, "an xp-only death still leaves a grave");
        Check.equal(helper, before, record.xp(), "stored xp");
        Check.equal(helper, 0, player.player().experienceLevel, "player experience level after death");
        Check.equal(helper, 0, player.player().totalExperience, "player total experience after death");
        Check.isTrue(helper, player.player().experienceProgress == 0.0F, "player experience progress after death");
        helper.succeed();
    }

    private static void destroysVanishingCurseItems(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        Holder<Enchantment> vanishing = helper.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.VANISHING_CURSE);
        ItemStack cursed = new ItemStack(Items.DIAMOND_HELMET);
        cursed.enchant(vanishing, 1);
        player.give(0, cursed);
        player.give(1, new ItemStack(Items.DIAMOND, 2));

        player.simulateDeath();

        DeathRecord record = player.newestRecord();
        Check.equal(helper, 1, record.entries().size(), "cursed items are not stored");
        Check.isTrue(helper, record.entries().getFirst().stack().is(Items.DIAMOND), "surviving entry");
        Check.equal(helper, 0, player.countItems(), "cursed item is gone from the inventory too");
        helper.succeed();
    }

    private static void neverStoresSpiritCompass(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(ModItems.SPIRIT_COMPASS.get()));
        player.give(Inventory.SLOT_OFFHAND, new ItemStack(ModItems.SPIRIT_COMPASS.get()));
        player.give(1, new ItemStack(Items.DIAMOND, 2));

        player.simulateDeath();

        DeathRecord record = player.newestRecord();
        for (CapturedEntry entry : record.entries()) {
            Check.isFalse(
                    helper,
                    entry.stack().is(ModItems.SPIRIT_COMPASS.get()),
                    "a spirit compass ended up inside the grave");
        }
        Check.equal(helper, 0, player.countOf(ModItems.SPIRIT_COMPASS.get()), "spirit compasses left in the inventory");

        DeathCaptureEvents.onRespawn(player.player());
        Check.equal(helper, 1, player.countOf(ModItems.SPIRIT_COMPASS.get()), "spirit compasses after respawn");
        helper.succeed();
    }

    private static void ignoresEmptyDeath(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);

        player.simulateDeath();

        Check.isTrue(helper, player.records().isEmpty(), "a death with nothing to keep left a grave");
        helper.succeed();
    }

    private static void respectsPlayerOptOut(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.profile().setEnabled(false);
        player.give(0, new ItemStack(Items.DIAMOND, 4));

        player.simulateDeath();

        Check.isTrue(helper, player.records().isEmpty(), "opted-out player still got a grave");
        Check.equal(helper, 4, player.countItems(), "opted-out player keeps their items for vanilla drops");
        helper.succeed();
    }

    private static void skipsSpectators(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.player().setGameMode(GameType.SPECTATOR);
        player.give(0, new ItemStack(Items.DIAMOND, 4));

        player.simulateDeath();

        Check.isTrue(helper, player.records().isEmpty(), "spectator death left a grave");
        helper.succeed();
    }

    private static void absorbsForeignDrops(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 1));

        player.beginDeath();
        Check.isTrue(helper, DeathCaptureEvents.hasPending(player.player()), "a death is pending");
        Check.equal(
                helper,
                0,
                DeathCaptureEvents.modifyDroppedXp(player.player(), 50),
                "dropped xp while a grave is pending");
        Check.isTrue(
                helper,
                DeathCaptureEvents.captureDrop(player.player(), new ItemStack(Items.EMERALD, 3)),
                "a foreign drop was swallowed by the grave");
        Check.isFalse(
                helper, DeathCaptureEvents.captureDrop(player.player(), ItemStack.EMPTY), "an empty drop was ignored");
        Check.isFalse(
                helper,
                DeathCaptureEvents.captureDrop(player.player(), new ItemStack(ModItems.SPIRIT_COMPASS.get())),
                "a dropped spirit compass was ignored");
        player.finishDeath();

        DeathRecord record = player.newestRecord();
        Check.equal(helper, 2, record.entries().size(), "entries after absorbing a foreign drop");
        Check.equal(helper, 4, record.itemCount(), "item count after absorbing a foreign drop");
        Check.isFalse(helper, DeathCaptureEvents.hasPending(player.player()), "pending death cleared");
        Check.equal(
                helper,
                50,
                DeathCaptureEvents.modifyDroppedXp(player.player(), 50),
                "dropped xp once no grave is pending");
        helper.succeed();
    }

    private static void snapshotsTerrain(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 1));

        player.simulateDeath();

        int expected =
                GraveMenuHandlers.TERRAIN_SIZE * GraveMenuHandlers.TERRAIN_SIZE * GraveMenuHandlers.TERRAIN_HEIGHT;
        Check.equal(helper, expected, player.newestRecord().terrain().length, "terrain snapshot length");
        helper.succeed();
    }

    private static void recordsDeathCause(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 1));

        player.simulateDeath();

        DeathRecord record = player.newestRecord();
        Check.isFalse(helper, record.cause().isBlank(), "death cause was blank");
        Check.isTrue(helper, record.epochMillis() > 0L, "death timestamp was not recorded");
        Check.equal(helper, helper.getLevel().dimension(), record.pos().dimension(), "grave dimension");
        helper.succeed();
    }

    private static void xpMathMatchesVanilla(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        for (int target : new int[] {0, 7, 100, 550, 1395, 5000, 25000}) {
            player.player().setExperienceLevels(0);
            player.player().experienceProgress = 0.0F;
            player.player().totalExperience = 0;
            player.player().giveExperiencePoints(target);

            int computed = player.storedXp();
            Check.isTrue(
                    helper,
                    Math.abs(computed - target) <= 2,
                    "XpMath read back " + computed + " for " + target + " granted points");
        }
        helper.succeed();
    }

    private static void realDeathInWaterKeepsTheGrave(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        BlockPos centre = player.player().blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.getLevel().setBlockAndUpdate(centre.offset(dx, dy, dz), Blocks.WATER.defaultBlockState());
                }
            }
        }
        player.give(0, new ItemStack(Items.NETHERITE_SCRAP, 5));
        player.give(1, new ItemStack(Items.BLAZE_ROD, 9));

        helper.startSequence()
                .thenExecute(player::killForReal)
                .thenIdle(5)
                .thenExecute(() -> {
                    Check.isTrue(
                            helper,
                            player.records().size() == 1,
                            "a death in water should create exactly one grave (graves="
                                    + player.records().size()
                                    + ", inWater=" + player.player().isInWater()
                                    + ", dying=" + player.player().isDeadOrDying()
                                    + ", inventory=" + player.countItems()
                                    + ", pending=" + DeathCaptureEvents.hasPending(player.player()) + ")");
                    Check.equal(
                            helper, 14, player.newestRecord().itemCount(), "grave item count after a death in water");
                    Check.equal(helper, 0, player.countItems(), "items left in the inventory");

                    AABB area = new AABB(player.player().blockPosition()).inflate(12.0);
                    List<ItemEntity> items = helper.getLevel()
                            .getEntitiesOfClass(
                                    ItemEntity.class,
                                    area,
                                    entity -> entity.getItem().is(Items.NETHERITE_SCRAP)
                                            || entity.getItem().is(Items.BLAZE_ROD));
                    Check.isTrue(helper, items.isEmpty(), items.size() + " items dropped on the ground");
                })
                .thenSucceed();
    }

    private static void interruptedDropsSavedOnRespawn(GameTestHelper helper) {
        interruptedDrops(helper, false);
    }

    private static void interruptedDropsSavedOnLogout(GameTestHelper helper) {
        interruptedDrops(helper, true);
    }

    private static void interruptedDrops(GameTestHelper helper, boolean logout) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.NETHERITE_SCRAP, 5));
        player.give(1, new ItemStack(Items.BLAZE_ROD, 9));

        player.beginDeath();
        Check.equal(helper, 14, player.countItems(), "items wait in the inventory for the drop event");
        Check.isTrue(helper, DeathCaptureEvents.hasPending(player.player()), "the record should be pending");

        if (logout) {
            DeathCaptureEvents.onLogout(player.player());
        } else {
            DeathCaptureEvents.onRespawn(player.player());
        }

        Check.isTrue(
                helper,
                player.records().size() == 1,
                "a death whose drop event never arrived must still leave a grave (graves="
                        + player.records().size() + ")");
        Check.equal(helper, 14, player.newestRecord().itemCount(), "rescued grave item count");
        Check.equal(
                helper,
                0,
                player.countOf(Items.NETHERITE_SCRAP) + player.countOf(Items.BLAZE_ROD),
                "rescued items left the inventory");
        helper.succeed();
    }

    private static void emptiesTheDropsItTakes(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.NETHERITE_SCRAP, 5));
        player.give(38, new ItemStack(Items.IRON_CHESTPLATE));

        player.beginDeath();
        ServerPlayer dying = player.player();
        dying.captureDrops(new ArrayList<>());
        dying.getInventory().dropAll();
        Collection<ItemEntity> drops = dying.captureDrops(null);
        Check.equal(helper, 2, drops.size(), "inventory drops handed to the event");
        boolean cancelled =
                CommonHooks.onLivingDrops(dying, dying.damageSources().generic(), drops, false);

        Check.isTrue(helper, cancelled, "the drop event is cancelled once the grave takes the items");
        Check.isTrue(
                helper,
                drops.stream().allMatch(drop -> drop.getItem().isEmpty()),
                "a captured drop still held items another mod could spawn");
        Check.equal(helper, 6, player.newestRecord().itemCount(), "grave item count");
        helper.succeed();
    }

    private static void magnetDrops(LivingDropsEvent event) {
        if (event.isCanceled()) {
            return;
        }
        ServerPlayer killer = MAGNET_KILLERS.get(event.getEntity().getUUID());
        if (killer == null) {
            return;
        }
        for (ItemEntity drop : event.getDrops()) {
            drop.setNoPickUpDelay();
            drop.playerTouch(killer);
        }
    }

    private static void skipsDropsAMagnetAlreadyTook(GameTestHelper helper) {
        TestPlayer victim = TestPlayer.join(helper);
        TestPlayer killer = TestPlayer.join(helper);
        victim.give(0, new ItemStack(Items.NETHERITE_SCRAP, 5));
        victim.give(1, new ItemStack(Items.BLAZE_ROD, 9));

        victim.beginDeath();
        ServerPlayer dying = victim.player();
        dying.captureDrops(new ArrayList<>());
        dying.getInventory().dropAll();
        Collection<ItemEntity> drops = dying.captureDrops(null);
        for (ItemEntity drop : drops) {
            if (drop.getItem().is(Items.NETHERITE_SCRAP)) {
                drop.setNoPickUpDelay();
                drop.playerTouch(killer.player());
            }
        }
        CommonHooks.onLivingDrops(dying, dying.damageSources().generic(), drops, false);

        Check.equal(helper, 5, killer.countOf(Items.NETHERITE_SCRAP), "items the magnet pulled to the killer");
        DeathRecord record = victim.newestRecord();
        Check.equal(helper, 9, record.itemCount(), "grave item count");
        Check.isFalse(
                helper,
                record.entries().stream().anyMatch(entry -> entry.stack().is(Items.NETHERITE_SCRAP)),
                "items the killer picked up were duplicated into the grave");
        helper.succeed();
    }

    private static void realDeathRunsBeforeLowestMagnets(GameTestHelper helper) {
        TestPlayer victim = TestPlayer.join(helper);
        TestPlayer killer = TestPlayer.join(helper);
        victim.give(0, new ItemStack(Items.NETHERITE_SCRAP, 5));
        victim.give(1, new ItemStack(Items.BLAZE_ROD, 9));
        MAGNET_KILLERS.put(victim.player().getUUID(), killer.player());

        helper.startSequence()
                .thenExecute(victim::killForReal)
                .thenIdle(5)
                .thenExecute(() -> {
                    MAGNET_KILLERS.remove(victim.player().getUUID());
                    Check.equal(
                            helper,
                            0,
                            killer.countOf(Items.NETHERITE_SCRAP) + killer.countOf(Items.BLAZE_ROD),
                            "a lowest priority magnet reached the victim's drops");
                    Check.isTrue(helper, victim.records().size() == 1, "a real death should create one grave");
                    Check.equal(helper, 14, victim.newestRecord().itemCount(), "grave item count");
                })
                .thenSucceed();
    }

    private static void interruptedDropsSavedAtEndOfTick(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.NETHERITE_SCRAP, 5));
        player.give(1, new ItemStack(Items.BLAZE_ROD, 9));

        player.beginDeath();

        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    Check.isFalse(
                            helper,
                            DeathCaptureEvents.hasPending(player.player()),
                            "the pending death outlived its tick");
                    Check.isTrue(helper, player.records().size() == 1, "the rescued death left one grave");
                    Check.equal(helper, 14, player.newestRecord().itemCount(), "rescued grave item count");
                    Check.equal(helper, 0, player.countItems(), "rescued items left the inventory");
                })
                .thenSucceed();
    }

    private static void keepSoulboundDrops(LivingDropsEvent event) {
        event.getDrops().removeIf(drop -> {
            ItemStack stack = drop.getItem();
            if (SOULBOUND_MARKER.equals(stack.get(DataComponents.CUSTOM_NAME))) {
                KEPT_BY_SOULBOUND.add(stack);
                return true;
            }
            return false;
        });
    }

    private static void leavesDropsKeptByOtherMods(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND_SWORD));
        player.give(1, new ItemStack(Items.BREAD, 12));
        player.give(38, new ItemStack(Items.IRON_CHESTPLATE));

        player.beginDeath();
        List<ItemStack> kept =
                player.finishDeath(stack -> stack.is(Items.DIAMOND_SWORD) || stack.is(Items.IRON_CHESTPLATE));

        Check.equal(helper, 2, kept.size(), "drops another mod held back");
        DeathRecord record = player.newestRecord();
        Check.equal(helper, 1, record.entries().size(), "captured entry count");
        CapturedEntry entry = record.entries().getFirst();
        Check.isTrue(helper, entry.stack().is(Items.BREAD), "only the unprotected item reaches the grave");
        Check.equal(helper, VanillaInventoryHook.ID, entry.handler(), "drop is matched back to the inventory");
        Check.equal(helper, 1, entry.slot(), "drop keeps its original slot");
        helper.succeed();
    }

    private static void realDeathLeavesSoulboundItemsAlone(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        ItemStack soulbound = new ItemStack(Items.NETHERITE_SWORD);
        soulbound.set(DataComponents.CUSTOM_NAME, SOULBOUND_MARKER);
        player.give(0, soulbound);
        player.give(1, new ItemStack(Items.BLAZE_ROD, 9));

        helper.startSequence()
                .thenExecute(player::killForReal)
                .thenIdle(5)
                .thenExecute(() -> {
                    Check.isTrue(
                            helper,
                            KEPT_BY_SOULBOUND.stream().anyMatch(stack -> stack == soulbound),
                            "the soulbound listener saw its item in the drops");
                    Check.isTrue(helper, player.records().size() == 1, "a real death should create one grave");
                    DeathRecord record = player.newestRecord();
                    Check.equal(helper, 9, record.itemCount(), "grave item count");
                    Check.isFalse(
                            helper,
                            record.entries().stream()
                                    .anyMatch(entry -> entry.stack().is(Items.NETHERITE_SWORD)),
                            "the soulbound item ended up in the grave");
                    KEPT_BY_SOULBOUND.removeIf(stack -> stack == soulbound);
                })
                .thenSucceed();
    }

    private static void realDeathLeavesNothingBehind(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.NETHERITE_SCRAP, 5));
        player.give(1, new ItemStack(Items.BLAZE_ROD, 9));
        player.player().giveExperiencePoints(400);

        helper.startSequence()
                .thenExecute(player::killForReal)
                .thenIdle(5)
                .thenExecute(() -> {
                    Check.isTrue(
                            helper,
                            player.records().size() == 1,
                            "a real death should create exactly one grave (graves="
                                    + player.records().size()
                                    + ", dying=" + player.player().isDeadOrDying()
                                    + ", health=" + player.player().getHealth()
                                    + ", pending=" + DeathCaptureEvents.hasPending(player.player()) + ")");
                    DeathRecord record = player.newestRecord();
                    Check.equal(helper, 14, record.itemCount(), "grave item count after a real death");
                    Check.isTrue(helper, record.xp() > 350, "grave xp after a real death, saw " + record.xp());

                    AABB area = new AABB(player.player().blockPosition()).inflate(12.0);
                    List<ItemEntity> items = helper.getLevel()
                            .getEntitiesOfClass(
                                    ItemEntity.class,
                                    area,
                                    entity -> entity.getItem().is(Items.NETHERITE_SCRAP)
                                            || entity.getItem().is(Items.BLAZE_ROD));
                    Check.isTrue(helper, items.isEmpty(), items.size() + " items dropped on the ground");
                    List<ExperienceOrb> orbs = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, area);
                    Check.isTrue(helper, orbs.isEmpty(), orbs.size() + " xp orbs dropped on the ground");
                })
                .thenSucceed();
    }

    private static void respectsKeepInventory(GameTestHelper helper) {
        TestCleanup cleanup = TestCleanup.attach(helper);
        cleanup.gameRule(helper.getLevel(), GameRules.KEEP_INVENTORY, true);

        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 4));

        player.simulateDeath();

        Check.isTrue(helper, player.records().isEmpty(), "keepInventory death still left a grave");
        Check.equal(helper, 4, player.countItems(), "keepInventory should leave the inventory alone");
        helper.succeed();
    }

    private static void respectsMasterSwitch(GameTestHelper helper) {
        TestCleanup cleanup = TestCleanup.attach(helper);
        cleanup.config(GravelessConfig.SERVER.enabled, false);

        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 4));

        player.simulateDeath();

        Check.isTrue(helper, player.records().isEmpty(), "grave created while the mod is switched off");
        Check.equal(helper, 4, player.countItems(), "inventory untouched while the mod is switched off");
        helper.succeed();
    }

    private static void enforcesRecordCap(GameTestHelper helper) {
        TestCleanup cleanup = TestCleanup.attach(helper);
        cleanup.config(GravelessConfig.SERVER.maxRecordsPerPlayer, 3);

        TestPlayer player = TestPlayer.join(helper);
        List<UUID> ids = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            player.give(0, new ItemStack(Items.DIAMOND, i));
            player.simulateDeath();
            ids.add(player.newestRecord().id());
        }

        Check.equal(helper, 3, player.records().size(), "records kept once the cap is exceeded");
        Check.equal(helper, ids.get(2), player.records().get(0).id(), "oldest surviving record");
        Check.equal(helper, ids.get(3), player.records().get(1).id(), "middle surviving record");
        Check.equal(helper, ids.get(4), player.records().get(2).id(), "newest surviving record");
        helper.succeed();
    }
}
