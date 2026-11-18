package com.hp.zephyrarch.item;

import com.hp.zephyrarch.client.ClientEvents;
import com.hp.zephyrarch.config.ZephyrarchConfig;
import com.hp.zephyrarch.entity.ZephyrChargeEntity;
import com.hp.zephyrarch.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
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
import java.util.function.Consumer;

public final class SovereignGauntletItem extends SwordItem implements GeoItem {
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("animation.sovereign_gauntlet.idle");
    private static final RawAnimation ANIM_STRIKE = RawAnimation.begin().thenPlay("animation.sovereign_gauntlet.strike");
    private static final RawAnimation ANIM_SLAM = RawAnimation.begin().thenPlay("animation.sovereign_gauntlet.slam");

    private static final String NBT_COMBO_COUNT = "zephyrarch_gauntlet_combo";
    private static final String NBT_COMBO_TIME = "zephyrarch_gauntlet_combo_time";
    private static final String NBT_SLAM_ACTIVE = "zephyrarch_slam_active";
    private static final String NBT_SLAM_START_Y = "zephyrarch_slam_start_y";
    private static final String NBT_SLAM_EXPIRE = "zephyrarch_slam_expire";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SovereignGauntletItem(Tier tier, Properties properties) {
        super(tier, properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (!attacker.level().isClientSide && attacker.level() instanceof ServerLevel serverLevel && attacker instanceof Player player) {
            CompoundTag data = player.getPersistentData();
            long now = serverLevel.getGameTime();
            long lastHit = data.getLong(NBT_COMBO_TIME);
            int combo = (now - lastHit <= 60L) ? data.getInt(NBT_COMBO_COUNT) + 1 : 1;
            data.putLong(NBT_COMBO_TIME, now);

            long animId = GeoItem.getOrAssignId(stack, serverLevel);
            triggerAnim(player, animId, "action", "strike");

            if (combo >= 3) {
                data.putInt(NBT_COMBO_COUNT, 0);
                performComboSweep(serverLevel, player, target);
            } else {
                data.putInt(NBT_COMBO_COUNT, combo);
                serverLevel.sendParticles(ParticleTypes.SMALL_GUST, target.getX(), target.getY(0.5D), target.getZ(), 4, 0.25D, 0.25D, 0.25D, 0.02D);
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SLIDE, SoundSource.PLAYERS, 0.9F, 1.05F + combo * 0.15F);
            }
        }
        return result;
    }

    private static void performComboSweep(ServerLevel serverLevel, Player player, LivingEntity primaryTarget) {
        Vec3 look = player.getLookAngle();
        Vec3 horizLook = new Vec3(look.x, 0.0D, look.z);
        if (horizLook.lengthSqr() < 1.0E-4D) {
            horizLook = Vec3.directionFromRotation(0.0F, player.getYRot());
        } else {
            horizLook = horizLook.normalize();
        }
        Vec3 center = player.position().add(horizLook.scale(1.8D)).add(0.0D, 1.1D, 0.0D);
        serverLevel.sendParticles(ParticleTypes.GUST, center.x, center.y, center.z, 4, 0.65D, 0.25D, 0.65D, 0.0D);
        serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 2, 0.35D, 0.1D, 0.35D, 0.0D);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2F, 1.15F);

        float sweepDmg = Math.max(4.0F, (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.65D));
        double reach = 3.8D;
        AABB box = player.getBoundingBox().inflate(reach, 1.5D, reach);
        List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, box, e -> canPlayerSkillHit(player, e));
        double kbScale = ZephyrarchConfig.KNOCKBACK_SCALE.get();

        for (LivingEntity victim : victims) {
            if (player.distanceToSqr(victim) > reach * reach) {
                continue;
            }
            Vec3 toVictim = victim.position().subtract(player.position()).normalize();
            if (victim == primaryTarget || toVictim.dot(horizLook) > -0.2D) {
                if (victim != primaryTarget) {
                    victim.hurt(player.damageSources().playerAttack(player), sweepDmg);
                }
                double resist = Math.max(0.0D, 1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                double push = 0.42D * resist * kbScale;
                if (push > 0.01D) {
                    victim.push(horizLook.x * push, 0.20D * resist * kbScale, horizLook.z * push);
                    victim.hurtMarked = true;
                }
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean triggerSlam = !player.onGround() && (player.getXRot() >= 25.0F || player.isShiftKeyDown());

        if (triggerSlam) {
            Vec3 look = player.getLookAngle();
            player.setDeltaMovement(look.x * 0.45D, -1.35D, look.z * 0.45D);
            player.fallDistance = 0.0F;

            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                CompoundTag data = player.getPersistentData();
                data.putBoolean(NBT_SLAM_ACTIVE, true);
                data.putDouble(NBT_SLAM_START_Y, player.getY());
                data.putLong(NBT_SLAM_EXPIRE, serverLevel.getGameTime() + 80L);

                long animId = GeoItem.getOrAssignId(stack, serverLevel);
                triggerAnim(player, animId, "action", "slam");

                serverLevel.sendParticles(ParticleTypes.GUST, player.getX(), player.getY() + 0.5D, player.getZ(), 3, 0.35D, 0.2D, 0.35D, 0.0D);
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 1.4F, 0.9F);
                EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                stack.hurtAndBreak(2, player, slot);
            }
            player.getCooldowns().addCooldown(this, 70);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        Vec3 look = player.getLookAngle();
        player.setDeltaMovement(look.x * 1.18D, Math.max(0.32D, look.y * 0.72D + 0.18D), look.z * 1.18D);
        player.fallDistance = 0.0F;

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            long animId = GeoItem.getOrAssignId(stack, serverLevel);
            triggerAnim(player, animId, "action", "strike");

            ZephyrChargeEntity charge = new ZephyrChargeEntity(serverLevel, player)
                    .configure(0.90D, 2.2D, 0.50D, 60, 1.0F);
            charge.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.60F, 0.4F);
            serverLevel.addFreshEntity(charge);
            serverLevel.sendParticles(ParticleTypes.GUST, player.getX(), player.getY() + 0.9D, player.getZ(), 2, 0.3D, 0.2D, 0.3D, 0.0D);
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.3F, 1.05F);
            EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(1, player, slot);
        }

        player.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
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
                .triggerableAnim("strike", ANIM_STRIKE)
                .triggerableAnim("slam", ANIM_SLAM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public static final class Events {
        @SubscribeEvent
        public static void onLivingFall(LivingFallEvent event) {
            LivingEntity entity = event.getEntity();
            boolean holdingGauntlet = entity.getMainHandItem().is(ModRegistry.SOVEREIGN_GAUNTLET.get())
                    || entity.getOffhandItem().is(ModRegistry.SOVEREIGN_GAUNTLET.get());
            boolean slamming = entity.getPersistentData().getBoolean(NBT_SLAM_ACTIVE);
            if (holdingGauntlet || slamming) {
                event.setDistance(0.0F);
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            Player player = event.getEntity();
            if (player.level().isClientSide || !(player.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            CompoundTag data = player.getPersistentData();
            if (!data.getBoolean(NBT_SLAM_ACTIVE)) {
                return;
            }
            if (serverLevel.getGameTime() > data.getLong(NBT_SLAM_EXPIRE) || player.isInWaterOrBubble() || player.getAbilities().flying) {
                data.putBoolean(NBT_SLAM_ACTIVE, false);
                return;
            }
            player.fallDistance = 0.0F;
            if (player.onGround()) {
                data.putBoolean(NBT_SLAM_ACTIVE, false);
                double drop = Math.max(0.0D, data.getDouble(NBT_SLAM_START_Y) - player.getY());
                double radius = Mth.clamp(4.8D + drop * 0.12D, 4.8D, 6.8D);
                double mult = Mth.clamp(1.35D + drop * 0.07D, 1.35D, 2.40D);
                float slamDamage = Math.max(8.0F, (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * mult));

                serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, player.getX(), player.getY() + 0.2D, player.getZ(), 3, 0.5D, 0.1D, 0.5D, 0.0D);
                for (int i = 0; i < 20; i++) {
                    double ang = (Math.PI * 2.0D * i) / 20.0D;
                    serverLevel.sendParticles(ParticleTypes.GUST,
                            player.getX() + Math.cos(ang) * (radius * 0.72D),
                            player.getY() + 0.25D,
                            player.getZ() + Math.sin(ang) * (radius * 0.72D),
                            1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.8F, 0.75F);
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 1.6F, 0.85F);

                AABB area = player.getBoundingBox().inflate(radius, 2.5D, radius);
                List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, area, e -> canPlayerSkillHit(player, e));
                double kbScale = ZephyrarchConfig.KNOCKBACK_SCALE.get();

                for (LivingEntity victim : victims) {
                    double dist = Math.sqrt(player.distanceToSqr(victim));
                    if (dist > radius) {
                        continue;
                    }
                    double falloff = Math.max(0.6D, 1.0D - dist / (radius + 2.0D));
                    victim.hurt(player.damageSources().playerAttack(player), (float) (slamDamage * falloff));

                    double resist = Math.max(0.0D, 1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                    double push = 0.55D * falloff * resist * kbScale;
                    double lift = 0.34D * falloff * resist * kbScale;
                    if (push > 0.01D || lift > 0.01D) {
                        Vec3 away = victim.position().subtract(player.position());
                        Vec3 horiz = new Vec3(away.x, 0.0D, away.z);
                        horiz = horiz.lengthSqr() > 1.0E-4D ? horiz.normalize() : Vec3.directionFromRotation(0.0F, player.getYRot());
                        victim.push(horiz.x * push, lift, horiz.z * push);
                        victim.hurtMarked = true;
                    }
                }
            }
        }
    }
}
