package com.breakinblocks.graveless.commands;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.data.GraveProfile;
import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.data.GraveStore;
import com.breakinblocks.graveless.event.GraveMenuHandlers;
import com.breakinblocks.graveless.restore.RestoreEngine;
import com.breakinblocks.graveless.util.GraveBackups;
import com.breakinblocks.graveless.util.SpiritCompassManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.authlib.GameProfile;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class GravelessCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Graveless.MOD_ID)
                .executes(GravelessCommands::openMenu)
                .then(Commands.literal("gui")
                        .executes(GravelessCommands::openMenu))
                .then(Commands.literal("allow")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> allow(context, true))))
                .then(Commands.literal("deny")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> allow(context, false))))
                .then(Commands.literal("clear")
                        .executes(GravelessCommands::clearAllowed))
                .then(Commands.literal("enable")
                        .executes(context -> setEnabled(context, true)))
                .then(Commands.literal("disable")
                        .executes(context -> setEnabled(context, false)))
                .then(Commands.literal("list")
                        .executes(context -> list(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> list(context, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("restore")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> restore(context, 1))
                                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                        .executes(context -> restore(context,
                                                IntegerArgumentType.getInteger(context, "index"))))))
                .then(Commands.literal("backups")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(GravelessCommands::listBackups)))
                .then(Commands.literal("restorebackup")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                        .executes(context -> restoreBackup(context,
                                                IntegerArgumentType.getInteger(context, "index"))))))
                .then(Commands.literal("prune")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("all")
                                .executes(context -> pruneAll(context, GraveBackups.retentionLimit()))
                                .then(Commands.argument("keep", IntegerArgumentType.integer(0))
                                        .executes(context -> pruneAll(context,
                                                IntegerArgumentType.getInteger(context, "keep")))))
                        .then(Commands.literal("player")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(context -> prunePlayer(context, GraveBackups.retentionLimit()))
                                        .then(Commands.argument("keep", IntegerArgumentType.integer(0))
                                                .executes(context -> prunePlayer(context,
                                                        IntegerArgumentType.getInteger(context, "keep"))))))));
    }

    private static int openMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        GraveMenuHandlers.openFor(player);
        return 1;
    }

    private static GraveProfile profileOf(ServerPlayer player) {
        return GraveStore.get(player.level().getServer()).profile(player.getUUID());
    }

    private static int allow(CommandContext<CommandSourceStack> context, boolean add) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        GraveStore store = GraveStore.get(player.level().getServer());
        GraveProfile profile = store.profile(player.getUUID());
        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(context, "player");
        int changed = 0;
        for (GameProfile target : targets) {
            if (target.getId().equals(player.getUUID())) {
                continue;
            }
            boolean result = add ? profile.allowed().add(target.getId()) : profile.allowed().remove(target.getId());
            if (result) {
                changed++;
                context.getSource().sendSuccess(() -> Component.translatable(
                        add ? "graveless.command.allowed" : "graveless.command.denied", target.getName()), false);
            }
        }
        if (changed > 0) {
            store.setDirty();
        }
        return changed;
    }

    private static int clearAllowed(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        GraveStore store = GraveStore.get(player.level().getServer());
        GraveProfile profile = store.profile(player.getUUID());
        int count = profile.allowed().size();
        profile.allowed().clear();
        store.setDirty();
        context.getSource().sendSuccess(() -> Component.translatable("graveless.command.cleared", count), false);
        return count;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> context, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        GraveStore store = GraveStore.get(player.level().getServer());
        GraveProfile profile = store.profile(player.getUUID());
        profile.setEnabled(enabled);
        store.setDirty();
        context.getSource().sendSuccess(() -> Component.translatable(
                enabled ? "graveless.command.enabled" : "graveless.command.disabled"), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        GraveProfile profile = profileOf(target);
        List<DeathRecord> records = profile.records();
        if (records.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("graveless.command.list.empty",
                    target.getName().getString()), false);
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("graveless.command.list.header",
                target.getName().getString(), records.size()), false);
        for (int i = 0; i < records.size(); i++) {
            DeathRecord record = records.get(records.size() - 1 - i);
            int index = i + 1;
            BlockPos pos = record.pos().pos();
            context.getSource().sendSuccess(() -> Component.translatable("graveless.command.list.entry",
                    index, record.itemCount(), record.xp(),
                    pos.getX(), pos.getY(), pos.getZ(),
                    record.pos().dimension().location().toString(),
                    record.cause()).withStyle(ChatFormatting.GRAY), false);
        }
        return records.size();
    }

    private static GameProfile backupTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(context, "player");
        return targets.iterator().next();
    }

    private static String timestamp(DeathRecord record) {
        if (record.epochMillis() > 0) {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .format(Instant.ofEpochMilli(record.epochMillis())
                            .atZone(ZoneId.systemDefault()));
        }
        return "day " + record.gameTime() / 24000L;
    }

    private static int listBackups(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        GameProfile target = backupTarget(context);
        MinecraftServer server = context.getSource().getServer();
        List<Path> files = GraveBackups.list(server, target.getId());
        if (files.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("graveless.command.backups.empty", target.getName()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("graveless.command.backups.header",
                target.getName(), files.size()), false);
        int shown = Math.min(files.size(), 10);
        for (int i = 0; i < shown; i++) {
            int index = i + 1;
            Path file = files.get(i);
            DeathRecord record = GraveBackups.read(server, file);
            if (record == null) {
                context.getSource().sendSuccess(() -> Component.translatable("graveless.command.backups.unreadable",
                        index, file.getFileName().toString()).withStyle(ChatFormatting.RED), false);
                continue;
            }
            BlockPos pos = record.pos().pos();
            context.getSource().sendSuccess(() -> Component.translatable("graveless.command.backups.entry",
                    index, record.itemCount(), timestamp(record),
                    pos.getX(), pos.getY(), pos.getZ(),
                    record.pos().dimension().location().toString()).withStyle(ChatFormatting.GRAY), false);
        }
        return files.size();
    }

    private static int restoreBackup(CommandContext<CommandSourceStack> context, int index) throws CommandSyntaxException {
        GameProfile target = backupTarget(context);
        MinecraftServer server = context.getSource().getServer();
        List<Path> files = GraveBackups.list(server, target.getId());
        if (files.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("graveless.command.backups.empty", target.getName()));
            return 0;
        }
        if (index > files.size()) {
            context.getSource().sendFailure(Component.translatable("graveless.command.restore.bad_index",
                    index, files.size()));
            return 0;
        }
        DeathRecord revived = GraveMenuHandlers.reviveBackup(server, target.getId(), files.get(index - 1));
        if (revived == null) {
            context.getSource().sendFailure(Component.translatable("graveless.command.backups.unreadable",
                    index, files.get(index - 1).getFileName().toString()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("graveless.command.restorebackup.done",
                index, revived.itemCount(), target.getName()), true);
        return revived.itemCount();
    }

    private static int prunePlayer(CommandContext<CommandSourceStack> context, int keep) throws CommandSyntaxException {
        if (keep < 0) {
            context.getSource().sendFailure(Component.translatable("graveless.command.prune.unlimited"));
            return 0;
        }
        GameProfile target = backupTarget(context);
        int deleted = GraveBackups.prune(context.getSource().getServer(), target.getId(), keep);
        context.getSource().sendSuccess(() -> Component.translatable("graveless.command.prune.player",
                deleted, target.getName(), keep), true);
        return deleted;
    }

    private static int pruneAll(CommandContext<CommandSourceStack> context, int keep) {
        if (keep < 0) {
            context.getSource().sendFailure(Component.translatable("graveless.command.prune.unlimited"));
            return 0;
        }
        MinecraftServer server = context.getSource().getServer();
        List<UUID> owners = GraveBackups.owners(server);
        int deleted = 0;
        int touched = 0;
        for (UUID owner : owners) {
            int removed = GraveBackups.prune(server, owner, keep);
            if (removed > 0) {
                deleted += removed;
                touched++;
            }
        }
        int total = deleted;
        int players = touched;
        context.getSource().sendSuccess(() -> Component.translatable("graveless.command.prune.all",
                total, players, owners.size(), keep), true);
        return deleted;
    }

    private static int restore(CommandContext<CommandSourceStack> context, int index) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        GraveStore store = GraveStore.get(target.level().getServer());
        GraveProfile profile = store.profile(target.getUUID());
        List<DeathRecord> records = profile.records();
        if (records.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("graveless.command.list.empty",
                    target.getName().getString()));
            return 0;
        }
        if (index > records.size()) {
            context.getSource().sendFailure(Component.translatable("graveless.command.restore.bad_index",
                    index, records.size()));
            return 0;
        }
        DeathRecord record = records.get(records.size() - index);
        RestoreEngine.Result result = RestoreEngine.claim(target, profile, record, store);
        SpiritCompassManager.refresh(target);
        context.getSource().sendSuccess(() -> Component.translatable("graveless.command.restore.done",
                result.restored(), target.getName().getString(), result.remaining()), true);
        target.sendSystemMessage(Component.translatable("graveless.restore.received",
                result.restored()).withStyle(ChatFormatting.AQUA));
        return result.restored();
    }
}
