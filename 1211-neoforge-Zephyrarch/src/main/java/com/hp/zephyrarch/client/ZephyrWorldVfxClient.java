package com.hp.zephyrarch.client;

import com.hp.zephyrarch.Zephyrarch;
import com.hp.zephyrarch.network.ZephyrWorldVfxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@EventBusSubscriber(modid = Zephyrarch.MOD_ID, value = Dist.CLIENT)
public final class ZephyrWorldVfxClient {
    private static final List<ActiveEffect> EFFECTS = new ArrayList<>();

    private ZephyrWorldVfxClient() {
    }

    public static void spawn(ZephyrWorldVfxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double y = p.y();
        if (p.kind() == ZephyrWorldVfxPayload.KIND_STORM_EYE_MARK || p.kind() == ZephyrWorldVfxPayload.KIND_DOWNDRAFT_COLUMN) {
            y = snapToGround(mc.level, p.x(), p.y(), p.z());
        }
        if (EFFECTS.size() > 160) {
            EFFECTS.removeFirst();
        }
        EFFECTS.add(new ActiveEffect(p.kind(), new Vec3(p.x(), y, p.z()), new Vec3(p.vx(), p.vy(), p.vz()), p.scale(), Math.max(1, p.duration())));
    }

    private static double snapToGround(Level level, double x, double y, double z) {
        BlockPos base = BlockPos.containing(x, y + 0.8D, z);
        for (int dy = 0; dy <= 7; dy++) {
            BlockPos check = base.below(dy);
            BlockState state = level.getBlockState(check);
            if (!state.getCollisionShape(level, check).isEmpty()) {
                return check.getY() + state.getCollisionShape(level, check).max(Direction.Axis.Y);
            }
        }
        return y;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            EFFECTS.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        Iterator<ActiveEffect> it = EFFECTS.iterator();
        while (it.hasNext()) {
            ActiveEffect fx = it.next();
            fx.age++;
            if (fx.age >= fx.duration) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || EFFECTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        Quaternionf camRot = camera.rotation();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        ps.pushPose();
        ps.translate(-camPos.x, -camPos.y, -camPos.z);

        for (ActiveEffect fx : EFFECTS) {
            float age = fx.age + partialTick;
            if (age > fx.duration) {
                continue;
            }
            ps.pushPose();
            ps.translate(fx.pos.x, fx.pos.y, fx.pos.z);
            switch (fx.kind) {
                case ZephyrWorldVfxPayload.KIND_STORM_EYE_MARK -> renderStormEyeMark(fx, age, ps, buffers);
                case ZephyrWorldVfxPayload.KIND_DOWNDRAFT_COLUMN -> renderDowndraftColumn(fx, age, ps, buffers, camRot);
                case ZephyrWorldVfxPayload.KIND_CHARGE_BURST -> renderChargeBurst(fx, age, ps, buffers, camRot);
                case ZephyrWorldVfxPayload.KIND_TWISTER_DISSIPATE -> renderTwisterDissipate(fx, age, ps, buffers);
                case ZephyrWorldVfxPayload.KIND_WIND_DASH_TRAIL -> renderWindDashTrail(fx, age, ps, buffers, camPos);
                default -> {
                }
            }
            ps.popPose();
        }

        ps.popPose();

        buffers.endBatch(RenderType.entityTranslucent(ZephyrarchVfx.RING));
        buffers.endBatch(RenderType.entityTranslucent(ZephyrarchVfx.RIBBON));
        buffers.endBatch(RenderType.entityTranslucent(ZephyrarchVfx.SLASH));
        buffers.endBatch(ZephyrarchVfx.additive(ZephyrarchVfx.RING_GLOW));
        buffers.endBatch(ZephyrarchVfx.additive(ZephyrarchVfx.RIBBON_GLOW));
        buffers.endBatch(ZephyrarchVfx.additive(ZephyrarchVfx.SLASH_GLOW));
        buffers.endBatch(ZephyrarchVfx.additive(ZephyrarchVfx.FLARE));
    }

    private static void renderStormEyeMark(ActiveEffect fx, float age, PoseStack ps, MultiBufferSource buffers) {
        float k = Mth.clamp(age / fx.duration, 0.0F, 1.0F);
        float fadeIn = Mth.clamp(age / 3.0F, 0.0F, 1.0F);
        float r = fx.scale;
        ZephyrarchVfx.drawFlatRing(ps, buffers, 0.04D, r, age * 14.0F,
                0.76F, 0.97F, 1.0F, 0.72F * fadeIn,
                0.48F * fadeIn, 0.85F * fadeIn, 0.98F * fadeIn);
        float innerR = r * (1.12F - 0.68F * k);
        ZephyrarchVfx.drawFlatRing(ps, buffers, 0.06D, innerR, -age * 24.0F,
                0.92F, 0.99F, 1.0F, (0.35F + 0.55F * k) * fadeIn,
                0.75F * k, 0.95F * k, 1.0F * k);
        double riseH = 0.25D + 2.35D * k * k;
        ZephyrarchVfx.drawFunnelWall(ps, buffers, 0.03D, riseH, 0.22F + 0.15F * k, 0.55F + 0.45F * k, -age * 0.35F,
                0.82F, 0.98F, 1.0F, 0.55F * k,
                0.45F * k, 0.82F * k, 0.96F * k);
    }

    private static void renderDowndraftColumn(ActiveEffect fx, float age, PoseStack ps, MultiBufferSource buffers, Quaternionf camRot) {
        float warnTicks = 15.0F;
        float radius = fx.scale;
        if (age < warnTicks) {
            float k = Mth.clamp(age / warnTicks, 0.0F, 1.0F);
            float fadeIn = Mth.clamp(age / 3.0F, 0.0F, 1.0F);
            float pulse = age > 10.5F ? (0.75F + 0.25F * Mth.sin(age * 2.6F)) : 1.0F;
            ZephyrarchVfx.drawFlatRing(ps, buffers, 0.04D, radius, -age * 10.0F,
                    0.78F, 0.97F, 1.0F, 0.68F * fadeIn,
                    0.50F * fadeIn * pulse, 0.86F * fadeIn * pulse, 1.0F * fadeIn);
            float fillR = radius * (0.20F + 0.80F * k);
            ZephyrarchVfx.drawFlatRing(ps, buffers, 0.06D, fillR, age * 22.0F,
                    1.0F, 0.94F, 0.68F, (0.30F + 0.58F * k) * fadeIn,
                    0.95F * k * pulse, 0.84F * k * pulse, 0.42F * k);
            ZephyrarchVfx.drawFunnelWall(ps, buffers, 7.6D - 1.6D * k, 9.4D, radius * 0.55F, radius * 1.25F, age * 0.28F,
                    0.82F, 0.97F, 1.0F, 0.48F * k,
                    0.45F * k, 0.78F * k, 0.95F * k);
        } else {
            float imp = age - warnTicks;
            float dur = Math.max(1.0F, fx.duration - warnTicks);
            float k = Mth.clamp(imp / dur, 0.0F, 1.0F);
            float fade = 1.0F - k * k;
            float dropProgress = Mth.clamp(imp / 2.0F, 0.0F, 1.0F);
            double bottomY = 9.0D * (1.0D - dropProgress);
            double topY = 9.4D;

            float coreR = radius * (0.42F - 0.14F * k);
            ZephyrarchVfx.drawFunnelWall(ps, buffers, bottomY, topY, coreR, coreR * 1.18F, imp * 0.55F,
                    0.92F, 0.99F, 1.0F, 0.88F * fade,
                    0.85F * fade, 0.96F * fade, 1.0F * fade);

            float outerR = radius * (0.86F + 0.16F * k);
            ZephyrarchVfx.drawFunnelWall(ps, buffers, bottomY, topY, outerR * 0.92F, outerR * 1.28F, -imp * 0.42F,
                    0.78F, 0.96F, 1.0F, 0.68F * fade,
                    0.62F * fade, 0.88F * fade, 1.0F * fade);

            if (imp >= 1.0F) {
                float sk = Mth.clamp((imp - 1.0F) / (dur - 1.0F), 0.0F, 1.0F);
                float ease = 1.0F - (float) Math.pow(1.0F - sk, 3.0D);
                float sFade = 1.0F - sk;
                float shockR = radius * (0.45F + 1.15F * ease);
                ZephyrarchVfx.drawFlatRing(ps, buffers, 0.06D, shockR, imp * 24.0F,
                        0.90F, 0.99F, 1.0F, 0.92F * sFade,
                        0.85F * sFade, 0.95F * sFade, 1.0F * sFade);
                ZephyrarchVfx.drawFunnelWall(ps, buffers, 0.03D, 0.25D + 1.15D * sFade, shockR * 0.82F, shockR * 1.04F, -imp * 0.25F,
                        0.84F, 0.98F, 1.0F, 0.72F * sFade,
                        0.60F * sFade, 0.90F * sFade, 1.0F * sFade);
                if (sk < 0.45F) {
                    float fl = 1.0F - sk / 0.45F;
                    ZephyrarchVfx.drawBillboardFlare(ps, buffers, camRot, new Vec3(0.0D, 0.45D, 0.0D),
                            1.55F * fl, imp * 12.0F, 0.95F * fl, 0.98F * fl, fl);
                }
            }
        }
    }

    private static void renderChargeBurst(ActiveEffect fx, float age, PoseStack ps, MultiBufferSource buffers, Quaternionf camRot) {
        float k = Mth.clamp(age / fx.duration, 0.0F, 1.0F);
        float ease = 1.0F - (float) Math.pow(1.0F - k, 3.0D);
        float fade = 1.0F - k;
        boolean heavy = fx.scale >= 2.0F;
        float r = 0.35F + fx.scale * 1.05F * ease;

        float gr = (heavy ? 1.0F : 0.65F) * fade;
        float gg = 0.92F * fade;
        float gb = (heavy ? 0.52F : 1.0F) * fade;

        ZephyrarchVfx.drawFlatRing(ps, buffers, 0.0D, r, age * 26.0F,
                0.88F, 0.98F, 1.0F, 0.88F * fade, gr, gg, gb);

        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(58.0F));
        ps.mulPose(Axis.ZP.rotationDegrees(34.0F));
        ZephyrarchVfx.drawFlatRing(ps, buffers, 0.0D, r * 0.92F, -age * 28.0F,
                0.85F, 0.98F, 1.0F, 0.78F * fade, gr * 0.9F, gg * 0.9F, gb * 0.9F);
        ps.popPose();

        if (k < 0.55F) {
            float fl = 1.0F - k / 0.55F;
            ZephyrarchVfx.drawBillboardFlare(ps, buffers, camRot, Vec3.ZERO,
                    (heavy ? 1.85F : 1.05F) * fl, -age * 14.0F,
                    heavy ? fl : 0.8F * fl, 0.95F * fl, heavy ? 0.6F * fl : fl);
        }
    }

    private static void renderTwisterDissipate(ActiveEffect fx, float age, PoseStack ps, MultiBufferSource buffers) {
        float k = Mth.clamp(age / fx.duration, 0.0F, 1.0F);
        float ease = 1.0F - (float) Math.pow(1.0F - k, 2.5D);
        float fade = 1.0F - k;
        float r = 0.5F + fx.scale * ease;
        ZephyrarchVfx.drawFlatRing(ps, buffers, 0.12D, r, age * 24.0F,
                0.82F, 0.98F, 1.0F, 0.80F * fade, 0.55F * fade, 0.88F * fade, 1.0F * fade);
        ZephyrarchVfx.drawFunnelWall(ps, buffers, 0.2D + 0.6D * ease, 2.4D + 0.8D * ease, r * 0.55F, r * 1.05F, -age * 0.35F,
                0.80F, 0.96F, 1.0F, 0.58F * fade, 0.45F * fade, 0.78F * fade, 0.95F * fade);
    }

    private static void renderWindDashTrail(ActiveEffect fx, float age, PoseStack ps, MultiBufferSource buffers, Vec3 camPos) {
        float k = Mth.clamp(age / fx.duration, 0.0F, 1.0F);
        float fade = 1.0F - k;
        Vec3 total = fx.vec;
        double len = total.length();
        if (len < 0.1D) {
            return;
        }
        Vec3 dir = total.scale(1.0D / len);
        float yaw = (float) (Mth.atan2(dir.x, dir.z) * (180.0D / Math.PI));

        ps.pushPose();
        ps.translate(0.0D, 1.35D, 0.0D);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.mulPose(Axis.XP.rotationDegrees(90.0F));
        float burstR = 0.85F + 1.85F * (1.0F - (float) Math.pow(1.0F - k, 3.0D));
        ZephyrarchVfx.drawFlatRing(ps, buffers, 0.0D, burstR, age * 26.0F,
                0.88F, 0.98F, 1.0F, 0.85F * fade, 0.75F * fade, 0.95F * fade, 1.0F * fade);

        double tailStart = len * Mth.clamp((k - 0.15F) * 0.85F, 0.0F, 0.75F);
        double headEnd = len * Mth.clamp(k * 2.2F, 0.15F, 1.0F);
        if (headEnd > tailStart + 0.2D) {
            ZephyrarchVfx.drawFunnelWall(ps, buffers, tailStart, headEnd, 1.15F * fade + 0.25F, 0.45F * fade + 0.15F, -age * 0.45F,
                    0.82F, 0.97F, 1.0F, 0.65F * fade, 0.55F * fade, 0.88F * fade, 1.0F * fade);
        }
        ps.popPose();

        Vec3 p0 = new Vec3(0.0D, 1.35D, 0.0D).add(dir.scale(len * Mth.clamp(k * 0.55F, 0.0F, 0.55F)));
        Vec3 p1 = new Vec3(0.0D, 1.35D, 0.0D).add(dir.scale(len * Mth.clamp(k * 2.0F, 0.2F, 1.0F)));
        Vec3 worldMid = fx.pos.add(p0.add(p1).scale(0.5D));
        Vec3 side = dir.cross(camPos.subtract(worldMid));
        side = side.lengthSqr() < 1.0E-5D ? new Vec3(0.0D, 1.0D, 0.0D) : side.normalize();
        float w = 0.55F * fade;
        PoseStack.Pose pose = ps.last();
        ZephyrarchVfx.quadTwoSided(pose, buffers.getBuffer(RenderType.entityTranslucent(ZephyrarchVfx.RIBBON)),
                p0.add(side.scale(w)), p1.add(side.scale(w * 0.45F)),
                p1.subtract(side.scale(w * 0.45F)), p0.subtract(side.scale(w)),
                0.0F, 0.0F, 2.0F, 1.0F, 0.86F, 0.98F, 1.0F, 0.75F * fade);
        ZephyrarchVfx.quadTwoSided(pose, buffers.getBuffer(ZephyrarchVfx.additive(ZephyrarchVfx.RIBBON_GLOW)),
                p0.add(side.scale(w * 0.65F)), p1.add(side.scale(w * 0.3F)),
                p1.subtract(side.scale(w * 0.3F)), p0.subtract(side.scale(w * 0.65F)),
                0.0F, 0.0F, 2.0F, 1.0F, 0.65F * fade, 0.92F * fade, 1.0F * fade, 1.0F);
    }

    private static final class ActiveEffect {
        final int kind;
        final Vec3 pos;
        final Vec3 vec;
        final float scale;
        final int duration;
        int age;

        ActiveEffect(int kind, Vec3 pos, Vec3 vec, float scale, int duration) {
            this.kind = kind;
            this.pos = pos;
            this.vec = vec;
            this.scale = scale;
            this.duration = duration;
        }
    }
}
