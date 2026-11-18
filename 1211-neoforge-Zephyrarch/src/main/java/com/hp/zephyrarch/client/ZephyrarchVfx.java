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
    public static final ResourceLocation SLASH = tex("gale_slash");
    public static final ResourceLocation SLASH_GLOW = tex("gale_slash_glow");
    public static final ResourceLocation RING = tex("gale_ring");
    public static final ResourceLocation RING_GLOW = tex("gale_ring_glow");
    public static final ResourceLocation RIBBON = tex("gale_ribbon");
    public static final ResourceLocation RIBBON_GLOW = tex("gale_ribbon_glow");
    public static final ResourceLocation FLARE = tex("gale_flare");

    private ZephyrarchVfx() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Zephyrarch.MOD_ID, "textures/effect/" + name + ".png");
    }

    public static RenderType additive(ResourceLocation texture) {
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

    public static void quadTwoSided(PoseStack.Pose pose, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
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

    public static void renderBossEffects(ZephyrarchEntity boss, float partialTick, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot, Vec3 cameraPos) {
        if (boss.isDeadOrDying()) {
            return;
        }
        float time = boss.tickCount + partialTick;
        boolean phase2 = boss.isPhase2();
        float bodyYaw = Mth.rotLerp(partialTick, boss.yBodyRotO, boss.yBodyRot);

        renderAmbientAura(boss, time, phase2, ps, buffers, cameraRot);

        renderCage(boss, partialTick, time, ps, buffers);
        renderLance(boss, partialTick, ps, buffers, cameraPos);

        ZephyrarchAttack attack = boss.getAttack();
        if (attack == ZephyrarchAttack.NONE) {
            return;
        }
        float age = boss.getClientAttackAge(partialTick);
        switch (attack) {
            case GALE_COMBO -> renderComboSlashes(age, bodyYaw, phase2, ps, buffers);
            case ARM_SWEEP -> renderArmSweep(boss, age, time, ps, buffers);
            case GALE_RUSH -> renderRush(age, bodyYaw, ps, buffers, cameraRot);
            case SKY_HAMMER -> renderHammer(boss, age, ps, buffers);
            case VORTEX_STOMP -> renderStomp(boss, age, time, ps, buffers);
            case PHASE_SHIFT -> renderPhaseShiftRebirth(age, time, ps, buffers, cameraRot);
            case TEMPEST_LANCE -> renderLanceCharge(age, time, bodyYaw, ps, buffers, cameraRot);
            case STORM_EYE, DOWNDRAFT -> renderRaisedFlare(age, time, attack == ZephyrarchAttack.STORM_EYE ? 20.0F : 12.0F, ps, buffers, cameraRot);
            case CYCLONE_CAGE -> renderCageCast(age, time, ps, buffers, cameraRot);
            default -> {
            }
        }
    }

    private static void renderArmSweep(ZephyrarchEntity boss, float age, float time, PoseStack ps, MultiBufferSource buffers) {
        double ground = findGroundOffset(boss);
        if (age < 15.0F) {
            float k = age / 15.0F;
            drawFlatRing(ps, buffers, ground + 0.05D, 5.0F, -time * 6.0F, 0.75F, 0.96F, 1.0F, 0.2F + 0.5F * k,
                    0.55F * k, 0.85F * k, 1.0F * k);
        } else if (age < 24.0F) {
            float k = (age - 15.0F) / 9.0F;
            float fade = 1.0F - k;
            float r = 2.0F + 3.6F * (1.0F - (float) Math.pow(1.0F - k, 3.0D));
            drawFlatRing(ps, buffers, 1.6D, r, time * 30.0F, 0.88F, 0.99F, 1.0F, 0.9F * fade, 0.8F * fade, 0.95F * fade, 1.0F * fade);
            drawFunnelWall(ps, buffers, 0.6D, 2.6D, r * 0.92F, r, time * 0.3F, 0.85F, 0.98F, 1.0F, 0.6F * fade,
                    0.6F * fade, 0.9F * fade, 1.0F * fade);
        }
    }

    private static void renderRush(float age, float bodyYaw, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        if (age >= 16.0F && age <= 23.0F) {
            float k = (age - 16.0F) / 7.0F;
            ps.pushPose();
            ps.translate(0.0D, 1.5D, 0.0D);
            ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
            ps.mulPose(Axis.XP.rotationDegrees(90.0F));
            drawFunnelWall(ps, buffers, -2.5D, 1.2D, 0.4F, 1.4F, age * 0.4F, 0.85F, 0.98F, 1.0F, 0.7F * (1.0F - k * 0.5F),
                    0.6F, 0.9F, 1.0F);
            ps.popPose();
        }
        if (age >= 21.0F && age <= 31.0F) {
            drawSlashArc(ps, buffers, bodyYaw, 1.5D, 1.6D, 3.2D, -10.0F, true, (age - 21.0F) / 10.0F, false);
        }
    }

    private static void renderHammer(ZephyrarchEntity boss, float age, PoseStack ps, MultiBufferSource buffers) {
        if (age < 22.0F || age > 36.0F) {
            return;
        }
        double ground = findGroundOffset(boss);
        float k = (age - 22.0F) / 14.0F;
        float ease = 1.0F - (float) Math.pow(1.0F - k, 3.0D);
        float fade = 1.0F - k;
        float waveR = 1.0F + 4.2F * ease;
        drawFlatRing(ps, buffers, ground + 0.07D, waveR, age * 14.0F, 0.88F, 0.98F, 1.0F, 0.9F * fade,
                0.72F * fade, 0.92F * fade, 1.0F * fade);
        drawFunnelWall(ps, buffers, ground + 0.05D, ground + 1.35D * fade + 0.25D, waveR * 0.86F, waveR * 1.02F, -age * 0.12F,
                0.82F, 0.98F, 1.0F, 0.78F * fade, 0.6F * fade, 0.86F * fade, 0.96F * fade);
    }

    private static void renderStomp(ZephyrarchEntity boss, float age, float time, PoseStack ps, MultiBufferSource buffers) {
        double ground = findGroundOffset(boss);
        if (age >= 6.0F && age < 30.0F) {
            float k = Mth.clamp((age - 6.0F) / 22.0F, 0.0F, 1.0F);
            float ramp = Mth.clamp((age - 6.0F) / 4.0F, 0.0F, 1.0F);
            float r = 6.0F - 3.5F * k;
            drawFlatRing(ps, buffers, ground + 0.06D, r, -time * 20.0F, 0.75F, 0.96F, 1.0F, 0.6F * ramp, 0.4F * ramp, 0.7F * ramp, 0.9F * ramp);
            drawFunnelWall(ps, buffers, ground, ground + 1.8D, r, r * 1.15F, -time * 0.2F, 0.8F, 0.97F, 1.0F, 0.5F * ramp,
                    0.4F * ramp, 0.75F * ramp, 0.9F * ramp);
        } else if (age >= 30.0F && age <= 42.0F) {
            float k = (age - 30.0F) / 12.0F;
            float fade = 1.0F - k;
            float r = 1.0F + 3.4F * (1.0F - (float) Math.pow(1.0F - k, 3.0D));
            drawFlatRing(ps, buffers, ground + 0.08D, r, time * 18.0F, 0.92F, 0.99F, 1.0F, 0.92F * fade, 0.75F * fade, 0.95F * fade, fade);
            drawFunnelWall(ps, buffers, ground, ground + 2.8D * fade + 0.3D, r * 0.85F, r, time * 0.15F, 0.88F, 0.99F, 1.0F,
                    0.8F * fade, 0.7F * fade, 0.9F * fade, fade);
        }
    }

    private static void renderLanceCharge(float age, float time, float bodyYaw, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        if (age < 36.0F) {
            float k = Mth.clamp(age / 30.0F, 0.0F, 1.0F);
            Vec3 core = Vec3.directionFromRotation(0.0F, bodyYaw).scale(0.6D).add(0.0D, 2.0D, 0.0D);
            drawBillboardFlare(ps, buffers, cameraRot, core, 0.3F + 0.9F * k, time * 9.0F, k, 0.95F * k, 0.75F * k);
        }
    }

    private static void renderRaisedFlare(float age, float time, float peak, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        float k = age < peak ? age / peak : Mth.clamp(1.0F - (age - peak) / 10.0F, 0.0F, 1.0F);
        drawBillboardFlare(ps, buffers, cameraRot, new Vec3(0.0D, 4.4D, 0.0D), 0.4F + 0.9F * k, time * 6.0F,
                0.7F * k, 0.95F * k, k);
    }

    private static void renderCageCast(float age, float time, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        if (age < 22.0F) {
            float k = age / 20.0F;
            drawFunnelWall(ps, buffers, 0.2D, 3.2D, 1.4F, 2.2F, -time * 0.3F, 0.82F, 0.98F, 1.0F, 0.6F * k, 0.5F * k, 0.8F * k, 0.9F * k);
        }
    }

    private static void renderCage(ZephyrarchEntity boss, float partialTick, float time, PoseStack ps, MultiBufferSource buffers) {
        int ticks = boss.getCageTicks();
        if (ticks <= 0) {
            return;
        }
        Vec3 rel = boss.getCageCenter().subtract(boss.getPosition(partialTick));
        float fadeIn = Mth.clamp((ZephyrarchEntity.CAGE_DURATION - ticks) / 8.0F, 0.0F, 1.0F);
        float fade = Math.min(fadeIn, Mth.clamp(ticks / 15.0F, 0.0F, 1.0F));
        float r = (float) ZephyrarchEntity.CAGE_RADIUS;
        ps.pushPose();
        ps.translate(rel.x, rel.y, rel.z);
        drawFunnelWall(ps, buffers, 0.0D, ZephyrarchEntity.CAGE_HEIGHT, r, r, -time * 0.18F, 0.8F, 0.97F, 1.0F, 0.55F * fade,
                0.35F * fade, 0.7F * fade, 0.9F * fade);
        drawFunnelWall(ps, buffers, 0.0D, ZephyrarchEntity.CAGE_HEIGHT * 0.7D, r - 0.15F, r - 0.15F, time * 0.24F, 0.75F, 0.95F, 1.0F,
                0.4F * fade, 0.25F * fade, 0.55F * fade, 0.75F * fade);
        drawFlatRing(ps, buffers, 0.06D, r, time * 4.0F, 0.75F, 0.96F, 1.0F, 0.5F * fade, 0.4F * fade, 0.7F * fade, 0.9F * fade);
        ps.popPose();
    }

    private static void renderLance(ZephyrarchEntity boss, float partialTick, PoseStack ps, MultiBufferSource buffers, Vec3 cameraPos) {
        int state = boss.getLanceState();
        if (state == 0) {
            return;
        }
        Vec3 vec = boss.getLanceVector();
        if (vec.lengthSqr() < 0.01D) {
            return;
        }
        Vec3 origin = boss.getLanceOrigin().subtract(boss.position());
        float age = boss.getClientAttackAge(partialTick);
        float width;
        float r;
        float g;
        float b;
        float a;
        if (state == 1) {
            width = 0.05F;
            r = 0.7F; g = 0.95F; b = 1.0F; a = 0.55F;
        } else if (state == 2) {
            width = 0.12F + 0.03F * Mth.sin(age * 2.0F);
            r = 1.0F; g = 0.55F; b = 0.45F; a = 0.9F;
        } else {
            float k = Mth.clamp((age - 36.0F) / 8.0F, 0.0F, 1.0F);
            width = 0.75F * (1.0F - k) + 0.1F;
            r = 0.9F; g = 0.98F; b = 1.0F; a = 1.0F - k;
        }
        Vec3 worldOrigin = boss.getPosition(partialTick).add(origin);
        Vec3 toCam = cameraPos.subtract(worldOrigin.add(vec.scale(0.5D)));
        Vec3 side = vec.cross(toCam);
        side = side.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : side.normalize();
        PoseStack.Pose pose = ps.last();
        Vec3 p0 = origin;
        Vec3 p1 = origin.add(vec);
        quadTwoSided(pose, buffers.getBuffer(RenderType.entityTranslucent(RIBBON)),
                p0.add(side.scale(width)), p1.add(side.scale(width)), p1.subtract(side.scale(width)), p0.subtract(side.scale(width)),
                0.0F, 0.0F, (float) vec.length() * 0.25F, 1.0F, r, g, b, a);
        quadTwoSided(pose, buffers.getBuffer(additive(RIBBON_GLOW)),
                p0.add(side.scale(width * 0.6F)), p1.add(side.scale(width * 0.6F)), p1.subtract(side.scale(width * 0.6F)), p0.subtract(side.scale(width * 0.6F)),
                0.0F, 0.0F, (float) vec.length() * 0.25F, 1.0F, r * a, g * a, b * a, 1.0F);
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

    public static void drawFlatRing(PoseStack ps, MultiBufferSource buffers, double yOffset, float radius, float spinDeg,
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

    public static void drawFunnelWall(PoseStack ps, MultiBufferSource buffers, double yBottom, double yTop,
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

    public static void drawBillboardFlare(PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot, Vec3 localPos,
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
