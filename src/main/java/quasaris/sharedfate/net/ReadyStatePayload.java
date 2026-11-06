package quasaris.sharedfate.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import quasaris.sharedfate.SharedFate;

public record ReadyStatePayload(boolean waiting, int ready, int total, boolean self, String deathLine)
        implements CustomPacketPayload {
    public static final Type<ReadyStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SharedFate.MOD_ID, "ready_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReadyStatePayload> CODEC =
            CustomPacketPayload.codec(ReadyStatePayload::write, ReadyStatePayload::new);

    private ReadyStatePayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(512));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(waiting);
        buf.writeVarInt(ready);
        buf.writeVarInt(total);
        buf.writeBoolean(self);
        buf.writeUtf(deathLine.length() > 500 ? deathLine.substring(0, 500) : deathLine, 512);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
