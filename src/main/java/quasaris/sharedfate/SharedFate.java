package quasaris.sharedfate;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import quasaris.sharedfate.net.DownloadRequestPayload;
import quasaris.sharedfate.net.ReadyStatePayload;
import quasaris.sharedfate.net.RestartSingleplayerPayload;
import quasaris.sharedfate.net.ReadyTogglePayload;
import quasaris.sharedfate.net.WorldResetPayload;

public class SharedFate implements ModInitializer {
    public static final String MOD_ID = "shared-fate";

    @Override
    public void onInitialize() {
        SharedFateConfig.load();

        PayloadTypeRegistry.clientboundPlay().register(ReadyStatePayload.TYPE, ReadyStatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ReadyTogglePayload.TYPE, ReadyTogglePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WorldResetPayload.TYPE, WorldResetPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RestartSingleplayerPayload.TYPE, RestartSingleplayerPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DownloadRequestPayload.TYPE, DownloadRequestPayload.CODEC);

        // Finish a pending reset before the server loads its world.
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            WorldReset.applyOnStartup();
        }

        DeathSync.register();
        WorldStartPhase.register();
        ReadyManager.register();
        WorldDownloads.register();
    }
}
