package quasaris.sharedfate.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;

public final class ReadyHud {
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientReadyState.isWaiting() || mc.gui.hud.isHidden() || mc.gui.screen() instanceof DeathScreen) {
            return;
        }
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        Component l1 = Component.literal(ClientReadyState.deathLine());
        Component l2 = Component.literal("Готовы: ").append(ClientReadyState.counter());

        graphics.text(mc.font, l1, (sw - mc.font.width(l1)) / 2, sh - 58, 0xFFFFFFFF, true);
        graphics.text(mc.font, l2, (sw - mc.font.width(l2)) / 2, sh - 47, 0xFFFFFFFF, true);
    }

    private ReadyHud() {}
}
