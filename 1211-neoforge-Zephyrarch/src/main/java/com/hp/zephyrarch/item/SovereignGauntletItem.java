package com.hp.zephyrarch.item;

import com.hp.zephyrarch.client.ClientEvents;
import com.hp.zephyrarch.config.ZephyrarchConfig;
import com.hp.zephyrarch.network.GauntletNetwork;
import com.hp.zephyrarch.registry.ModRegistry;
import com.hp.zephyrarch.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class SovereignGauntletItem extends SwordItem implements GeoItem {
    public static final int SINGLE_CHARGE_TICKS = 14;
    public static final int DUAL_CHARGE_TICKS = 10;
    private static final int POST_PUNCH_RECOVERY_TICKS = 3;
    private static final int DASH_COOLDOWN_TICKS = 28;

    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("animation.sovereign_gauntlet.idle");
    private static final RawAnimation ANIM_CHARGE = RawAnimation.begin().thenPlay("animation.sovereign_gauntlet.charge");
    private static final RawAnimation ANIM_STRIKE = RawAnimation.begin().thenPlay("animation.sovereign_gauntlet.strike");

    private static final Map<UUID, ChargeState> SERVER_STATES = new ConcurrentHashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SovereignGauntletItem(Tier tier, Properties properties) {
        super(tier, properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public static boolean canHoldPunch(Player player) {
        boolean main = player.getMainHandItem().is(ModRegistry.SOVEREIGN_GAUNTLET.get());
        boolean off = player.getOffhandItem().is(ModRegistry.SOVEREIGN_GAUNTLET.get());
        return main || (off && player.getMainHandItem().isEmpty());
    }

    public static boolean isDualWielding(Player player) {
        return player.getMainHandItem().is(ModRegistry.SOVEREIGN_GAUNTLET.get())
                && player.getOffhandItem().is(ModRegistry.SOVEREIGN_GAUNTLET.get());
    }

    public static void setHoldingAttack(ServerPlayer player, boolean holding) {
        ChargeState state = SERVER_STATES.computeIfAbsent(player.getUUID(), k -> new ChargeState());
        state.holding = holding;
        if (!holding && state.charging) {
            cancelCharge(player, state);
        }
    }

    private static void cancelCharge(ServerPlayer player, ChargeState state) {
        InteractionHand prevHand = state.activeHand;
        state.charging = false;
        state.chargeTicks = 0;
        GauntletNetwork.broadcastAction(player, GauntletNetwork.ACTION_CHARGE_STOP, prevHand, 0, player.position(), Vec3.ZERO);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Vec3 look = player.getLookAngle();
        Vec3 dir = look.lengthSqr() > 1.0E-5D ? look.normalize() : Vec3.directionFromRotation(player.getXRot(), player.getYRot());

        Vec3 dashMotion = new Vec3(
                dir.x * 1.52D,
                Mth.clamp(dir.y * 0.95D + 0.22D, 0.20D, 1.15D),
                dir.z * 1.52D
        );
        player.setDeltaMovement(dashMotion);
        player.fallDistance = 0.0F;

        if (!level.isClientSide && level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
            ChargeState state = SERVER_STATES.get(sp.getUUID());
            if (state != null && state.charging) {
                cancelCharge(sp, state);
            }
            sp.hurtMarked = true;

            long animId = GeoItem.getOrAssignId(stack, serverLevel);
            triggerAnim(sp, animId, "action", "strike");

            serverLevel.playSound(null, sp.getX(), sp.getY(), sp.getZ(), ModSounds.WIND_DASH.get(), SoundSource.PLAYERS, 1.2F, 1.08F);
            serverLevel.playSound(null, sp.getX(), sp.getY(), sp.getZ(), ModSounds.RUSH_DASH.get(), SoundSource.PLAYERS, 0.85F, 1.22F);
            serverLevel.sendParticles(ParticleTypes.GUST, sp.getX(), sp.getY() + 0.9D, sp.getZ(), 3, 0.35D, 0.25D, 0.35D, 0.0D);

            GauntletNetwork.broadcastAction(sp, GauntletNetwork.ACTION_WIND_DASH, hand, 0, sp.position(), dir);

            EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(1, sp, slot);
        }

        player.getCooldowns().addCooldown(this, DASH_COOLDOWN_TICKS);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private void startCharge(ServerLevel serverLevel, ServerPlayer player, ChargeState state, boolean mainHas, boolean offHas) {
        boolean dual = mainHas && offHas;
        InteractionHand hand;
        if (dual) {
            hand = state.nextOffhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        } else {
            hand = mainHas ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            state.nextOffhand = false;
        }

        int maxTicks = dual ? DUAL_CHARGE_TICKS : SINGLE_CHARGE_TICKS;
        state.charging = true;
        state.activeHand = hand;
        state.chargeTicks = 0;
        state.maxTicks = maxTicks;

        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(this)) {
            long animId = GeoItem.getOrAssignId(stack, serverLevel);
            triggerAnim(player, animId, "action", "charge");
        }

        float pitch = dual ? (hand == InteractionHand.OFF_HAND ? 1.38F : 1.26F) : 1.12F;
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.RUSH_CHARGE.get(), SoundSource.PLAYERS, 0.65F, pitch);
        GauntletNetwork.broadcastAction(player, GauntletNetwork.ACTION_CHARGE_START, hand, maxTicks, player.position(), player.getLookAngle());
    }

    private void performChargedPunch(ServerLevel serverLevel, ServerPlayer player, InteractionHand hand, boolean dualWield) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(this)) {
            return;
        }

        Vec3 rawLook = player.getLookAngle();
        Vec3 dir = rawLook.lengthSqr() > 1.0E-5D ? rawLook.normalize() : Vec3.directionFromRotation(player.getXRot(), player.getYRot());
        Vec3 horiz = new Vec3(dir.x, 0.0D, dir.z);
        horiz = horiz.lengthSqr() > 1.0E-5D ? horiz.normalize() : Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 rightVec = new Vec3(-horiz.z, 0.0D, horiz.x);

        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        double sideSign = arm == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        Vec3 fistOrigin = player.getEyePosition()
                .add(0.0D, -0.26D, 0.0D)
                .add(rightVec.scale(0.42D * sideSign))
                .add(dir.scale(0.55D));

        double reach = 5.4D;
        double radius = 1.45D;
        Vec3 rayStart = player.getEyePosition().add(0.0D, -0.25D, 0.0D);
        Vec3 rayEnd = rayStart.add(dir.scale(reach));
        AABB searchBox = new AABB(rayStart, rayEnd).inflate(radius + 0.85D);
        List<LivingEntity> candidates = serverLevel.getEntitiesOfClass(LivingEntity.class, searchBox, e -> canPlayerSkillHit(player, e));

        boolean closeTargetAhead = false;
        for (LivingEntity candidate : candidates) {
            if (player.distanceToSqr(candidate) <= 5.6D) {
                Vec3 to = candidate.getBoundingBox().getCenter().subtract(rayStart);
                if (to.lengthSqr() < 1.0E-4D || to.normalize().dot(dir) > 0.15D) {
                    closeTargetAhead = true;
                    break;
                }
            }
        }

        double lungeSpeed = closeTargetAhead ? 0.22D : 0.78D;
        double lungeY = Math.max(player.getDeltaMovement().y * 0.35D, 0.11D + Mth.clamp(dir.y * 0.34D, -0.06D, 0.28D));
        player.setDeltaMovement(horiz.x * lungeSpeed, lungeY, horiz.z * lungeSpeed);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;

        double baseAttack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float punchDamage = Math.max(6.0F, (float) (baseAttack * (dualWield ? 1.55D : 1.85D)));
        double kbScale = ZephyrarchConfig.KNOCKBACK_SCALE.get();

        boolean hitAny = false;
        for (LivingEntity victim : candidates) {
            AABB targetBox = victim.getBoundingBox().inflate(radius);
            if (!targetBox.contains(rayStart) && targetBox.clip(rayStart, rayEnd).isEmpty()) {
                continue;
            }
            if (victim.invulnerableTime <= 12) {
                victim.invulnerableTime = 0;
            }
            if (victim.hurt(player.damageSources().playerAttack(player), punchDamage)) {
                hitAny = true;
            }
            double resist = Math.max(0.0D, 1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            double push = (dualWield ? 0.48D : 0.62D) * resist * kbScale;
            double lift = (dualWield ? 0.18D : 0.24D) * resist * kbScale;
            if (push > 0.01D || lift > 0.01D) {
                victim.push(horiz.x * push, lift, horiz.z * push);
                victim.hurtMarked = true;
            }
            serverLevel.sendParticles(ParticleTypes.SMALL_GUST, victim.getX(), victim.getY(0.55D), victim.getZ(), 5, 0.25D, 0.25D, 0.25D, 0.03D);
        }

        long animId = GeoItem.getOrAssignId(stack, serverLevel);
        triggerAnim(player, animId, "action", "strike");

        float swingPitch = dualWield ? (hand == InteractionHand.OFF_HAND ? 1.18F : 1.04F) : 0.96F;
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.PUNCH_SWING.get(), SoundSource.PLAYERS, 1.15F, swingPitch);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.90F, 1.24F);
        if (hitAny) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.PUNCH_IMPACT.get(), SoundSource.PLAYERS, 1.25F, swingPitch);
        }

        GauntletNetwork.broadcastAction(player, GauntletNetwork.ACTION_PUNCH_FIRE, hand, 0, fistOrigin, dir);

        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        stack.hurtAndBreak(1, player, slot);
    }

    private static boolean canPlayerSkillHit(Player player, LivingEntity candidate) {
        if (candidate == player || !candidate.isAlive() || candidate instanceof ArmorStand || player.isAlliedTo(candidate)) {
            return false;
        }
        if (candidate instanceof Player other && (other.isCreative() || other.isSpectator() || !player.canHarmPlayer(other))) {
            return false;
        }
        if (candidate instanceof OwnableEntity ownable && player.getUUID().equals(ownable.getOwnerUUID())) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.is(ModRegistry.ZEPHYR_CORE.get()) || super.isValidRepairItem(toRepair, repair);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.zephyrarch.sovereign_gauntlet.desc1").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.zephyrarch.sovereign_gauntlet.desc2").withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            consumer.accept(ClientEvents.createGauntletRenderProvider());
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "rings", 0, state -> state.setAndContinue(ANIM_IDLE)));
        controllers.add(new AnimationController<>(this, "action", 1, state -> PlayState.STOP)
                .triggerableAnim("charge", ANIM_CHARGE)
                .triggerableAnim("strike", ANIM_STRIKE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    private static final class ChargeState {
        boolean holding;
        boolean charging;
        InteractionHand activeHand = InteractionHand.MAIN_HAND;
        int chargeTicks;
        int maxTicks = SINGLE_CHARGE_TICKS;
        int recoveryTicks;
        boolean nextOffhand;
    }

    public static final class Events {
        @SubscribeEvent
        public static void onLivingFall(LivingFallEvent event) {
            LivingEntity entity = event.getEntity();
            if (entity.getMainHandItem().is(ModRegistry.SOVEREIGN_GAUNTLET.get())
                    || entity.getOffhandItem().is(ModRegistry.SOVEREIGN_GAUNTLET.get())) {
                event.setDistance(0.0F);
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            SERVER_STATES.remove(event.getEntity().getUUID());
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            Player rawPlayer = event.getEntity();
            if (rawPlayer.level().isClientSide || !(rawPlayer instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel serverLevel)) {
                return;
            }

            ChargeState state = SERVER_STATES.get(sp.getUUID());
            if (state == null) {
                return;
            }

            if (state.recoveryTicks > 0) {
                state.recoveryTicks--;
            }

            if (!sp.isAlive() || sp.isSpectator() || sp.isUsingItem() || !canHoldPunch(sp)) {
                if (state.charging) {
                    cancelCharge(sp, state);
                }
                state.holding = false;
                return;
            }

            if (!state.holding) {
                if (state.charging) {
                    cancelCharge(sp, state);
                }
                return;
            }

            if (!(ModRegistry.SOVEREIGN_GAUNTLET.get() instanceof SovereignGauntletItem gauntletItem)) {
                return;
            }

            boolean mainHas = sp.getMainHandItem().is(gauntletItem);
            boolean offHas = sp.getOffhandItem().is(gauntletItem);

            if (!state.charging) {
                if (state.recoveryTicks <= 0) {
                    gauntletItem.startCharge(serverLevel, sp, state, mainHas, offHas);
                }
                return;
            }

            if (!sp.getItemInHand(state.activeHand).is(gauntletItem)) {
                cancelCharge(sp, state);
                return;
            }

            state.chargeTicks++;
            if (state.chargeTicks >= state.maxTicks) {
                InteractionHand firedHand = state.activeHand;
                boolean dualNow = mainHas && offHas;
                state.charging = false;
                state.chargeTicks = 0;
                state.recoveryTicks = POST_PUNCH_RECOVERY_TICKS;
                state.nextOffhand = dualNow && (firedHand == InteractionHand.MAIN_HAND);
                gauntletItem.performChargedPunch(serverLevel, sp, firedHand, dualNow);
            }
        }
    }
}
