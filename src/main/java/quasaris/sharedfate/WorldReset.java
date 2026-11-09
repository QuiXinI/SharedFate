package quasaris.sharedfate;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

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

/**
 * Creates a new world.
 *
 * Dedicated server:
 *   1. freeze and save the old world;
 *   2. write a new level-name (unique per reset) and a new level-seed to server.properties,
 *      and a flag file with the old world folder name;
 *   3. start the same server command again as a new JVM with -Dsharedfate.waitForPid=<this pid>.
 *      The new JVM (inheriting this terminal) waits for this process to exit, deletes the old
 *      world folder and starts with the new level-name;
 *   4. disconnect players with the restart message and halt.
 *
 * Singleplayer: the client leaves the world and creates a new one (see SingleplayerRestart).
 */
public final class WorldReset {
    private static final String FLAG_NAME = "shared-fate-reset.flag";
    private static final String WAIT_PROPERTY = "sharedfate.waitForPid";

    private static final Component RESTART_MESSAGE = Component.empty()
            .append(Component.translatable("text.shared-fate.server_restart_title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal("\n"))
            .append(Component.translatable("text.shared-fate.server_restart_subtitle")
                    .withStyle(ChatFormatting.GRAY));

    private static boolean requested = false;
    private static boolean downloadRequested = false;

    public static boolean isRequested() {
        return requested;
    }

    /** Called when a new death round starts. */
    public static void onNewRound() {
        downloadRequested = false;
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(DownloadRequestPayload.TYPE,
                (payload, context) -> onDownloadRequest(context.player()));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            requested = false;
            downloadRequested = false;
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            // Only a dedicated server may exit: in singleplayer the process is the game client.
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
                Component.translatable("text.shared-fate.download_requested_broadcast", player.getName().getString())
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
                DebugLog.error("Could not archive world for download", e);
                ok = false;
            }
            boolean finalOk = ok;
            server.execute(() -> {
                if (!requested) {
                    server.tickRateManager().setFrozen(false);
                }
                if (finalOk) {
                    server.getPlayerList().broadcastSystemMessage(
                            Component.translatable("text.shared-fate.download_ready_broadcast")
                                    .append(WorldDownloads.linkComponent(WorldDownloads.url())), false);
                } else {
                    server.getPlayerList().broadcastSystemMessage(
                            Component.translatable("text.shared-fate.download_failed_broadcast")
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
            boolean hardcore = server.isHardcore();
            String levelId = server.getWorldPath(LevelResource.ROOT).getFileName().toString();
            String label = ReadyManager.currentLabel();
            DebugLog.info("singleplayer reset requested (hardcore=" + hardcore + ", level=" + levelId
                    + ", label=" + label + ")");
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(p, new RestartSingleplayerPayload(hardcore, levelId, label));
            }
            return;
        }

        requested = true;
        DebugLog.info("all players ready: resetting dedicated server world");
        Path gameDir = FabricLoader.getInstance().getGameDir();
        try {
            server.tickRateManager().setFrozen(true);
            server.saveEverything(true, true, true);

            String oldLevelName = server.getWorldData().getLevelName();
            String newLevelName = "world-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            long newSeed = new Random().nextLong();
            Files.writeString(gameDir.resolve(FLAG_NAME),
                    "world=" + oldLevelName + "\nnewlevel=" + newLevelName + "\nseed=" + newSeed + "\n",
                    StandardCharsets.UTF_8);
            DebugLog.info("reset prepared: old level=" + oldLevelName + ", new level-name=" + newLevelName
                    + ", seed=" + newSeed + " (settings are written by the new JVM after the old one exits)");

            scheduleRelaunch(gameDir);
        } catch (Exception e) {
            DebugLog.error("World reset aborted, game continues", e);
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
                Component.translatable("text.shared-fate.reset_failed_broadcast")
                        .withStyle(ChatFormatting.RED), false);
    }

    /**
     * Runs before the server loads its world. The old JVM has exited at this point, so nothing can
     * overwrite server.properties any more: delete the old world folder and write the new level-name and seed.
     */
    public static void applyOnStartup() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path flag = gameDir.resolve(FLAG_NAME);
        if (!Files.exists(flag)) {
            return;
        }
        DebugLog.info("reset flag found, finishing reset");
        String pid = System.getProperty(WAIT_PROPERTY);
        if (pid != null) {
            waitForExit(Long.parseLong(pid.trim()));
        }
        try {
            String oldLevel = null, newLevel = null, seed = null;
            for (String line : Files.readAllLines(flag, StandardCharsets.UTF_8)) {
                if (line.startsWith("world=")) oldLevel = line.substring("world=".length()).trim();
                if (line.startsWith("newlevel=")) newLevel = line.substring("newlevel=".length()).trim();
                if (line.startsWith("seed=")) seed = line.substring("seed=".length()).trim();
            }

            if (oldLevel != null && isSafeName(oldLevel) && !oldLevel.equals(newLevel)) {
                Path oldDir = gameDir.resolve(oldLevel);
                deleteRecursively(oldDir);
                DebugLog.info("old world folder " + oldLevel + " still exists after delete: " + Files.exists(oldDir));
            } else {
                DebugLog.info("old world name missing or unsafe (" + oldLevel + "), nothing removed");
            }

            if (newLevel != null && isSafeName(newLevel) && seed != null) {
                writeWorldSettings(gameDir, Long.parseLong(seed), newLevel);
                DebugLog.info("server.properties updated: level-name=" + newLevel + ", level-seed=" + seed);
            } else {
                DebugLog.info("new level settings missing in flag, server.properties not changed");
            }
            DebugLog.info("server.properties now: " + readProperty(gameDir, "level-name")
                    + " / " + readProperty(gameDir, "level-seed"));
            Files.deleteIfExists(flag);
        } catch (IOException | RuntimeException e) {
            DebugLog.error("Failed to finish world reset", e);
        }
    }

    private static String readProperty(Path gameDir, String key) {
        try {
            Path props = gameDir.resolve("server.properties");
            if (!Files.exists(props)) {
                return "(no server.properties)";
            }
            return Files.readAllLines(props, StandardCharsets.ISO_8859_1).stream()
                    .filter(l -> l.startsWith(key + "="))
                    .findFirst()
                    .orElse("(not set)");
        } catch (IOException e) {
            return "(unreadable)";
        }
    }

    private static boolean isSafeName(String name) {
        return name.matches("[A-Za-z0-9._ -]+") && !name.equals(".") && !name.contains("..");
    }

    private static void waitForExit(long pid) {
        ProcessHandle.of(pid).ifPresent(handle -> {
            try {
                handle.onExit().get(3, TimeUnit.MINUTES);
            } catch (Exception e) {
                DebugLog.error("waiting for previous server process failed", e);
            }
        });
        try {
            Thread.sleep(2000); // give the OS a moment to release ports and file locks
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The new JVM inherits this terminal, so its output and console stay visible. */
    private static void scheduleRelaunch(Path gameDir) throws IOException {
        List<String> cmd = buildServerCommand();
        DebugLog.info("relaunch command: " + String.join(" ", cmd));
        new ProcessBuilder(cmd)
                .directory(gameDir.toFile())
                .inheritIO()
                .start();
    }

    /** Rebuilds the command that started this JVM: java, JVM flags, classpath or -jar, main class, args. */
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
        if (first.endsWith(".jar")) {
            // "java -jar server.jar ..." : sun.java.command starts with the jar as it was typed.
            cmd.add("-jar");
            cmd.add(first);
        } else {
            cmd.add("-cp");
            cmd.add(classpath);
            cmd.add(first);
        }
        cmd.addAll(Arrays.asList(tokens).subList(1, tokens.length));
        return cmd;
    }

    private static void writeWorldSettings(Path gameDir, long seed, String levelName) throws IOException {
        Path props = gameDir.resolve("server.properties");
        List<String> lines = Files.exists(props)
                ? new ArrayList<>(Files.readAllLines(props, StandardCharsets.ISO_8859_1))
                : new ArrayList<>();
        setProperty(lines, "level-seed", Long.toString(seed));
        setProperty(lines, "level-name", levelName);
        Files.write(props, lines, StandardCharsets.ISO_8859_1);
    }

    private static void setProperty(List<String> lines, String key, String value) {
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(key + "=")) {
                lines.set(i, key + "=" + value);
                found = true;
            }
        }
        if (!found) {
            lines.add(key + "=" + value);
        }
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
