package quasaris.sharedfate;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import quasaris.sharedfate.net.ReadyStatePayload;
import quasaris.sharedfate.net.ReadyTogglePayload;

public final class ReadyManager {
    private static boolean waiting = false;
    private static final Set<UUID> ready = new HashSet<>();
    private static String deathLine = "";
    public static String deathTime = "";

    public static boolean isWaiting() {
        return waiting;
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ReadyTogglePayload.TYPE,
                (payload, context) -> onToggle(context.player(), payload.action()));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("ready")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        if (!waiting) {
                            context.getSource().sendFailure(Component.literal("Опрос готовности сейчас не активен."));
                            return 0;
                        }
                        onToggle(player, (byte) 1);
                        return 1;
                    }));

            dispatcher.register(Commands.literal("unready")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        if (!waiting) {
                            context.getSource().sendFailure(Component.literal("Опрос готовности сейчас не активен."));
                            return 0;
                        }
                        onToggle(player, (byte) 2);
                        return 1;
                    }));
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (waiting) {
                newPlayer.setGameMode(GameType.SPECTATOR);
            }
            MinecraftServer server = newPlayer.level().getServer();
            if (server != null) {
                server.execute(() -> broadcast(server));
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (waiting) {
                handler.getPlayer().setGameMode(GameType.SPECTATOR);
            }
            server.execute(() -> broadcast(server));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ready.remove(handler.getPlayer().getUUID());
            server.execute(() -> {
                broadcast(server);
                checkAllReady(server);
            });
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            waiting = false;
            ready.clear();
        });
    }

    public static void beginWaiting(MinecraftServer server, String line) {
        waiting = true;
        ready.clear();
        deathLine = line;
        deathTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        broadcast(server);
    }

    private static void onToggle(ServerPlayer player, byte action) {
        MinecraftServer server = player.level().getServer();
        if (action == 3) {
            sendStateTo(player);
            return;
        }

        if (!waiting) {
            return;
        }
        UUID id = player.getUUID();
        if (action == 1) {
            ready.add(id);
        } else if (action == 2) {
            ready.remove(id);
        } else {
            if (!ready.add(id)) {
                ready.remove(id);
            }
        }
        if (server == null) {
            return;
        }
        broadcast(server);

        int total = server.getPlayerList().getPlayers().size();
        int readyCount = ready.size();
        if (readyCount < total) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("Готовы " + readyCount + " / " + total + " игроков")
                            .withStyle(ChatFormatting.YELLOW), false);
        } else {
            checkAllReady(server);
        }
    }

    private static void checkAllReady(MinecraftServer server) {
        if (!waiting) {
            return;
        }
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        for (ServerPlayer p : players) {
            if (!ready.contains(p.getUUID())) {
                return;
            }
        }
        onAllReady(server);
    }

    private static void onAllReady(MinecraftServer server) {
        int total = server.getPlayerList().getPlayers().size();
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("Готовы " + total + " / " + total + " игроков")
                        .withStyle(ChatFormatting.YELLOW), false);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("Готовы все игроки, запускаю пересоздание мира")
                        .withStyle(ChatFormatting.GREEN), false);
        broadcast(server);
    }

    private static void sendStateTo(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        int total = server != null ? server.getPlayerList().getPlayers().size() : 1;
        int readyCount = 0;
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (ready.contains(p.getUUID())) {
                    readyCount++;
                }
            }
        }
        ServerPlayNetworking.send(player,
                new ReadyStatePayload(waiting, readyCount, total, ready.contains(player.getUUID()), deathLine));
    }

    private static void broadcast(MinecraftServer server) {
        var players = server.getPlayerList().getPlayers();
        int total = players.size();
        int readyCount = 0;
        for (ServerPlayer p : players) {
            if (ready.contains(p.getUUID())) {
                readyCount++;
            }
        }
        for (ServerPlayer p : players) {
            ServerPlayNetworking.send(p,
                    new ReadyStatePayload(waiting, readyCount, total, ready.contains(p.getUUID()), deathLine));
        }
    }

    private ReadyManager() {}
}
