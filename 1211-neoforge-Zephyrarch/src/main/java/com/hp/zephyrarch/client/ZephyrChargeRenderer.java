package com.hp.zephyrarch.client;

import com.hp.zephyrarch.entity.ZephyrChargeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class ZephyrChargeRenderer extends GeoEntityRenderer<ZephyrChargeEntity> {
    public ZephyrChargeRenderer(EntityRendererProvider.Context context) {
        super(context, new ZephyrChargeModel());
        this.shadowRadius = 0.25F;
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderType getRenderType(ZephyrChargeEntity animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }
}
