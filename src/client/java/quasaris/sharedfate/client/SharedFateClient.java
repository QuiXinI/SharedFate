package quasaris.sharedfate.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import quasaris.sharedfate.SharedFate;
import quasaris.sharedfate.net.ReadyStatePayload;

public class SharedFateClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ReadyStatePayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientReadyState.update(payload)));

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(SharedFate.MOD_ID, "ready_status"),
                ReadyHud::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> ReadyUi.refresh());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientReadyState.reset());
    }
}
