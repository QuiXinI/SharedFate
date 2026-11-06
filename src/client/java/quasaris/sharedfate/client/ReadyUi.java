package quasaris.sharedfate.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import quasaris.sharedfate.net.DownloadRequestPayload;
import quasaris.sharedfate.net.ReadyTogglePayload;

public final class ReadyUi {
    private static Button readyDeathButton, unreadyDeathButton, pauseButton, downloadDeathButton, downloadPauseButton;
    private static MultiLineTextWidget line1, line2;

    public static Button createButton(int x, int y, int width, int type) {
        Button b;
        if (type == 1) {
            b = Button.builder(readyLabel(), btn -> action((byte) 1)).bounds(x, y, width, 20).build();
            readyDeathButton = b;
        } else if (type == 2) {
            b = Button.builder(Component.literal("Не готов").withStyle(ChatFormatting.RED), btn -> action((byte) 2)).bounds(x, y, width, 20).build();
            unreadyDeathButton = b;
        } else if (type == 3 || type == 4) {
            b = Button.builder(downloadLabel(), btn -> requestDownload()).bounds(x, y, width, 20).build();
            if (type == 3) {
                downloadDeathButton = b;
            } else {
                downloadPauseButton = b;
            }
            b.visible = ClientReadyState.isWaiting();
            b.active = ClientReadyState.isWaiting() && !ClientReadyState.isDownloadRequested();
        } else {
            b = Button.builder(pauseLabel(), btn -> action((byte) 0)).bounds(x, y, width, 20).build();
            pauseButton = b;
            b.visible = true;
            b.active = ClientReadyState.isWaiting();
            if (Minecraft.getInstance().player != null) {
                ClientPlayNetworking.send(new ReadyTogglePayload((byte) 3));
            }
        }
        return b;
    }

    public static MultiLineTextWidget createLine(int screenWidth, int y, Font font, boolean first) {
        MultiLineTextWidget w = new MultiLineTextWidget(0, y, Component.empty(), font);
        w.setMaxWidth(screenWidth);
        w.setCentered(true);
        if (first) {
            line1 = w;
        } else {
            line2 = w;
        }
        return w;
    }

    private static Component readyLabel() {
        Component status = Component.literal("Готов").withStyle(ChatFormatting.GREEN);
        return Component.empty().append(status).append("  ").append(ClientReadyState.counter());
    }

    private static Component pauseLabel() {
        if (!ClientReadyState.isWaiting()) {
            return Component.literal("Готовность (недоступно)").withStyle(ChatFormatting.GRAY);
        }
        Component status = ClientReadyState.isSelfReady()
                ? Component.literal("Готов").withStyle(ChatFormatting.GREEN)
                : Component.literal("Не готов").withStyle(ChatFormatting.RED);
        return Component.empty().append(status).append("  ").append(ClientReadyState.counter());
    }

    private static Component downloadLabel() {
        return ClientReadyState.isDownloadRequested()
                ? Component.literal("Скачивание запрошено").withStyle(ChatFormatting.GRAY)
                : Component.literal("Скачать мир").withStyle(ChatFormatting.AQUA);
    }

    private static void requestDownload() {
        if (ClientReadyState.isDownloadRequested() || !ClientReadyState.isWaiting()) {
            return;
        }
        ClientPlayNetworking.send(new DownloadRequestPayload());
        ClientReadyState.markDownloadRequested();
        refresh();
    }

    private static void action(byte mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ClientPlayNetworking.send(new ReadyTogglePayload(mode));
        if (mc.player.isDeadOrDying() && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        }
    }

    public static void refresh() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof DeathScreen)) {
            downloadDeathButton = null;
            readyDeathButton = null;
            unreadyDeathButton = null;
            line1 = null;
            line2 = null;
        }
        if (!(mc.gui.screen() instanceof PauseScreen)) {
            pauseButton = null;
            downloadPauseButton = null;
        }
        boolean waiting = ClientReadyState.isWaiting();
        if (readyDeathButton != null) {
            readyDeathButton.setMessage(readyLabel());
            readyDeathButton.active = waiting;
        }
        if (unreadyDeathButton != null) {
            unreadyDeathButton.active = waiting;
        }
        if (pauseButton != null) {
            pauseButton.setMessage(pauseLabel());
            pauseButton.visible = true;
            pauseButton.active = waiting;
        }
        for (Button d : new Button[] {downloadDeathButton, downloadPauseButton}) {
            if (d != null) {
                d.setMessage(downloadLabel());
                d.visible = waiting;
                d.active = waiting && !ClientReadyState.isDownloadRequested();
            }
        }
        if (line1 != null) {
            line1.setMessage(Component.literal(ClientReadyState.deathLine()));
        }
        if (line2 != null) {
            line2.setMessage(Component.literal("Готовы: ").append(ClientReadyState.counter()));
        }
    }

    private ReadyUi() {}
}
