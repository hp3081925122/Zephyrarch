package com.hp.zephyrarch.client;

import com.hp.zephyrarch.Zephyrarch;
import com.hp.zephyrarch.entity.ZephyrBladeEntity;
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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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
        renderLance(boss, partialTick, time, ps, buffers, cameraRot, cameraPos);

        ZephyrarchAttack attack = boss.getAttack();
        if (attack == ZephyrarchAttack.NONE) {
            return;
        }
        float age = boss.getClientAttackAge(partialTick);
        switch (attack) {
            case GALE_COMBO -> renderComboSlashes(age, time, bodyYaw, phase2, ps, buffers, cameraRot);
            case ARM_SWEEP -> renderArmSweep(boss, age, time, bodyYaw, ps, buffers);
            case GALE_RUSH -> renderRush(boss, age, time, bodyYaw, ps, buffers, cameraRot);
            case SKY_HAMMER -> renderHammer(boss, partialTick, age, time, ps, buffers, cameraRot);
            case GALE_GRAB -> renderGrab(boss, partialTick, age, time, bodyYaw, ps, buffers, cameraRot);
            case GRAB_WHIFF -> renderGrabWhiff(age, bodyYaw, ps, buffers);
            case VORTEX_STOMP -> renderStomp(boss, age, time, ps, buffers, cameraRot);
            case PHASE_SHIFT -> renderPhaseShiftRebirth(age, time, ps, buffers, cameraRot);
            case GALE_BOLTS -> renderGaleBolts(age, bodyYaw, ps, buffers, cameraRot);
            case TEMPEST_LANCE -> renderLanceCharge(boss, age, time, bodyYaw, ps, buffers, cameraRot);
            case STORM_EYE -> renderStormEyeCast(age, time, ps, buffers, cameraRot);
            case DOWNDRAFT -> renderDowndraftCast(boss, age, time, ps, buffers, cameraRot);
            case BOOMERANG_BLADES -> renderBoomerangCast(age, time, bodyYaw, ps, buffers, cameraRot);
            case CYCLONE_CAGE -> renderCageCast(age, time, bodyYaw, ps, buffers, cameraRot);
            case STAGGER -> renderStagger(age, time, ps, buffers, cameraRot);
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

    private static void renderComboSlashes(float age, float time, float bodyYaw, boolean phase2, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, bodyYaw);
        Vec3 rgt = new Vec3(-fwd.z, 0.0D, fwd.x);

        if (age >= 2.0F && age < 10.0F) {
            float k = (age - 2.0F) / 8.0F;
            Vec3 fist = fwd.scale(-0.35D).add(rgt.scale(1.35D)).add(0.0D, 2.05D, 0.0D);
            drawBillboardFlare(ps, buffers, cameraRot, fist, 0.35F + 0.55F * k, time * 12.0F, 0.65F * k, 0.92F * k, k);
        }
        if (age >= 8.5F && age <= 16.5F) {
            float k = (age - 8.5F) / 8.0F;
            drawSlashArc(ps, buffers, bodyYaw, 1.48D, 2.0D, 3.75D, -22.0F, true, k, phase2);
            if (age >= 10.5F && age <= 15.5F) {
                float rk = (age - 10.5F) / 5.0F;
                drawDirectedRing(ps, buffers, bodyYaw, 1.55D, 1.4D + 1.8D * rk, 0.75F + 1.15F * rk, age * 24.0F, 1.0F - rk, false);
            }
        }
        if (age >= 13.5F && age < 21.5F) {
            float k = (age - 13.5F) / 8.0F;
            Vec3 fist = fwd.scale(-0.35D).subtract(rgt.scale(1.35D)).add(0.0D, 1.95D, 0.0D);
            drawBillboardFlare(ps, buffers, cameraRot, fist, 0.40F + 0.70F * k, -time * 14.0F, 0.75F * k, 0.95F * k, k);
        }
        if (age >= 20.0F && age <= 30.5F) {
            float k = (age - 20.0F) / 10.5F;
            drawSlashArc(ps, buffers, bodyYaw, 1.38D, 2.2D, 4.35D, 10.0F, false, k, phase2);
            if (age >= 22.5F && age <= 28.5F) {
                float rk = (age - 22.5F) / 6.0F;
                drawDirectedRing(ps, buffers, bodyYaw, 1.48D, 1.5D + 2.2D * rk, 0.95F + 1.45F * rk, -age * 26.0F, 1.0F - rk, false);
            }
        }
    }

    private static void renderArmSweep(ZephyrarchEntity boss, float age, float time, float bodyYaw, PoseStack ps, MultiBufferSource buffers) {
        double ground = findGroundOffset(boss);
        if (age < 15.0F) {
            float k = age / 15.0F;
            drawFlatRing(ps, buffers, ground + 0.05D, 5.0F, -time * 6.0F,
                    0.75F, 0.96F, 1.0F, 0.25F + 0.55F * k,
                    0.55F * k, 0.88F * k, 1.0F * k);
            drawFlatRing(ps, buffers, ground + 0.07D, 5.0F * (1.05F - 0.55F * k), time * 16.0F,
                    0.95F, 0.98F, 0.85F, 0.45F * k,
                    0.75F * k, 0.92F * k, 0.65F * k);
            drawTiltedOrbitRibbon(ps, buffers, 1.75D, 1.5F + 0.6F * k, 0.35F, 8.0F, -8.0F, -time * 0.18F,
                    0.80F, 0.97F, 1.0F, 0.55F * k, 0.50F * k, 0.85F * k, 0.98F * k);
        } else if (age < 26.0F) {
            float k = (age - 15.0F) / 11.0F;
            float fade = 1.0F - k;
            float ease = 1.0F - (float) Math.pow(1.0F - k, 3.0D);
            float r = 2.0F + 3.3F * ease;

            drawSlashArcRange(ps, buffers, bodyYaw + k * 260.0F, 1.65D, 1.9D, r, 4.0F, -195.0F, 15.0F, fade, false);
            drawSlashArcRange(ps, buffers, bodyYaw + 180.0F + k * 260.0F, 1.55D, 1.9D, r, -4.0F, -195.0F, 15.0F, fade * 0.92F, false);

            drawFlatRing(ps, buffers, 1.62D, r, time * 32.0F,
                    0.90F, 0.99F, 1.0F, 0.88F * fade,
                    0.82F * fade, 0.96F * fade, 1.0F * fade);
            drawFlatRing(ps, buffers, ground + 0.06D, r * 1.02F, -time * 20.0F,
                    0.82F, 0.97F, 1.0F, 0.70F * fade,
                    0.60F * fade, 0.90F * fade, 1.0F * fade);
            drawFunnelWall(ps, buffers, 0.45D, 2.75D, r * 0.88F, r * 1.02F, time * 0.32F,
                    0.85F, 0.98F, 1.0F, 0.62F * fade,
                    0.62F * fade, 0.92F * fade, 1.0F * fade);
        }
    }

    private static void renderRush(ZephyrarchEntity boss, float age, float time, float bodyYaw, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        Vec3 skillDir = boss.getSkillVec();
        Vec3 flatDir = new Vec3(skillDir.x, 0.0D, skillDir.z);
        float rushYaw = flatDir.lengthSqr() > 1.0E-4D
                ? (float) (Mth.atan2(flatDir.z, flatDir.x) * (180.0D / Math.PI)) - 90.0F
                : bodyYaw;

        if (age < 16.0F) {
            double ground = findGroundOffset(boss);
            float k = Mth.clamp(age / 15.0F, 0.0F, 1.0F);
            boolean locked = age >= 12.0F;
            float pulse = locked ? (0.82F + 0.18F * Mth.sin(age * 3.2F)) : 1.0F;
            float length = 10.0F * Mth.clamp(age / 5.0F, 0.2F, 1.0F);
            float halfW = 0.82F;

            ps.pushPose();
            ps.translate(0.0D, ground + 0.05D, 0.0D);
            ps.mulPose(Axis.YP.rotationDegrees(-rushYaw));
            PoseStack.Pose pose = ps.last();

            float uOff = -time * 0.22F;
            float uEnd = uOff + length * 0.32F;
            Vec3 a = new Vec3(-halfW, 0.0D, 0.4D);
            Vec3 b = new Vec3(halfW, 0.0D, 0.4D);
            Vec3 c = new Vec3(halfW, 0.0D, length);
            Vec3 d = new Vec3(-halfW, 0.0D, length);

            float tr = locked ? 1.0F : 0.78F;
            float tg = locked ? 0.95F : 0.97F;
            float tb = locked ? 0.68F : 1.0F;
            float alpha = (0.32F + 0.50F * k) * pulse;
            quadTwoSided(pose, buffers.getBuffer(RenderType.entityTranslucent(RIBBON)),
                    a, b, c, d, 0.0F, uOff, 1.0F, uEnd, tr, tg, tb, alpha);
            quadTwoSided(pose, buffers.getBuffer(additive(RIBBON_GLOW)),
                    a.add(0.0D, 0.01D, 0.0D), b.add(0.0D, 0.01D, 0.0D),
                    c.add(0.0D, 0.01D, 0.0D), d.add(0.0D, 0.01D, 0.0D),
                    0.0F, uOff, 1.0F, uEnd,
                    (locked ? 1.0F : 0.55F) * k * pulse, 0.88F * k * pulse, (locked ? 0.45F : 1.0F) * k, 1.0F);
            ps.popPose();

            Vec3 fwd = Vec3.directionFromRotation(0.0F, rushYaw);
            Vec3 rgt = new Vec3(-fwd.z, 0.0D, fwd.x);
            Vec3 chargeFist = fwd.scale(-0.55D).add(rgt.scale(1.35D)).add(0.0D, 1.85D, 0.0D);
            drawBillboardFlare(ps, buffers, cameraRot, chargeFist, 0.45F + 0.85F * k, time * 14.0F,
                    locked ? 1.0F : 0.70F * k, 0.94F * k, locked ? 0.55F * k : k);
            drawDirectedRing(ps, buffers, rushYaw, 1.85D, 0.8D, 1.45F * (1.15F - 0.55F * k), -time * 18.0F, 0.75F * k, locked);
        }

        if (age >= 16.0F && age <= 23.0F) {
            float k = (age - 16.0F) / 7.0F;
            ps.pushPose();
            ps.translate(0.0D, 1.55D, 0.0D);
            ps.mulPose(Axis.YP.rotationDegrees(-rushYaw));
            ps.mulPose(Axis.XP.rotationDegrees(90.0F));
            drawFunnelWall(ps, buffers, -3.2D, 1.4D, 1.65F, 0.35F, -age * 0.5F,
                    0.86F, 0.98F, 1.0F, 0.78F * (1.0F - k * 0.35F),
                    0.68F, 0.94F, 1.0F);
            drawFunnelWall(ps, buffers, -2.2D, 1.5D, 0.95F, 0.20F, age * 0.65F,
                    0.95F, 0.99F, 1.0F, 0.85F * (1.0F - k * 0.35F),
                    0.85F, 0.98F, 1.0F);
            ps.popPose();

            drawDirectedRing(ps, buffers, rushYaw, 1.55D, -0.8D - 2.4D * k, 1.1F + 1.2F * k, age * 28.0F, 0.85F * (1.0F - k), false);
        }

        if (age >= 21.5F && age <= 32.0F) {
            float k = (age - 21.5F) / 10.5F;
            drawSlashArc(ps, buffers, rushYaw, 1.52D, 1.6D, 3.45D, -8.0F, true, k, false);
            if (age >= 22.5F && age <= 28.5F) {
                float rk = (age - 22.5F) / 6.0F;
                drawDirectedRing(ps, buffers, rushYaw, 1.55D, 1.3D + 2.0D * rk, 0.9F + 1.55F * rk, age * 25.0F, 1.0F - rk, false);
            }
        }
    }

    private static void renderHammer(ZephyrarchEntity boss, float partialTick, float age, float time, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        double ground = findGroundOffset(boss);
        if (age < 14.0F) {
            float k = age / 14.0F;
            if (age < 8.0F) {
                float bk = age / 8.0F;
                drawFlatRing(ps, buffers, ground + 0.06D, 1.2F + 2.4F * bk, age * 18.0F,
                        0.85F, 0.98F, 1.0F, 0.78F * (1.0F - bk),
                        0.65F * (1.0F - bk), 0.92F * (1.0F - bk), 1.0F * (1.0F - bk));
            }
            drawFunnelWall(ps, buffers, 0.2D, 2.8D, 0.75F, 1.45F, -time * 0.3F,
                    0.82F, 0.98F, 1.0F, 0.55F * k, 0.50F * k, 0.85F * k, 0.98F * k);
            drawBillboardFlare(ps, buffers, cameraRot, new Vec3(0.0D, 3.75D, 0.0D), 0.45F + 0.95F * k, time * 12.0F,
                    0.85F * k, 0.96F * k, k);
            return;
        }

        if (age < 22.0F) {
            float k = (age - 14.0F) / 8.0F;
            Vec3 targetWorld = boss.getSkillVec();
            Vec3 bossPos = boss.getPosition(partialTick);
            Vec3 rel = targetWorld.lengthSqr() > 1.0E-3D
                    ? new Vec3(targetWorld.x - bossPos.x, ground, targetWorld.z - bossPos.z)
                    : new Vec3(0.0D, ground, 0.0D);

            ps.pushPose();
            ps.translate(rel.x, rel.y, rel.z);
            drawFlatRing(ps, buffers, 0.05D, 4.0F, -time * 12.0F,
                    1.0F, 0.82F, 0.58F, 0.45F + 0.45F * k,
                    0.95F * k, 0.65F * k, 0.35F * k);
            drawFlatRing(ps, buffers, 0.07D, 1.55F, time * 24.0F,
                    1.0F, 0.95F, 0.72F, 0.55F + 0.40F * k,
                    1.0F * k, 0.85F * k, 0.45F * k);
            drawFlatRing(ps, buffers, 0.06D, 4.0F * (1.05F - 0.62F * k), -time * 20.0F,
                    0.85F, 0.98F, 1.0F, 0.65F * k,
                    0.70F * k, 0.92F * k, 1.0F * k);
            ps.popPose();

            if (age >= 18.0F) {
                float diveK = (age - 18.0F) / 4.0F;
                drawFunnelWall(ps, buffers, 0.2D, 3.4D, 0.55F, 1.95F, age * 0.4F,
                        0.88F, 0.98F, 1.0F, 0.80F * diveK, 0.72F * diveK, 0.94F * diveK, diveK);
            }
            drawBillboardFlare(ps, buffers, cameraRot, new Vec3(0.0D, 3.55D, 0.0D), 1.15F + 0.45F * k, -time * 16.0F,
                    1.0F, 0.92F, 0.65F);
            return;
        }

        if (age <= 38.0F) {
            float k = (age - 22.0F) / 16.0F;
            float ease = 1.0F - (float) Math.pow(1.0F - k, 3.0D);
            float fade = 1.0F - k;
            float waveR = 1.0F + 4.35F * ease;

            drawFlatRing(ps, buffers, ground + 0.07D, waveR, age * 16.0F,
                    0.90F, 0.98F, 1.0F, 0.92F * fade,
                    0.80F * fade, 0.94F * fade, 1.0F * fade);
            drawFlatRing(ps, buffers, ground + 0.09D, waveR * 0.62F, -age * 22.0F,
                    1.0F, 0.94F, 0.68F, 0.85F * fade,
                    0.95F * fade, 0.85F * fade, 0.48F * fade);
            drawFunnelWall(ps, buffers, ground + 0.04D, ground + 1.55D * fade + 0.25D, waveR * 0.86F, waveR * 1.03F, -age * 0.16F,
                    0.84F, 0.98F, 1.0F, 0.80F * fade,
                    0.65F * fade, 0.88F * fade, 0.98F * fade);

            if (k < 0.60F) {
                float pFade = 1.0F - k / 0.60F;
                drawFunnelWall(ps, buffers, ground + 0.04D, ground + 4.8D * (0.35F + 0.65F * ease),
                        0.75F + 0.65F * ease, 1.65F + 1.05F * ease, age * 0.26F,
                        0.92F, 0.99F, 1.0F, 0.78F * pFade,
                        0.88F * pFade, 0.96F * pFade, 1.0F * pFade);
                drawBillboardFlare(ps, buffers, cameraRot, new Vec3(0.0D, ground + 0.65D, 0.0D),
                        2.35F * pFade, age * 10.0F, pFade, 0.95F * pFade, 0.72F * pFade);
            }
        }
    }

    private static void renderGrab(ZephyrarchEntity boss, float partialTick, float age, float time, float bodyYaw, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, bodyYaw);
        Vec3 rgt = new Vec3(-fwd.z, 0.0D, fwd.x);

        if (age < 11.0F) {
            float k = age / 10.0F;
            Vec3 reachPos = fwd.scale(0.6D + 1.5D * k).add(rgt.scale(0.85D)).add(0.0D, 2.05D + 0.45D * k, 0.0D);
            drawBillboardFlare(ps, buffers, cameraRot, reachPos, 0.40F + 0.65F * k, time * 16.0F,
                    0.65F * k, 0.94F * k, k);
            if (age >= 7.0F) {
                float sk = (age - 7.0F) / 4.0F;
                drawSlashArc(ps, buffers, bodyYaw, 2.15D, 1.5D, 3.35D, -28.0F, true, sk * 0.55F, false);
            }
            return;
        }

        int grabbedId = boss.getGrabbedEntityId();
        if (age < 32.0F && grabbedId >= 0) {
            Entity held = boss.level().getEntity(grabbedId);
            Vec3 center;
            if (held != null) {
                center = held.getPosition(partialTick).add(0.0D, held.getBbHeight() * 0.5D, 0.0D).subtract(boss.getPosition(partialTick));
            } else {
                center = fwd.scale(1.6D).add(rgt.scale(0.9D)).add(0.0D, 3.0D, 0.0D);
            }
            float squeezePulse = 0.75F + 0.25F * Mth.sin(age * 1.25F);
            ps.pushPose();
            ps.translate(center.x, center.y, center.z);
            drawTiltedOrbitRibbon(ps, buffers, 0.0D, 0.92F * squeezePulse, 0.28F, 34.0F, 18.0F, -time * 0.28F,
                    0.82F, 0.98F, 1.0F, 0.78F, 0.65F, 0.92F, 1.0F);
            drawTiltedOrbitRibbon(ps, buffers, 0.0D, 0.86F * squeezePulse, 0.25F, -32.0F, -22.0F, time * 0.32F,
                    1.0F, 0.94F, 0.65F, 0.68F, 0.85F, 0.82F, 0.45F);
            drawFlatRing(ps, buffers, -0.35D, 1.05F * squeezePulse, time * 26.0F,
                    0.85F, 0.98F, 1.0F, 0.65F, 0.55F, 0.88F, 1.0F);
            ps.popPose();
            drawBillboardFlare(ps, buffers, cameraRot, center, 0.65F * squeezePulse, -time * 14.0F, 0.75F, 0.95F, 1.0F);
            return;
        }

        if (age >= 31.0F && age <= 42.0F) {
            float k = (age - 31.0F) / 11.0F;
            ps.pushPose();
            ps.translate(0.0D, 2.45D, 0.0D);
            ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw + 180.0F));
            ps.mulPose(Axis.XP.rotationDegrees(-35.0F));
            drawSlashArc(ps, buffers, 0.0F, 0.0D, 1.4D, 3.6D, -15.0F, true, k, true);
            ps.popPose();

            drawDirectedRing(ps, buffers, bodyYaw + 180.0F, 2.55D, 0.8D + 2.2D * k, 0.95F + 1.55F * k, age * 24.0F, 1.0F - k, true);
        }
    }

    private static void renderGrabWhiff(float age, float bodyYaw, PoseStack ps, MultiBufferSource buffers) {
        if (age <= 8.0F) {
            float k = age / 8.0F;
            drawSlashArc(ps, buffers, bodyYaw, 2.0D, 1.6D, 3.1D, -24.0F, true, 0.35F + 0.65F * k, false);
        }
    }

    private static void renderStomp(ZephyrarchEntity boss, float age, float time, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        double ground = findGroundOffset(boss);
        if (age < 30.0F) {
            float ramp = Mth.clamp(age / 7.0F, 0.0F, 1.0F);
            float compress = age > 24.0F ? Mth.clamp(1.0F - (age - 24.0F) / 6.0F * 0.58F, 0.42F, 1.0F) : 1.0F;
            float rScale = ramp * compress;

            drawFlatRing(ps, buffers, ground + 0.05D, 6.0F * ramp, -time * 18.0F,
                    0.75F, 0.96F, 1.0F, 0.58F * ramp,
                    0.48F * ramp, 0.80F * ramp, 0.95F * ramp);
            drawFlatRing(ps, buffers, ground + 0.07D, (6.0F - 3.6F * Mth.clamp((age - 6.0F) / 22.0F, 0.0F, 1.0F)) * ramp, time * 26.0F,
                    0.90F, 0.99F, 1.0F, 0.62F * ramp,
                    0.65F * ramp, 0.92F * ramp, 1.0F * ramp);

            drawFunnelWall(ps, buffers, ground + 0.05D, ground + 1.5D, 1.25F * rScale, 2.45F * rScale, -time * 0.22F,
                    0.82F, 0.97F, 1.0F, 0.68F * ramp, 0.52F * ramp, 0.82F * ramp, 0.95F * ramp);
            drawFunnelWall(ps, buffers, ground + 1.2D, ground + 2.8D, 2.15F * rScale, 3.55F * rScale, time * 0.18F,
                    0.78F, 0.96F, 1.0F, 0.56F * ramp, 0.45F * ramp, 0.78F * ramp, 0.92F * ramp);
            drawFunnelWall(ps, buffers, ground + 2.4D, ground + 3.9D, 3.15F * rScale, 4.45F * rScale, -time * 0.14F,
                    0.74F, 0.94F, 1.0F, 0.42F * ramp, 0.35F * ramp, 0.68F * ramp, 0.85F * ramp);

            if (age > 18.0F) {
                float ck = (age - 18.0F) / 12.0F;
                drawBillboardFlare(ps, buffers, cameraRot, new Vec3(0.0D, 1.75D, 0.0D),
                        0.55F + 1.35F * ck, time * 12.0F, 0.85F * ck, 0.96F * ck, ck);
            }
        } else if (age <= 44.0F) {
            float k = (age - 30.0F) / 14.0F;
            float ease = 1.0F - (float) Math.pow(1.0F - k, 3.0D);
            float fade = 1.0F - k;
            float r = 1.0F + 3.6F * ease;

            drawFlatRing(ps, buffers, ground + 0.08D, r, time * 22.0F,
                    0.92F, 0.99F, 1.0F, 0.94F * fade,
                    0.80F * fade, 0.96F * fade, 1.0F * fade);
            drawFlatRing(ps, buffers, 1.45D, r * 0.92F, -time * 24.0F,
                    0.95F, 0.98F, 0.82F, 0.86F * fade,
                    0.90F * fade, 0.92F * fade, 0.65F * fade);
            drawFunnelWall(ps, buffers, ground + 0.04D, ground + 2.9D * fade + 0.35D, r * 0.86F, r * 1.04F, time * 0.20F,
                    0.88F, 0.99F, 1.0F, 0.82F * fade,
                    0.72F * fade, 0.92F * fade, fade);
            if (k < 0.45F) {
                float fl = 1.0F - k / 0.45F;
                drawBillboardFlare(ps, buffers, cameraRot, new Vec3(0.0D, 1.65D, 0.0D),
                        2.5F * fl + 0.6F, -time * 8.0F, 0.95F * fl, 0.98F * fl, fl);
            }
        }
    }

    private static void renderGaleBolts(float age, float bodyYaw, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, bodyYaw);
        Vec3 rgt = new Vec3(-fwd.z, 0.0D, fwd.x);

        for (int shot = 0; shot < 6; shot++) {
            float shotAt = 10.0F + shot * 6.0F;
            if (age < shotAt - 4.5F || age > shotAt + 4.5F) {
                continue;
            }
            boolean rightHand = (shot % 2 == 0);
            boolean predictive = (shot >= 3);
            Vec3 palm = fwd.scale(1.15D).add(rgt.scale(rightHand ? 1.25D : -1.25D)).add(0.0D, 2.28D, 0.0D);

            float scale;
            float alpha;
            if (age <= shotAt) {
                float ck = (age - (shotAt - 4.5F)) / 4.5F;
                scale = 0.95F * (1.30F - 0.55F * ck);
                alpha = 0.82F * ck;
            } else {
                float bk = (age - shotAt) / 4.5F;
                scale = 0.65F + 1.35F * bk;
                alpha = 0.92F * (1.0F - bk);
                drawBillboardFlare(ps, buffers, cameraRot, palm, 0.85F * (1.0F - bk), age * 18.0F,
                        predictive ? 1.0F : 0.75F, 0.95F, predictive ? 0.55F : 1.0F);
            }

            ps.pushPose();
            ps.translate(palm.x, palm.y, palm.z);
            ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
            ps.mulPose(Axis.ZP.rotationDegrees(age * (rightHand ? 26.0F : -26.0F)));
            PoseStack.Pose pose = ps.last();
            Vec3 a = new Vec3(-scale, scale, 0.0D);
            Vec3 b = new Vec3(scale, scale, 0.0D);
            Vec3 c = new Vec3(scale, -scale, 0.0D);
            Vec3 d = new Vec3(-scale, -scale, 0.0D);
            quadTwoSided(pose, buffers.getBuffer(RenderType.entityTranslucent(RING)),
                    a, b, c, d, 0.0F, 0.0F, 1.0F, 1.0F, 0.88F, 0.98F, 1.0F, alpha);
            quadTwoSided(pose, buffers.getBuffer(additive(RING_GLOW)),
                    a, b, c, d, 0.0F, 0.0F, 1.0F, 1.0F,
                    (predictive ? 1.0F : 0.65F) * alpha, 0.92F * alpha, (predictive ? 0.48F : 1.0F) * alpha, 1.0F);
            ps.popPose();
        }
    }

    private static void renderLanceCharge(ZephyrarchEntity boss, float age, float time, float bodyYaw, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        Vec3 core = Vec3.directionFromRotation(0.0F, bodyYaw).scale(0.6D).add(0.0D, 2.0D, 0.0D);
        if (age < 36.0F) {
            float k = Mth.clamp(age / 30.0F, 0.0F, 1.0F);
            boolean locked = age >= 30.0F;
            float flareSize = locked ? 1.45F + 0.25F * Mth.sin(age * 2.5F) : 0.35F + 1.05F * k;
            drawBillboardFlare(ps, buffers, cameraRot, core, flareSize, time * 12.0F,
                    1.0F * k, (locked ? 0.85F : 0.96F) * k, (locked ? 0.45F : 0.78F) * k);

            drawDirectedRing(ps, buffers, bodyYaw, 2.0D, 0.65D, 1.75F * (1.15F - 0.68F * k), time * 20.0F, 0.78F * k, locked);
            drawDirectedRing(ps, buffers, bodyYaw, 2.0D, 0.95D, 1.15F * (1.15F - 0.60F * k), -time * 28.0F, 0.72F * k, true);

            ps.pushPose();
            ps.translate(core.x, core.y, core.z);
            drawTiltedOrbitRibbon(ps, buffers, 0.0D, 1.25F * (1.1F - 0.45F * k), 0.22F, 38.0F, 16.0F, -time * 0.24F,
                    0.85F, 0.98F, 1.0F, 0.68F * k, 0.75F * k, 0.92F * k, (locked ? 0.55F : 1.0F) * k);
            ps.popPose();
        } else if (age <= 46.0F) {
            float fk = (age - 36.0F) / 10.0F;
            float fade = 1.0F - fk;
            drawBillboardFlare(ps, buffers, cameraRot, core, 2.4F * fade + 0.4F, -time * 16.0F,
                    fade, 0.96F * fade, 0.75F * fade);
            drawDirectedRing(ps, buffers, bodyYaw, 2.0D, 0.75D, 0.9F + 2.6F * (1.0F - (float) Math.pow(1.0F - fk, 3.0D)),
                    age * 24.0F, 0.95F * fade, true);
        }
    }

    private static void renderLance(ZephyrarchEntity boss, float partialTick, float time, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot, Vec3 cameraPos) {
        int state = boss.getLanceState();
        if (state == 0) {
            return;
        }
        Vec3 vec = boss.getLanceVector();
        double len = vec.length();
        if (len < 0.1D) {
            return;
        }
        Vec3 dir = vec.scale(1.0D / len);
        Vec3 origin = boss.getLanceOrigin().subtract(boss.position());
        float age = boss.getClientAttackAge(partialTick);

        if (state == 1 || state == 2) {
            boolean locked = (state == 2);
            float width = locked ? (0.14F + 0.04F * Mth.sin(age * 2.8F)) : 0.065F;
            float r = locked ? 1.0F : 0.72F;
            float g = locked ? 0.78F : 0.96F;
            float b = locked ? 0.42F : 1.0F;
            float a = locked ? 0.92F : 0.62F;

            Vec3 worldOrigin = boss.getPosition(partialTick).add(origin);
            Vec3 toCam = cameraPos.subtract(worldOrigin.add(vec.scale(0.5D)));
            Vec3 side = dir.cross(toCam);
            side = side.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : side.normalize();
            PoseStack.Pose pose = ps.last();
            Vec3 p0 = origin;
            Vec3 p1 = origin.add(vec);
            float u0 = -time * 0.25F;
            float u1 = u0 + (float) len * 0.35F;

            quadTwoSided(pose, buffers.getBuffer(RenderType.entityTranslucent(RIBBON)),
                    p0.add(side.scale(width)), p1.add(side.scale(width)),
                    p1.subtract(side.scale(width)), p0.subtract(side.scale(width)),
                    u0, 0.0F, u1, 1.0F, r, g, b, a);
            quadTwoSided(pose, buffers.getBuffer(additive(RIBBON_GLOW)),
                    p0.add(side.scale(width * 0.65F)), p1.add(side.scale(width * 0.65F)),
                    p1.subtract(side.scale(width * 0.65F)), p0.subtract(side.scale(width * 0.65F)),
                    u0, 0.0F, u1, 1.0F, r * a, g * a, b * a, 1.0F);
            return;
        }

        float k = Mth.clamp((age - 36.0F) / 8.5F, 0.0F, 1.0F);
        float fade = 1.0F - k * k;
        float coreW = (0.68F * (1.0F - k * 0.75F) + 0.12F);

        Vec3 upRef = Math.abs(dir.y) > 0.92D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 axisA = dir.cross(upRef).normalize();
        Vec3 axisB = dir.cross(axisA).normalize();

        PoseStack.Pose pose = ps.last();
        Vec3 p0 = origin;
        Vec3 p1 = origin.add(vec);
        float u0 = -time * 0.55F;
        float u1 = u0 + (float) len * 0.28F;

        VertexConsumer vcBase = buffers.getBuffer(RenderType.entityTranslucent(RIBBON));
        quadTwoSided(pose, vcBase,
                p0.add(axisA.scale(coreW)), p1.add(axisA.scale(coreW)),
                p1.subtract(axisA.scale(coreW)), p0.subtract(axisA.scale(coreW)),
                u0, 0.0F, u1, 1.0F, 0.92F, 0.99F, 1.0F, 0.92F * fade);
        quadTwoSided(pose, vcBase,
                p0.add(axisB.scale(coreW)), p1.add(axisB.scale(coreW)),
                p1.subtract(axisB.scale(coreW)), p0.subtract(axisB.scale(coreW)),
                u0, 0.0F, u1, 1.0F, 0.92F, 0.99F, 1.0F, 0.92F * fade);

        VertexConsumer vcGlow = buffers.getBuffer(additive(RIBBON_GLOW));
        float glowW = coreW * 0.72F;
        quadTwoSided(pose, vcGlow,
                p0.add(axisA.scale(glowW)), p1.add(axisA.scale(glowW)),
                p1.subtract(axisA.scale(glowW)), p0.subtract(axisA.scale(glowW)),
                u0, 0.0F, u1, 1.0F, 0.95F * fade, 0.95F * fade, 0.72F * fade, 1.0F);
        quadTwoSided(pose, vcGlow,
                p0.add(axisB.scale(glowW)), p1.add(axisB.scale(glowW)),
                p1.subtract(axisB.scale(glowW)), p0.subtract(axisB.scale(glowW)),
                u0, 0.0F, u1, 1.0F, 0.80F * fade, 0.96F * fade, 1.0F * fade, 1.0F);

        Quaternionf align = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) dir.x, (float) dir.y, (float) dir.z);
        ps.pushPose();
        ps.translate(origin.x, origin.y, origin.z);
        ps.mulPose(align);

        int segCount = Math.max(1, (int) Math.ceil(len / 6.0D));
        double segLen = len / segCount;
        float tubeR = 0.58F * (1.0F - k * 0.55F) + 0.15F;
        for (int s = 0; s < segCount; s++) {
            drawFunnelWall(ps, buffers, s * segLen, (s + 1) * segLen, tubeR, tubeR, -time * 0.45F + s * 0.5F,
                    0.82F, 0.98F, 1.0F, 0.62F * fade,
                    0.65F * fade, 0.92F * fade, 1.0F * fade);
        }

        for (double distAlong = 2.5D; distAlong < len - 0.5D; distAlong += 5.5D) {
            float ringR = (0.75F + 1.15F * k);
            drawFlatRing(ps, buffers, distAlong, ringR, time * 30.0F + (float) distAlong * 25.0F,
                    0.92F, 0.99F, 1.0F, 0.82F * fade,
                    0.85F * fade, 0.95F * fade, 0.75F * fade);
        }
        ps.popPose();

        drawBillboardFlare(ps, buffers, cameraRot, p1, 1.65F * fade, time * 14.0F, fade, 0.96F * fade, 0.78F * fade);
    }

    private static void renderStormEyeCast(float age, float time, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        float k = age < 20.0F ? age / 20.0F : Mth.clamp(1.0F - (age - 20.0F) / 14.0F, 0.0F, 1.0F);
        drawFlatRing(ps, buffers, 3.85D, 1.85F * k + 0.35F, time * 18.0F,
                0.82F, 0.98F, 1.0F, 0.80F * k,
                0.62F * k, 0.92F * k, 1.0F * k);
        drawFlatRing(ps, buffers, 3.90D, 1.10F * k + 0.25F, -time * 28.0F,
                1.0F, 0.94F, 0.65F, 0.75F * k,
                0.90F * k, 0.84F * k, 0.45F * k);
        drawFunnelWall(ps, buffers, 2.3D, 4.1D, 0.45F * k, 1.55F * k, -time * 0.28F,
                0.80F, 0.97F, 1.0F, 0.58F * k,
                0.50F * k, 0.86F * k, 0.98F * k);
        drawBillboardFlare(ps, buffers, cameraRot, new Vec3(0.0D, 4.05D, 0.0D), 0.55F + 1.05F * k, time * 10.0F,
                0.78F * k, 0.96F * k, k);
    }

    private static void renderDowndraftCast(ZephyrarchEntity boss, float age, float time, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        double ground = findGroundOffset(boss);
        if (age < 12.0F) {
            float k = age / 12.0F;
            drawFlatRing(ps, buffers, 3.95D, 2.55F * k + 0.4F, -time * 20.0F,
                    0.88F, 0.98F, 1.0F, 0.82F * k,
                    0.75F * k, 0.94F * k, 1.0F * k);
            drawBillboardFlare(ps, buffers, cameraRot, new Vec3(0.0D, 4.0D, 0.0D), 0.5F + 1.1F * k, -time * 12.0F,
                    0.95F * k, 0.92F * k, 0.62F * k);
        } else if (age <= 24.0F) {
            float k = (age - 12.0F) / 12.0F;
            float drop = Mth.clamp(k * 2.4F, 0.0F, 1.0F);
            float fade = 1.0F - k;
            double ringY = Mth.lerp(drop, 3.95D, ground + 0.12D);
            float ringR = 2.6F + 1.4F * k;
            drawFlatRing(ps, buffers, ringY, ringR, time * 26.0F,
                    0.92F, 0.99F, 1.0F, 0.88F * fade,
                    0.85F * fade, 0.95F * fade, 0.72F * fade);
            drawFunnelWall(ps, buffers, ringY, ringY + 1.8D * fade, ringR * 0.85F, ringR * 1.05F, age * 0.35F,
                    0.82F, 0.97F, 1.0F, 0.65F * fade,
                    0.62F * fade, 0.90F * fade, 1.0F * fade);
        }
    }

    private static void renderBoomerangCast(float age, float time, float bodyYaw, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, bodyYaw);
        if (age < 12.0F) {
            float k = age / 12.0F;
            Vec3 crossWrist = fwd.scale(0.85D).add(0.0D, 2.05D, 0.0D);
            ps.pushPose();
            ps.translate(crossWrist.x, crossWrist.y, crossWrist.z);
            drawTiltedOrbitRibbon(ps, buffers, 0.0D, 0.95F * k + 0.25F, 0.22F, 36.0F, 32.0F, time * 0.35F,
                    0.85F, 0.98F, 1.0F, 0.78F * k, 0.68F * k, 0.94F * k, 1.0F * k);
            drawTiltedOrbitRibbon(ps, buffers, 0.0D, 0.95F * k + 0.25F, 0.22F, 36.0F, -32.0F, -time * 0.35F,
                    0.85F, 0.98F, 1.0F, 0.78F * k, 0.68F * k, 0.94F * k, 1.0F * k);
            ps.popPose();
            drawBillboardFlare(ps, buffers, cameraRot, crossWrist, 0.45F + 0.85F * k, time * 16.0F,
                    0.78F * k, 0.96F * k, k);
        } else if (age <= 22.0F) {
            float k = (age - 12.0F) / 10.0F;
            drawSlashArcRange(ps, buffers, bodyYaw, 1.65D, 1.8D, 3.85D, -12.0F, -15.0F, 118.0F, 1.0F - k, true);
            drawSlashArcRange(ps, buffers, bodyYaw, 1.65D, 1.8D, 3.85D, 12.0F, 15.0F, -118.0F, 1.0F - k, true);
        }
    }

    private static void renderCageCast(float age, float time, float bodyYaw, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        if (age < 22.0F) {
            float k = Mth.clamp(age / 20.0F, 0.0F, 1.0F);
            drawFunnelWall(ps, buffers, 0.1D, 1.9D, 1.15F, 2.05F, -time * 0.34F,
                    0.85F, 0.98F, 1.0F, 0.72F * k, 0.65F * k, 0.88F * k, 0.98F * k);
            drawFunnelWall(ps, buffers, 1.6D, 3.6D, 1.85F, 2.85F, time * 0.28F,
                    1.0F, 0.94F, 0.62F, 0.62F * k, 0.92F * k, 0.82F * k, 0.45F * k);
            drawFlatRing(ps, buffers, 0.12D, 1.5F + 4.5F * k, time * 24.0F,
                    1.0F, 0.94F, 0.65F, 0.78F * (1.0F - k * 0.45F),
                    0.95F * k, 0.86F * k, 0.45F * k);
            drawBillboardFlare(ps, buffers, cameraRot, new Vec3(0.0D, 2.15D, 0.0D), 0.65F + 1.25F * k, -time * 14.0F,
                    1.0F * k, 0.92F * k, 0.52F * k);
        }

        Vec3 fwd = Vec3.directionFromRotation(0.0F, bodyYaw);
        Vec3 rgt = new Vec3(-fwd.z, 0.0D, fwd.x);
        float[] orbTimes = {28.0F, 40.0F, 52.0F};
        for (int i = 0; i < orbTimes.length; i++) {
            float shotAt = orbTimes[i];
            if (age < shotAt - 5.0F || age > shotAt + 5.0F) {
                continue;
            }
            Vec3 origin = i == 0
                    ? fwd.scale(1.05D).add(rgt.scale(1.25D)).add(0.0D, 2.28D, 0.0D)
                    : (i == 1 ? fwd.scale(1.05D).subtract(rgt.scale(1.25D)).add(0.0D, 2.28D, 0.0D)
                    : fwd.scale(1.05D).add(0.0D, 2.15D, 0.0D));
            float k = age <= shotAt ? (age - (shotAt - 5.0F)) / 5.0F : 1.0F - (age - shotAt) / 5.0F;
            float ringScale = age <= shotAt ? 1.25F * (1.2F - 0.5F * k) : 0.85F + 1.55F * (1.0F - k);

            ps.pushPose();
            ps.translate(origin.x, origin.y, origin.z);
            ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
            ps.mulPose(Axis.ZP.rotationDegrees(time * 24.0F));
            PoseStack.Pose pose = ps.last();
            Vec3 a = new Vec3(-ringScale, ringScale, 0.0D);
            Vec3 b = new Vec3(ringScale, ringScale, 0.0D);
            Vec3 c = new Vec3(ringScale, -ringScale, 0.0D);
            Vec3 d = new Vec3(-ringScale, -ringScale, 0.0D);
            quadTwoSided(pose, buffers.getBuffer(RenderType.entityTranslucent(RING)),
                    a, b, c, d, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.94F, 0.65F, 0.88F * k);
            quadTwoSided(pose, buffers.getBuffer(additive(RING_GLOW)),
                    a, b, c, d, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F * k, 0.88F * k, 0.45F * k, 1.0F);
            ps.popPose();
            drawBillboardFlare(ps, buffers, cameraRot, origin, 0.95F * k, -time * 12.0F, k, 0.92F * k, 0.50F * k);
        }
    }

    private static void renderStagger(float age, float time, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot) {
        float fade = Mth.clamp(1.0F - age / 30.0F, 0.0F, 1.0F);
        float flicker = 0.55F + 0.45F * Mth.sin(age * 2.2F);
        drawTiltedOrbitRibbon(ps, buffers, 2.15D, 1.15F, 0.22F, 28.0F * Mth.sin(age * 0.4F), 22.0F, time * 0.18F,
                0.82F, 0.96F, 1.0F, 0.55F * fade, 0.45F * fade, 0.78F * fade, 0.92F * fade);
        drawBillboardFlare(ps, buffers, cameraRot, new Vec3(0.0D, 1.95D, 0.0D), 0.55F * fade * flicker, time * 7.0F,
                0.75F * fade, 0.90F * fade, fade);
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
        drawFunnelWall(ps, buffers, 0.0D, ZephyrarchEntity.CAGE_HEIGHT * 0.55D, r, r, -time * 0.20F,
                0.82F, 0.97F, 1.0F, 0.62F * fade,
                0.45F * fade, 0.78F * fade, 0.95F * fade);
        drawFunnelWall(ps, buffers, ZephyrarchEntity.CAGE_HEIGHT * 0.45D, ZephyrarchEntity.CAGE_HEIGHT, r, r * 1.02F, -time * 0.16F,
                0.80F, 0.96F, 1.0F, 0.48F * fade,
                0.38F * fade, 0.70F * fade, 0.88F * fade);
        drawFunnelWall(ps, buffers, 0.0D, ZephyrarchEntity.CAGE_HEIGHT * 0.78D, r - 0.16F, r - 0.16F, time * 0.26F,
                1.0F, 0.94F, 0.65F, 0.42F * fade,
                0.62F * fade, 0.55F * fade, 0.30F * fade);
        drawFlatRing(ps, buffers, 0.06D, r * 1.02F, time * 5.0F,
                0.80F, 0.97F, 1.0F, 0.68F * fade,
                0.55F * fade, 0.85F * fade, 0.98F * fade);
        drawFlatRing(ps, buffers, ZephyrarchEntity.CAGE_HEIGHT * 0.5D, r, -time * 6.5F,
                1.0F, 0.94F, 0.65F, 0.42F * fade,
                0.65F * fade, 0.58F * fade, 0.32F * fade);
        ps.popPose();
    }

    private static void drawDirectedRing(PoseStack ps, MultiBufferSource buffers, float yawDeg, double yOff, double forwardOff,
                                         float radius, float spinDeg, float alpha, boolean warm) {
        if (alpha <= 0.01F || radius <= 0.02F) {
            return;
        }
        ps.pushPose();
        ps.translate(0.0D, yOff, 0.0D);
        ps.mulPose(Axis.YP.rotationDegrees(-yawDeg));
        ps.translate(0.0D, 0.0D, forwardOff);
        ps.mulPose(Axis.XP.rotationDegrees(90.0F));
        drawFlatRing(ps, buffers, 0.0D, radius, spinDeg,
                warm ? 1.0F : 0.86F, 0.98F, warm ? 0.68F : 1.0F, 0.86F * alpha,
                (warm ? 0.98F : 0.70F) * alpha, 0.92F * alpha, (warm ? 0.48F : 1.0F) * alpha);
        ps.popPose();
    }

    private static void drawSlashArc(PoseStack ps, MultiBufferSource buffers, float bodyYaw, double centerY,
                                     double innerR, double outerR, float tiltZDeg, boolean rightToLeft, float k, boolean phase2) {
        float startDeg = rightToLeft ? -78.0F : 86.0F;
        float endDeg = rightToLeft ? 78.0F : -86.0F;
        float sweepProgress = Mth.clamp(k * 2.2F, 0.0F, 1.0F);
        float fade = k < 0.45F ? 1.0F : Mth.clamp((1.0F - k) / 0.55F, 0.0F, 1.0F);
        float currentEnd = Mth.lerp(sweepProgress, startDeg, endDeg);
        float tailStart = Mth.lerp(Mth.clamp((k - 0.25F) * 1.15F, 0.0F, 0.65F), startDeg, endDeg);
        drawSlashArcRange(ps, buffers, bodyYaw, centerY, innerR, outerR, tiltZDeg, tailStart, currentEnd, fade, phase2);
    }

    private static void drawSlashArcRange(PoseStack ps, MultiBufferSource buffers, float bodyYaw, double centerY,
                                          double innerR, double outerR, float tiltZDeg, float tailStart, float currentEnd, float fade, boolean phase2) {
        if (fade <= 0.01F || Math.abs(currentEnd - tailStart) <= 2.0F) {
            return;
        }

        ps.pushPose();
        ps.translate(0.0D, centerY, 0.0D);
        ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        ps.translate(0.0D, 0.0D, 0.35D);
        ps.mulPose(Axis.ZP.rotationDegrees(tiltZDeg));

        int segments = 18;
        float tr = phase2 ? 0.88F : 0.78F;
        float tg = 0.98F;
        float tb = phase2 ? 0.92F : 1.0F;
        float gr = phase2 ? 1.0F : 0.65F * fade;
        float gg = 0.92F * fade;
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
        if (age < 21.0F) {
            float k = Mth.clamp(age / 21.0F, 0.0F, 1.0F);
            float shrinkR = 4.6F * (1.0F - 0.72F * k);

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
            float k = Mth.clamp((age - 21.0F) / 19.0F, 0.0F, 1.0F);
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

    public static void renderChargeTrail(ZephyrChargeEntity charge, float partialTick, PoseStack ps, MultiBufferSource buffers, Quaternionf cameraRot, Vec3 cameraPos) {
        boolean orb = charge.isHomingOrb();
        if (orb) {
            float time = charge.tickCount + partialTick;
            ps.pushPose();
            ps.translate(0.0D, 0.35D, 0.0D);
            drawTiltedOrbitRibbon(ps, buffers, 0.0D, 0.72F, 0.16F, 34.0F, 18.0F, -time * 0.28F,
                    1.0F, 0.94F, 0.62F, 0.75F, 0.95F, 0.84F, 0.42F);
            drawTiltedOrbitRibbon(ps, buffers, 0.0D, 0.68F, 0.14F, -32.0F, -22.0F, time * 0.32F,
                    0.82F, 0.98F, 1.0F, 0.68F, 0.55F, 0.88F, 1.0F);
            ps.popPose();
            drawBillboardFlare(ps, buffers, cameraRot, new Vec3(0.0D, 0.35D, 0.0D), 0.75F, time * 8.0F, 1.0F, 0.92F, 0.52F);
        }

        if (charge.clientTrail.size() < 2) {
            return;
        }
        renderRibbonTrail(charge.getPosition(partialTick).add(0.0D, 0.25D, 0.0D), 0.25D, charge.clientTrail,
                orb ? 0.42F : 0.28F, orb, ps, buffers, cameraPos);
    }

    public static void renderBladeTrail(ZephyrBladeEntity blade, float partialTick, PoseStack ps, MultiBufferSource buffers, Vec3 cameraPos) {
        if (blade.clientTrail.size() < 2) {
            return;
        }
        renderRibbonTrail(blade.getPosition(partialTick).add(0.0D, 0.15D, 0.0D), 0.15D, blade.clientTrail,
                0.52F, false, ps, buffers, cameraPos);
    }

    private static void renderRibbonTrail(Vec3 headWorld, double localY, Iterable<Vec3> worldTrail, float baseWidth, boolean warm,
                                          PoseStack ps, MultiBufferSource buffers, Vec3 cameraPos) {
        java.util.List<Vec3> relList = new java.util.ArrayList<>(14);
        relList.add(Vec3.ZERO);
        for (Vec3 worldPt : worldTrail) {
            relList.add(worldPt.subtract(headWorld));
        }
        int count = relList.size() - 1;
        if (count < 1) {
            return;
        }

        ps.pushPose();
        ps.translate(0.0D, localY, 0.0D);
        PoseStack.Pose pose = ps.last();

        VertexConsumer vcBase = buffers.getBuffer(RenderType.entityTranslucent(RIBBON));
        for (int i = 0; i < count; i++) {
            Vec3 p0 = relList.get(i);
            Vec3 p1 = relList.get(i + 1);
            Vec3 seg = p1.subtract(p0);
            if (seg.lengthSqr() < 1.0E-5D) {
                continue;
            }
            float t0 = i / (float) count;
            float t1 = (i + 1) / (float) count;
            float w0 = baseWidth * (1.0F - t0 * 0.85F);
            float w1 = baseWidth * (1.0F - t1 * 0.85F);
            float alpha = (1.0F - t0) * 0.82F;

            Vec3 toCam = cameraPos.subtract(headWorld.add(p0));
            Vec3 side = seg.cross(toCam);
            side = side.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : side.normalize();

            quadTwoSided(pose, vcBase,
                    p0.add(side.scale(w0)), p1.add(side.scale(w1)),
                    p1.subtract(side.scale(w1)), p0.subtract(side.scale(w0)),
                    t0, 0.0F, t1, 1.0F, warm ? 1.0F : 0.84F, 0.98F, warm ? 0.72F : 1.0F, alpha);
        }

        VertexConsumer vcGlow = buffers.getBuffer(additive(RIBBON_GLOW));
        for (int i = 0; i < count; i++) {
            Vec3 p0 = relList.get(i);
            Vec3 p1 = relList.get(i + 1);
            Vec3 seg = p1.subtract(p0);
            if (seg.lengthSqr() < 1.0E-5D) {
                continue;
            }
            float t0 = i / (float) count;
            float t1 = (i + 1) / (float) count;
            float w0 = baseWidth * 0.72F * (1.0F - t0 * 0.85F);
            float w1 = baseWidth * 0.72F * (1.0F - t1 * 0.85F);
            float g = (1.0F - t0) * 0.80F;

            Vec3 toCam = cameraPos.subtract(headWorld.add(p0));
            Vec3 side = seg.cross(toCam);
            side = side.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : side.normalize();

            quadTwoSided(pose, vcGlow,
                    p0.add(side.scale(w0)), p1.add(side.scale(w1)),
                    p1.subtract(side.scale(w1)), p0.subtract(side.scale(w0)),
                    t0, 0.0F, t1, 1.0F, (warm ? 1.0F : 0.65F) * g, 0.92F * g, (warm ? 0.48F : 1.0F) * g, 1.0F);
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

    public static void drawTiltedOrbitRibbon(PoseStack ps, MultiBufferSource buffers, double centerY, float radius, float halfHeight,
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
                Vec3 bot1 = new Vec3(Math.cos(a1) * (rBottom + 0.01F), yBottom, Math.sin(a1) * (rBottom + 0.01F));
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
                double topY = check.getY() + state.getCollisionShape(level, check).max(Direction.Axis.Y);
                return topY - boss.getY();
            }
        }
        return 0.0D;
    }
}
