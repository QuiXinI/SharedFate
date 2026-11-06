package quasaris.sharedfate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

public final class DeathSync {
    private static final Map<UUID, Component> PENDING = new HashMap<>();
    private static boolean propagating = false;
    public static boolean enabled = true;

    private static final Map<String, String> CAUSES = Map.ofEntries(
            Map.entry("fall", "падения"),
            Map.entry("lava", "лавы"),
            Map.entry("inFire", "огня"),
            Map.entry("onFire", "горения"),
            Map.entry("drown", "утопления"),
            Map.entry("starve", "голода"),
            Map.entry("outOfWorld", "падения в бездну"),
            Map.entry("explosion", "взрыва"),
            Map.entry("magic", "магии"),
            Map.entry("wither", "иссушения"),
            Map.entry("cactus", "кактуса"),
            Map.entry("sweetBerryBush", "ягодного куста"),
            Map.entry("freeze", "замерзания"),
            Map.entry("lightningBolt", "удара молнии"),
            Map.entry("hotFloor", "раскалённого пола"),
            Map.entry("inWall", "удушья в стене"),
            Map.entry("cramming", "давки"),
            Map.entry("flyIntoWall", "удара о стену на элитрах"),
            Map.entry("fallingBlock", "упавшего блока"),
            Map.entry("anvil", "наковальни"),
            Map.entry("stalagmite", "сталагмита"),
            Map.entry("fallingStalactite", "сталактита"),
            Map.entry("dragonBreath", "дыхания дракона"),
            Map.entry("thorns", "шипов"),
            Map.entry("dryout", "высыхания"),
            Map.entry("genericKill", "команды kill"),
            Map.entry("generic", "неизвестной причины"));

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!enabled || propagating || ReadyManager.isWaiting() || !(entity instanceof ServerPlayer dead)) {
                return true;
            }
            MinecraftServer server = dead.level().getServer();
            if (server == null) {
                return true;
            }

            Component message = buildMessage(dead, source);
            PENDING.put(dead.getUUID(), message);
            ReadyManager.beginWaiting(server, message.getString());

            List<ServerPlayer> others = server.getPlayerList().getPlayers().stream()
                    .filter(p -> p != dead && p.isAlive() && !p.isSpectator())
                    .toList();

            propagating = true;
            try {
                for (ServerPlayer p : others) {
                    PENDING.put(p.getUUID(), message);
                    p.kill((ServerLevel) p.level());
                }
            } finally {
                propagating = false;
            }
            return true;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> PENDING.clear());
    }

    public static Component pendingFor(UUID id) {
        return PENDING.get(id);
    }

    private static Component buildMessage(ServerPlayer dead, DamageSource source) {
        return Component.literal("Игрок ")
                .append(dead.getDisplayName())
                .append(" умер от ")
                .append(describeCause(source));
    }

    private static Component describeCause(DamageSource source) {
        Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        if (attacker != null) {
            return Component.literal("атаки (").append(attacker.getDisplayName()).append(")");
        }
        String id = source.type().msgId();
        return Component.literal(CAUSES.getOrDefault(id, id));
    }

    private DeathSync() {}
}
