package quasaris.sharedfate.client.mixin;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the mod press the vanilla "Create" button programmatically. */
@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
    @Invoker("onCreate")
    void sharedfate$onCreate();
}
