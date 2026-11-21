package chatutilities.mixin;

import chatutilities.core.config.ModConfig;
import chatutilities.hud.ScaleHelper;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * The DiosesMon client mod draws its own scoreboard panel instead of Minecraft's sidebar. This
 * shrinks that panel too, by the "Scoreboard" setting. {@code @Pseudo} and {@code require = 0}:
 * without that mod (or if it changes) nothing happens and nothing breaks.
 */
@Pseudo
@Mixin(targets = "net.diosesmon.client.scoreboard.ScoreboardModule", remap = false)
public abstract class DiosesmonScoreboardMixin {
    @WrapMethod(method = "renderPanel", require = 0, remap = false)
    private static void chatutilities$shrink(DrawContext context, Operation<Void> original) {
        ScaleHelper.push(context, ModConfig.get().scoreboardScale);
        try {
            original.call(context);
        } finally {
            context.getMatrices().pop();
        }
    }
}
