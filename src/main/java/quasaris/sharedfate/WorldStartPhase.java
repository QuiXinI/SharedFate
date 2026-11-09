package quasaris.sharedfate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Preparation phase of a fresh world: mob AI is frozen (see MobSpawnMixin), Chunky pregenerates,
 * and the server list description shows the progress, refreshed every 10 seconds.
 * Distant Horizons is left enabled all the time.
 */
public final class WorldStartPhase {
    private static final Logger LOG = LoggerFactory.getLogger("SharedFate");
    private static final long TICK_MS = 50;
    private static final int MOTD_INTERVAL_TICKS = 200; // 10 s
    private static final int CHUNKY_POLL_DELAY_TICKS = 40;

    public static volatile Component chunkyStatus = Component.empty();

    private static boolean active = false;
    private static boolean chunkyMissing = false;
    private static boolean chunkyStarted = false;
    private static boolean chunkyDone = false;
    private static int chunkyStartTick = 0;
    private static volatile float chunkyPercent = 0f;
    private static long elapsedMs = 0;
    private static Path file;
    private static String originalMotd = null;

    public static boolean isActive() {
        return active;
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(WorldStartPhase::onStart);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            save();
            if (originalMotd != null) {
                server.setMotd(originalMotd);
            }
            resetChunkyState();
        });
        ServerTickEvents.END_SERVER_TICK.register(WorldStartPhase::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (chunkyMissing) {
                handler.getPlayer().sendSystemMessage(
                        Component.translatable("text.shared-fate.chunky_missing")
                                .withStyle(ChatFormatting.RED), false);
            }
        });
    }

    private static void onStart(MinecraftServer server) {
        resetChunkyState();
        originalMotd = server.getMotd();
        if (!FabricLoader.getInstance().isModLoaded("chunky")) {
            chunkyMissing = true;
            active = false;
            return;
        }
        chunkyMissing = false;

        file = server.getWorldPath(LevelResource.ROOT).resolve("sharedfate_phase.txt");
        elapsedMs = 0;
        try {
            if (Files.exists(file)) {
                elapsedMs = Long.parseLong(Files.readString(file).trim());
            }
        } catch (IOException | NumberFormatException e) {
            elapsedMs = 0;
        }
        active = elapsedMs < SharedFateConfig.getDurationMs();
        save();
        server.setMotd(motdText());
    }

    private static void resetChunkyState() {
        chunkyStarted = false;
        chunkyDone = false;
        chunkyStartTick = 0;
        chunkyPercent = 0f;
        chunkyStatus = Component.empty();
    }

    private static void tick(MinecraftServer server) {
        if (!active) {
            return;
        }

        if (!chunkyMissing) {
            if (!chunkyStarted) {
                startChunky(server);
            } else if (!chunkyDone && server.getTickCount() - chunkyStartTick > CHUNKY_POLL_DELAY_TICKS) {
                pollChunky();
            }
        }

        elapsedMs += TICK_MS;
        if (elapsedMs >= SharedFateConfig.getDurationMs()) {
            end(server);
            return;
        }

        int t = server.getTickCount();
        if (t % 10 == 0) {
            broadcastActionBar(server, statusLine());
        }
        if (t % MOTD_INTERVAL_TICKS == 0) {
            server.setMotd(motdText());
        }
        if (t % 200 == 0) {
            save();
        }
    }

    private static void startChunky(MinecraftServer server) {
        chunkyStarted = true;
        chunkyStartTick = server.getTickCount();
        try {
            var api = org.popcraft.chunky.ChunkyProvider.get().getApi();
            String worldName = SharedFateConfig.getChunkyWorld();

            if (!api.isRunning(worldName)) {
                api.startTask(
                        worldName,
                        SharedFateConfig.getChunkyShape(),
                        SharedFateConfig.getChunkyCenterX(),
                        SharedFateConfig.getChunkyCenterZ(),
                        SharedFateConfig.getChunkyRadius(),
                        SharedFateConfig.getChunkyRadius(),
                        SharedFateConfig.getChunkyPattern()
                );
            }

            api.onGenerationProgress(event -> {
                if (event.world().equals(worldName)) {
                    chunkyPercent = (float) event.progress();
                    chunkyStatus = Component.translatable("text.shared-fate.chunky_progress", String.format("%.1f", event.progress()))
                            .withStyle(ChatFormatting.AQUA);
                }
            });

            api.onGenerationComplete(event -> {
                if (event.world().equals(worldName)) {
                    finishChunky();
                }
            });
        } catch (Throwable e) {
            LOG.warn("Chunky could not be started", e);
            chunkyStatus = Component.translatable("text.shared-fate.chunky_error", e.getMessage()).withStyle(ChatFormatting.RED);
            chunkyDone = true;
        }
    }

    /** Polling catches completion even if the Chunky event never fires. */
    private static void pollChunky() {
        try {
            var api = org.popcraft.chunky.ChunkyProvider.get().getApi();
            if (!api.isRunning(SharedFateConfig.getChunkyWorld())) {
                finishChunky();
            }
        } catch (Throwable e) {
            finishChunky();
        }
    }

    private static void finishChunky() {
        if (chunkyDone) {
            return;
        }
        chunkyDone = true;
        chunkyPercent = 100f;
        chunkyStatus = Component.translatable("text.shared-fate.chunky_complete").withStyle(ChatFormatting.GREEN);
    }

    private static void end(MinecraftServer server) {
        active = false;
        elapsedMs = SharedFateConfig.getDurationMs();
        save();

        if (chunkyStarted && !chunkyDone && !chunkyMissing) {
            cancelChunky();
        }
        chunkyDone = true;
        chunkyStatus = Component.empty();

        if (originalMotd != null) {
            server.setMotd(originalMotd);
        }
        broadcastActionBar(server, Component.translatable("text.shared-fate.prep_complete")
                .withStyle(ChatFormatting.GREEN));
    }

    private static void cancelChunky() {
        try {
            var api = org.popcraft.chunky.ChunkyProvider.get().getApi();
            String worldName = SharedFateConfig.getChunkyWorld();
            if (api.isRunning(worldName)) {
                api.cancelTask(worldName);
            }
        } catch (Throwable e) {
            LOG.warn("Failed to cancel Chunky task", e);
        }
    }

    /** Server list description: prep timer, plus Chunky progress while it runs. */
    private static String motdText() {
        if (!active) {
            return originalMotd != null ? originalMotd : "";
        }
        String time = timeLeft();
        if (!chunkyMissing && chunkyStarted && !chunkyDone) {
            return Component.translatable("text.shared-fate.prep_motd_chunky", time, String.format("%.1f", chunkyPercent)).getString();
        }
        return Component.translatable("text.shared-fate.prep_motd", time).getString();
    }

    private static String timeLeft() {
        long left = Math.max(0, (SharedFateConfig.getDurationMs() - elapsedMs) / 1000);
        return String.format("%d:%02d", left / 60, left % 60);
    }

    private static Component statusLine() {
        Component line = Component.translatable("text.shared-fate.prep_status", timeLeft()).withStyle(ChatFormatting.YELLOW);
        if (!chunkyStatus.getString().isEmpty()) {
            return Component.empty().append(line)
                    .append(Component.literal("  |  ").withStyle(ChatFormatting.GRAY))
                    .append(chunkyStatus);
        }
        return line;
    }

    private static void broadcastActionBar(MinecraftServer server, Component msg) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg, true);
        }
    }

    private static void save() {
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file, Long.toString(elapsedMs));
        } catch (IOException ignored) {
        }
    }

    private WorldStartPhase() {}
}
