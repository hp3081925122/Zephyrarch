package com.hp.zephyrarch.client;

import com.hp.zephyrarch.item.SovereignGauntletItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class SovereignGauntletRenderer extends GeoItemRenderer<SovereignGauntletItem> {
    public SovereignGauntletRenderer() {
        super(new SovereignGauntletModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderType getRenderType(SovereignGauntletItem animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }
}
