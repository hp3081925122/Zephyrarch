package com.hp.zephyrarch.client;

import com.hp.zephyrarch.entity.ZephyrBladeEntity;
import com.hp.zephyrarch.entity.ZephyrTwisterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** 小龙卷和回旋风刃没有模型，直接用风特效贴图绘制。 */
public final class ZephyrWindRenderers {
    private ZephyrWindRenderers() {
    }

    public static final class Twister extends EntityRenderer<ZephyrTwisterEntity> {
        public Twister(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.5F;
        }

        @Override
        public void render(ZephyrTwisterEntity entity, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
            float time = entity.tickCount + partialTick;
            float fade = Math.max(0.0F, entity.getFade(partialTick));
            float wobble = (float) Math.sin(time * 0.2F) * 0.08F;
            ps.pushPose();
            ps.mulPose(Axis.ZP.rotation(wobble));
            ZephyrarchVfx.drawFunnelWall(ps, buffers, 0.0D, 0.9D, 0.25F, 0.55F, -time * 0.35F,
                    0.82F, 0.97F, 1.0F, 0.75F * fade, 0.5F * fade, 0.8F * fade, 0.95F * fade);
            ZephyrarchVfx.drawFunnelWall(ps, buffers, 0.8D, 1.8D, 0.5F, 0.9F, time * 0.3F,
                    0.8F, 0.96F, 1.0F, 0.65F * fade, 0.45F * fade, 0.75F * fade, 0.9F * fade);
            ZephyrarchVfx.drawFunnelWall(ps, buffers, 1.7D, 2.7D, 0.85F, 1.3F, -time * 0.25F,
                    0.78F, 0.95F, 1.0F, 0.5F * fade, 0.35F * fade, 0.6F * fade, 0.8F * fade);
            ps.popPose();
            super.render(entity, yaw, partialTick, ps, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(ZephyrTwisterEntity entity) {
            return ZephyrarchVfx.RIBBON;
        }
    }

    public static final class Blade extends EntityRenderer<ZephyrBladeEntity> {
        public Blade(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void render(ZephyrBladeEntity entity, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
            float time = entity.tickCount + partialTick;
            float size = 0.9F;
            ps.pushPose();
            ps.translate(0.0D, 0.2D, 0.0D);
            ps.mulPose(Axis.YP.rotationDegrees(time * 40.0F));
            PoseStack.Pose pose = ps.last();
            Vec3 a = new Vec3(-size, 0.0D, -size);
            Vec3 b = new Vec3(size, 0.0D, -size);
            Vec3 c = new Vec3(size, 0.0D, size);
            Vec3 d = new Vec3(-size, 0.0D, size);
            ZephyrarchVfx.quadTwoSided(pose, buffers.getBuffer(RenderType.entityTranslucent(ZephyrarchVfx.SLASH)), a, b, c, d,
                    0.0F, 0.0F, 1.0F, 1.0F, 0.85F, 0.98F, 1.0F, 0.95F);
            ZephyrarchVfx.quadTwoSided(pose, buffers.getBuffer(ZephyrarchVfx.additive(ZephyrarchVfx.SLASH_GLOW)),
                    a.add(0.0D, 0.01D, 0.0D), b.add(0.0D, 0.01D, 0.0D), c.add(0.0D, 0.01D, 0.0D), d.add(0.0D, 0.01D, 0.0D),
                    0.0F, 0.0F, 1.0F, 1.0F, 0.65F, 0.92F, 1.0F, 1.0F);
            ps.mulPose(Axis.YP.rotationDegrees(90.0F));
            pose = ps.last();
            ZephyrarchVfx.quadTwoSided(pose, buffers.getBuffer(ZephyrarchVfx.additive(ZephyrarchVfx.SLASH_GLOW)), a, b, c, d,
                    0.0F, 0.0F, 1.0F, 1.0F, 0.5F, 0.8F, 0.95F, 1.0F);
            ps.popPose();
            super.render(entity, yaw, partialTick, ps, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(ZephyrBladeEntity entity) {
            return ZephyrarchVfx.SLASH;
        }
    }
}
