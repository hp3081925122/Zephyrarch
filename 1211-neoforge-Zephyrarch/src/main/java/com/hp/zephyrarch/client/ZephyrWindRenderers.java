package com.hp.zephyrarch.client;

import com.hp.zephyrarch.entity.ZephyrBladeEntity;
import com.hp.zephyrarch.entity.ZephyrTwisterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class ZephyrWindRenderers {
    private ZephyrWindRenderers() {
    }

    public static final class Twister extends EntityRenderer<ZephyrTwisterEntity> {
        public Twister(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.55F;
        }

        @Override
        public void render(ZephyrTwisterEntity entity, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
            float time = entity.tickCount + partialTick;
            float fade = Math.max(0.0F, entity.getFade(partialTick));
            if (fade <= 0.01F) {
                return;
            }
            float wobbleX = Mth.sin(time * 0.22F) * 0.07F;
            float wobbleZ = Mth.cos(time * 0.18F) * 0.07F;

            ZephyrarchVfx.drawFlatRing(ps, buffers, 0.05D, 1.15F, time * 22.0F,
                    0.82F, 0.98F, 1.0F, 0.72F * fade,
                    0.55F * fade, 0.88F * fade, 1.0F * fade);

            ps.pushPose();
            ps.mulPose(Axis.XP.rotation(wobbleX));
            ps.mulPose(Axis.ZP.rotation(wobbleZ));

            ZephyrarchVfx.drawFunnelWall(ps, buffers, 0.05D, 2.55D, 0.16F, 0.36F, time * 0.48F,
                    0.94F, 0.99F, 1.0F, 0.82F * fade,
                    0.75F * fade, 0.95F * fade, 1.0F * fade);

            ZephyrarchVfx.drawFunnelWall(ps, buffers, 0.0D, 0.95D, 0.28F, 0.62F, -time * 0.36F,
                    0.82F, 0.97F, 1.0F, 0.75F * fade,
                    0.52F * fade, 0.82F * fade, 0.96F * fade);
            ZephyrarchVfx.drawFunnelWall(ps, buffers, 0.82D, 1.85D, 0.55F, 0.96F, time * 0.31F,
                    0.80F, 0.96F, 1.0F, 0.65F * fade,
                    0.46F * fade, 0.76F * fade, 0.92F * fade);
            ZephyrarchVfx.drawFunnelWall(ps, buffers, 1.70D, 2.75D, 0.88F, 1.38F, -time * 0.26F,
                    0.78F, 0.95F, 1.0F, 0.52F * fade,
                    0.36F * fade, 0.65F * fade, 0.84F * fade);

            ZephyrarchVfx.drawFlatRing(ps, buffers, 2.65D, 1.32F, -time * 16.0F,
                    0.80F, 0.96F, 1.0F, 0.46F * fade,
                    0.42F * fade, 0.75F * fade, 0.92F * fade);
            ps.popPose();

            super.render(entity, yaw, partialTick, ps, buffers, light);
        }

        @Override
        public boolean shouldRender(ZephyrTwisterEntity entity, Frustum frustum, double x, double y, double z) {
            return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().inflate(3.0D));
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
            int side = entity.getBladeSide();
            float spinDir = side >= 0 ? 1.0F : -1.0F;
            float size = 1.08F;

            ps.pushPose();
            ps.translate(0.0D, 0.16D, 0.0D);
            ps.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
            ps.mulPose(Axis.ZP.rotationDegrees(18.0F * spinDir));
            ps.mulPose(Axis.YP.rotationDegrees(time * 48.0F * spinDir));

            PoseStack.Pose pose = ps.last();
            Vec3 a = new Vec3(-size, 0.0D, -size);
            Vec3 b = new Vec3(size, 0.0D, -size);
            Vec3 c = new Vec3(size, 0.0D, size);
            Vec3 d = new Vec3(-size, 0.0D, size);

            ZephyrarchVfx.quadTwoSided(pose, buffers.getBuffer(RenderType.entityTranslucent(ZephyrarchVfx.SLASH)),
                    a, b, c, d, 0.0F, 0.0F, 1.0F, 1.0F, 0.86F, 0.98F, 1.0F, 0.96F);
            ZephyrarchVfx.quadTwoSided(pose, buffers.getBuffer(ZephyrarchVfx.additive(ZephyrarchVfx.SLASH_GLOW)),
                    a.add(0.0D, 0.01D, 0.0D), b.add(0.0D, 0.01D, 0.0D),
                    c.add(0.0D, 0.01D, 0.0D), d.add(0.0D, 0.01D, 0.0D),
                    0.0F, 0.0F, 1.0F, 1.0F, 0.72F, 0.95F, 1.0F, 1.0F);

            ps.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose = ps.last();
            float inner = size * 0.88F;
            Vec3 ia = new Vec3(-inner, 0.015D, -inner);
            Vec3 ib = new Vec3(inner, 0.015D, -inner);
            Vec3 ic = new Vec3(inner, 0.015D, inner);
            Vec3 id = new Vec3(-inner, 0.015D, inner);
            ZephyrarchVfx.quadTwoSided(pose, buffers.getBuffer(RenderType.entityTranslucent(ZephyrarchVfx.SLASH)),
                    ia, ib, ic, id, 0.0F, 0.0F, 1.0F, 1.0F, 0.82F, 0.97F, 1.0F, 0.88F);
            ZephyrarchVfx.quadTwoSided(pose, buffers.getBuffer(ZephyrarchVfx.additive(ZephyrarchVfx.SLASH_GLOW)),
                    ia, ib, ic, id, 0.0F, 0.0F, 1.0F, 1.0F, 0.62F, 0.92F, 1.0F, 1.0F);

            ZephyrarchVfx.drawFlatRing(ps, buffers, 0.02D, 0.68F, -time * 32.0F * spinDir,
                    0.92F, 0.99F, 1.0F, 0.78F, 0.68F, 0.94F, 1.0F);
            ps.popPose();

            ZephyrarchVfx.drawBillboardFlare(ps, buffers, this.entityRenderDispatcher.cameraOrientation(),
                    new Vec3(0.0D, 0.18D, 0.0D), 0.42F, time * 18.0F * spinDir, 0.75F, 0.96F, 1.0F);
            ZephyrarchVfx.renderBladeTrail(entity, partialTick, ps, buffers, this.entityRenderDispatcher.camera.getPosition());

            super.render(entity, yaw, partialTick, ps, buffers, light);
        }

        @Override
        public boolean shouldRender(ZephyrBladeEntity entity, Frustum frustum, double x, double y, double z) {
            return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().inflate(8.0D));
        }

        @Override
        public ResourceLocation getTextureLocation(ZephyrBladeEntity entity) {
            return ZephyrarchVfx.SLASH;
        }
    }
}
