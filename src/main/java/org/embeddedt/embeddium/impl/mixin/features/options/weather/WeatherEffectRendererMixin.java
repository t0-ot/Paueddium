package org.embeddedt.embeddium.impl.mixin.features.options.weather;

import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.embeddedt.embeddium.impl.Embeddium;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {
    @Redirect(method = "render(Lnet/minecraft/world/level/Level;Lnet/minecraft/client/renderer/LightTexture;IFLnet/minecraft/world/phys/Vec3;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;useFancyGraphics()Z"))
    private boolean redirectGetFancyWeather() {
        return Embeddium.options().quality.weatherQuality.isFancy(Minecraft.getInstance().options.graphicsMode().get());
    }
}