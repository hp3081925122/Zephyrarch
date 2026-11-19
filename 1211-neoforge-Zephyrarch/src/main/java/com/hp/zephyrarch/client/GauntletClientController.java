package com.hp.zephyrarch.client;

import com.hp.zephyrarch.Zephyrarch;
import com.hp.zephyrarch.item.SovereignGauntletItem;
import com.hp.zephyrarch.network.GauntletNetwork;
import com.hp.zephyrarch.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@EventBusSubscriber(modid = Zephyrarch.MOD_ID, value = Dist.CLIENT)
public final class GauntletClientController {
    private static final int PUNCH_ANIM_DURATION = 9;
    private static final int DASH_ANIM_DURATION = 11;
    private static final int PUNCH_VFX_DURATION = 11;
    private static final int DASH_VFX_DURATION = 13;

    public static final EnumProxy<HumanoidModel.ArmPose> GAUNTLET_ARM_POSE_PROXY = new EnumProxy<>(
            HumanoidModel.ArmPose.class,
            false,
            (IArmPoseTransformer) GauntletClientController::transformArmPose
    );

    public static final IClientItemExtensions ITEM_EXTENSIONS = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entityLiving, InteractionHand hand, ItemStack itemStack) {
            return GAUNTLET_ARM_POSE_PROXY.getValue();
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack itemInHand,
                                               float partialTick, float equipProcess, float swingProcess) {
            return GauntletClientController.applyFirstPersonTransform(poseStack, player, arm, partialTick, equipProcess);
        }
    };

    private static final Int2ObjectMap<PlayerAnimState> PLAYER_STATES = new Int2ObjectOpenHashMap<>();
    private static final List<WorldBurst> WORLD_BURSTS = new ArrayList<>();
    private static boolean lastSentHolding = false;
    private static int holdResyncTicker = 0;

    private GauntletClientController() {
    }

    public static void handleAction(GauntletNetwork.ActionPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        InteractionHand hand = payload.handOrd() == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        PlayerAnimState state = PLAYER_STATES.computeIfAbsent(payload.entityId(), k -> new PlayerAnimState());
        Entity entity = mc.level.getEntity(payload.entityId());

        switch (payload.action()) {
            case GauntletNetwork.ACTION_CHARGE_START -> {
                state.charging = true;
                state.chargeHand = hand;
                state.chargeTicks = 0;
                state.maxChargeTicks = Math.max(1, payload.maxChargeTicks());
            }
            case GauntletNetwork.ACTION_CHARGE_STOP -> {
                state.charging = false;
                state.chargeTicks = 0;
            }
            case GauntletNetwork.ACTION_PUNCH_FIRE -> {
                state.charging = false;
                state.chargeTicks = 0;
                HumanoidArm firedArm = resolveArm(entity, hand);
                if (firedArm == HumanoidArm.RIGHT) {
                    state.rightPunchTicks = 0;
                } else {
                    state.leftPunchTicks = 0;
                }
                Vec3 pos = new Vec3(payload.x(), payload.y(), payload.z());
                Vec3 dir = new Vec3(payload.dx(), payload.dy(), payload.dz());
                if (dir.lengthSqr() < 1.0E-5D) {
                    dir = new Vec3(0.0D, 0.0D, 1.0D);
                } else {
                    dir = dir.normalize();
                }
                boolean rightArm = firedArm == HumanoidArm.RIGHT;
                spawnBurst(new WorldBurst(true, payload.entityId(), rightArm, pos, dir, PUNCH_VFX_DURATION));
            }
            case GauntletNetwork.ACTION_WIND_DASH -> {
                state.charging = false;
                state.chargeTicks = 0;
                state.dashTicks = 0;
                Vec3 pos = new Vec3(payload.x(), payload.y(), payload.z());
                Vec3 dir = new Vec3(payload.dx(), payload.dy(), payload.dz());
                if (dir.lengthSqr() < 1.0E-5D) {
                    dir = new Vec3(0.0D, 0.0D, 1.0D);
                } else {
                    dir = dir.normalize();
                }
                state.dashDir = dir;
                spawnBurst(new WorldBurst(false, payload.entityId(), true, pos, dir, DASH_VFX_DURATION));
            }
            default -> {
            }
        }
    }

    private static void spawnBurst(WorldBurst burst) {
        if (WORLD_BURSTS.size() >= 96) {
            WORLD_BURSTS.removeFirst();
        }
        WORLD_BURSTS.add(burst);
    }

    private static HumanoidArm resolveArm(Entity entity, InteractionHand hand) {
        if (entity instanceof LivingEntity living) {
            HumanoidArm main = living.getMainArm();
            return hand == InteractionHand.MAIN_HAND ? main : main.getOpposite();
        }
        return hand == InteractionHand.MAIN_HAND ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && SovereignGauntletItem.canHoldPunch(mc.player)) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            PLAYER_STATES.clear();
            WORLD_BURSTS.clear();
            lastSentHolding = false;
            holdResyncTicker = 0;
            return;
        }

        boolean wantHold = mc.screen == null
                && mc.options.keyAttack.isDown()
                && SovereignGauntletItem.canHoldPunch(mc.player)
                && !mc.player.isUsingItem()
                && !mc.player.isSpectator();

        if (wantHold != lastSentHolding) {
            lastSentHolding = wantHold;
            holdResyncTicker = 0;
            PacketDistributor.sendToServer(new GauntletNetwork.HoldPayload(wantHold));
        } else if (wantHold) {
            holdResyncTicker++;
            if (holdResyncTicker >= 15) {
                holdResyncTicker = 0;
                PlayerAnimState local = PLAYER_STATES.get(mc.player.getId());
                if (local == null || (!local.charging && local.rightPunchTicks < 0 && local.leftPunchTicks < 0)) {
                    PacketDistributor.sendToServer(new GauntletNetwork.HoldPayload(true));
                }
            }
        }

        if (mc.isPaused()) {
            return;
        }

        Iterator<Int2ObjectMap.Entry<PlayerAnimState>> stateIt = PLAYER_STATES.int2ObjectEntrySet().iterator();
        while (stateIt.hasNext()) {
            Int2ObjectMap.Entry<PlayerAnimState> entry = stateIt.next();
            Entity ent = mc.level.getEntity(entry.getIntKey());
            if (!(ent instanceof LivingEntity living) || !living.isAlive()) {
                stateIt.remove();
                continue;
            }
            PlayerAnimState st = entry.getValue();
            if (st.charging) {
                ItemStack held = living.getItemInHand(st.chargeHand);
                if (!held.is(ModRegistry.SOVEREIGN_GAUNTLET.get())) {
                    st.charging = false;
                    st.chargeTicks = 0;
                } else if (st.chargeTicks < st.maxChargeTicks + 4) {
                    st.chargeTicks++;
                }
            }
            if (st.rightPunchTicks >= 0) {
                st.rightPunchTicks++;
                if (st.rightPunchTicks >= PUNCH_ANIM_DURATION) {
                    st.rightPunchTicks = -1;
                }
            }
            if (st.leftPunchTicks >= 0) {
                st.leftPunchTicks++;
                if (st.leftPunchTicks >= PUNCH_ANIM_DURATION) {
                    st.leftPunchTicks = -1;
                }
            }
            if (st.dashTicks >= 0) {
                st.dashTicks++;
                if (st.dashTicks >= DASH_ANIM_DURATION) {
                    st.dashTicks = -1;
                }
            }
            if (!st.charging && st.rightPunchTicks < 0 && st.leftPunchTicks < 0 && st.dashTicks < 0) {
                stateIt.remove();
            }
        }

        Iterator<WorldBurst> burstIt = WORLD_BURSTS.iterator();
        while (burstIt.hasNext()) {
            WorldBurst b = burstIt.next();
            b.age++;
            if (b.age >= b.duration) {
                burstIt.remove();
            }
        }
    }

    private static boolean applyFirstPersonTransform(PoseStack ps, LocalPlayer player, HumanoidArm arm, float partialTick, float equipProcess) {
        float sign = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        PlayerAnimState state = PLAYER_STATES.get(player.getId());
        boolean activeAnim = state != null && (state.charging || state.rightPunchTicks >= 0 || state.leftPunchTicks >= 0 || state.dashTicks >= 0);
        float equipDip = activeAnim ? 0.0F : equipProcess * -0.6F;

        float time = player.tickCount + partialTick;
        float idleBobY = Mth.sin(time * 0.10F) * 0.012F;
        float idleBobX = Mth.cos(time * 0.05F) * 0.008F * sign;

        ps.translate(sign * 0.48F + idleBobX, -0.46F + equipDip + idleBobY, -0.68F);

        if (state == null) {
            return true;
        }

        int punchTicks = arm == HumanoidArm.RIGHT ? state.rightPunchTicks : state.leftPunchTicks;
        if (punchTicks >= 0) {
            float age = punchTicks + partialTick;
            float thrust;
            if (age < 1.8F) {
                float t = age / 1.8F;
                thrust = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            } else if (age < 3.2F) {
                thrust = 1.0F - (age - 1.8F) * 0.04F;
            } else {
                float t = Mth.clamp((age - 3.2F) / (PUNCH_ANIM_DURATION - 3.2F), 0.0F, 1.0F);
                thrust = (1.0F - t) * (1.0F - t) * 0.94F;
            }

            float shockJitter = (age >= 1.4F && age <= 3.6F) ? Mth.sin(age * 16.0F) * 0.014F : 0.0F;
            ps.translate(-sign * 0.28F * thrust + shockJitter, 0.14F * thrust, -0.62F * thrust);
            ps.mulPose(Axis.YP.rotationDegrees(sign * 8.0F * thrust));
            ps.mulPose(Axis.XP.rotationDegrees(-20.0F * thrust));
            ps.mulPose(Axis.ZP.rotationDegrees(sign * 34.0F * thrust));
            return true;
        }

        if (state.charging) {
            HumanoidArm chargingArm = resolveArm(player, state.chargeHand);
            float chargeRatio = Mth.clamp((state.chargeTicks + partialTick) / (float) state.maxChargeTicks, 0.0F, 1.0F);
            float ease = 1.0F - (1.0F - chargeRatio) * (1.0F - chargeRatio);
            if (chargingArm == arm) {
                float tremor = Mth.sin(time * 2.4F) * 0.014F * chargeRatio * chargeRatio;
                ps.translate(sign * 0.10F * ease + tremor, -0.06F * ease + tremor * 0.6F, 0.26F * ease);
                ps.mulPose(Axis.YP.rotationDegrees(-sign * 10.0F * ease));
                ps.mulPose(Axis.XP.rotationDegrees(16.0F * ease));
                ps.mulPose(Axis.ZP.rotationDegrees(-sign * 18.0F * ease));
                return true;
            } else {
                ps.translate(sign * 0.04F * ease, -0.06F * ease, 0.06F * ease);
                ps.mulPose(Axis.XP.rotationDegrees(-6.0F * ease));
                return true;
            }
        }

        if (state.dashTicks >= 0) {
            float k = Mth.clamp((state.dashTicks + partialTick) / DASH_ANIM_DURATION, 0.0F, 1.0F);
            float wave = Mth.sin(k * (float) Math.PI);
            ps.translate(-sign * 0.16F * wave, 0.10F * wave, -0.26F * wave);
            ps.mulPose(Axis.XP.rotationDegrees(-16.0F * wave));
            ps.mulPose(Axis.YP.rotationDegrees(sign * 18.0F * wave));
            ps.mulPose(Axis.ZP.rotationDegrees(sign * 24.0F * wave));
        }

        return true;
    }

    private static void transformArmPose(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        ModelPart armPart = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float sign = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float pitch = Mth.clamp(model.head.xRot, -1.15F, 1.15F);
        float headYaw = Mth.clamp(model.head.yRot, -0.85F, 0.85F);

        armPart.xRot = armPart.xRot * 0.40F - 0.66F;
        armPart.yRot = -sign * 0.22F;
        armPart.zRot = sign * 0.12F;

        PlayerAnimState state = PLAYER_STATES.get(entity.getId());
        if (state == null) {
            return;
        }

        int punchTicks = arm == HumanoidArm.RIGHT ? state.rightPunchTicks : state.leftPunchTicks;
        if (punchTicks >= 0) {
            float age = punchTicks + partialTick;
            float thrust;
            if (age < 1.8F) {
                float t = age / 1.8F;
                thrust = 1.0F - (1.0F - t) * (1.0F - t);
            } else if (age < 3.4F) {
                thrust = 1.0F;
            } else {
                float t = Mth.clamp((age - 3.4F) / (PUNCH_ANIM_DURATION - 3.4F), 0.0F, 1.0F);
                thrust = (1.0F - t) * (1.0F - t);
            }

            float targetX = -1.56F + pitch;
            float targetY = -sign * 0.16F + headYaw * 0.65F;
            float targetZ = -sign * 0.48F;

            armPart.xRot = Mth.lerp(thrust, armPart.xRot, targetX);
            armPart.yRot = Mth.lerp(thrust, armPart.yRot, targetY);
            armPart.zRot = Mth.lerp(thrust, armPart.zRot, targetZ);
            model.body.yRot = -sign * 0.34F * thrust;
            syncShoulderPivots(model, armPart, -0.85F * thrust);
            return;
        }

        if (state.charging) {
            HumanoidArm chargingArm = resolveArm(entity, state.chargeHand);
            float ratio = Mth.clamp((state.chargeTicks + partialTick) / (float) state.maxChargeTicks, 0.0F, 1.0F);
            float ease = 1.0F - (1.0F - ratio) * (1.0F - ratio);
            if (chargingArm == arm) {
                float tremor = Mth.sin((entity.tickCount + partialTick) * 2.5F) * 0.045F * ratio;
                armPart.xRot = Mth.lerp(ease, armPart.xRot, 0.42F + pitch * 0.30F) + tremor;
                armPart.yRot = Mth.lerp(ease, armPart.yRot, sign * 0.38F);
                armPart.zRot = Mth.lerp(ease, armPart.zRot, sign * 0.32F) + tremor;
                model.body.yRot = sign * 0.28F * ease;
                syncShoulderPivots(model, armPart, 0.55F * ease);
                if (entity instanceof Player p && !SovereignGauntletItem.isDualWielding(p)) {
                    ModelPart otherArm = arm == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;
                    otherArm.xRot = Mth.lerp(ease, otherArm.xRot, -1.05F + pitch * 0.45F);
                    otherArm.yRot = Mth.lerp(ease, otherArm.yRot, sign * 0.32F);
                }
                return;
            } else {
                armPart.xRot = Mth.lerp(ease, armPart.xRot, -1.12F + pitch * 0.45F);
                armPart.yRot = Mth.lerp(ease, armPart.yRot, -sign * 0.30F);
                return;
            }
        }

        if (state.dashTicks >= 0) {
            float k = Mth.clamp((state.dashTicks + partialTick) / DASH_ANIM_DURATION, 0.0F, 1.0F);
            float wave = Mth.sin(k * (float) Math.PI);
            armPart.xRot = Mth.lerp(wave, armPart.xRot, 0.58F);
            armPart.yRot = Mth.lerp(wave, armPart.yRot, sign * 0.18F);
            armPart.zRot = Mth.lerp(wave, armPart.zRot, sign * 0.34F);
        }
    }

    private static void syncShoulderPivots(HumanoidModel<?> model, ModelPart activeArm, float extraZ) {
        float by = model.body.yRot;
        model.rightArm.z = Mth.sin(by) * 5.0F;
        model.rightArm.x = -Mth.cos(by) * 5.0F;
        model.leftArm.z = -Mth.sin(by) * 5.0F;
        model.leftArm.x = Mth.cos(by) * 5.0F;
        activeArm.z += extraZ;
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (PLAYER_STATES.isEmpty() && WORLD_BURSTS.isEmpty()) {
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
        boolean firstPersonSelf = mc.options.getCameraType().isFirstPerson();
        int selfId = mc.player != null ? mc.player.getId() : -1;

        ps.pushPose();
        ps.translate(-camPos.x, -camPos.y, -camPos.z);

        for (Int2ObjectMap.Entry<PlayerAnimState> entry : PLAYER_STATES.int2ObjectEntrySet()) {
            Entity ent = mc.level.getEntity(entry.getIntKey());
            if (!(ent instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            PlayerAnimState state = entry.getValue();
            boolean isFirstPersonOwner = firstPersonSelf && entry.getIntKey() == selfId;
            if (state.charging) {
                renderChargingFistVfx(living, state, partialTick, isFirstPersonOwner, ps, buffers, camRot);
            }
            if (state.dashTicks >= 0 && !isFirstPersonOwner) {
                renderDashBodyAura(living, state, partialTick, ps, buffers);
            }
        }

        for (WorldBurst burst : WORLD_BURSTS) {
            float age = burst.age + partialTick;
            if (age > burst.duration) {
                continue;
            }
            boolean isFirstPersonOwner = firstPersonSelf && burst.ownerId == selfId;
            if (burst.isPunch) {
                renderPunchConeVfx(burst, age, isFirstPersonOwner, ps, buffers, camRot, camPos);
            } else {
                renderDashLaunchVfx(burst, age, isFirstPersonOwner, ps, buffers, camRot);
            }
        }

        ps.popPose();

        buffers.endBatch(RenderType.entityTranslucent(ZephyrarchVfx.RING));
        buffers.endBatch(RenderType.entityTranslucent(ZephyrarchVfx.RIBBON));
        buffers.endBatch(ZephyrarchVfx.additive(ZephyrarchVfx.RING_GLOW));
        buffers.endBatch(ZephyrarchVfx.additive(ZephyrarchVfx.RIBBON_GLOW));
        buffers.endBatch(ZephyrarchVfx.additive(ZephyrarchVfx.FLARE));
    }

    private static void renderChargingFistVfx(LivingEntity living, PlayerAnimState state, float partialTick, boolean firstPersonOwner,
                                              PoseStack ps, MultiBufferSource buffers, Quaternionf camRot) {
        float ratio = Mth.clamp((state.chargeTicks + partialTick) / (float) state.maxChargeTicks, 0.0F, 1.0F);
        HumanoidArm arm = resolveArm(living, state.chargeHand);
        double sign = arm == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        float time = living.tickCount + partialTick;

        Vec3 fistWorld;
        Vec3 look = living.getViewVector(partialTick).normalize();
        Vec3 horiz = new Vec3(look.x, 0.0D, look.z);
        horiz = horiz.lengthSqr() > 1.0E-5D ? horiz.normalize() : Vec3.directionFromRotation(0.0F, living.getViewYRot(partialTick));
        Vec3 right = new Vec3(-horiz.z, 0.0D, horiz.x);

        if (firstPersonOwner) {
            Vec3 up = right.cross(look).normalize();
            fistWorld = living.getEyePosition(partialTick)
                    .add(look.scale(0.62D - 0.14D * ratio))
                    .add(right.scale(sign * (0.36D + 0.05D * ratio)))
                    .add(up.scale(-0.24D - 0.04D * ratio));
        } else {
            float bodyYaw = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
            Vec3 bodyFwd = Vec3.directionFromRotation(0.0F, bodyYaw);
            Vec3 bodyRight = new Vec3(-bodyFwd.z, 0.0D, bodyFwd.x);
            fistWorld = living.getPosition(partialTick)
                    .add(0.0D, living.getBbHeight() * 0.62D, 0.0D)
                    .add(bodyRight.scale(sign * 0.42D))
                    .add(bodyFwd.scale(0.18D - 0.36D * ratio));
        }

        float scaleMul = firstPersonOwner ? 0.52F : 1.0F;
        float contractR = (0.62F - 0.30F * ratio) * scaleMul;
        Quaternionf align = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) look.x, (float) look.y, (float) look.z);

        ps.pushPose();
        ps.translate(fistWorld.x, fistWorld.y, fistWorld.z);
        ps.mulPose(align);
        ZephyrarchVfx.drawFlatRing(ps, buffers, 0.0D, contractR, time * (22.0F + 24.0F * ratio) * (float) sign,
                0.82F, 0.98F, 1.0F, 0.78F * ratio,
                0.65F * ratio, 0.94F * ratio, 1.0F * ratio);
        ZephyrarchVfx.drawFlatRing(ps, buffers, 0.03D, contractR * 0.68F, -time * 34.0F * (float) sign,
                1.0F, 0.94F, 0.62F, 0.72F * ratio,
                0.98F * ratio, 0.88F * ratio, 0.45F * ratio);
        if (!firstPersonOwner) {
            ZephyrarchVfx.drawFunnelWall(ps, buffers, -0.18D, 0.22D, contractR * 1.15F, contractR * 0.45F, -time * 0.35F,
                    0.82F, 0.98F, 1.0F, 0.52F * ratio,
                    0.55F * ratio, 0.90F * ratio, 1.0F * ratio);
        }
        ps.popPose();

        ps.pushPose();
        ps.translate(fistWorld.x, fistWorld.y, fistWorld.z);
        ZephyrarchVfx.drawBillboardFlare(ps, buffers, camRot, Vec3.ZERO, (0.18F + 0.36F * ratio) * scaleMul, time * 16.0F * (float) sign,
                0.95F * ratio, 0.94F * ratio, (0.62F + 0.38F * (1.0F - ratio)) * ratio);
        ps.popPose();
    }

    private static void renderPunchConeVfx(WorldBurst burst, float age, boolean firstPersonOwner,
                                           PoseStack ps, MultiBufferSource buffers, Quaternionf camRot, Vec3 camPos) {
        float k = Mth.clamp(age / burst.duration, 0.0F, 1.0F);
        float ease = 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k);
        float fade = 1.0F - k * k;
        float spinSign = burst.rightArm ? 1.0F : -1.0F;

        Vec3 dir = burst.dir;
        Vec3 startOrigin = firstPersonOwner ? burst.pos.add(dir.scale(0.38D)) : burst.pos;
        Quaternionf align = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) dir.x, (float) dir.y, (float) dir.z);

        ps.pushPose();
        ps.translate(startOrigin.x, startOrigin.y, startOrigin.z);

        if (k < 0.45F) {
            float fl = 1.0F - k / 0.45F;
            float flareSize = (firstPersonOwner ? 0.42F : 0.88F) * fl;
            ZephyrarchVfx.drawBillboardFlare(ps, buffers, camRot, Vec3.ZERO, flareSize, age * 22.0F * spinSign,
                    fl, 0.96F * fl, 0.68F * fl);
        }

        ps.mulPose(align);

        double coneStart = 0.15D + 0.85D * k;
        double coneEnd = 1.25D + 3.85D * ease;
        float baseR = 0.24F + 0.16F * ease;
        float tipR = 0.62F + 0.72F * ease;

        ZephyrarchVfx.drawFunnelWall(ps, buffers, coneStart, coneEnd, baseR, tipR, -age * 0.58F * spinSign,
                0.80F, 0.98F, 1.0F, 0.78F * fade,
                0.58F * fade, 0.92F * fade, 1.0F * fade);

        ZephyrarchVfx.drawFunnelWall(ps, buffers, coneStart * 0.8D, coneEnd * 0.92D, baseR * 0.52F, tipR * 0.42F, age * 0.75F * spinSign,
                1.0F, 0.95F, 0.68F, 0.84F * fade,
                0.98F * fade, 0.90F * fade, 0.50F * fade);

        double ring1Dist = 0.45D + 2.45D * ease;
        float ring1R = 0.48F + 0.95F * ease;
        ZephyrarchVfx.drawFlatRing(ps, buffers, ring1Dist, ring1R, age * 32.0F * spinSign,
                0.90F, 0.99F, 1.0F, 0.90F * fade,
                0.76F * fade, 0.95F * fade, 1.0F * fade);

        double ring2Dist = 0.85D + 3.75D * ease;
        float ring2R = 0.36F + 0.68F * ease;
        ZephyrarchVfx.drawFlatRing(ps, buffers, ring2Dist, ring2R, -age * 38.0F * spinSign,
                1.0F, 0.94F, 0.66F, 0.85F * fade,
                0.96F * fade, 0.88F * fade, 0.48F * fade);

        ps.popPose();

        Vec3 upRef = Math.abs(dir.y) > 0.92D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 axisA = dir.cross(upRef).normalize();
        Vec3 axisB = dir.cross(axisA).normalize();
        PoseStack.Pose worldPose = ps.last();
        VertexConsumer vcRibbon = buffers.getBuffer(RenderType.entityTranslucent(ZephyrarchVfx.RIBBON));
        for (int i = 0; i < 4; i++) {
            double ang = (Math.PI * 0.5D * i) + (age * 0.28D * spinSign);
            Vec3 radial = axisA.scale(Math.cos(ang)).add(axisB.scale(Math.sin(ang)));
            Vec3 p0 = startOrigin.add(dir.scale(coneStart)).add(radial.scale(baseR * 0.85D));
            Vec3 p1 = startOrigin.add(dir.scale(coneEnd * 1.04D)).add(radial.scale(tipR * 0.92D));
            Vec3 side = dir.cross(camPos.subtract(p0.add(p1).scale(0.5D)));
            side = side.lengthSqr() < 1.0E-5D ? radial : side.normalize();
            float w = 0.16F * fade;

            ZephyrarchVfx.quadTwoSided(worldPose, vcRibbon,
                    p0.add(side.scale(w)), p1.add(side.scale(w * 0.35F)),
                    p1.subtract(side.scale(w * 0.35F)), p0.subtract(side.scale(w)),
                    0.0F, 0.0F, 1.8F, 1.0F, 0.88F, 0.99F, 1.0F, 0.82F * fade);
        }

        VertexConsumer vcGlow = buffers.getBuffer(ZephyrarchVfx.additive(ZephyrarchVfx.RIBBON_GLOW));
        for (int i = 0; i < 4; i++) {
            double ang = (Math.PI * 0.5D * i) + (age * 0.28D * spinSign);
            Vec3 radial = axisA.scale(Math.cos(ang)).add(axisB.scale(Math.sin(ang)));
            Vec3 p0 = startOrigin.add(dir.scale(coneStart)).add(radial.scale(baseR * 0.85D));
            Vec3 p1 = startOrigin.add(dir.scale(coneEnd * 1.04D)).add(radial.scale(tipR * 0.92D));
            Vec3 side = dir.cross(camPos.subtract(p0.add(p1).scale(0.5D)));
            side = side.lengthSqr() < 1.0E-5D ? radial : side.normalize();
            float w = 0.16F * fade;

            ZephyrarchVfx.quadTwoSided(worldPose, vcGlow,
                    p0.add(side.scale(w * 0.65F)), p1.add(side.scale(w * 0.25F)),
                    p1.subtract(side.scale(w * 0.25F)), p0.subtract(side.scale(w * 0.65F)),
                    0.0F, 0.0F, 1.8F, 1.0F, 0.80F * fade, 0.95F * fade, 1.0F * fade, 1.0F);
        }
    }

    private static void renderDashLaunchVfx(WorldBurst burst, float age, boolean firstPersonOwner,
                                            PoseStack ps, MultiBufferSource buffers, Quaternionf camRot) {
        float k = Mth.clamp(age / burst.duration, 0.0F, 1.0F);
        float ease = 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k);
        float fade = 1.0F - k;
        Vec3 dir = burst.dir;
        Vec3 center = burst.pos.add(0.0D, 0.95D, 0.0D);
        Quaternionf align = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) dir.x, (float) dir.y, (float) dir.z);

        ps.pushPose();
        ps.translate(center.x, center.y, center.z);
        if (!firstPersonOwner && k < 0.45F) {
            float fl = 1.0F - k / 0.45F;
            ZephyrarchVfx.drawBillboardFlare(ps, buffers, camRot, Vec3.ZERO, 0.95F * fl, age * 18.0F,
                    0.82F * fl, 0.96F * fl, fl);
        }
        ps.mulPose(align);

        float ringR = 0.65F + 1.65F * ease;
        ZephyrarchVfx.drawFlatRing(ps, buffers, 0.0D, ringR, age * 28.0F,
                0.86F, 0.98F, 1.0F, 0.88F * fade,
                0.72F * fade, 0.94F * fade, 1.0F * fade);
        ZephyrarchVfx.drawFlatRing(ps, buffers, 0.45D + 1.1D * ease, ringR * 0.68F, -age * 32.0F,
                1.0F, 0.95F, 0.68F, 0.76F * fade,
                0.92F * fade, 0.88F * fade, 0.48F * fade);

        double wakeStart = 0.15D + 0.9D * k;
        double wakeEnd = 0.9D + 3.6D * ease;
        ZephyrarchVfx.drawFunnelWall(ps, buffers, wakeStart, wakeEnd, 0.95F * fade + 0.25F, 0.32F * fade + 0.12F, -age * 0.52F,
                0.82F, 0.98F, 1.0F, 0.68F * fade,
                0.58F * fade, 0.90F * fade, 1.0F * fade);
        ps.popPose();
    }

    private static void renderDashBodyAura(LivingEntity living, PlayerAnimState state, float partialTick,
                                           PoseStack ps, MultiBufferSource buffers) {
        float age = state.dashTicks + partialTick;
        float k = Mth.clamp(age / DASH_ANIM_DURATION, 0.0F, 1.0F);
        float fade = Mth.sin(k * (float) Math.PI);
        Vec3 vel = living.getDeltaMovement();
        Vec3 dir = vel.lengthSqr() > 0.04D ? vel.normalize() : state.dashDir;
        Quaternionf align = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) dir.x, (float) dir.y, (float) dir.z);
        Vec3 bodyCenter = living.getPosition(partialTick).add(0.0D, living.getBbHeight() * 0.52D, 0.0D);

        ps.pushPose();
        ps.translate(bodyCenter.x, bodyCenter.y, bodyCenter.z);
        ps.mulPose(align);
        ZephyrarchVfx.drawFunnelWall(ps, buffers, -1.05D, 0.95D, 0.88F, 0.22F, -age * 0.62F,
                0.84F, 0.98F, 1.0F, 0.68F * fade,
                0.62F * fade, 0.92F * fade, 1.0F * fade);
        ZephyrarchVfx.drawFlatRing(ps, buffers, 0.25D, 0.72F, age * 36.0F,
                0.92F, 0.99F, 1.0F, 0.75F * fade,
                0.78F * fade, 0.95F * fade, 1.0F * fade);
        ps.popPose();
    }

    private static final class PlayerAnimState {
        boolean charging;
        InteractionHand chargeHand = InteractionHand.MAIN_HAND;
        int chargeTicks;
        int maxChargeTicks = SovereignGauntletItem.SINGLE_CHARGE_TICKS;
        int rightPunchTicks = -1;
        int leftPunchTicks = -1;
        int dashTicks = -1;
        Vec3 dashDir = new Vec3(0.0D, 0.0D, 1.0D);
    }

    private static final class WorldBurst {
        final boolean isPunch;
        final int ownerId;
        final boolean rightArm;
        final Vec3 pos;
        final Vec3 dir;
        final int duration;
        int age;

        WorldBurst(boolean isPunch, int ownerId, boolean rightArm, Vec3 pos, Vec3 dir, int duration) {
            this.isPunch = isPunch;
            this.ownerId = ownerId;
            this.rightArm = rightArm;
            this.pos = pos;
            this.dir = dir;
            this.duration = duration;
        }
    }
}
