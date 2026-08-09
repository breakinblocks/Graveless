package com.breakinblocks.graveless.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class GravelessConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ServerConfig SERVER;

    public static final ModConfigSpec CLIENT_SPEC;
    public static final ClientConfig CLIENT;

    static {
        final Pair<ServerConfig, ModConfigSpec> serverPair =
                new ModConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = serverPair.getRight();
        SERVER = serverPair.getLeft();

        final Pair<ClientConfig, ModConfigSpec> clientPair =
                new ModConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT_SPEC = clientPair.getRight();
        CLIENT = clientPair.getLeft();
    }

    public static class ServerConfig {
        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.IntValue maxRecordsPerPlayer;
        public final ModConfigSpec.IntValue visibilityRadius;
        public final ModConfigSpec.IntValue claimRange;
        public final ModConfigSpec.BooleanValue requireLineOfSight;
        public final ModConfigSpec.BooleanValue protectionEnabled;
        public final ModConfigSpec.IntValue protectionDuration;
        public final ModConfigSpec.IntValue protectionLinger;

        ServerConfig(ModConfigSpec.Builder builder) {
            builder.push("general");
            enabled = builder
                    .comment("Master switch for Graveless death handling.")
                    .define("enabled", true);
            maxRecordsPerPlayer = builder
                    .comment("Maximum death records retained per player. Oldest records are dropped first (backup files on disk are kept).")
                    .defineInRange("max_records_per_player", 30, 1, 1000);
            visibilityRadius = builder
                    .comment("Distance in blocks at which grave ghosts are sent to nearby players and remain visible.")
                    .defineInRange("visibility_radius", 256, 16, 512);
            claimRange = builder
                    .comment("Maximum distance in blocks from which a ghost can be claimed. The claim aim passes through blocks.")
                    .defineInRange("claim_range", 64, 2, 128);
            requireLineOfSight = builder
                    .comment("Require an unobstructed line of sight to the ghost to claim it. When enabled, claims no longer work through walls.")
                    .define("require_line_of_sight", false);
            builder.pop();
            builder.push("protection");
            protectionEnabled = builder
                    .comment("Grant Spirit Ward (invisibility, night vision, mobs ignore you) when entering visibility range of your own grave.")
                    .define("protection_enabled", true);
            protectionDuration = builder
                    .comment("How long Spirit Ward lasts in seconds if the grave is not claimed.")
                    .defineInRange("protection_duration", 120, 5, 600);
            protectionLinger = builder
                    .comment("How long Spirit Ward lingers in seconds after claiming the grave.")
                    .defineInRange("protection_linger", 1, 0, 60);
            builder.pop();
        }
    }

    public static class ClientConfig {
        public final ModConfigSpec.BooleanValue useShaders;
        public final ModConfigSpec.BooleanValue debugLogging;

        ClientConfig(ModConfigSpec.Builder builder) {
            builder.push("general");
            useShaders = builder
                    .comment("Use the custom ghost shader (fresnel rim glow, shimmer). Disable to fall back to plain translucent rendering if the shader causes issues on your system.")
                    .define("use_shaders", true);
            debugLogging = builder
                    .comment("Log extra client-side diagnostic information.")
                    .define("debug_logging", false);
            builder.pop();
        }
    }
}
