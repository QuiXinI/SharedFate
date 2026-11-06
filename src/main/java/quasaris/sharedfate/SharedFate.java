package quasaris.sharedfate;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import quasaris.sharedfate.net.ReadyStatePayload;
import quasaris.sharedfate.net.ReadyTogglePayload;

public class SharedFate implements ModInitializer {
    public static final String MOD_ID = "shared-fate";

    @Override
    public void onInitialize() {
        SharedFateConfig.load();

        PayloadTypeRegistry.clientboundPlay().register(ReadyStatePayload.TYPE, ReadyStatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ReadyTogglePayload.TYPE, ReadyTogglePayload.CODEC);

        DeathSync.register();
        WorldStartPhase.register();
        ReadyManager.register();
    }
}
