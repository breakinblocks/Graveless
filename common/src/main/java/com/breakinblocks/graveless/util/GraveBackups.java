package com.breakinblocks.graveless.util;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.data.DeathRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

public final class GraveBackups {
    private GraveBackups() {
    }

    public static final int UNLIMITED = -1;

    private static final Comparator<Path> NEWEST_FIRST =
            Comparator.comparingLong(GraveBackups::gameTimeOf)
                    .thenComparing(path -> path.getFileName().toString())
                    .reversed();

    public static Path directory(MinecraftServer server, UUID owner) {
        return archiveRoot(server).resolve(owner.toString());
    }

    public static List<Path> list(MinecraftServer server, UUID owner) {
        Path dir = directory(server, owner);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .sorted(NEWEST_FIRST)
                    .toList();
        } catch (IOException e) {
            Graveless.LOGGER.error("Failed to list grave backups for {}", owner, e);
            return List.of();
        }
    }

    public static List<UUID> owners(MinecraftServer server) {
        Path root = archiveRoot(server);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(root)) {
            return stream.filter(Files::isDirectory)
                    .map(GraveBackups::ownerOf)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            Graveless.LOGGER.error("Failed to list grave backup owners", e);
            return List.of();
        }
    }

    public static int retentionLimit() {
        return GravelessConfig.SERVER.maxBackupsPerPlayer.get();
    }

    public static int prune(MinecraftServer server, UUID owner, int keep) {
        if (keep < 0) {
            return 0;
        }
        List<Path> files = list(server, owner);
        if (files.size() <= keep) {
            return 0;
        }
        int deleted = 0;
        for (Path file : files.subList(keep, files.size())) {
            try {
                if (Files.deleteIfExists(file)) {
                    deleted++;
                }
            } catch (IOException e) {
                Graveless.LOGGER.error("Failed to delete grave backup {}", file, e);
            }
        }
        if (deleted > 0) {
            Graveless.LOGGER.info("Pruned {} archived grave files for {} (keeping {})", deleted, owner, keep);
        }
        return deleted;
    }

    private static Path archiveRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("graveless");
    }

    private static UUID ownerOf(Path dir) {
        try {
            return UUID.fromString(dir.getFileName().toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static DeathRecord read(MinecraftServer server, Path file) {
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            return DeathRecord.CODEC.parse(ops, tag)
                    .resultOrPartial(error -> Graveless.LOGGER.error("Failed to parse grave backup {}: {}", file, error))
                    .orElse(null);
        } catch (Exception e) {
            Graveless.LOGGER.error("Failed to read grave backup {}", file, e);
            return null;
        }
    }

    private static long gameTimeOf(Path path) {
        String name = path.getFileName().toString();
        int dash = name.indexOf('-');
        if (dash > 0) {
            try {
                return Long.parseLong(name.substring(0, dash));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }

    public static void write(MinecraftServer server, UUID owner, DeathRecord record) {
        try {
            Path dir = directory(server, owner);
            Files.createDirectories(dir);
            RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            Tag tag = DeathRecord.CODEC.encodeStart(ops, record).getOrThrow();
            if (tag instanceof CompoundTag compound) {
                NbtIo.writeCompressed(compound, dir.resolve(record.gameTime() + "-" + record.id() + ".nbt"));
            }
        } catch (Exception e) {
            Graveless.LOGGER.error("Failed to write grave backup for {}", owner, e);
        }
        prune(server, owner, retentionLimit());
    }
}
