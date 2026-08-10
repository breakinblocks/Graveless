package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.event.GraveMenuHandlers;
import com.breakinblocks.graveless.restore.RestoreEngine;
import com.breakinblocks.graveless.util.GraveBackups;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BackupTests {
    private BackupTests() {
    }

    static void register(TestRegistrar tests) {
        tests.add("backup_is_written_and_readable", BackupTests::writtenAndReadable);
        tests.add("backup_outlives_the_claimed_grave", BackupTests::outlivesClaimedGrave);
        tests.add("backup_revives_as_a_fresh_grave", BackupTests::revivesAsFreshGrave);
        tests.add("backup_owners_lists_the_archive", BackupTests::ownersListsArchive);
        tests.add("backup_prune_keeps_the_requested_count", BackupTests::pruneKeepsRequestedCount);

        tests.addIsolated("backup_retention_trims_the_oldest", 400, BackupTests::retentionTrimsOldest);
        tests.addIsolated("backup_retention_unlimited_keeps_everything", 400, BackupTests::retentionUnlimitedKeepsEverything);
    }

    private static void writtenAndReadable(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 3));
        player.simulateDeath();
        DeathRecord record = player.newestRecord();

        List<Path> files = GraveBackups.list(player.server(), player.id());
        Check.equal(helper, 1, files.size(), "backup files on disk");

        DeathRecord restored = GraveBackups.read(player.server(), files.getFirst());
        Check.notNull(helper, restored, "backup file could not be read back");
        Check.equal(helper, record.id(), restored.id(), "backup record id");
        Check.equal(helper, record.itemCount(), restored.itemCount(), "backup item count");
        Check.equal(helper, record.pos(), restored.pos(), "backup position");
        Check.equal(helper, record.terrain().length, restored.terrain().length, "backup terrain snapshot");
        helper.succeed();
    }

    private static void outlivesClaimedGrave(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 3));
        player.simulateDeath();

        RestoreEngine.claim(player.player(), player.profile(), player.newestRecord(), player.store());
        Check.isTrue(helper, player.records().isEmpty(), "grave should be claimed");
        Check.equal(helper, 1, GraveBackups.list(player.server(), player.id()).size(),
                "claiming a grave should not delete its archive file");
        helper.succeed();
    }

    private static void revivesAsFreshGrave(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 3));
        player.simulateDeath();
        DeathRecord original = player.newestRecord();
        RestoreEngine.claim(player.player(), player.profile(), original, player.store());

        Path file = GraveBackups.list(player.server(), player.id()).getFirst();
        DeathRecord revived = GraveMenuHandlers.reviveBackup(player.server(), player.id(), file);

        Check.notNull(helper, revived, "backup could not be revived");
        Check.isFalse(helper, original.id().equals(revived.id()), "a revived grave should get a fresh id");
        Check.equal(helper, 3, revived.itemCount(), "revived grave item count");
        Check.equal(helper, original.pos(), revived.pos(), "revived grave position");
        Check.equal(helper, 1, player.records().size(), "revived grave is live again");
        helper.succeed();
    }

    private static void ownersListsArchive(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 1));
        player.simulateDeath();

        Check.isTrue(helper, GraveBackups.owners(player.server()).contains(player.id()),
                "the archive listing did not include a player who just died");
        helper.succeed();
    }

    private static void pruneKeepsRequestedCount(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);

        GameTestSequence sequence = helper.startSequence();
        sequence = deaths(sequence, player, 4);
        sequence.thenExecute(() -> {
                    Check.equal(helper, 4, GraveBackups.list(player.server(), player.id()).size(),
                            "archive size before pruning");
                    Check.equal(helper, 0, GraveBackups.prune(player.server(), player.id(), GraveBackups.UNLIMITED),
                            "an unlimited prune should delete nothing");
                    Check.equal(helper, 4, GraveBackups.list(player.server(), player.id()).size(),
                            "archive size after an unlimited prune");

                    Check.equal(helper, 2, GraveBackups.prune(player.server(), player.id(), 2),
                            "files deleted when keeping two");
                    List<Path> kept = GraveBackups.list(player.server(), player.id());
                    Check.equal(helper, 2, kept.size(), "archive size after keeping two");
                    Check.equal(helper, 4, GraveBackups.read(player.server(), kept.get(0)).itemCount(),
                            "newest surviving backup");
                    Check.equal(helper, 3, GraveBackups.read(player.server(), kept.get(1)).itemCount(),
                            "second newest surviving backup");

                    Check.equal(helper, 2, GraveBackups.prune(player.server(), player.id(), 0),
                            "files deleted when keeping none");
                    Check.equal(helper, 0, GraveBackups.list(player.server(), player.id()).size(),
                            "archive size after keeping none");
                })
                .thenSucceed();
    }

    private static void retentionTrimsOldest(GameTestHelper helper) {
        TestCleanup cleanup = TestCleanup.attach(helper);
        cleanup.config(GravelessConfig.SERVER.maxBackupsPerPlayer, 3);

        TestPlayer player = TestPlayer.join(helper);
        GameTestSequence sequence = helper.startSequence();
        sequence = deaths(sequence, player, 6);
        sequence.thenExecute(() -> {
                    List<Path> files = GraveBackups.list(player.server(), player.id());
                    Check.equal(helper, 3, files.size(), "archive size under a retention limit of three");

                    List<Integer> counts = new ArrayList<>();
                    for (Path file : files) {
                        DeathRecord record = GraveBackups.read(player.server(), file);
                        Check.notNull(helper, record, "surviving backup " + file.getFileName());
                        counts.add(record.itemCount());
                    }
                    Check.equal(helper, List.of(6, 5, 4), counts,
                            "retention should keep the newest deaths, newest first");
                })
                .thenSucceed();
    }

    private static void retentionUnlimitedKeepsEverything(GameTestHelper helper) {
        TestCleanup cleanup = TestCleanup.attach(helper);
        cleanup.config(GravelessConfig.SERVER.maxBackupsPerPlayer, GraveBackups.UNLIMITED);

        TestPlayer player = TestPlayer.join(helper);
        GameTestSequence sequence = helper.startSequence();
        sequence = deaths(sequence, player, 6);
        sequence.thenExecute(() -> Check.equal(helper, 6,
                        GraveBackups.list(player.server(), player.id()).size(),
                        "unlimited retention should keep every archive file"))
                .thenSucceed();
    }

    static GameTestSequence deaths(GameTestSequence sequence, TestPlayer player, int count) {
        GameTestSequence current = sequence;
        for (int i = 1; i <= count; i++) {
            int items = i;
            current = current.thenExecute(() -> {
                player.give(0, new ItemStack(Items.DIAMOND, items));
                player.simulateDeath();
            }).thenIdle(2);
        }
        return current;
    }
}
