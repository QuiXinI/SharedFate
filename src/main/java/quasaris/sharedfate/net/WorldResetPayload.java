package quasaris.sharedfate.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import quasaris.sharedfate.SharedFate;

public record WorldResetPayload(boolean resetting, String message) implements CustomPacketPayload {
    public static final Type<WorldResetPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SharedFate.MOD_ID, "world_reset"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldResetPayload> CODEC =
            CustomPacketPayload.codec(WorldResetPayload::write, WorldResetPayload::new);

    private WorldResetPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readUtf(256));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(resetting);
        buf.writeUtf(message, 256);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
