package quasaris.sharedfate.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import quasaris.sharedfate.SharedFate;
import quasaris.sharedfate.net.ReadyStatePayload;
import quasaris.sharedfate.net.RestartSingleplayerPayload;
import quasaris.sharedfate.net.WorldResetPayload;

public class SharedFateClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ReadyStatePayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientReadyState.update(payload)));

        ClientPlayNetworking.registerGlobalReceiver(WorldResetPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (payload.resetting()) {
                        mc.gui.setScreen(new WorldResetScreen(Component.literal(payload.message())));
                    } else {
                        if (mc.gui.screen() instanceof WorldResetScreen) {
                            mc.gui.setScreen(null);
                        }
                    }
                }));

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(SharedFate.MOD_ID, "ready_status"),
                ReadyHud::render);

        ClientPlayNetworking.registerGlobalReceiver(RestartSingleplayerPayload.TYPE,
                (payload, context) -> context.client().execute(() -> SingleplayerRestart.begin(context.client(), payload.hardcore(), payload.levelId(), payload.label())));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ReadyUi.refresh();
            SingleplayerRestart.tick(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientReadyState.reset());
    }
}
