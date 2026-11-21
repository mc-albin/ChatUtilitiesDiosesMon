package chatutilities.mixin;

import chatutilities.core.config.ModConfig;
import chatutilities.hud.ScaleHelper;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Draws Minecraft's scoreboard sidebar smaller when "Scoreboard" is below 100% in the settings.
 * It shrinks towards its usual place (right edge, vertically centred) and is never removed.
 * The whole method is wrapped, so the scale is always undone even if another mod cancels it.
 */
@Mixin(InGameHud.class)
public abstract class ScoreboardMixin {
    @WrapMethod(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V")
    private void chatutilities$shrink(DrawContext context, ScoreboardObjective objective, Operation<Void> original) {
        ScaleHelper.push(context, ModConfig.get().scoreboardScale);
        try {
            original.call(context, objective);
        } finally {
            context.getMatrices().pop();
        }
    }
}
