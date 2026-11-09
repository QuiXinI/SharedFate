package quasaris.sharedfate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final Set<String> KNOWN_CAUSES = Set.of(
            "fall", "lava", "inFire", "onFire", "drown", "starve", "outOfWorld",
            "explosion", "magic", "wither", "cactus", "sweetBerryBush", "freeze",
            "lightningBolt", "hotFloor", "inWall", "cramming", "flyIntoWall",
            "fallingBlock", "anvil", "stalagmite", "fallingStalactite", "dragonBreath",
            "thorns", "dryout", "genericKill", "generic");

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
        return Component.translatable("death.shared-fate.message", dead.getDisplayName(), describeCause(source));
    }

    private static Component describeCause(DamageSource source) {
        Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        if (attacker != null) {
            return Component.translatable("death.shared-fate.cause.attack", attacker.getDisplayName());
        }
        String id = source.type().msgId();
        if (KNOWN_CAUSES.contains(id)) {
            return Component.translatable("death.shared-fate.cause." + id);
        }
        return Component.translatable("death.shared-fate.cause.generic");
    }

    private DeathSync() {}
}
