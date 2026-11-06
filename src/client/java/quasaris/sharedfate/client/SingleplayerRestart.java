package quasaris.sharedfate.client;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import quasaris.sharedfate.client.mixin.CreateWorldScreenAccessor;

public final class SingleplayerRestart {
    private static boolean leaving = false;
    private static boolean opening = false;
    private static boolean creating = false;

    public static void begin(Minecraft mc) {
        if (leaving || opening || creating) {
            return;
        }
        leaving = true;
        mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
    }

    public static void tick(Minecraft mc) {
        if (leaving && mc.gui.screen() instanceof TitleScreen) {
            leaving = false;
            opening = true;
            CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen()));
            return;
        }
        if (opening && mc.gui.screen() instanceof CreateWorldScreen screen) {
            opening = false;
            creating = true;
            screen.getUiState().setSeed(Long.toString(new Random().nextLong()));
            ((CreateWorldScreenAccessor) screen).sharedfate$onCreate();
            creating = false;
        }
    }

    private SingleplayerRestart() {}
}
