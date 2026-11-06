package quasaris.sharedfate.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WorldResetScreen extends Screen {
    public WorldResetScreen(Component title) {
        super(title);
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, DeltaTracker delta) {
        graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, this.height / 2 - 20, 0xFFFFFFFF, true);
        Component sub = Component.literal("Пожалуйста, подождите, мир создается заново...");
        graphics.text(this.font, sub, (this.width - this.font.width(sub)) / 2, this.height / 2 + 10, 0xFFAAAAAA, true);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
