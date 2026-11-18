package com.hp.zephyrarch.entity;

import com.hp.zephyrarch.config.ZephyrarchConfig;
import com.hp.zephyrarch.event.ZephyrarchCombatEvents;
import com.hp.zephyrarch.network.ZephyrWorldVfxPayload;
import com.hp.zephyrarch.registry.ModRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
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
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(ZephyrChargeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_HOMING = SynchedEntityData.defineId(ZephyrChargeEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public final Deque<Vec3> clientTrail = new ArrayDeque<>(12);
    private double damageMultiplier = 0.45D;
    private double blastRadius = 1.6D;
    private double knockback = 0.5D;
    private int maxLife = 40;
    private int life;
    private int homingTargetId = -1;
    private float turnRateDeg;
    private boolean hittable;

    public ZephyrChargeEntity(EntityType<? extends ZephyrChargeEntity> type, Level level) {
        super(type, level);
    }

    public ZephyrChargeEntity(Level level, LivingEntity shooter) {
        this(ModRegistry.ZEPHYR_CHARGE.get(), level);
        setOwner(shooter);
        setPos(shooter.getX(), shooter.getEyeY() - 0.25D, shooter.getZ());
    }

    public ZephyrChargeEntity configure(double damageMultiplier, double blastRadius, double knockback, int maxLife, float scale) {
        this.damageMultiplier = damageMultiplier;
        this.blastRadius = blastRadius;
        this.knockback = knockback;
        this.maxLife = maxLife;
        this.entityData.set(DATA_SCALE, scale);
        return this;
    }

    public ZephyrChargeEntity homing(LivingEntity target, float turnRateDeg) {
        this.homingTargetId = target.getId();
        this.turnRateDeg = turnRateDeg;
        this.hittable = true;
        this.entityData.set(DATA_HOMING, true);
        return this;
    }

    public float getScale() {
        return this.entityData.get(DATA_SCALE);
    }

    public boolean isHomingOrb() {
        return this.entityData.get(DATA_HOMING);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SCALE, 1.0F);
        builder.define(DATA_HOMING, false);
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        life++;
        if (!level().isClientSide && life > maxLife) {
            if (hittable) {
                explodeBurst(null);
            }
            discard();
            return;
        }

        if (!level().isClientSide && homingTargetId >= 0 && level().getEntity(homingTargetId) instanceof LivingEntity target && target.isAlive()) {
            Vec3 motion = getDeltaMovement();
            double speed = motion.length();
            Vec3 want = target.getBoundingBox().getCenter().subtract(position()).normalize();
            Vec3 cur = speed > 1.0E-4D ? motion.scale(1.0D / speed) : want;
            double angle = Math.acos(Math.max(-1.0D, Math.min(1.0D, cur.dot(want))));
            double maxTurn = Math.toRadians(turnRateDeg);
            Vec3 next = angle <= maxTurn ? want : cur.add(want.subtract(cur).scale(maxTurn / angle)).normalize();
            setDeltaMovement(next.scale(speed));
            hasImpulse = true;
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
        }
    }

    @Override
    public boolean isPickable() {
        return hittable;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (hittable && !level().isClientSide && source.getEntity() instanceof Player) {
            ZephyrWorldVfxPayload.sendPoint(this, ZephyrWorldVfxPayload.KIND_CHARGE_BURST, position().add(0.0D, 0.25D, 0.0D), 1.15F, 10);
            playSound(SoundEvents.WIND_CHARGE_BURST.value(), 0.8F, 1.5F);
            discard();
            return true;
        }
        return false;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target) || target instanceof ZephyrChargeEntity || target instanceof ZephyrTwisterEntity || target instanceof ZephyrBladeEntity) {
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
        double attack = livingOwner != null && livingOwner.getAttribute(Attributes.ATTACK_DAMAGE) != null
                ? livingOwner.getAttributeValue(Attributes.ATTACK_DAMAGE) : 12.0D;
        float damage = (float) (attack * damageMultiplier);
        DamageSource source = livingOwner != null ? damageSources().mobProjectile(this, livingOwner) : damageSources().magic();

        ZephyrWorldVfxPayload.sendPoint(this, ZephyrWorldVfxPayload.KIND_CHARGE_BURST,
                position().add(0.0D, 0.2D, 0.0D), (float) blastRadius, blastRadius >= 2.0D ? 14 : 10);
        serverLevel.sendParticles(ParticleTypes.GUST, getX(), getY() + 0.2D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 1.1F, 0.95F + random.nextFloat() * 0.2F);

        AABB area = getBoundingBox().inflate(blastRadius);
        List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive()
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
                && (owner == null || (!e.is(owner) && !owner.isAlliedTo(e))));
        double globalKb = ZephyrarchConfig.KNOCKBACK_SCALE.get();

        for (LivingEntity victim : victims) {
            double dist = Math.sqrt(victim.distanceToSqr(this));
            if (dist > blastRadius + 0.6D && victim != directHit) {
                continue;
            }
            float falloff = victim == directHit ? 1.0F : (float) Math.max(0.5D, 1.0D - dist / (blastRadius + 1.0D));
            if (owner != null) {
                ZephyrarchCombatEvents.applyAntiHealIfEligible(owner, victim);
            }
            victim.hurt(source, damage * falloff);

            double resist = Math.max(0.0D, 1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            double kbStrength = knockback * falloff * resist * globalKb;
            if (kbStrength > 0.01D) {
                Vec3 push = victim.position().subtract(position());
                Vec3 horiz = new Vec3(push.x, 0.0D, push.z);
                if (horiz.lengthSqr() < 1.0E-4D) {
                    horiz = getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
                }
                if (horiz.lengthSqr() > 1.0E-4D) {
                    horiz = horiz.normalize().scale(kbStrength);
                }
                victim.push(horiz.x, 0.2D * resist * globalKb, horiz.z);
                victim.hurtMarked = true;
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("DamageMult", damageMultiplier);
        tag.putDouble("BlastRadius", blastRadius);
        tag.putDouble("Knockback", knockback);
        tag.putInt("MaxLife", maxLife);
        tag.putInt("Life", life);
        tag.putFloat("Scale", getScale());
        tag.putBoolean("Hittable", hittable);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damageMultiplier = tag.getDouble("DamageMult");
        blastRadius = tag.getDouble("BlastRadius");
        knockback = tag.getDouble("Knockback");
        maxLife = Math.max(1, tag.getInt("MaxLife"));
        life = tag.getInt("Life");
        if (tag.contains("Scale")) {
            entityData.set(DATA_SCALE, tag.getFloat("Scale"));
        }
        hittable = tag.getBoolean("Hittable");
        entityData.set(DATA_HOMING, hittable);
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
