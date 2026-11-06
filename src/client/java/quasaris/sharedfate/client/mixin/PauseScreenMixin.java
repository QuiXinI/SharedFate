package quasaris.sharedfate.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import quasaris.sharedfate.client.ReadyUi;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void sharedfate$addReady(CallbackInfo ci) {
        this.addRenderableWidget(ReadyUi.createButton(this.width / 2 - 100, this.height / 4 + 144, 200, 0));
        this.addRenderableWidget(ReadyUi.createButton(this.width / 2 - 100, this.height / 4 + 168, 200, 4));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sharedfate$onTick(CallbackInfo ci) {
        ReadyUi.refresh();
    }
}
