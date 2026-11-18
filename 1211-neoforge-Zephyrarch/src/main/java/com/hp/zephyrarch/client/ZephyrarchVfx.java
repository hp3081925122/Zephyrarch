package com.hp.zephyrarch.client;

import com.hp.zephyrarch.Zephyrarch;
import com.hp.zephyrarch.entity.ZephyrChargeEntity;
import com.hp.zephyrarch.entity.ZephyrarchAttack;
import com.hp.zephyrarch.entity.ZephyrarchEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class ZephyrarchVfx {
    private static final ResourceLocation SLASH = tex("gale_slash");
    private static final ResourceLocation SLASH_GLOW = tex("gale_slash_glow");
    private static final ResourceLocation RING = tex("gale_ring");
    private static final ResourceLocation RING_GLOW = tex("gale_ring_glow");
    private static final ResourceLocation RIBBON = tex("gale_ribbon");
    private static final ResourceLocation RIBBON_GLOW = tex("gale_ribbon_glow");
    private static final ResourceLocation FLARE = tex("gale_flare");

    private ZephyrarchVfx() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Zephyrarch.MOD_ID, "textures/effect/" + name + ".png");
    }

    private static RenderType additive(ResourceLocation texture) {
        return RenderType.eyes(texture);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, Vec3 p, float u, float v, float r, float g, float b, float a) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(Mth.clamp(r, 0.0F, 1.0F), Mth.clamp(g, 0.0F, 1.0F), Mth.clamp(b, 0.0F, 1.0F), Mth.clamp(a, 0.0F, 1.0F))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void quadTwoSided(PoseStack.Pose pose, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                                     float u0, float v0, float u1, float v1, float r, float g, float bl, float alpha) {
        vertex(pose, vc, a, u0, v0, r, g, bl, alpha);
        vertex(pose, vc, b, u1, v0, r, g, bl, alpha);
        vertex(pose, vc, c, u1, v1, r, g, bl, alpha);
        vertex(pose, vc, d, u0, v1, r, g, bl, alpha);
        vertex(pose, vc, d, u0, v1, r, g, bl, alpha);
        vertex(pose, vc, c, u1, v1, r, g, bl, alpha);
        vertex(pose, vc, b, u1, v0, r, g, bl, alpha);
        vertex(pose, vc, a, u0, v0, r, g, bl, alpha);
    }

    public static void renderBossEffects(ZephyrarchEntity boss, float partialTick, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        if (boss.isDeadOrDying()) {
            return;
        }
        float time = boss.tickCount + partialTick;
        boolean phase2 = boss.isPhase2();
        float bodyYaw = Mth.rotLerp(partialTick, boss.yBodyRotO, boss.yBodyRot);

        renderAmbientAura(boss, time, phase2, ps, buffers, cameraRot);

        ZephyrarchAttack attack = boss.getAttack();
        if (attack == ZephyrarchAttack.NONE) {
            return;
        }
        float age = boss.getClientAttackAge(partialTick);
        switch (attack) {
            case COMBO_PUNCH -> renderComboSlashes(age, bodyYaw, phase2, ps, buffers);
            case TEMPEST_SLAM -> renderTempestSlam(boss, age, phase2, ps, buffers);
            case GALE_BARRAGE -> renderGaleBarrage(age, bodyYaw, phase2, ps, buffers);
            case CYCLONE_VORTEX -> renderCycloneVortex(age, time, phase2, ps, buffers, cameraRot);
            case PHASE_SHIFT -> renderPhaseShiftRebirth(age, time, ps, buffers, cameraRot);
            default -> {
            }
        }
    }

    private static void renderAmbientAura(ZephyrarchEntity boss, float time, boolean phase2, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        double groundOffset = findGroundOffset(boss);
        float baseSpin = time * (phase2 ? 4.8F : 2.6F);
        float baseRadius = phase2 ? 1.85F : 1.35F;
        float pulse = 0.85F + 0.15F * Mth.sin(time * 0.15F);

        drawFlatRing(ps, buffers, groundOffset + 0.04D, baseRadius, baseSpin,
                0.75F, 0.96F, 1.0F, phase2 ? 0.55F : 0.36F,
                phase2 ? 0.50F * pulse : 0.25F * pulse, phase2 ? 0.72F * pulse : 0.35F * pulse, phase2 ? 0.78F * pulse : 0.42F * pulse);

        if (phase2) {
            drawFlatRing(ps, buffers, groundOffset + 0.06D, 1.15F, -baseSpin * 1.4F,
                    1.0F, 0.90F, 0.52F, 0.45F,
                    0.55F * pulse, 0.46F * pulse, 0.22F * pulse);

            drawTiltedOrbitRibbon(ps, buffers, 1.65D, 1.45F, 0.36F, 26.0F, 18.0F, -time * 0.055F,
                    0.65F, 0.96F, 1.0F, 0.52F, 0.45F, 0.65F, 0.72F);
            drawTiltedOrbitRibbon(ps, buffers, 1.85D, 1.32F, 0.30F, -24.0F, -20.0F, time * 0.065F,
                    1.0F, 0.92F, 0.55F, 0.42F, 0.52F, 0.44F, 0.20F);

            float coreBob = 1.92F + 0.08F * Mth.sin(time * 0.12F);
            drawBillboardFlare(ps, buffers, cameraRot, new Vec3(0.0D, coreBob, 0.0D), 0.52F * pulse, time * 2.5F,
                    0.95F * pulse, 0.86F * pulse, 0.48F * pulse);
        }
    }

    private static void renderComboSlashes(float age, float bodyYaw, boolean phase2, PoseStack ps, MultiBufferSource buffers) {
        if (age >= 8.5F && age <= 16.5F) {
            float k = (age - 8.5F) / 8.0F;
            drawSlashArc(ps, buffers, bodyYaw, 1.48D, 2.0D, 3.65D, -22.0F, true, k, phase2);
        }
        if (age >= 20.0F && age <= 30.5F) {
            float k = (age - 20.0F) / 10.5F;
            drawSlashArc(ps, buffers, bodyYaw, 1.38D, 2.2D, 4.25D, 10.0F, false, k, phase2);
        }
    }

    private static void drawSlashArc(PoseStack ps, MultiBufferSource buffers, float bodyYaw, double centerY,
                                     double innerR, double outerR, float tiltZDeg, boolean rightToLeft, float k, boolean phase2) {
        float sweepProgress = Mth.clamp(k * 2.2F, 0.0F, 1.0F);
        float fade = k < 0.45F ? 1.0F : Mth.clamp((1.0F - k) / 0.55F, 0.0F, 1.0F);
        if (fade <= 0.01F || sweepProgress <= 0.02F) {
            return;
        }

        ps.pushPose();
        ps.translate(0.0D, centerY, 0.0D);
        ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        ps.translate(0.0D, 0.0D, 0.35D);
        ps.mulPose(Axis.ZP.rotationDegrees(tiltZDeg));

        int segments = 16;
        float startDeg = rightToLeft ? -78.0F : 86.0F;
        float endDeg = rightToLeft ? 78.0F : -86.0F;
        float currentEnd = Mth.lerp(sweepProgress, startDeg, endDeg);
        float tailStart = Mth.lerp(Mth.clamp((k - 0.25F) * 1.15F, 0.0F, 0.65F), startDeg, endDeg);

        float tr = phase2 ? 0.85F : 0.78F;
        float tg = 0.98F;
        float tb = phase2 ? 0.92F : 1.0F;
        float gr = phase2 ? 1.0F : 0.65F * fade;
        float gg = phase2 ? 0.92F * fade : 0.92F * fade;
        float gb = phase2 ? 0.48F * fade : 1.0F * fade;

        PoseStack.Pose pose = ps.last();
        VertexConsumer vcBase = buffers.getBuffer(RenderType.entityTranslucent(SLASH));
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;
            float a0 = Mth.lerp(t0, tailStart, currentEnd) * Mth.DEG_TO_RAD;
            float a1 = Mth.lerp(t1, tailStart, currentEnd) * Mth.DEG_TO_RAD;
            double widthScale0 = 0.35D + 0.65D * Math.sin(Math.PI * Math.pow(t0, 0.75D));
            double widthScale1 = 0.35D + 0.65D * Math.sin(Math.PI * Math.pow(t1, 0.75D));
            double rIn0 = outerR - (outerR - innerR) * widthScale0;
            double rIn1 = outerR - (outerR - innerR) * widthScale1;

            Vec3 in0 = new Vec3(Math.sin(a0) * rIn0, 0.0D, Math.cos(a0) * rIn0);
            Vec3 out0 = new Vec3(Math.sin(a0) * outerR, 0.0D, Math.cos(a0) * outerR);
            Vec3 out1 = new Vec3(Math.sin(a1) * outerR, 0.0D, Math.cos(a1) * outerR);
            Vec3 in1 = new Vec3(Math.sin(a1) * rIn1, 0.0D, Math.cos(a1) * rIn1);

            quadTwoSided(pose, vcBase, in0, in1, out1, out0, t0, 0.0F, t1, 1.0F, tr, tg, tb, 0.92F * fade);
        }

        VertexConsumer vcGlow = buffers.getBuffer(additive(SLASH_GLOW));
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;
            float a0 = Mth.lerp(t0, tailStart, currentEnd) * Mth.DEG_TO_RAD;
            float a1 = Mth.lerp(t1, tailStart, currentEnd) * Mth.DEG_TO_RAD;
            double widthScale0 = 0.35D + 0.65D * Math.sin(Math.PI * Math.pow(t0, 0.75D));
            double widthScale1 = 0.35D + 0.65D * Math.sin(Math.PI * Math.pow(t1, 0.75D));
            double rIn0 = outerR - (outerR - innerR) * widthScale0;
            double rIn1 = outerR - (outerR - innerR) * widthScale1;

            Vec3 in0 = new Vec3(Math.sin(a0) * rIn0, 0.01D, Math.cos(a0) * rIn0);
            Vec3 out0 = new Vec3(Math.sin(a0) * outerR, 0.01D, Math.cos(a0) * outerR);
            Vec3 out1 = new Vec3(Math.sin(a1) * outerR, 0.01D, Math.cos(a1) * outerR);
            Vec3 in1 = new Vec3(Math.sin(a1) * rIn1, 0.01D, Math.cos(a1) * rIn1);

            quadTwoSided(pose, vcGlow, in0, in1, out1, out0, t0, 0.0F, t1, 1.0F, gr * fade, gg, gb, 1.0F);
        }

        ps.popPose();
    }

    private static void renderTempestSlam(ZephyrarchEntity boss, float age, boolean phase2, PoseStack ps, MultiBufferSource buffers) {
        double groundOffset = findGroundOffset(boss);
        float maxRadius = phase2 ? 6.8F : 5.4F;

        if (age >= 4.0F && age < 20.0F) {
            float k = (age - 4.0F) / 16.0F;
            float teleRadius = maxRadius * (1.05F - 0.35F * k);
            float alpha = 0.25F + 0.55F * k;
            drawFlatRing(ps, buffers, groundOffset + 0.05D, teleRadius, -age * 12.0F,
                    0.75F, 0.96F, 1.0F, alpha,
                    (phase2 ? 0.85F : 0.45F) * k, 0.80F * k, (phase2 ? 0.45F : 0.95F) * k);

            if (age >= 14.0F) {
                float diveK = (age - 14.0F) / 6.0F;
                drawFunnelWall(ps, buffers, 0.3D, 2.8D, 0.65F, 1.75F, age * 0.25F,
                        0.80F, 0.98F, 1.0F, 0.65F * diveK, 0.55F * diveK, 0.80F * diveK, 0.90F * diveK);
            }
        } else if (age >= 20.0F && age <= 34.0F) {
            float k = (age - 20.0F) / 14.0F;
            float ease = 1.0F - (float) Math.pow(1.0F - k, 3.0D);
            float fade = 1.0F - k;
            float waveR = 1.0F + (maxRadius + 0.8F) * ease;

            drawFlatRing(ps, buffers, groundOffset + 0.07D, waveR, age * 14.0F,
                    0.88F, 0.98F, 1.0F, 0.90F * fade,
                    (phase2 ? 1.0F : 0.72F) * fade, 0.92F * fade, (phase2 ? 0.52F : 1.0F) * fade);

            drawFunnelWall(ps, buffers, groundOffset + 0.05D, groundOffset + 1.35D * fade + 0.25D,
                    waveR * 0.86F, waveR * 1.02F, -age * 0.12F,
                    0.82F, 0.98F, 1.0F, 0.78F * fade,
                    (phase2 ? 0.95F : 0.60F) * fade, 0.86F * fade, (phase2 ? 0.45F : 0.96F) * fade);

            if (k < 0.55F) {
                float pillarFade = 1.0F - k / 0.55F;
                drawFunnelWall(ps, buffers, groundOffset + 0.05D, groundOffset + 4.2D * (0.4F + 0.6F * ease),
                        0.75F + 0.6F * ease, 1.65F + 0.9F * ease, age * 0.22F,
                        0.90F, 0.99F, 1.0F, 0.72F * pillarFade,
                        0.85F * pillarFade, 0.95F * pillarFade, 1.0F * pillarFade);
            }
        }
    }

    private static void renderGaleBarrage(float age, float bodyYaw, boolean phase2, PoseStack ps, MultiBufferSource buffers) {
        float[] shots = {14.0F, 24.0F, 34.0F};
        for (int idx = 0; idx < shots.length; idx++) {
            float shotAt = shots[idx];
            if (age >= shotAt - 6.0F && age <= shotAt + 5.0F) {
                boolean isFinal = idx == 2;
                float scale;
                float alpha;
                if (age <= shotAt) {
                    float chargeK = (age - (shotAt - 6.0F)) / 6.0F;
                    scale = (isFinal && phase2 ? 1.45F : 1.05F) * (1.35F - 0.55F * chargeK);
                    alpha = 0.85F * chargeK;
                } else {
                    float burstK = (age - shotAt) / 5.0F;
                    scale = (isFinal && phase2 ? 1.15F : 0.85F) + 1.65F * burstK;
                    alpha = 0.90F * (1.0F - burstK);
                }

                ps.pushPose();
                ps.translate(0.0D, 2.05D, 0.0D);
                ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
                ps.translate(0.0D, 0.0D, 1.35D);
                ps.mulPose(Axis.ZP.rotationDegrees(age * (idx % 2 == 0 ? 22.0F : -22.0F)));

                PoseStack.Pose pose = ps.last();
                Vec3 a = new Vec3(-scale, scale, 0.0D);
                Vec3 b = new Vec3(scale, scale, 0.0D);
                Vec3 c = new Vec3(scale, -scale, 0.0D);
                Vec3 d = new Vec3(-scale, -scale, 0.0D);

                quadTwoSided(pose, buffers.getBuffer(RenderType.entityTranslucent(RING)), a, b, c, d,
                        0.0F, 0.0F, 1.0F, 1.0F, 0.85F, 0.98F, 1.0F, alpha);
                float gr = (isFinal && phase2 ? 1.0F : 0.65F) * alpha;
                float gg = 0.92F * alpha;
                float gb = (isFinal && phase2 ? 0.45F : 1.0F) * alpha;
                quadTwoSided(pose, buffers.getBuffer(additive(RING_GLOW)), a, b, c, d,
                        0.0F, 0.0F, 1.0F, 1.0F, gr, gg, gb, 1.0F);
                ps.popPose();
            }
        }
    }

    private static void renderCycloneVortex(float age, float time, boolean phase2, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        if (age < 49.0F) {
            float ramp = Mth.clamp(age / 8.0F, 0.0F, 1.0F);
            float compress = age > 43.0F ? Mth.clamp(1.0F - (age - 43.0F) / 6.0F * 0.65F, 0.35F, 1.0F) : 1.0F;
            float rScale = ramp * compress;

            drawFlatRing(ps, buffers, 0.08D, (phase2 ? 7.2F : 6.2F) * ramp, time * 16.0F,
                    0.75F, 0.96F, 1.0F, 0.55F * ramp,
                    0.45F * ramp, 0.72F * ramp, 0.85F * ramp);

            drawFunnelWall(ps, buffers, 0.1D, 1.6D, 1.35F * rScale, 2.65F * rScale, -time * 0.16F,
                    0.80F, 0.97F, 1.0F, 0.68F * ramp,
                    (phase2 ? 0.75F : 0.45F) * ramp, 0.75F * ramp, 0.88F * ramp);

            drawFunnelWall(ps, buffers, 1.3D, 3.1D, 2.25F * rScale, 3.75F * rScale, time * 0.13F,
                    0.78F, 0.96F, 1.0F, 0.58F * ramp,
                    (phase2 ? 0.85F : 0.42F) * ramp, 0.72F * ramp, (phase2 ? 0.48F : 0.85F) * ramp);

            drawFunnelWall(ps, buffers, 2.7D, 4.4D, 3.35F * rScale, 4.75F * rScale, -time * 0.10F,
                    0.72F, 0.94F, 1.0F, 0.45F * ramp,
                    0.35F * ramp, 0.62F * ramp, 0.75F * ramp);

            if (age > 34.0F) {
                float coreCharge = (age - 34.0F) / 15.0F;
                drawBillboardFlare(ps, buffers, cameraRot, new Vec3(0.0D, 1.85D, 0.0D),
                        0.6F + 1.35F * coreCharge, time * 8.0F,
                        coreCharge, 0.94F * coreCharge, (phase2 ? 0.52F : 0.98F) * coreCharge);
            }
        } else {
            float burstK = Mth.clamp((age - 49.0F) / 11.0F, 0.0F, 1.0F);
            float ease = 1.0F - (float) Math.pow(1.0F - burstK, 3.0D);
            float fade = 1.0F - burstK;
            float maxR = phase2 ? 7.6F : 6.4F;
            float curR = 1.2F + maxR * ease;

            drawFlatRing(ps, buffers, 1.45D, curR, -time * 18.0F,
                    0.92F, 0.99F, 1.0F, 0.92F * fade,
                    (phase2 ? 1.0F : 0.75F) * fade, 0.95F * fade, (phase2 ? 0.55F : 1.0F) * fade);

            drawFunnelWall(ps, buffers, 0.4D, 2.8D, curR * 0.88F, curR * 1.04F, time * 0.15F,
                    0.88F, 0.99F, 1.0F, 0.82F * fade,
                    (phase2 ? 1.0F : 0.68F) * fade, 0.90F * fade, (phase2 ? 0.50F : 1.0F) * fade);

            if (burstK < 0.45F) {
                float flash = 1.0F - burstK / 0.45F;
                drawBillboardFlare(ps, buffers, cameraRot, new Vec3(0.0D, 1.85D, 0.0D),
                        2.8F * flash + 0.8F, time * 5.0F, flash, 0.96F * flash, 0.72F * flash);
            }
        }
    }

    private static void renderPhaseShiftRebirth(float age, float time, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        if (age < 24.0F) {
            float k = Mth.clamp(age / 24.0F, 0.0F, 1.0F);
            float shrinkR = 4.5F * (1.0F - 0.72F * k);

            drawFlatRing(ps, buffers, 0.08D, shrinkR, -time * 15.0F,
                    1.0F, 0.94F, 0.62F, 0.35F + 0.55F * k,
                    0.85F * k, 0.76F * k, 0.38F * k);

            drawTiltedOrbitRibbon(ps, buffers, 1.85D, shrinkR * 0.75F, 0.45F, 35.0F, 12.0F, time * 0.14F,
                    0.85F, 0.98F, 1.0F, 0.75F * k, 0.85F * k, 0.90F * k, 0.55F * k);
            drawTiltedOrbitRibbon(ps, buffers, 1.85D, shrinkR * 0.75F, 0.45F, -35.0F, -12.0F, -time * 0.14F,
                    1.0F, 0.92F, 0.55F, 0.75F * k, 0.95F * k, 0.82F * k, 0.38F * k);

            drawBillboardFlare(ps, buffers, cameraRot, new Vec3(0.0D, 1.90D, 0.0D),
                    0.5F + 1.9F * k * k, -time * 10.0F,
                    1.0F * k, 0.92F * k, 0.52F * k);
        } else {
            float k = Mth.clamp((age - 24.0F) / 16.0F, 0.0F, 1.0F);
            float ease = 1.0F - (float) Math.pow(1.0F - k, 3.0D);
            float fade = 1.0F - k;

            drawFunnelWall(ps, buffers, 0.0D, 9.2D * (0.45F + 0.55F * ease),
                    1.15F + 0.8F * ease, 2.25F + 1.4F * ease, -time * 0.22F,
                    0.95F, 0.98F, 0.86F, 0.88F * fade,
                    1.0F * fade, 0.92F * fade, 0.52F * fade);

            float waveR = 1.2F + 7.2F * ease;
            drawFlatRing(ps, buffers, 0.12D, waveR, time * 18.0F,
                    1.0F, 0.95F, 0.68F, 0.92F * fade,
                    1.0F * fade, 0.88F * fade, 0.45F * fade);
            drawFlatRing(ps, buffers, 1.85D, waveR * 0.85F, -time * 18.0F,
                    0.82F, 0.98F, 1.0F, 0.85F * fade,
                    0.75F * fade, 0.95F * fade, 1.0F * fade);

            drawBillboardFlare(ps, buffers, cameraRot, new Vec3(0.0D, 2.0D, 0.0D),
                    3.6F * fade + 0.6F, time * 6.0F,
                    fade, 0.94F * fade, 0.58F * fade);
        }
    }

    public static void renderChargeTrail(ZephyrChargeEntity charge, float partialTick, PoseStack ps, MultiBufferSource buffers, Vec3 cameraPos) {
        if (charge.clientTrail.size() < 2) {
            return;
        }
        Vec3 currentOrigin = charge.getPosition(partialTick);
        Vec3 head = currentOrigin.add(0.0D, 0.25D, 0.0D);

        Vec3[] pts = new Vec3[charge.clientTrail.size() + 1];
        pts[0] = Vec3.ZERO;
        int idx = 1;
        for (Vec3 worldPt : charge.clientTrail) {
            pts[idx++] = worldPt.subtract(head);
        }

        PoseStack.Pose pose = ps.last();
        ps.pushPose();
        ps.translate(0.0D, 0.25D, 0.0D);
        pose = ps.last();

        VertexConsumer vcBase = buffers.getBuffer(RenderType.entityTranslucent(RIBBON));
        int count = pts.length - 1;
        for (int i = 0; i < count; i++) {
            Vec3 p0 = pts[i];
            Vec3 p1 = pts[i + 1];
            Vec3 seg = p1.subtract(p0);
            if (seg.lengthSqr() < 1.0E-5D) {
                continue;
            }
            float t0 = i / (float) count;
            float t1 = (i + 1) / (float) count;
            float w0 = 0.28F * (1.0F - t0 * 0.85F);
            float w1 = 0.28F * (1.0F - t1 * 0.85F);
            float alpha = (1.0F - t0) * 0.78F;

            Vec3 toCam = cameraPos.subtract(head.add(p0));
            Vec3 side = seg.cross(toCam);
            if (side.lengthSqr() < 1.0E-6D) {
                side = new Vec3(0.0D, 1.0D, 0.0D);
            } else {
                side = side.normalize();
            }

            quadTwoSided(pose, vcBase,
                    p0.add(side.scale(w0)), p1.add(side.scale(w1)),
                    p1.subtract(side.scale(w1)), p0.subtract(side.scale(w0)),
                    t0, 0.0F, t1, 1.0F, 0.82F, 0.98F, 1.0F, alpha);
        }

        VertexConsumer vcGlow = buffers.getBuffer(additive(RIBBON_GLOW));
        for (int i = 0; i < count; i++) {
            Vec3 p0 = pts[i];
            Vec3 p1 = pts[i + 1];
            Vec3 seg = p1.subtract(p0);
            if (seg.lengthSqr() < 1.0E-5D) {
                continue;
            }
            float t0 = i / (float) count;
            float t1 = (i + 1) / (float) count;
            float w0 = 0.22F * (1.0F - t0 * 0.85F);
            float w1 = 0.22F * (1.0F - t1 * 0.85F);
            float g = (1.0F - t0) * 0.75F;

            Vec3 toCam = cameraPos.subtract(head.add(p0));
            Vec3 side = seg.cross(toCam);
            if (side.lengthSqr() < 1.0E-6D) {
                side = new Vec3(0.0D, 1.0D, 0.0D);
            } else {
                side = side.normalize();
            }

            quadTwoSided(pose, vcGlow,
                    p0.add(side.scale(w0)), p1.add(side.scale(w1)),
                    p1.subtract(side.scale(w1)), p0.subtract(side.scale(w0)),
                    t0, 0.0F, t1, 1.0F, 0.65F * g, 0.92F * g, g, 1.0F);
        }

        ps.popPose();
    }

    private static void drawFlatRing(PoseStack ps, MultiBufferSource buffers, double yOffset, float radius, float spinDeg,
                                     float tr, float tg, float tb, float alpha, float gr, float gg, float gb) {
        if (radius <= 0.02F || alpha <= 0.01F) {
            return;
        }
        ps.pushPose();
        ps.translate(0.0D, yOffset, 0.0D);
        ps.mulPose(Axis.YP.rotationDegrees(spinDeg));
        PoseStack.Pose pose = ps.last();

        Vec3 a = new Vec3(-radius, 0.0D, -radius);
        Vec3 b = new Vec3(radius, 0.0D, -radius);
        Vec3 c = new Vec3(radius, 0.0D, radius);
        Vec3 d = new Vec3(-radius, 0.0D, radius);

        quadTwoSided(pose, buffers.getBuffer(RenderType.entityTranslucent(RING)), a, b, c, d, 0.0F, 0.0F, 1.0F, 1.0F, tr, tg, tb, alpha);
        if (gr > 0.01F || gg > 0.01F || gb > 0.01F) {
            Vec3 ag = a.add(0.0D, 0.01D, 0.0D);
            Vec3 bg = b.add(0.0D, 0.01D, 0.0D);
            Vec3 cg = c.add(0.0D, 0.01D, 0.0D);
            Vec3 dg = d.add(0.0D, 0.01D, 0.0D);
            quadTwoSided(pose, buffers.getBuffer(additive(RING_GLOW)), ag, bg, cg, dg, 0.0F, 0.0F, 1.0F, 1.0F, gr, gg, gb, 1.0F);
        }
        ps.popPose();
    }

    private static void drawTiltedOrbitRibbon(PoseStack ps, MultiBufferSource buffers, double centerY, float radius, float halfHeight,
                                              float tiltXDeg, float tiltZDeg, float uOffset,
                                              float tr, float tg, float tb, float alpha, float gr, float gg, float gb) {
        ps.pushPose();
        ps.translate(0.0D, centerY, 0.0D);
        ps.mulPose(Axis.XP.rotationDegrees(tiltXDeg));
        ps.mulPose(Axis.ZP.rotationDegrees(tiltZDeg));
        drawFunnelWall(ps, buffers, -halfHeight, halfHeight, radius, radius, uOffset, tr, tg, tb, alpha, gr, gg, gb);
        ps.popPose();
    }

    private static void drawFunnelWall(PoseStack ps, MultiBufferSource buffers, double yBottom, double yTop,
                                       float rBottom, float rTop, float uScroll,
                                       float tr, float tg, float tb, float alpha, float gr, float gg, float gb) {
        if (alpha <= 0.01F) {
            return;
        }
        int segs = 20;
        PoseStack.Pose pose = ps.last();
        VertexConsumer vcBase = buffers.getBuffer(RenderType.entityTranslucent(RIBBON));
        for (int i = 0; i < segs; i++) {
            float f0 = i / (float) segs;
            float f1 = (i + 1) / (float) segs;
            double a0 = f0 * Math.PI * 2.0D;
            double a1 = f1 * Math.PI * 2.0D;
            float u0 = uScroll + f0 * 2.0F;
            float u1 = uScroll + f1 * 2.0F;

            Vec3 top0 = new Vec3(Math.cos(a0) * rTop, yTop, Math.sin(a0) * rTop);
            Vec3 top1 = new Vec3(Math.cos(a1) * rTop, yTop, Math.sin(a1) * rTop);
            Vec3 bot1 = new Vec3(Math.cos(a1) * rBottom, yBottom, Math.sin(a1) * rBottom);
            Vec3 bot0 = new Vec3(Math.cos(a0) * rBottom, yBottom, Math.sin(a0) * rBottom);

            quadTwoSided(pose, vcBase, top0, top1, bot1, bot0, u0, 0.0F, u1, 1.0F, tr, tg, tb, alpha);
        }

        if (gr > 0.01F || gg > 0.01F || gb > 0.01F) {
            VertexConsumer vcGlow = buffers.getBuffer(additive(RIBBON_GLOW));
            for (int i = 0; i < segs; i++) {
                float f0 = i / (float) segs;
                float f1 = (i + 1) / (float) segs;
                double a0 = f0 * Math.PI * 2.0D;
                double a1 = f1 * Math.PI * 2.0D;
                float u0 = uScroll + f0 * 2.0F;
                float u1 = uScroll + f1 * 2.0F;

                Vec3 top0 = new Vec3(Math.cos(a0) * (rTop + 0.01F), yTop, Math.sin(a0) * (rTop + 0.01F));
                Vec3 top1 = new Vec3(Math.cos(a1) * (rTop + 0.01F), yTop, Math.sin(a1) * (rTop + 0.01F));
                Vec3 bot1 = new Vec3(Math.cos(a1) * (rBottom + 0.01F), yBottom, Math.sin(a1) * rBottom);
                Vec3 bot0 = new Vec3(Math.cos(a0) * (rBottom + 0.01F), yBottom, Math.sin(a0) * (rBottom + 0.01F));

                quadTwoSided(pose, vcGlow, top0, top1, bot1, bot0, u0, 0.0F, u1, 1.0F, gr, gg, gb, 1.0F);
            }
        }
    }

    private static void drawBillboardFlare(PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot, Vec3 localPos,
                                           float size, float spinDeg, float r, float g, float b) {
        if (size <= 0.01F) {
            return;
        }
        ps.pushPose();
        ps.translate(localPos.x, localPos.y, localPos.z);
        ps.mulPose(cameraRot);
        ps.mulPose(Axis.ZP.rotationDegrees(spinDeg));
        PoseStack.Pose pose = ps.last();
        quadTwoSided(pose, buffers.getBuffer(additive(FLARE)),
                new Vec3(-size, size, 0.0D), new Vec3(size, size, 0.0D),
                new Vec3(size, -size, 0.0D), new Vec3(-size, -size, 0.0D),
                0.0F, 0.0F, 1.0F, 1.0F, r, g, b, 1.0F);
        ps.popPose();
    }

    private static double findGroundOffset(ZephyrarchEntity boss) {
        if (boss.onGround()) {
            return 0.0D;
        }
        Level level = boss.level();
        BlockPos base = boss.blockPosition();
        for (int dy = 0; dy <= 10; dy++) {
            BlockPos check = base.below(dy);
            BlockState state = level.getBlockState(check);
            if (!state.getCollisionShape(level, check).isEmpty()) {
                double topY = check.getY() + state.getCollisionShape(level, check).max(net.minecraft.core.Direction.Axis.Y);
                return topY - boss.getY();
            }
        }
        return 0.0D;
    }
}
