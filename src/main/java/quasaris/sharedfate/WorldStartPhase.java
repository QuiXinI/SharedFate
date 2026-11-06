package quasaris.sharedfate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class WorldStartPhase {
    private static final long TICK_MS = 50;

    public static volatile Component chunkyStatus = Component.empty();

    private static boolean active = false;
    private static boolean chunkyMissing = false;
    private static boolean chunkyStarted = false;
    private static long elapsedMs = 0;
    private static Path file;

    public static boolean isActive() {
        return active;
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(WorldStartPhase::onStart);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            save();
            chunkyStarted = false;
        });
        ServerTickEvents.END_SERVER_TICK.register(WorldStartPhase::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (chunkyMissing) {
                handler.getPlayer().sendSystemMessage(
                        Component.literal("Chunky не установлен, пропускаю подготовку мира")
                                .withStyle(ChatFormatting.RED), false);
            }
        });
    }

    private static void onStart(MinecraftServer server) {
        if (!FabricLoader.getInstance().isModLoaded("chunky")) {
            chunkyMissing = true;
            active = false;
            return;
        }

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
    }

    private static void startChunky(MinecraftServer server) {
        try {
            var chunky = org.popcraft.chunky.ChunkyProvider.get();
            var api = chunky.getApi();
            String worldName = SharedFateConfig.getChunkyWorld();
            String shape = SharedFateConfig.getChunkyShape();
            double centerX = SharedFateConfig.getChunkyCenterX();
            double centerZ = SharedFateConfig.getChunkyCenterZ();
            double radius = SharedFateConfig.getChunkyRadius();
            String pattern = SharedFateConfig.getChunkyPattern();

            if (!api.isRunning(worldName)) {
                api.startTask(
                        worldName,
                        shape,
                        centerX,
                        centerZ,
                        radius,
                        radius,
                        pattern
                );
            }

            api.onGenerationProgress(event -> {
                if (event.world().equals(worldName)) {
                    chunkyStatus = Component.literal(String.format("Chunky: %.1f%%", event.progress()))
                            .withStyle(ChatFormatting.AQUA);
                }
            });

            api.onGenerationComplete(event -> {
                if (event.world().equals(worldName)) {
                    chunkyStatus = Component.literal("Chunky завершен").withStyle(ChatFormatting.GREEN);
                }
            });
            chunkyStarted = true;
        } catch (Throwable e) {
            chunkyStatus = Component.literal("Chunky ошибка: " + e.getMessage()).withStyle(ChatFormatting.RED);
            chunkyStarted = true;
        }
    }

    private static void tick(MinecraftServer server) {
        if (!active) {
            return;
        }

        if (!chunkyStarted) {
            startChunky(server);
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
        if (t % 200 == 0) {
            save();
        }
    }

    private static void end(MinecraftServer server) {
        active = false;
        elapsedMs = SharedFateConfig.getDurationMs();
        save();

        try {
            var chunky = org.popcraft.chunky.ChunkyProvider.get();
            if (chunky != null) {
                var api = chunky.getApi();
                String worldName = SharedFateConfig.getChunkyWorld();
                if (api.isRunning(worldName)) {
                    api.cancelTask(worldName);
                }
            }
        } catch (Throwable ignored) {
        }
        chunkyStatus = Component.empty();

        broadcastActionBar(server, Component.literal("Мир запущен: мобы включены")
                .withStyle(ChatFormatting.GREEN));
    }

    private static Component statusLine() {
        long left = Math.max(0, (SharedFateConfig.getDurationMs() - elapsedMs) / 1000);
        String time = String.format("%d:%02d", left / 60, left % 60);
        Component line = Component.literal("Подготовка мира: " + time).withStyle(ChatFormatting.YELLOW);
        if (!chunkyStatus.getString().isEmpty()) {
            return Component.empty().append(line).append(Component.literal("  |  ").withStyle(ChatFormatting.GRAY))
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
