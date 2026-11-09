package quasaris.sharedfate.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import quasaris.sharedfate.SharedFate;

/**
 * Server -> client (singleplayer only): leave the world, rename it to the death label,
 * and create a new one with a fresh seed.
 */
public record RestartSingleplayerPayload(boolean hardcore, String levelId, String label) implements CustomPacketPayload {
    public static final Type<RestartSingleplayerPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SharedFate.MOD_ID, "restart_singleplayer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RestartSingleplayerPayload> CODEC =
            CustomPacketPayload.codec(RestartSingleplayerPayload::write, RestartSingleplayerPayload::new);

    private RestartSingleplayerPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readUtf(256), buf.readUtf(512));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(hardcore);
        buf.writeUtf(levelId, 256);
        buf.writeUtf(label, 512);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
