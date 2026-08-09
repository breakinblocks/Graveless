package com.breakinblocks.graveless.util;

import com.breakinblocks.graveless.Graveless;
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
import java.util.UUID;
import java.util.stream.Stream;

public final class GraveBackups {
    private GraveBackups() {
    }

    public static List<Path> list(MinecraftServer server, UUID owner) {
        Path dir = server.getWorldPath(LevelResource.ROOT)
                .resolve("graveless")
                .resolve(owner.toString());
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .sorted(Comparator.comparingLong(GraveBackups::gameTimeOf).reversed())
                    .toList();
        } catch (IOException e) {
            Graveless.LOGGER.error("Failed to list grave backups for {}", owner, e);
            return List.of();
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
            Path dir = server.getWorldPath(LevelResource.ROOT)
                    .resolve("graveless")
                    .resolve(owner.toString());
            Files.createDirectories(dir);
            RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            Tag tag = DeathRecord.CODEC.encodeStart(ops, record).getOrThrow();
            if (tag instanceof CompoundTag compound) {
                NbtIo.writeCompressed(compound, dir.resolve(record.gameTime() + "-" + record.id() + ".nbt"));
            }
        } catch (Exception e) {
            Graveless.LOGGER.error("Failed to write grave backup for {}", owner, e);
        }
    }
}
