package quasaris.sharedfate.net;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import quasaris.sharedfate.SharedFate;

public record DownloadRequestPayload() implements CustomPacketPayload {
    public static final Type<DownloadRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SharedFate.MOD_ID, "download_request"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, DownloadRequestPayload> CODEC =
            StreamCodec.unit(new DownloadRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
