package chatutilities.mixin;

import chatutilities.core.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides Minecraft's own chat messages while "Chat de Minecraft" is turned off in the mod, also
 * when the chat is opened with T: only the typing box is shown. Messages keep arriving (the mod
 * reads them and shows them in its panel).
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void chatutilities$hideWhenOff(DrawContext context, int currentTick, int mouseX, int mouseY,
                                            boolean focused, CallbackInfo ci) {
        if (ModConfig.get().hideVanillaChat) {
            ci.cancel();
        }
    }
}
