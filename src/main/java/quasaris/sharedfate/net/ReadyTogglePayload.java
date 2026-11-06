package quasaris.sharedfate.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import quasaris.sharedfate.SharedFate;

public record ReadyTogglePayload(byte action) implements CustomPacketPayload {
    public static final Type<ReadyTogglePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SharedFate.MOD_ID, "ready_toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReadyTogglePayload> CODEC =
            CustomPacketPayload.codec(ReadyTogglePayload::write, ReadyTogglePayload::new);

    private ReadyTogglePayload(RegistryFriendlyByteBuf buf) {
        this(buf.readByte());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeByte(action);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
