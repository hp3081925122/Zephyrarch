package com.hp.zephyrarch.entity;

import com.hp.zephyrarch.config.ZephyrarchConfig;
import com.hp.zephyrarch.event.ZephyrarchCombatEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
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
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class ZephyrarchEntity extends Monster implements GeoEntity {
    public static final String BAR_KEY = "bossbar.zephyrarch.zephyrarch";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NBT_CONFIG_APPLIED = "ConfigAttributesApplied";
    private static final String NBT_PHASE2 = "Phase2Triggered";

    private static final EntityDataAccessor<Integer> DATA_ATTACK = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACK_SERIAL = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PHASE2 = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RAPID_COMBAT = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation ANIM_VORTEX = RawAnimation.begin().thenLoop("animation.zephyrarch.vortex_spin");
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("animation.zephyrarch.idle");
    private static final RawAnimation ANIM_COMBO = RawAnimation.begin().thenPlay("animation.zephyrarch.combo_punch");
    private static final RawAnimation ANIM_SLAM = RawAnimation.begin().thenPlay("animation.zephyrarch.tempest_slam");
    private static final RawAnimation ANIM_BARRAGE = RawAnimation.begin().thenPlay("animation.zephyrarch.gale_barrage");
    private static final RawAnimation ANIM_CYCLONE = RawAnimation.begin().thenPlay("animation.zephyrarch.cyclone_vortex");
    private static final RawAnimation ANIM_PHASE_SHIFT = RawAnimation.begin().thenPlay("animation.zephyrarch.phase_shift");

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable(BAR_KEY, getDisplayName()),
            BossEvent.BossBarColor.BLUE,
            BossEvent.BossBarOverlay.PROGRESS
    );
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final int[] skillCooldowns = new int[ZephyrarchAttack.values().length];

    private boolean configAttributesApplied;
    private boolean phase2Triggered;
    private boolean bypassKillInProgress;
    private int attackTick;
    private double attackStepAccumulator;
    private int globalAttackDelay = 20;

    private int clientLastSerial = -1;
    private int clientVfxSerial = -1;
    private float clientAttackAge;

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
        builder.define(DATA_RAPID_COMBAT, false);
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
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, this::isValidCricketTarget));
    }

    private boolean isValidCricketTarget(LivingEntity candidate) {
        if (!ZephyrarchConfig.CRICKET_MODE_ENABLED.get()) {
            return false;
        }
        if (candidate == this || candidate instanceof Player || candidate instanceof ZephyrarchEntity || candidate instanceof ArmorStand) {
            return false;
        }
        return candidate.isAlive() && candidate.attackable() && !this.isAlliedTo(candidate);
    }

    private boolean canAffectVictim(LivingEntity entity) {
        if (entity == this || !entity.isAlive() || this.isAlliedTo(entity)) {
            return false;
        }
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return true;
    }

    public ZephyrarchAttack getAttack() {
        return ZephyrarchAttack.byId(this.entityData.get(DATA_ATTACK));
    }

    public boolean isPhase2() {
        return this.entityData.get(DATA_PHASE2);
    }

    public boolean isRapidCombat() {
        return this.entityData.get(DATA_RAPID_COMBAT);
    }

    public float getActionSpeedMultiplier() {
        if (getAttack() == ZephyrarchAttack.PHASE_SHIFT) {
            return 1.0F;
        }
        return isRapidCombat() ? (float) ZephyrarchConfig.NO_PLAYER_ACTION_SPEED.get().doubleValue() : 1.0F;
    }

    public float getClientAttackAge(float partialTick) {
        if (getAttack() == ZephyrarchAttack.NONE) {
            return 0.0F;
        }
        return this.clientAttackAge + partialTick * getActionSpeedMultiplier();
    }

    public float getNonPlayerDamageCap() {
        double ratio = ZephyrarchConfig.NON_PLAYER_DAMAGE_CAP_RATIO.get();
        return Math.max(1.0F, (float) (this.getMaxHealth() * ratio));
    }

    private void startAttack(ZephyrarchAttack next) {
        ZephyrarchAttack prev = getAttack();
        this.entityData.set(DATA_ATTACK, next.id);
        if (next != ZephyrarchAttack.NONE) {
            int nextSerial = this.entityData.get(DATA_ATTACK_SERIAL) + 1;
            this.entityData.set(DATA_ATTACK_SERIAL, nextSerial);
            this.getNavigation().stop();
            LivingEntity target = this.getTarget();
            if (target != null && target.isAlive()) {
                double dx = target.getX() - this.getX();
                double dz = target.getZ() - this.getZ();
                float faceYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
                this.setYRot(faceYaw);
                this.yBodyRot = faceYaw;
                this.yHeadRot = faceYaw;
            }
            LOGGER.debug("Zephyrarch [id={}] started attack {} -> {} (serial={}, phase2={}, rapid={})",
                    this.getId(), prev, next, nextSerial, isPhase2(), isRapidCombat());
        } else if (prev != ZephyrarchAttack.NONE) {
            LOGGER.debug("Zephyrarch [id={}] finished attack {}", this.getId(), prev);
        }
        this.attackTick = 0;
        this.attackStepAccumulator = 0.0D;
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
        super.setHealth(this.getMaxHealth());
        this.configAttributesApplied = true;
    }

    private void setBaseAttribute(Holder<Attribute> attr, double val) {
        AttributeInstance inst = this.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(val);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            this.bypassKillInProgress = true;
            try {
                return super.hurt(source, amount);
            } finally {
                this.bypassKillInProgress = false;
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void setHealth(float health) {
        if (!this.level().isClientSide
                && this.configAttributesApplied
                && !this.phase2Triggered
                && !this.bypassKillInProgress
                && !this.isRemoved()
                && health <= 0.0F) {
            triggerPhase2Rebirth();
            return;
        }
        super.setHealth(health);
    }

    public void triggerPhase2Rebirth() {
        if (this.phase2Triggered) {
            return;
        }
        this.phase2Triggered = true;
        this.entityData.set(DATA_PHASE2, true);
        this.dead = false;
        this.deathTime = 0;
        super.setHealth(this.getMaxHealth());
        this.clearFire();

        List<Holder<MobEffect>> harmful = new ArrayList<>();
        for (MobEffectInstance effectInstance : this.getActiveEffects()) {
            if (effectInstance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                harmful.add(effectInstance.getEffect());
            }
        }
        for (Holder<MobEffect> effect : harmful) {
            this.removeEffect(effect);
        }

        this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
        this.bossEvent.setProgress(1.0F);
        startAttack(ZephyrarchAttack.PHASE_SHIFT);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, getX(), getY() + 1.6D, getZ(), 3, 0.5D, 0.5D, 0.5D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.8D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.TOTEM_USE, SoundSource.HOSTILE, 1.5F, 1.15F);
        this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_CHARGE, SoundSource.HOSTILE, 2.2F, 0.7F);
        LOGGER.debug("Zephyrarch [id={}] triggered Phase 2 rebirth and restored full health ({})", this.getId(), this.getMaxHealth());
    }

    private void updateRapidCombatState() {
        if (!ZephyrarchConfig.CRICKET_MODE_ENABLED.get()) {
            if (isRapidCombat()) {
                this.entityData.set(DATA_RAPID_COMBAT, false);
            }
            return;
        }
        double range = ZephyrarchConfig.SURVIVAL_PLAYER_CHECK_RANGE.get();
        double rangeSq = range * range;
        boolean hasSurvivalPlayer = false;
        for (Player player : this.level().players()) {
            if (player.isAlive() && !player.isCreative() && !player.isSpectator() && this.distanceToSqr(player) <= rangeSq) {
                hasSurvivalPlayer = true;
                break;
            }
        }
        boolean shouldRapid = !hasSurvivalPlayer;
        if (shouldRapid != isRapidCombat()) {
            this.entityData.set(DATA_RAPID_COMBAT, shouldRapid);
            LOGGER.debug("Zephyrarch [id={}] rapid combat mode changed to {} (survivalPlayerNearby={})", this.getId(), shouldRapid, hasSurvivalPlayer);
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
            tickClientVfxTimer();
            spawnAmbientClientParticles();
            return;
        }

        applyConfigAttributesOnce();

        if (this.tickCount % 10 == 0) {
            updateRapidCombatState();
        }

        int cdDecrement = isRapidCombat() ? 2 : 1;
        for (int i = 0; i < this.skillCooldowns.length; i++) {
            if (this.skillCooldowns[i] > 0) {
                this.skillCooldowns[i] = Math.max(0, this.skillCooldowns[i] - cdDecrement);
            }
        }
        if (this.globalAttackDelay > 0 && getAttack() == ZephyrarchAttack.NONE) {
            this.globalAttackDelay = Math.max(0, this.globalAttackDelay - cdDecrement);
        }

        ZephyrarchAttack current = getAttack();
        if (current != ZephyrarchAttack.NONE) {
            this.attackStepAccumulator += getActionSpeedMultiplier();
            while (this.attackStepAccumulator >= 1.0D && getAttack() == current) {
                this.attackStepAccumulator -= 1.0D;
                this.attackTick++;
                tickActiveSkill(current);
                if (this.attackTick >= current.duration) {
                    startAttack(ZephyrarchAttack.NONE);
                    int baseDelay = isPhase2() ? 14 : 20;
                    if (isRapidCombat()) {
                        baseDelay = Math.max(2, (int) Math.round(baseDelay * ZephyrarchConfig.NO_PLAYER_COOLDOWN_SCALE.get() * 0.5D));
                    }
                    this.globalAttackDelay = baseDelay;
                    break;
                }
            }
        }

        this.bossEvent.setProgress(Mth.clamp(this.getHealth() / Math.max(1.0F, this.getMaxHealth()), 0.0F, 1.0F));
    }

    private void tickClientVfxTimer() {
        int serial = this.entityData.get(DATA_ATTACK_SERIAL);
        ZephyrarchAttack current = getAttack();
        if (current == ZephyrarchAttack.NONE) {
            this.clientAttackAge = 0.0F;
            return;
        }
        if (serial != this.clientVfxSerial) {
            this.clientVfxSerial = serial;
            this.clientAttackAge = 0.0F;
        } else {
            this.clientAttackAge += getActionSpeedMultiplier();
        }
    }

    private void spawnAmbientClientParticles() {
        if (this.random.nextInt(2) == 0) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double rad = 0.85D + this.random.nextDouble() * 0.75D;
            double px = getX() + Math.cos(angle) * rad;
            double pz = getZ() + Math.sin(angle) * rad;
            double py = getY() + 0.15D + this.random.nextDouble() * 2.3D;
            this.level().addParticle(ParticleTypes.SMALL_GUST, px, py, pz, -Math.sin(angle) * 0.10D, 0.05D, Math.cos(angle) * 0.10D);
        }
        if (isPhase2()) {
            double spinAngle = (this.tickCount * 0.32D) % (Math.PI * 2.0D);
            for (int i = 0; i < 2; i++) {
                double a = spinAngle + i * Math.PI;
                double r = 1.35D;
                this.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                        getX() + Math.cos(a) * r,
                        getY() + 1.5D + Math.sin(this.tickCount * 0.15D + i) * 0.45D,
                        getZ() + Math.sin(a) * r,
                        -Math.sin(a) * 0.06D, 0.04D, Math.cos(a) * 0.06D);
            }
            if (this.tickCount % 3 == 0) {
                this.level().addParticle(ParticleTypes.CLOUD,
                        getX() + (random.nextDouble() - 0.5D) * 1.8D,
                        getY() + 0.25D,
                        getZ() + (random.nextDouble() - 0.5D) * 1.8D,
                        0.0D, 0.05D, 0.0D);
            }
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
                    horiz = horiz.normalize().scale(isPhase2() ? 0.50D : 0.42D);
                    this.setDeltaMovement(horiz.x, this.getDeltaMovement().y, horiz.z);
                }
            }
        }
        if (this.attackTick == 11) {
            performArcStrike(serverLevel, 3.9D, ZephyrarchConfig.COMBO_PUNCH_MULTIPLIER.get(), 0.42D, 0.16D);
        } else if (this.attackTick == 23) {
            performArcStrike(serverLevel, 4.4D, ZephyrarchConfig.COMBO_FINISHER_MULTIPLIER.get(), 0.55D, 0.22D);
            if (isPhase2() && target != null && target.isAlive()) {
                shootChargeTowards(serverLevel, target, 1.40F, 0.0F);
            }
        }
    }

    private void performArcStrike(ServerLevel serverLevel, double reach, double damageMult, double horizKb, double liftY) {
        Vec3 look = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        Vec3 center = position().add(look.scale(1.8D)).add(0.0D, 1.4D, 0.0D);
        serverLevel.sendParticles(ParticleTypes.GUST, center.x, center.y, center.z, 4, 0.6D, 0.3D, 0.6D, 0.0D);
        serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 2, 0.35D, 0.15D, 0.35D, 0.0D);
        serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.75F);

        AABB box = this.getBoundingBox().inflate(reach, 1.6D, reach);
        List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, box, this::canAffectVictim);
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
            Vec3 jumpBoost = new Vec3(0.0D, 0.54D, 0.0D);
            if (target != null && target.isAlive()) {
                Vec3 diff = target.position().subtract(position());
                Vec3 horiz = new Vec3(diff.x, 0.0D, diff.z);
                if (horiz.lengthSqr() > 0.25D) {
                    horiz = horiz.normalize().scale(Math.min(0.68D, Math.sqrt(horiz.lengthSqr()) * 0.13D));
                    jumpBoost = jumpBoost.add(horiz);
                }
            }
            this.setDeltaMovement(jumpBoost);
        } else if (this.attackTick == 16) {
            this.setDeltaMovement(this.getDeltaMovement().x * 0.3D, -0.98D, this.getDeltaMovement().z * 0.3D);
        } else if (this.attackTick == 20) {
            double radius = isPhase2() ? 6.8D : 5.4D;
            serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, getX(), getY() + 0.2D, getZ(), 3, 0.5D, 0.1D, 0.5D, 0.0D);
            for (int i = 0; i < 24; i++) {
                double ang = (Math.PI * 2.0D * i) / 24.0D;
                serverLevel.sendParticles(ParticleTypes.GUST, getX() + Math.cos(ang) * (radius * 0.72D), getY() + 0.3D, getZ() + Math.sin(ang) * (radius * 0.72D), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 2.0F, 0.7F);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_LAND, SoundSource.HOSTILE, 1.8F, 0.8F);

            AABB area = this.getBoundingBox().inflate(radius, 2.5D, radius);
            List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, area, this::canAffectVictim);
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
                float speed = isPhase2() ? 1.48F : 1.25F;
                shootChargeTowards(serverLevel, target, speed, 0.0F);
                if (this.attackTick == 34 && isPhase2()) {
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

        double pullRadius = isPhase2() ? 10.0D : 9.0D;
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
            List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, area, this::canAffectVictim);
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
                if (this.attackTick % 12 == 10 && dist <= 4.4D) {
                    float sliceDmg = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * ZephyrarchConfig.CYCLONE_TICK_MULTIPLIER.get());
                    ZephyrarchCombatEvents.applyAntiHealIfEligible(this, victim);
                    victim.hurt(this.damageSources().mobAttack(this), sliceDmg);
                }
            }
        } else if (this.attackTick == 50) {
            double burstRadius = isPhase2() ? 7.2D : 6.0D;
            serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, getX(), getY() + 1.4D, getZ(), 4, 0.7D, 0.4D, 0.7D, 0.0D);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 2.2F, 0.65F);

            AABB burstBox = this.getBoundingBox().inflate(burstRadius, 3.0D, burstRadius);
            List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, burstBox, this::canAffectVictim);
            for (LivingEntity victim : victims) {
                if (this.distanceToSqr(victim) <= burstRadius * burstRadius) {
                    applyScaledAttack(victim, ZephyrarchConfig.CYCLONE_BURST_MULTIPLIER.get(), 0.62D, 0.28D);
                }
            }
            if (isPhase2()) {
                for (int k = 0; k < 6; k++) {
                    float yaw = k * 60.0F;
                    float dmg = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * ZephyrarchConfig.GALE_CHARGE_MULTIPLIER.get());
                    ZephyrChargeEntity ringShot = new ZephyrChargeEntity(serverLevel, this, dmg);
                    ringShot.setPos(getX(), getY() + 1.8D, getZ());
                    Vec3 dir = Vec3.directionFromRotation(0.0F, yaw);
                    ringShot.shoot(dir.x, -0.05D, dir.z, 1.20F, 0.5F);
                    serverLevel.addFreshEntity(ringShot);
                }
            }
        }
    }

    private void tickPhaseShift(ServerLevel serverLevel) {
        this.getNavigation().stop();
        this.setDeltaMovement(0.0D, 0.02D, 0.0D);
        if (this.attackTick == 24) {
            serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, getX(), getY() + 1.6D, getZ(), 5, 0.9D, 0.6D, 0.9D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.8D, getZ(), 2, 0.2D, 0.2D, 0.2D, 0.0D);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 2.4F, 0.6F);
            double radius = 7.0D;
            AABB box = this.getBoundingBox().inflate(radius, 3.2D, radius);
            List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, box, this::canAffectVictim);
            for (LivingEntity victim : victims) {
                if (this.distanceToSqr(victim) <= radius * radius) {
                    applyScaledAttack(victim, ZephyrarchConfig.PHASE_SHIFT_BURST_MULTIPLIER.get(), 0.58D, 0.28D);
                }
            }
        }
    }

    private void applyScaledAttack(LivingEntity victim, double multiplier, double baseHorizKb, double baseLiftY) {
        ZephyrarchCombatEvents.applyAntiHealIfEligible(this, victim);
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
        if (getAttack() == ZephyrarchAttack.PHASE_SHIFT && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
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
        this.bossEvent.setName(Component.translatable(BAR_KEY, this.getDisplayName()));
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
        this.bossEvent.setName(Component.translatable(BAR_KEY, this.getDisplayName()));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "vortex", 0, state -> state.setAndContinue(ANIM_VORTEX))
                .setAnimationSpeedHandler(entity -> entity.isPhase2() ? 1.65D : 1.0D));

        controllers.add(new AnimationController<>(this, "action", 2, this::handleActionAnim)
                .setAnimationSpeedHandler(entity -> (double) entity.getActionSpeedMultiplier()));
    }

    private PlayState handleActionAnim(AnimationState<ZephyrarchEntity> state) {
        int serial = this.entityData.get(DATA_ATTACK_SERIAL);
        ZephyrarchAttack current = getAttack();
        if (serial != this.clientLastSerial) {
            this.clientLastSerial = serial;
            state.getController().forceAnimationReset();
        }
        return switch (current) {
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

            if (distSq > 16.0D) {
                double moveMult = isPhase2() ? 1.25D : 1.08D;
                if (isRapidCombat()) {
                    moveMult *= 1.25D;
                }
                ZephyrarchEntity.this.getNavigation().moveTo(target, moveMult);
            } else {
                ZephyrarchEntity.this.getNavigation().stop();
            }

            if (globalAttackDelay > 0) {
                return;
            }

            selectNextSkill(distSq);
        }

        private void selectNextSkill(double distSq) {
            double scale = isPhase2() ? 0.80D : 1.00D;
            if (isRapidCombat()) {
                scale *= ZephyrarchConfig.NO_PLAYER_COOLDOWN_SCALE.get();
            }

            if (distSq <= 64.0D && skillCooldowns[ZephyrarchAttack.CYCLONE_VORTEX.id] <= 0 && (isPhase2() || random.nextFloat() < 0.38F)) {
                startAttack(ZephyrarchAttack.CYCLONE_VORTEX);
                skillCooldowns[ZephyrarchAttack.CYCLONE_VORTEX.id] = Math.max(15, (int) Math.round(180 * scale));
                return;
            }

            if (distSq <= 30.0D && skillCooldowns[ZephyrarchAttack.TEMPEST_SLAM.id] <= 0 && random.nextFloat() < 0.58F) {
                startAttack(ZephyrarchAttack.TEMPEST_SLAM);
                skillCooldowns[ZephyrarchAttack.TEMPEST_SLAM.id] = Math.max(12, (int) Math.round(110 * scale));
                return;
            }

            if (distSq <= 18.0D && skillCooldowns[ZephyrarchAttack.COMBO_PUNCH.id] <= 0) {
                startAttack(ZephyrarchAttack.COMBO_PUNCH);
                skillCooldowns[ZephyrarchAttack.COMBO_PUNCH.id] = Math.max(8, (int) Math.round(52 * scale));
                return;
            }

            if (skillCooldowns[ZephyrarchAttack.GALE_BARRAGE.id] <= 0) {
                startAttack(ZephyrarchAttack.GALE_BARRAGE);
                skillCooldowns[ZephyrarchAttack.GALE_BARRAGE.id] = Math.max(10, (int) Math.round(85 * scale));
                return;
            }

            if (isRapidCombat()) {
                if (distSq <= 18.0D) {
                    startAttack(ZephyrarchAttack.COMBO_PUNCH);
                } else {
                    startAttack(ZephyrarchAttack.GALE_BARRAGE);
                }
            }
        }
    }
}
