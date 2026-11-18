package com.hp.zephyrarch.client;

import com.hp.zephyrarch.entity.ZephyrChargeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
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
    public void render(ZephyrChargeEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float scale = entity.getScale();
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
        ZephyrarchVfx.renderChargeTrail(entity, partialTick, poseStack, bufferSource,
                this.entityRenderDispatcher.cameraOrientation(), this.entityRenderDispatcher.camera.getPosition());
    }

    @Override
    public RenderType getRenderType(ZephyrChargeEntity animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public boolean shouldRender(ZephyrChargeEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().inflate(6.0D));
    }
}
