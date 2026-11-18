package com.hp.zephyrarch.client;

import com.hp.zephyrarch.entity.ZephyrarchEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class ZephyrarchRenderer extends GeoEntityRenderer<ZephyrarchEntity> {
    public ZephyrarchRenderer(EntityRendererProvider.Context context) {
        super(context, new ZephyrarchModel());
        this.shadowRadius = 1.15F;
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderType getRenderType(ZephyrarchEntity animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public boolean shouldRender(ZephyrarchEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().inflate(4.0D));
    }
}
