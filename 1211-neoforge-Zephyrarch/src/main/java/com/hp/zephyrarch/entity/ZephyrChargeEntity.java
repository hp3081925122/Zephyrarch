package com.hp.zephyrarch.entity;

import com.hp.zephyrarch.config.ZephyrarchConfig;
import com.hp.zephyrarch.event.ZephyrarchCombatEvents;
import com.hp.zephyrarch.registry.ModRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class ZephyrChargeEntity extends Projectile implements GeoEntity {
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.zephyr_charge.spin");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public final Deque<Vec3> clientTrail = new ArrayDeque<>(12);
    private float baseDamage = 8.0F;
    private int life;

    public ZephyrChargeEntity(EntityType<? extends ZephyrChargeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public ZephyrChargeEntity(Level level, LivingEntity shooter, float baseDamage) {
        this(ModRegistry.ZEPHYR_CHARGE.get(), level);
        setOwner(shooter);
        this.baseDamage = baseDamage;
        setPos(shooter.getX(), shooter.getEyeY() - 0.25D, shooter.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        life++;
        if (life > 100) {
            discard();
            return;
        }

        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            hitTargetOrDeflectSelf(hitResult);
        }

        if (isRemoved()) {
            return;
        }

        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        ProjectileUtil.rotateTowardsMovement(this, 0.5F);

        if (level().isClientSide) {
            if (clientTrail.size() >= 10) {
                clientTrail.removeLast();
            }
            clientTrail.addFirst(position().add(0.0D, 0.25D, 0.0D));
            level().addParticle(ParticleTypes.SMALL_GUST, getX(), getY() + 0.15D, getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) {
            return false;
        }
        Entity owner = getOwner();
        return owner == null || (!target.is(owner) && !owner.isAlliedTo(target));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!level().isClientSide) {
            explodeBurst(result.getEntity());
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide) {
            explodeBurst(null);
            discard();
        }
    }

    private void explodeBurst(Entity directHit) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity owner = getOwner();
        LivingEntity livingOwner = owner instanceof LivingEntity le ? le : null;
        float computedBase = baseDamage;
        if (livingOwner != null && livingOwner.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            computedBase = (float) (livingOwner.getAttributeValue(Attributes.ATTACK_DAMAGE) * ZephyrarchConfig.GALE_CHARGE_MULTIPLIER.get());
        }
        DamageSource source = livingOwner != null
                ? damageSources().mobProjectile(this, livingOwner)
                : damageSources().magic();

        serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, getX(), getY() + 0.2D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        serverLevel.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2D, getZ(), 10, 0.35D, 0.25D, 0.35D, 0.08D);
        serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 1.2F, 0.95F + random.nextFloat() * 0.2F);

        double radius = 2.8D;
        AABB area = getBoundingBox().inflate(radius);
        List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive()
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
                && (owner == null || (!e.is(owner) && !owner.isAlliedTo(e))));
        double globalKb = ZephyrarchConfig.KNOCKBACK_SCALE.get();

        for (LivingEntity victim : victims) {
            double dist = Math.sqrt(victim.distanceToSqr(this));
            if (dist > radius + 0.6D && victim != directHit) {
                continue;
            }
            float falloff = victim == directHit ? 1.0F : (float) Math.max(0.55D, 1.0D - dist / (radius + 1.0D));
            if (owner != null) {
                ZephyrarchCombatEvents.applyAntiHealIfEligible(owner, victim);
            }
            victim.hurt(source, computedBase * falloff);

            double resist = Math.max(0.0D, 1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            double kbStrength = 0.45D * falloff * resist * globalKb;
            if (kbStrength > 0.01D) {
                Vec3 push = victim.position().subtract(position());
                Vec3 horiz = new Vec3(push.x, 0.0D, push.z);
                if (horiz.lengthSqr() < 1.0E-4D) {
                    horiz = getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
                }
                if (horiz.lengthSqr() > 1.0E-4D) {
                    horiz = horiz.normalize().scale(kbStrength);
                }
                victim.push(horiz.x, 0.22D * resist * globalKb, horiz.z);
                victim.hurtMarked = true;
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("BaseDamage", baseDamage);
        tag.putInt("Life", life);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("BaseDamage")) {
            baseDamage = tag.getFloat("BaseDamage");
        }
        life = tag.getInt("Life");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spin", 0, state -> state.setAndContinue(SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
