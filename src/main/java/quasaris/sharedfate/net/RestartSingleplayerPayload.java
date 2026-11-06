package quasaris.sharedfate.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import quasaris.sharedfate.SharedFate;

public record RestartSingleplayerPayload() implements CustomPacketPayload {
    public static final Type<RestartSingleplayerPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SharedFate.MOD_ID, "restart_singleplayer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RestartSingleplayerPayload> CODEC =
            StreamCodec.unit(new RestartSingleplayerPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
