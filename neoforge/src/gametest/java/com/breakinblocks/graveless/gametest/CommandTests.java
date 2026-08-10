package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.util.GraveBackups;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CommandTests {
    private CommandTests() {
    }

    static void register(TestRegistrar tests) {
        tests.add("command_tree_is_registered", CommandTests::treeIsRegistered);
        tests.add("command_list_reports_a_players_graves", CommandTests::listReportsGraves);
        tests.add("command_restore_hands_a_grave_back", CommandTests::restoreHandsGraveBack);
        tests.add("command_prune_player_trims_the_archive", CommandTests::prunePlayerTrimsArchive);
        tests.add("command_prune_refuses_unlimited_retention", CommandTests::pruneRefusesUnlimitedRetention);

        tests.addIsolated("command_prune_all_uses_configured_retention", 400,
                CommandTests::pruneAllUsesConfiguredRetention);
    }

    private static int run(GameTestHelper helper, String command) {
        MinecraftServer server = helper.getLevel().getServer();
        try {
            return server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack());
        } catch (CommandSyntaxException e) {
            helper.fail("command '" + command + "' could not be parsed: " + e.getMessage());
            return 0;
        }
    }

    private static void treeIsRegistered(GameTestHelper helper) {
        Check.notNull(helper, helper.getLevel().getServer().getCommands().getDispatcher()
                .getRoot().getChild(Graveless.MOD_ID), "the /graveless command");
        helper.succeed();
    }

    private static void listReportsGraves(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        Check.equal(helper, 0, run(helper, "graveless list " + player.name()), "list with no graves");

        player.give(0, new ItemStack(Items.DIAMOND, 2));
        player.simulateDeath();
        player.give(0, new ItemStack(Items.EMERALD, 2));
        player.simulateDeath();

        Check.equal(helper, 2, run(helper, "graveless list " + player.name()), "list with two graves");
        helper.succeed();
    }

    private static void restoreHandsGraveBack(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 3));
        player.simulateDeath();

        Check.equal(helper, 3, run(helper, "graveless restore " + player.name()), "restored item count");
        Check.isTrue(helper, player.records().isEmpty(), "grave should be gone after a command restore");
        Check.equal(helper, 3, player.countOf(Items.DIAMOND), "items returned by the command");
        Check.equal(helper, 0, run(helper, "graveless restore " + player.name()),
                "restoring again with no graves left");
        helper.succeed();
    }

    private static void prunePlayerTrimsArchive(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        GameTestSequence sequence = helper.startSequence();
        sequence = BackupTests.deaths(sequence, player, 4);
        sequence.thenExecute(() -> {
                    Check.equal(helper, 4, GraveBackups.list(player.server(), player.id()).size(),
                            "archive size before pruning");
                    Check.equal(helper, 3, run(helper, "graveless prune player " + player.name() + " 1"),
                            "files reported deleted");
                    Check.equal(helper, 1, GraveBackups.list(player.server(), player.id()).size(),
                            "archive size after pruning to one");
                    Check.equal(helper, 0, run(helper, "graveless prune player " + player.name() + " 1"),
                            "pruning again should delete nothing");
                })
                .thenSucceed();
    }

    private static void pruneRefusesUnlimitedRetention(GameTestHelper helper) {
        TestPlayer player = TestPlayer.join(helper);
        player.give(0, new ItemStack(Items.DIAMOND, 1));
        player.simulateDeath();

        Check.equal(helper, GraveBackups.UNLIMITED, GraveBackups.retentionLimit(),
                "the default retention limit");
        Check.equal(helper, 0, run(helper, "graveless prune player " + player.name()),
                "prune with no keep count under unlimited retention");
        Check.equal(helper, 1, GraveBackups.list(player.server(), player.id()).size(),
                "archive should be untouched");
        helper.succeed();
    }

    private static void pruneAllUsesConfiguredRetention(GameTestHelper helper) {
        TestCleanup cleanup = TestCleanup.attach(helper);
        cleanup.config(GravelessConfig.SERVER.maxBackupsPerPlayer, 1);

        TestPlayer player = TestPlayer.join(helper);
        GameTestSequence sequence = helper.startSequence();
        sequence = BackupTests.deaths(sequence, player, 3);
        sequence.thenExecute(() -> {
                    Check.equal(helper, 1, GraveBackups.list(player.server(), player.id()).size(),
                            "retention already trims on write");
                    run(helper, "graveless prune all");
                    Check.equal(helper, 1, GraveBackups.list(player.server(), player.id()).size(),
                            "archive size after a sweep");
                    Check.equal(helper, 3, GraveBackups.read(player.server(),
                                    GraveBackups.list(player.server(), player.id()).getFirst()).itemCount(),
                            "the surviving archive file should be the newest death");
                })
                .thenSucceed();
    }
}
