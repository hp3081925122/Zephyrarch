package com.hp.zephyrarch.entity;

import com.hp.zephyrarch.config.ZephyrarchConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.List;

public final class ZephyrarchEntity extends Monster implements GeoEntity {
    private static final String NBT_CONFIG_APPLIED = "ConfigAttributesApplied";
    private static final String NBT_PHASE2 = "Phase2Triggered";

    private static final EntityDataAccessor<Integer> DATA_ATTACK = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACK_SERIAL = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PHASE2 = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation ANIM_VORTEX = RawAnimation.begin().thenLoop("animation.zephyrarch.vortex_spin");
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("animation.zephyrarch.idle");
    private static final RawAnimation ANIM_COMBO = RawAnimation.begin().thenPlay("animation.zephyrarch.combo_punch");
    private static final RawAnimation ANIM_SLAM = RawAnimation.begin().thenPlay("animation.zephyrarch.tempest_slam");
    private static final RawAnimation ANIM_BARRAGE = RawAnimation.begin().thenPlay("animation.zephyrarch.gale_barrage");
    private static final RawAnimation ANIM_CYCLONE = RawAnimation.begin().thenPlay("animation.zephyrarch.cyclone_vortex");
    private static final RawAnimation ANIM_PHASE_SHIFT = RawAnimation.begin().thenPlay("animation.zephyrarch.phase_shift");

    private final ServerBossEvent bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final int[] skillCooldowns = new int[ZephyrarchAttack.values().length];

    private boolean configAttributesApplied;
    private boolean phase2Triggered;
    private int attackTick;
    private int globalAttackDelay = 25;
    private int clientLastSerial = -1;

    public ZephyrarchEntity(EntityType<? extends ZephyrarchEntity> type, Level level) {
        super(type, level);
        this.xpReward = 350;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 320.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.29D)
                .add(Attributes.FOLLOW_RANGE, 44.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85D)
                .add(Attributes.STEP_HEIGHT, 1.5D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACK, 0);
        builder.define(DATA_ATTACK_SERIAL, 0);
        builder.define(DATA_PHASE2, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new CombatGoal());
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.75D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public ZephyrarchAttack getAttack() {
        return ZephyrarchAttack.byId(this.entityData.get(DATA_ATTACK));
    }

    public boolean isPhase2() {
        return this.entityData.get(DATA_PHASE2);
    }

    public boolean isOverdrive() {
        return this.getHealth() <= this.getMaxHealth() * (float) ZephyrarchConfig.LOW_HEALTH_BURST_RATIO.get().doubleValue();
    }

    private void startAttack(ZephyrarchAttack next) {
        this.entityData.set(DATA_ATTACK, next.id);
        if (next != ZephyrarchAttack.NONE) {
            this.entityData.set(DATA_ATTACK_SERIAL, this.entityData.get(DATA_ATTACK_SERIAL) + 1);
        }
        this.attackTick = 0;
    }

    private void applyConfigAttributesOnce() {
        if (this.configAttributesApplied) {
            return;
        }
        setBaseAttribute(Attributes.MAX_HEALTH, ZephyrarchConfig.MAX_HEALTH.get());
        setBaseAttribute(Attributes.ATTACK_DAMAGE, ZephyrarchConfig.ATTACK_DAMAGE.get());
        setBaseAttribute(Attributes.ARMOR, ZephyrarchConfig.ARMOR.get());
        setBaseAttribute(Attributes.MOVEMENT_SPEED, ZephyrarchConfig.MOVEMENT_SPEED.get());
        setBaseAttribute(Attributes.FOLLOW_RANGE, ZephyrarchConfig.FOLLOW_RANGE.get());
        this.setHealth(this.getMaxHealth());
        this.configAttributesApplied = true;
    }

    private void setBaseAttribute(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double val) {
        AttributeInstance inst = this.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(val);
        }
    }

    @Override
    public void tick() {
        super.tick();

        Vec3 motion = this.getDeltaMovement();
        if (!this.onGround() && motion.y < -0.18D && getAttack() != ZephyrarchAttack.TEMPEST_SLAM) {
            this.setDeltaMovement(motion.x, motion.y * 0.78D, motion.z);
        }

        if (this.level().isClientSide) {
            spawnAmbientClientParticles();
            return;
        }

        applyConfigAttributesOnce();

        for (int i = 0; i < this.skillCooldowns.length; i++) {
            if (this.skillCooldowns[i] > 0) {
                this.skillCooldowns[i]--;
            }
        }
        if (this.globalAttackDelay > 0 && getAttack() == ZephyrarchAttack.NONE) {
            this.globalAttackDelay--;
        }

        if (!this.phase2Triggered && this.isAlive() && this.getHealth() <= this.getMaxHealth() * (float) ZephyrarchConfig.PHASE2_HEALTH_RATIO.get().doubleValue()) {
            this.phase2Triggered = true;
            this.entityData.set(DATA_PHASE2, true);
            this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
            startAttack(ZephyrarchAttack.PHASE_SHIFT);
            this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_CHARGE, SoundSource.HOSTILE, 2.0F, 0.75F);
        }

        ZephyrarchAttack current = getAttack();
        if (current != ZephyrarchAttack.NONE) {
            this.attackTick++;
            tickActiveSkill(current);
            if (this.attackTick >= current.duration) {
                startAttack(ZephyrarchAttack.NONE);
                this.globalAttackDelay = isOverdrive() ? 10 : (isPhase2() ? 15 : 22);
            }
        }

        this.bossEvent.setProgress(Mth.clamp(this.getHealth() / Math.max(1.0F, this.getMaxHealth()), 0.0F, 1.0F));
    }

    private void spawnAmbientClientParticles() {
        if (this.random.nextInt(2) == 0) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double rad = 0.8D + this.random.nextDouble() * 0.7D;
            double px = getX() + Math.cos(angle) * rad;
            double pz = getZ() + Math.sin(angle) * rad;
            double py = getY() + 0.2D + this.random.nextDouble() * 2.2D;
            this.level().addParticle(ParticleTypes.SMALL_GUST, px, py, pz, -Math.sin(angle) * 0.08D, 0.04D, Math.cos(angle) * 0.08D);
        }
        if (isPhase2() && this.tickCount % 4 == 0) {
            this.level().addParticle(ParticleTypes.CLOUD, getX() + (random.nextDouble() - 0.5D) * 1.6D, getY() + 1.8D, getZ() + (random.nextDouble() - 0.5D) * 1.6D, 0.0D, 0.06D, 0.0D);
        }
    }

    private void tickActiveSkill(ZephyrarchAttack attack) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && attack != ZephyrarchAttack.CYCLONE_VORTEX) {
            this.getLookControl().setLookAt(target, 35.0F, 35.0F);
        }

        switch (attack) {
            case COMBO_PUNCH -> tickComboPunch(serverLevel, target);
            case TEMPEST_SLAM -> tickTempestSlam(serverLevel, target);
            case GALE_BARRAGE -> tickGaleBarrage(serverLevel, target);
            case CYCLONE_VORTEX -> tickCycloneVortex(serverLevel);
            case PHASE_SHIFT -> tickPhaseShift(serverLevel);
            default -> {
            }
        }
    }

    private void tickComboPunch(ServerLevel serverLevel, LivingEntity target) {
        if (this.attackTick == 6 || this.attackTick == 18) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_SLIDE, SoundSource.HOSTILE, 1.4F, 0.9F);
            if (target != null && target.isAlive()) {
                Vec3 dir = target.position().subtract(position());
                Vec3 horiz = new Vec3(dir.x, 0.0D, dir.z);
                if (horiz.lengthSqr() > 0.01D) {
                    horiz = horiz.normalize().scale(0.42D);
                    this.setDeltaMovement(horiz.x, this.getDeltaMovement().y, horiz.z);
                }
            }
        }
        if (this.attackTick == 11) {
            performArcStrike(serverLevel, 3.8D, ZephyrarchConfig.COMBO_PUNCH_MULTIPLIER.get(), 0.42D, 0.16D);
        } else if (this.attackTick == 23) {
            performArcStrike(serverLevel, 4.2D, ZephyrarchConfig.COMBO_FINISHER_MULTIPLIER.get(), 0.55D, 0.22D);
            if (isPhase2() && target != null && target.isAlive()) {
                shootChargeTowards(serverLevel, target, 1.35F, 0.0F);
            }
        }
    }

    private void performArcStrike(ServerLevel serverLevel, double reach, double damageMult, double horizKb, double liftY) {
        Vec3 look = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        Vec3 center = position().add(look.scale(1.8D)).add(0.0D, 1.4D, 0.0D);
        serverLevel.sendParticles(ParticleTypes.GUST, center.x, center.y, center.z, 3, 0.5D, 0.3D, 0.5D, 0.0D);
        serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.75F);

        AABB box = this.getBoundingBox().inflate(reach, 1.6D, reach);
        List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive() && !this.isAlliedTo(e));
        for (LivingEntity victim : victims) {
            if (this.distanceToSqr(victim) <= reach * reach) {
                Vec3 toVictim = victim.position().subtract(position()).normalize();
                if (toVictim.dot(look) > -0.25D) {
                    applyScaledAttack(victim, damageMult, horizKb, liftY);
                }
            }
        }
    }

    private void tickTempestSlam(ServerLevel serverLevel, LivingEntity target) {
        if (this.attackTick == 4) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_INHALE, SoundSource.HOSTILE, 1.8F, 0.8F);
            Vec3 jumpBoost = new Vec3(0.0D, 0.52D, 0.0D);
            if (target != null && target.isAlive()) {
                Vec3 diff = target.position().subtract(position());
                Vec3 horiz = new Vec3(diff.x, 0.0D, diff.z);
                if (horiz.lengthSqr() > 0.25D) {
                    horiz = horiz.normalize().scale(Math.min(0.65D, Math.sqrt(horiz.lengthSqr()) * 0.12D));
                    jumpBoost = jumpBoost.add(horiz);
                }
            }
            this.setDeltaMovement(jumpBoost);
        } else if (this.attackTick == 16) {
            this.setDeltaMovement(this.getDeltaMovement().x * 0.3D, -0.95D, this.getDeltaMovement().z * 0.3D);
        } else if (this.attackTick == 20) {
            double radius = isPhase2() ? 6.5D : 5.2D;
            serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, getX(), getY() + 0.2D, getZ(), 2, 0.4D, 0.1D, 0.4D, 0.0D);
            for (int i = 0; i < 20; i++) {
                double ang = (Math.PI * 2.0D * i) / 20.0D;
                serverLevel.sendParticles(ParticleTypes.GUST, getX() + Math.cos(ang) * (radius * 0.7D), getY() + 0.3D, getZ() + Math.sin(ang) * (radius * 0.7D), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 2.0F, 0.7F);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_LAND, SoundSource.HOSTILE, 1.8F, 0.8F);

            AABB area = this.getBoundingBox().inflate(radius, 2.5D, radius);
            List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive() && !this.isAlliedTo(e));
            for (LivingEntity victim : victims) {
                double dist = Math.sqrt(this.distanceToSqr(victim));
                if (dist <= radius) {
                    double falloff = Math.max(0.6D, 1.0D - (dist / (radius + 2.0D)));
                    applyScaledAttack(victim, ZephyrarchConfig.TEMPEST_SLAM_MULTIPLIER.get() * falloff, 0.58D * falloff, 0.32D * falloff);
                }
            }
        }
    }

    private void tickGaleBarrage(ServerLevel serverLevel, LivingEntity target) {
        if (this.attackTick == 5) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_JUMP, SoundSource.HOSTILE, 1.6F, 1.0F);
            Vec3 sideDir = Vec3.directionFromRotation(0.0F, this.yBodyRot + (this.random.nextBoolean() ? 75.0F : -75.0F));
            this.setDeltaMovement(sideDir.x * 0.55D, 0.48D, sideDir.z * 0.55D);
        }
        if (this.attackTick == 14 || this.attackTick == 24 || this.attackTick == 34) {
            if (target != null && target.isAlive()) {
                float speed = isPhase2() ? 1.45F : 1.25F;
                shootChargeTowards(serverLevel, target, speed, 0.0F);
                if (this.attackTick == 34 && (isPhase2() || isOverdrive())) {
                    shootChargeTowards(serverLevel, target, speed, -14.0F);
                    shootChargeTowards(serverLevel, target, speed, 14.0F);
                }
            }
        }
    }

    private void shootChargeTowards(ServerLevel serverLevel, LivingEntity target, float velocity, float yawOffsetDeg) {
        float scaledDmg = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * ZephyrarchConfig.GALE_CHARGE_MULTIPLIER.get());
        ZephyrChargeEntity charge = new ZephyrChargeEntity(serverLevel, this, scaledDmg);
        charge.setPos(getX(), getY() + 2.1D, getZ());

        double dx = target.getX() - charge.getX();
        double dy = target.getY(0.45D) - charge.getY();
        double dz = target.getZ() - charge.getZ();
        Vec3 dir = new Vec3(dx, dy, dz);
        if (Math.abs(yawOffsetDeg) > 0.01F) {
            dir = dir.yRot(yawOffsetDeg * Mth.DEG_TO_RAD);
        }
        charge.shoot(dir.x, dir.y, dir.z, velocity, 1.0F);
        serverLevel.addFreshEntity(charge);
        serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.HOSTILE, 1.6F, 0.9F + this.random.nextFloat() * 0.2F);
    }

    private void tickCycloneVortex(ServerLevel serverLevel) {
        this.getNavigation().stop();
        this.setDeltaMovement(0.0D, Math.max(-0.05D, this.getDeltaMovement().y * 0.5D), 0.0D);

        double pullRadius = 9.0D;
        if (this.attackTick < 46) {
            if (this.attackTick % 6 == 0) {
                serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_WHIRL, SoundSource.HOSTILE, 1.5F, 0.85F + (this.attackTick / 60.0F) * 0.4F);
                for (int i = 0; i < 12; i++) {
                    double ang = (this.attackTick * 0.3D) + (Math.PI * 2.0D * i / 12.0D);
                    double r = 2.0D + (i % 3) * 2.2D;
                    serverLevel.sendParticles(ParticleTypes.SMALL_GUST, getX() + Math.cos(ang) * r, getY() + 0.5D + (i % 4) * 0.6D, getZ() + Math.sin(ang) * r, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }

            AABB area = this.getBoundingBox().inflate(pullRadius, 3.5D, pullRadius);
            List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive() && !this.isAlliedTo(e));
            double globalKb = ZephyrarchConfig.KNOCKBACK_SCALE.get();

            for (LivingEntity victim : victims) {
                double dist = Math.sqrt(this.distanceToSqr(victim));
                if (dist > pullRadius || dist < 0.8D) {
                    continue;
                }
                double resist = Math.max(0.0D, 1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                double pull = 0.085D * resist * globalKb;
                if (pull > 0.005D) {
                    Vec3 toBoss = position().subtract(victim.position()).normalize().scale(pull);
                    victim.push(toBoss.x, 0.02D * resist, toBoss.z);
                    victim.hurtMarked = true;
                }
                if (this.attackTick % 12 == 10 && dist <= 4.2D) {
                    float sliceDmg = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * ZephyrarchConfig.CYCLONE_TICK_MULTIPLIER.get());
                    victim.hurt(this.damageSources().mobAttack(this), sliceDmg);
                }
            }
        } else if (this.attackTick == 50) {
            double burstRadius = isOverdrive() ? 7.2D : 6.0D;
            serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, getX(), getY() + 1.4D, getZ(), 3, 0.6D, 0.4D, 0.6D, 0.0D);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 2.2F, 0.65F);

            AABB burstBox = this.getBoundingBox().inflate(burstRadius, 3.0D, burstRadius);
            List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, burstBox, e -> e != this && e.isAlive() && !this.isAlliedTo(e));
            for (LivingEntity victim : victims) {
                if (this.distanceToSqr(victim) <= burstRadius * burstRadius) {
                    applyScaledAttack(victim, ZephyrarchConfig.CYCLONE_BURST_MULTIPLIER.get(), 0.62D, 0.28D);
                }
            }
            if (isOverdrive()) {
                for (int k = 0; k < 6; k++) {
                    float yaw = k * 60.0F;
                    float dmg = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * ZephyrarchConfig.GALE_CHARGE_MULTIPLIER.get());
                    ZephyrChargeEntity ringShot = new ZephyrChargeEntity(serverLevel, this, dmg);
                    ringShot.setPos(getX(), getY() + 1.8D, getZ());
                    Vec3 dir = Vec3.directionFromRotation(0.0F, yaw);
                    ringShot.shoot(dir.x, -0.05D, dir.z, 1.15F, 0.5F);
                    serverLevel.addFreshEntity(ringShot);
                }
            }
        }
    }

    private void tickPhaseShift(ServerLevel serverLevel) {
        this.getNavigation().stop();
        this.setDeltaMovement(0.0D, 0.02D, 0.0D);
        if (this.attackTick == 24) {
            serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, getX(), getY() + 1.6D, getZ(), 4, 0.8D, 0.6D, 0.8D, 0.0D);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 2.4F, 0.6F);
            double radius = 6.5D;
            AABB box = this.getBoundingBox().inflate(radius, 3.0D, radius);
            List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive() && !this.isAlliedTo(e));
            for (LivingEntity victim : victims) {
                if (this.distanceToSqr(victim) <= radius * radius) {
                    applyScaledAttack(victim, ZephyrarchConfig.PHASE_SHIFT_BURST_MULTIPLIER.get(), 0.55D, 0.26D);
                }
            }
        }
    }

    private void applyScaledAttack(LivingEntity victim, double multiplier, double baseHorizKb, double baseLiftY) {
        float damage = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * multiplier);
        boolean hit = victim.hurt(this.damageSources().mobAttack(this), damage);
        if (!hit) {
            return;
        }
        double resist = Math.max(0.0D, 1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        double scale = ZephyrarchConfig.KNOCKBACK_SCALE.get();
        double finalKb = baseHorizKb * resist * scale;
        double finalLift = baseLiftY * resist * scale;
        if (finalKb > 0.01D || finalLift > 0.01D) {
            Vec3 away = victim.position().subtract(this.position());
            Vec3 horiz = new Vec3(away.x, 0.0D, away.z);
            if (horiz.lengthSqr() < 1.0E-4D) {
                horiz = Vec3.directionFromRotation(0.0F, this.yBodyRot);
            } else {
                horiz = horiz.normalize();
            }
            victim.push(horiz.x * finalKb, finalLift, horiz.z * finalKb);
            victim.hurtMarked = true;
        }
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(DamageTypes.FALL) || source.is(DamageTypes.IN_WALL)) {
            return true;
        }
        if (getAttack() == ZephyrarchAttack.PHASE_SHIFT && !source.isCreativePlayer()) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.onGround() ? SoundEvents.BREEZE_IDLE_GROUND : SoundEvents.BREEZE_IDLE_AIR;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.BREEZE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BREEZE_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(NBT_CONFIG_APPLIED, this.configAttributesApplied);
        tag.putBoolean(NBT_PHASE2, this.phase2Triggered);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.configAttributesApplied = tag.getBoolean(NBT_CONFIG_APPLIED);
        this.phase2Triggered = tag.getBoolean(NBT_PHASE2);
        this.entityData.set(DATA_PHASE2, this.phase2Triggered);
        this.bossEvent.setColor(this.phase2Triggered ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.BLUE);
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "vortex", 0, state -> state.setAndContinue(ANIM_VORTEX))
                .setAnimationSpeedHandler(entity -> entity.isPhase2() ? 1.55D : 1.0D));

        controllers.add(new AnimationController<>(this, "action", 4, this::handleActionAnim));
    }

    private PlayState handleActionAnim(AnimationState<ZephyrarchEntity> state) {
        int serial = this.entityData.get(DATA_ATTACK_SERIAL);
        if (serial != this.clientLastSerial) {
            this.clientLastSerial = serial;
            state.getController().forceAnimationReset();
        }
        return switch (getAttack()) {
            case COMBO_PUNCH -> state.setAndContinue(ANIM_COMBO);
            case TEMPEST_SLAM -> state.setAndContinue(ANIM_SLAM);
            case GALE_BARRAGE -> state.setAndContinue(ANIM_BARRAGE);
            case CYCLONE_VORTEX -> state.setAndContinue(ANIM_CYCLONE);
            case PHASE_SHIFT -> state.setAndContinue(ANIM_PHASE_SHIFT);
            case NONE -> state.setAndContinue(ANIM_IDLE);
        };
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    private final class CombatGoal extends Goal {
        CombatGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = ZephyrarchEntity.this.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = ZephyrarchEntity.this.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void stop() {
            ZephyrarchEntity.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = ZephyrarchEntity.this.getTarget();
            if (target == null || !target.isAlive()) {
                return;
            }

            if (getAttack() != ZephyrarchAttack.NONE) {
                return;
            }

            ZephyrarchEntity.this.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distSq = ZephyrarchEntity.this.distanceToSqr(target);

            if (distSq > 18.0D) {
                ZephyrarchEntity.this.getNavigation().moveTo(target, isPhase2() ? 1.2D : 1.05D);
            } else {
                ZephyrarchEntity.this.getNavigation().stop();
            }

            if (globalAttackDelay > 0) {
                return;
            }

            selectNextSkill(distSq);
        }

        private void selectNextSkill(double distSq) {
            int cdScale = isOverdrive() ? 70 : (isPhase2() ? 85 : 100);

            if (distSq <= 64.0D && skillCooldowns[ZephyrarchAttack.CYCLONE_VORTEX.id] <= 0 && (isPhase2() || random.nextFloat() < 0.35F)) {
                startAttack(ZephyrarchAttack.CYCLONE_VORTEX);
                skillCooldowns[ZephyrarchAttack.CYCLONE_VORTEX.id] = (180 * cdScale) / 100;
                return;
            }

            if (distSq <= 30.0D && skillCooldowns[ZephyrarchAttack.TEMPEST_SLAM.id] <= 0 && random.nextFloat() < 0.55F) {
                startAttack(ZephyrarchAttack.TEMPEST_SLAM);
                skillCooldowns[ZephyrarchAttack.TEMPEST_SLAM.id] = (110 * cdScale) / 100;
                return;
            }

            if (distSq <= 16.0D && skillCooldowns[ZephyrarchAttack.COMBO_PUNCH.id] <= 0) {
                startAttack(ZephyrarchAttack.COMBO_PUNCH);
                skillCooldowns[ZephyrarchAttack.COMBO_PUNCH.id] = (55 * cdScale) / 100;
                return;
            }

            if (skillCooldowns[ZephyrarchAttack.GALE_BARRAGE.id] <= 0) {
                startAttack(ZephyrarchAttack.GALE_BARRAGE);
                skillCooldowns[ZephyrarchAttack.GALE_BARRAGE.id] = (90 * cdScale) / 100;
            }
        }
    }
}
