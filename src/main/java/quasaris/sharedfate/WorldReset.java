package quasaris.sharedfate;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import quasaris.sharedfate.net.DownloadRequestPayload;
import quasaris.sharedfate.net.RestartSingleplayerPayload;
import quasaris.sharedfate.net.WorldResetPayload;

public final class WorldReset {
    private static final Logger LOG = LoggerFactory.getLogger("SharedFate");
    private static final String FLAG_NAME = "shared-fate-reset.flag";
    private static final String WAIT_PROPERTY = "sharedfate.waitForPid";

    private static final Component RESTART_MESSAGE = Component.empty()
            .append(Component.literal("Сервер ушёл на рестарт").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal("\n"))
            .append(Component.literal("Создаётся новый мир. Переподключитесь через пару минут.")
                    .withStyle(ChatFormatting.GRAY));

    private static boolean requested = false;
    private static boolean downloadRequested = false;

    public static boolean isRequested() {
        return requested;
    }

    public static void onNewRound() {
        downloadRequested = false;
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(DownloadRequestPayload.TYPE,
                (payload, context) -> onDownloadRequest(context.player()));

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (requested && server.isDedicatedServer()) {
                Runtime.getRuntime().halt(0);
            }
        });
    }

    private static void onDownloadRequest(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !ReadyManager.isWaiting() || downloadRequested || requested) {
            return;
        }
        downloadRequested = true;
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(player.getName().getString() + " запросил скачивание мира, архив готовится...")
                        .withStyle(ChatFormatting.AQUA), false);

        server.tickRateManager().setFrozen(true);
        server.saveEverything(true, true, true);
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        String label = ReadyManager.currentLabel();

        Thread archiver = new Thread(() -> {
            boolean ok;
            try {
                WorldDownloads.archive(worldDir, label);
                ok = true;
            } catch (IOException e) {
                LOG.error("Could not archive world for download", e);
                ok = false;
            }
            boolean finalOk = ok;
            server.execute(() -> {
                if (!requested) {
                    server.tickRateManager().setFrozen(false);
                }
                if (finalOk) {
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal("Мир готов к скачиванию: ")
                                    .append(WorldDownloads.linkComponent(WorldDownloads.url())), false);
                } else {
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal("Не удалось подготовить архив мира, см. лог сервера")
                                    .withStyle(ChatFormatting.RED), false);
                }
            });
        }, "sharedfate-archive");
        archiver.setDaemon(true);
        archiver.start();
    }

    public static void begin(MinecraftServer server) {
        if (requested) {
            return;
        }
        if (!server.isDedicatedServer()) {
            requested = true;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(p, new RestartSingleplayerPayload());
            }
            return;
        }
        requested = true;
        LOG.info("All players ready: resetting world");

        Path gameDir = FabricLoader.getInstance().getGameDir();
        try {
            server.tickRateManager().setFrozen(true);
            server.saveEverything(true, true, true);

            Path worldDir = server.getWorldPath(LevelResource.ROOT);
            writeNewSeed(gameDir, new Random().nextLong());
            Files.writeString(gameDir.resolve(FLAG_NAME),
                    "world=" + worldDir.getFileName() + "\n", StandardCharsets.UTF_8);

            scheduleRelaunch(gameDir);
        } catch (Exception e) {
            LOG.error("World reset aborted, game continues", e);
            abort(server, gameDir);
            return;
        }

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.disconnect(RESTART_MESSAGE);
        }
        server.halt(false);
    }

    private static void abort(MinecraftServer server, Path gameDir) {
        requested = false;
        try {
            Files.deleteIfExists(gameDir.resolve(FLAG_NAME));
        } catch (IOException ignored) {
        }
        server.tickRateManager().setFrozen(false);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, new WorldResetPayload(false, ""));
        }
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("Не удалось пересоздать мир, подробности в логе сервера")
                        .withStyle(ChatFormatting.RED), false);
    }

    public static void applyOnStartup() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path flag = gameDir.resolve(FLAG_NAME);
        if (!Files.exists(flag)) {
            return;
        }
        String pid = System.getProperty(WAIT_PROPERTY);
        if (pid != null) {
            waitForExit(Long.parseLong(pid.trim()));
        }
        try {
            String world = null;
            for (String line : Files.readAllLines(flag, StandardCharsets.UTF_8)) {
                if (line.startsWith("world=")) {
                    world = line.substring("world=".length()).trim();
                }
            }
            if (world != null && world.matches("[A-Za-z0-9._ -]+") && !world.equals(".") && !world.contains("..")) {
                Path dir = gameDir.resolve(world);
                deleteRecursively(dir);
                LOG.info("Old world removed for reset: {}", world);
            }
            Files.deleteIfExists(flag);
        } catch (IOException e) {
            LOG.error("Failed to finish world reset", e);
        }
    }

    private static void waitForExit(long pid) {
        ProcessHandle.of(pid).ifPresent(handle -> {
            try {
                handle.onExit().get(3, TimeUnit.MINUTES);
            } catch (Exception ignored) {
            }
        });
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void scheduleRelaunch(Path gameDir) throws IOException {
        List<String> cmd = buildServerCommand();
        Path log = gameDir.resolve("logs").resolve("shared-fate-relaunch.log");
        Files.createDirectories(log.getParent());
        new ProcessBuilder(cmd)
                .directory(gameDir.toFile())
                .redirectInput(ProcessBuilder.Redirect.from(new File(isWindows() ? "NUL" : "/dev/null")))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                .start();
    }

    private static List<String> buildServerCommand() {
        String java = ProcessHandle.current().info().command().orElseGet(() ->
                Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString());

        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        cmd.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
        cmd.add("-D" + WAIT_PROPERTY + "=" + ProcessHandle.current().pid());

        String classpath = System.getProperty("java.class.path");
        String[] tokens = System.getProperty("sun.java.command", "").trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            throw new IllegalStateException("Cannot determine the server command line");
        }
        String first = tokens[0];
        if (first.equals(classpath)) {
            cmd.add("-jar");
            cmd.add(classpath);
        } else {
            cmd.add("-cp");
            cmd.add(classpath);
            cmd.add(first);
        }
        cmd.addAll(Arrays.asList(tokens).subList(1, tokens.length));
        return cmd;
    }

    private static void writeNewSeed(Path gameDir, long seed) throws IOException {
        Path props = gameDir.resolve("server.properties");
        List<String> lines = Files.exists(props)
                ? new ArrayList<>(Files.readAllLines(props, StandardCharsets.ISO_8859_1))
                : new ArrayList<>();

        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("level-seed=")) {
                lines.set(i, "level-seed=" + seed);
                found = true;
            }
        }
        if (!found) {
            lines.add("level-seed=" + seed);
        }
        Files.write(props, lines, StandardCharsets.ISO_8859_1);
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private WorldReset() {}
}
