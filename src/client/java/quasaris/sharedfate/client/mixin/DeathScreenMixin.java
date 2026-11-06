package quasaris.sharedfate.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import quasaris.sharedfate.client.ReadyUi;

@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin extends Screen {
    protected DeathScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void sharedfate$addReady(CallbackInfo ci) {
        java.util.List<net.minecraft.client.gui.components.events.GuiEventListener> toRemove = new java.util.ArrayList<>();
        for (var child : this.children()) {
            if (child instanceof Button btn) {
                String text = btn.getMessage().getString().toLowerCase();
                if (text.contains("spectate") || text.contains("respawn") || text.contains("наблюдать") || text.contains("возродиться")) {
                    toRemove.add(btn);
                } else {
                    btn.setY(this.height / 4 + 120);
                }
            }
        }
        for (var widget : toRemove) {
            this.removeWidget(widget);
        }

        this.addRenderableWidget(ReadyUi.createButton(this.width / 2 - 100, this.height / 4 + 72, 200, 1));
        this.addRenderableWidget(ReadyUi.createButton(this.width / 2 - 100, this.height / 4 + 96, 200, 2));
        this.addRenderableWidget(ReadyUi.createLine(this.width, this.height - 32, this.font, true));
        this.addRenderableWidget(ReadyUi.createLine(this.width, this.height - 20, this.font, false));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sharedfate$onTick(CallbackInfo ci) {
        ReadyUi.refresh();
    }
}
