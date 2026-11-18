package com.hp.zephyrarch.entity;

import com.hp.zephyrarch.config.ZephyrarchConfig;
import com.hp.zephyrarch.event.ZephyrarchCombatEvents;
import com.hp.zephyrarch.network.ZephyrWorldVfxPayload;
import com.hp.zephyrarch.registry.ModSounds;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ZephyrarchEntity extends Monster implements GeoEntity {
    public static final String BAR_KEY = "bossbar.zephyrarch.zephyrarch";
    public static final double LANCE_RANGE = 24.0D;
    public static final double CAGE_RADIUS = 7.0D;
    public static final double CAGE_HEIGHT = 6.0D;
    public static final int CAGE_DURATION = 100;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NBT_CONFIG_APPLIED = "ConfigAttributesApplied";
    private static final String NBT_PHASE2 = "Phase2Triggered";
    private static final double[] WIND_DASH_STEP_WEIGHTS = {0.26D, 0.22D, 0.18D, 0.14D, 0.10D, 0.06D, 0.04D};

    private static final EntityDataAccessor<Integer> DATA_ATTACK = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACK_SERIAL = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PHASE2 = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RAPID_COMBAT = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_LANCE_STATE = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_LANCE_VEC = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> DATA_CAGE_CENTER = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_CAGE_TICKS = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_SKILL_VEC = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_GRABBED_ID = SynchedEntityData.defineId(ZephyrarchEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation ANIM_VORTEX = RawAnimation.begin().thenLoop("animation.zephyrarch.vortex_spin");
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("animation.zephyrarch.idle");
    private static final RawAnimation ANIM_HOVER = RawAnimation.begin().thenLoop("animation.zephyrarch.hover_idle");
    private static final RawAnimation[] ACTION_ANIMS = new RawAnimation[ZephyrarchAttack.values().length];

    static {
        for (ZephyrarchAttack attack : ZephyrarchAttack.values()) {
            if (attack.animation != null) {
                ACTION_ANIMS[attack.id] = RawAnimation.begin().thenPlay(attack.animation);
            }
        }
    }

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
    private ZephyrarchAttack lastSkill = ZephyrarchAttack.NONE;
    private int farFromTargetTicks;
    private float lockedYaw;

    private Vec3 rushDir = Vec3.ZERO;
    private boolean rushStopped;
    private final Set<Integer> rushHit = new HashSet<>();
    private Vec3 hammerTarget = Vec3.ZERO;
    private int grabbedId = -1;
    private final List<Vec3> stormEyeMarks = new ArrayList<>();
    private Vec3 lanceDir = Vec3.ZERO;
    private double lanceLength = LANCE_RANGE;
    private int downdraftLeft;
    private int downdraftTimer;
    private final List<Downdraft> downdrafts = new ArrayList<>();
    private Vec3 cageCenter = Vec3.ZERO;
    private int cageTicks;
    private final Set<Integer> cageInside = new HashSet<>();
    private final Map<Integer, Integer> cageLastHit = new HashMap<>();
    private Vec3 windDashTotalVec = Vec3.ZERO;

    private int clientLastSerial = -1;
    private int clientVfxSerial = -1;
    private float clientAttackAge;

    private static final class Downdraft {
        final Vec3 pos;
        int warn = 15;

        Downdraft(Vec3 pos) {
            this.pos = pos;
        }
    }

    public ZephyrarchEntity(EntityType<? extends ZephyrarchEntity> type, Level level) {
        super(type, level);
        this.xpReward = 350;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 320.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
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
        builder.define(DATA_LANCE_STATE, 0);
        builder.define(DATA_LANCE_VEC, new Vector3f());
        builder.define(DATA_CAGE_CENTER, new Vector3f());
        builder.define(DATA_CAGE_TICKS, 0);
        builder.define(DATA_SKILL_VEC, new Vector3f());
        builder.define(DATA_GRABBED_ID, -1);
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

    public int getLanceState() {
        return this.entityData.get(DATA_LANCE_STATE);
    }

    public Vec3 getLanceVector() {
        Vector3f v = this.entityData.get(DATA_LANCE_VEC);
        return new Vec3(v.x, v.y, v.z);
    }

    public Vec3 getLanceOrigin() {
        return position().add(Vec3.directionFromRotation(0.0F, this.yBodyRot).scale(0.6D)).add(0.0D, 2.0D, 0.0D);
    }

    public int getCageTicks() {
        return this.entityData.get(DATA_CAGE_TICKS);
    }

    public Vec3 getCageCenter() {
        Vector3f v = this.entityData.get(DATA_CAGE_CENTER);
        return new Vec3(v.x, v.y, v.z);
    }

    public Vec3 getSkillVec() {
        Vector3f v = this.entityData.get(DATA_SKILL_VEC);
        return new Vec3(v.x, v.y, v.z);
    }

    public int getGrabbedEntityId() {
        return this.entityData.get(DATA_GRABBED_ID);
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

    private void setSkillVec(Vec3 v) {
        this.entityData.set(DATA_SKILL_VEC, new Vector3f((float) v.x, (float) v.y, (float) v.z));
    }

    private void startAttack(ZephyrarchAttack next) {
        ZephyrarchAttack prev = getAttack();
        this.entityData.set(DATA_ATTACK, next.id);
        if (next != ZephyrarchAttack.NONE) {
            int nextSerial = this.entityData.get(DATA_ATTACK_SERIAL) + 1;
            this.entityData.set(DATA_ATTACK_SERIAL, nextSerial);
            this.getNavigation().stop();
            faceTarget();
            LOGGER.debug("Zephyrarch [id={}] started attack {} -> {} (serial={}, phase2={}, rapid={})",
                    this.getId(), prev, next, nextSerial, isPhase2(), isRapidCombat());
        }
        if (prev == ZephyrarchAttack.TEMPEST_LANCE || next == ZephyrarchAttack.NONE) {
            this.entityData.set(DATA_LANCE_STATE, 0);
        }
        if (prev == ZephyrarchAttack.GALE_GRAB) {
            this.grabbedId = -1;
            this.entityData.set(DATA_GRABBED_ID, -1);
        }
        this.attackTick = 0;
        this.attackStepAccumulator = 0.0D;
    }

    private void faceTarget() {
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();
            if (dx * dx + dz * dz > 1.0E-4D) {
                this.lockedYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
            }
        } else {
            this.lockedYaw = this.yBodyRot;
        }
        applyLockedYaw();
    }

    private void applyLockedYaw() {
        this.setYRot(this.lockedYaw);
        this.yBodyRot = this.lockedYaw;
        this.yHeadRot = this.lockedYaw;
    }

    private Vec3 forward() {
        return Vec3.directionFromRotation(0.0F, this.lockedYaw);
    }

    private Vec3 right() {
        Vec3 f = forward();
        return new Vec3(-f.z, 0.0D, f.x);
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

        releaseGrab();
        this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
        this.bossEvent.setProgress(1.0F);
        java.util.Arrays.fill(this.skillCooldowns, 0);
        startAttack(ZephyrarchAttack.PHASE_SHIFT);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.8D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        playBossSound(ModSounds.PHASE_SHIFT.get(), 2.6F, 1.0F);
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
        }
    }

    private void playBossSound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, getX(), getY() + 1.5D, getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    @Override
    public void tick() {
        boolean hovering = isPhase2();
        this.setNoGravity(hovering);
        super.tick();

        ZephyrarchAttack current = getAttack();
        Vec3 motion = this.getDeltaMovement();
        if (!hovering && !this.onGround() && motion.y < -0.18D && current != ZephyrarchAttack.SKY_HAMMER) {
            this.setDeltaMovement(motion.x, motion.y * 0.78D, motion.z);
        }

        if (this.level().isClientSide) {
            tickClientVfxTimer();
            spawnAmbientClientParticles();
            return;
        }
        ServerLevel serverLevel = (ServerLevel) this.level();

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
        if (this.globalAttackDelay > 0 && current == ZephyrarchAttack.NONE) {
            this.globalAttackDelay = Math.max(0, this.globalAttackDelay - cdDecrement);
        }

        if (hovering && current != ZephyrarchAttack.WIND_DASH) {
            tickHover(current);
        }

        if (current != ZephyrarchAttack.NONE) {
            applyLockedYaw();
            this.attackStepAccumulator += getActionSpeedMultiplier();
            while (this.attackStepAccumulator >= 1.0D && getAttack() == current) {
                this.attackStepAccumulator -= 1.0D;
                this.attackTick++;
                tickActiveSkill(serverLevel, current);
                if (getAttack() == current && this.attackTick >= current.duration) {
                    finishAttack();
                    break;
                }
            }
        }

        tickDowndrafts(serverLevel);
        tickCage(serverLevel);
        this.bossEvent.setProgress(Mth.clamp(this.getHealth() / Math.max(1.0F, this.getMaxHealth()), 0.0F, 1.0F));
    }

    private void finishAttack() {
        ZephyrarchAttack finished = getAttack();
        startAttack(ZephyrarchAttack.NONE);
        if (finished == ZephyrarchAttack.WIND_DASH) {
            this.globalAttackDelay = Math.min(this.globalAttackDelay, isRapidCombat() ? 3 : 6);
            return;
        }
        int baseDelay = isPhase2() ? 28 : 20;
        if (isRapidCombat()) {
            baseDelay = Math.max(2, (int) Math.round(baseDelay * ZephyrarchConfig.NO_PLAYER_COOLDOWN_SCALE.get() * 0.5D));
        } else {
            baseDelay += this.random.nextInt(isPhase2() ? 7 : 11);
        }
        this.globalAttackDelay = baseDelay;
    }

    private void tickHover(ZephyrarchAttack current) {
        double groundY = findGroundY();
        double wantY = groundY + ZephyrarchConfig.HOVER_HEIGHT.get();
        double vy = Mth.clamp((wantY - getY()) * 0.18D, -0.25D, 0.25D);
        Vec3 m = getDeltaMovement();
        double hx = m.x * 0.8D;
        double hz = m.z * 0.8D;

        LivingEntity target = getTarget();
        if (current == ZephyrarchAttack.NONE && target != null && target.isAlive()) {
            Vec3 to = target.position().subtract(position());
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            double dist = flat.length();
            if (dist > 1.0E-3D) {
                Vec3 dir = flat.scale(1.0D / dist);
                double baseSpeed = getAttributeValue(Attributes.MOVEMENT_SPEED);
                double sideSign = ((this.tickCount / 80) % 2 == 0) ? 1.0D : -1.0D;
                Vec3 sideVec = new Vec3(-dir.z, 0.0D, dir.x).scale(sideSign);
                Vec3 want;
                if (dist > 16.0D) {
                    want = dir.scale(baseSpeed * 0.68D).add(sideVec.scale(baseSpeed * 0.18D));
                } else if (dist < 8.5D) {
                    double urgency = Mth.clamp((8.5D - dist) / 6.0D, 0.0D, 1.0D);
                    double retreatSpeed = baseSpeed * Mth.lerp(urgency, 0.62D, 1.08D);
                    want = dir.scale(-retreatSpeed).add(sideVec.scale(baseSpeed * 0.36D));
                } else {
                    want = sideVec.scale(baseSpeed * 0.32D);
                }
                hx = Mth.lerp(0.24D, m.x, want.x);
                hz = Mth.lerp(0.24D, m.z, want.z);
            }
            this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        setDeltaMovement(hx, vy, hz);
    }

    private double findGroundY() {
        Vec3 from = position().add(0.0D, 0.5D, 0.0D);
        HitResult hit = this.level().clip(new ClipContext(from, from.add(0.0D, -12.0D, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        return hit.getType() == HitResult.Type.MISS ? getY() - ZephyrarchConfig.HOVER_HEIGHT.get() : hit.getLocation().y;
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
        if (this.random.nextInt(4) == 0) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double rad = 0.90D + this.random.nextDouble() * 0.65D;
            double px = getX() + Math.cos(angle) * rad;
            double pz = getZ() + Math.sin(angle) * rad;
            double py = getY() + 0.20D + this.random.nextDouble() * 2.1D;
            this.level().addParticle(ParticleTypes.SMALL_GUST, px, py, pz, -Math.sin(angle) * 0.08D, 0.04D, Math.cos(angle) * 0.08D);
        }
    }

    private int strikeArc(ServerLevel level, double reach, double cosHalf, double mult, double kb, double lift) {
        Vec3 look = forward();
        Vec3 center = position().add(look.scale(reach * 0.5D)).add(0.0D, 1.5D, 0.0D);
        level.sendParticles(ParticleTypes.SMALL_GUST, center.x, center.y, center.z, 2, 0.35D, 0.2D, 0.35D, 0.01D);
        int hits = 0;
        for (LivingEntity victim : victimsInRadius(level, reach + 1.0D, 2.0D)) {
            Vec3 to = victim.position().subtract(position());
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            double d = flat.length() - victim.getBbWidth() * 0.5D;
            if (d > reach) {
                continue;
            }
            if (flat.lengthSqr() > 0.5D && flat.normalize().dot(look) < cosHalf) {
                continue;
            }
            if (applyScaledAttack(victim, mult, kb, lift)) {
                hits++;
            }
        }
        return hits;
    }

    private List<LivingEntity> victimsInRadius(ServerLevel level, double radius, double height) {
        AABB box = new AABB(getX() - radius, getY() - 1.0D, getZ() - radius, getX() + radius, getY() + height + 1.5D, getZ() + radius);
        return level.getEntitiesOfClass(LivingEntity.class, box, this::canAffectVictim);
    }

    private List<LivingEntity> victimsAround(ServerLevel level, Vec3 center, double radius, double below, double above) {
        AABB box = new AABB(center.x - radius, center.y - below, center.z - radius, center.x + radius, center.y + above, center.z + radius);
        List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, box, this::canAffectVictim);
        list.removeIf(e -> horizontalDist(e.position(), center) > radius + e.getBbWidth() * 0.5D);
        return list;
    }

    private static double horizontalDist(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean applyScaledAttack(LivingEntity victim, double multiplier, double baseHorizKb, double baseLiftY) {
        return applyScaledAttackFrom(victim, position(), multiplier, baseHorizKb, baseLiftY);
    }

    private boolean applyScaledAttackFrom(LivingEntity victim, Vec3 origin, double multiplier, double baseHorizKb, double baseLiftY) {
        ZephyrarchCombatEvents.applyAntiHealIfEligible(this, victim);
        float damage = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * multiplier);
        boolean hit = victim.hurt(this.damageSources().mobAttack(this), damage);
        if (!hit) {
            return false;
        }
        double resist = Math.max(0.0D, 1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        double scale = ZephyrarchConfig.KNOCKBACK_SCALE.get();
        double finalKb = baseHorizKb * resist * scale;
        double finalLift = baseLiftY * resist * scale;
        if (Math.abs(finalKb) > 0.01D || Math.abs(finalLift) > 0.01D) {
            Vec3 away = victim.position().subtract(origin);
            Vec3 horiz = new Vec3(away.x, 0.0D, away.z);
            horiz = horiz.lengthSqr() < 1.0E-4D ? forward() : horiz.normalize();
            victim.push(horiz.x * finalKb, finalLift, horiz.z * finalKb);
            victim.hurtMarked = true;
        }
        return true;
    }

    private void tickActiveSkill(ServerLevel level, ZephyrarchAttack attack) {
        LivingEntity target = this.getTarget();
        if (target != null && !target.isAlive()) {
            target = null;
        }
        if (attack != ZephyrarchAttack.SKY_HAMMER && attack != ZephyrarchAttack.GALE_RUSH && attack != ZephyrarchAttack.WIND_DASH && !isPhase2()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.5D, 1.0D, 0.5D));
        }
        switch (attack) {
            case GALE_COMBO -> tickGaleCombo(level, target);
            case ARM_SWEEP -> tickArmSweep(level);
            case GALE_RUSH -> tickGaleRush(level, target);
            case SKY_HAMMER -> tickSkyHammer(level, target);
            case GALE_GRAB -> tickGaleGrab(level, target);
            case VORTEX_STOMP -> tickVortexStomp(level);
            case PHASE_SHIFT -> tickPhaseShift(level);
            case GALE_BOLTS -> tickGaleBolts(level, target);
            case TEMPEST_LANCE -> tickTempestLance(level, target);
            case STORM_EYE -> tickStormEye(level, target);
            case DOWNDRAFT -> tickDowndraftCast(level, target);
            case BOOMERANG_BLADES -> tickBoomerangBlades(level, target);
            case CYCLONE_CAGE -> tickCycloneCage(level, target);
            case WIND_DASH -> tickWindDash(level, target);
            default -> {
            }
        }
    }

    private void tickGaleCombo(ServerLevel level, LivingEntity target) {
        int t = this.attackTick;
        if (t < 9 || (t > 12 && t < 21)) {
            faceTarget();
        }
        if (t == 6 || t == 18) {
            playBossSound(ModSounds.PUNCH_SWING.get(), 1.4F, t == 6 ? 1.0F : 0.85F);
            Vec3 lunge = forward().scale(0.42D);
            this.setDeltaMovement(lunge.x, this.getDeltaMovement().y, lunge.z);
        }
        if (t == 11) {
            strikeArc(level, 3.9D, 0.707D, ZephyrarchConfig.COMBO_FIRST_MULTIPLIER.get(), 0.4D, 0.12D);
            playBossSound(ModSounds.PUNCH_IMPACT.get(), 1.6F, 1.05F);
        } else if (t == 23) {
            strikeArc(level, 4.4D, 0.64D, ZephyrarchConfig.COMBO_SECOND_MULTIPLIER.get(), 0.6D, 0.2D);
            playBossSound(ModSounds.PUNCH_IMPACT.get(), 1.8F, 0.85F);
        }
    }

    private void tickArmSweep(ServerLevel level) {
        int t = this.attackTick;
        if (t == 13) {
            playBossSound(ModSounds.SWEEP_SPIN.get(), 1.8F, 1.0F);
        }
        if (t == 17) {
            for (LivingEntity victim : victimsAround(level, position(), 5.0D, 0.5D, 3.0D)) {
                if (victim.getY() - getY() <= 3.0D) {
                    applyScaledAttack(victim, ZephyrarchConfig.ARM_SWEEP_MULTIPLIER.get(), 1.2D, 0.25D);
                }
            }
            level.sendParticles(ParticleTypes.GUST, getX(), getY() + 1.3D, getZ(), 2, 0.5D, 0.1D, 0.5D, 0.0D);
        }
    }

    private void tickGaleRush(ServerLevel level, LivingEntity target) {
        int t = this.attackTick;
        if (t == 1) {
            this.rushHit.clear();
            this.rushStopped = false;
        }
        if (t <= 12) {
            faceTarget();
            this.rushDir = forward();
            setSkillVec(this.rushDir);
        }
        if (t == 2) {
            playBossSound(ModSounds.RUSH_CHARGE.get(), 1.6F, 1.0F);
        }
        if (t >= 16 && t <= 22) {
            if (t == 16) {
                playBossSound(ModSounds.RUSH_DASH.get(), 2.0F, 1.0F);
            }
            if (!this.rushStopped) {
                Vec3 before = position();
                this.move(MoverType.SELF, this.rushDir.scale(1.43D));
                if (this.horizontalCollision || position().distanceToSqr(before) < 0.25D) {
                    this.rushStopped = true;
                }
                Vec3 side = right();
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.75D, 0.2D, 0.75D), this::canAffectVictim)) {
                    if (this.rushHit.add(victim.getId())) {
                        double sign = victim.position().subtract(position()).dot(side) >= 0.0D ? 1.0D : -1.0D;
                        if (applyScaledAttackFrom(victim, victim.position().subtract(side.scale(sign)), ZephyrarchConfig.RUSH_PATH_MULTIPLIER.get(), 0.9D, 0.2D)) {
                            playBossSound(ModSounds.PUNCH_IMPACT.get(), 1.4F, 1.1F);
                        }
                    }
                }
            }
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        }
        if (t == 23) {
            strikeArc(level, 3.0D, 0.5D, ZephyrarchConfig.RUSH_FINISH_MULTIPLIER.get(), 1.0D, 0.25D);
            playBossSound(ModSounds.PUNCH_IMPACT.get(), 1.8F, 0.9F);
        }
    }

    private void tickSkyHammer(ServerLevel level, LivingEntity target) {
        int t = this.attackTick;
        if (t == 1) {
            playBossSound(ModSounds.HAMMER_RISE.get(), 1.8F, 1.0F);
            this.hammerTarget = position();
            setSkillVec(this.hammerTarget);
        }
        if (t <= 10) {
            faceTarget();
            this.setDeltaMovement(0.0D, 0.32D, 0.0D);
        } else if (t <= 18) {
            if (t == 14) {
                Vec3 wanted = target != null ? target.position() : position();
                Vec3 flat = new Vec3(wanted.x - getX(), 0.0D, wanted.z - getZ());
                if (flat.length() > 6.0D) {
                    flat = flat.normalize().scale(6.0D);
                }
                this.hammerTarget = new Vec3(getX() + flat.x, wanted.y, getZ() + flat.z);
                setSkillVec(this.hammerTarget);
            }
            if (t >= 14) {
                Vec3 flat = new Vec3(this.hammerTarget.x - getX(), 0.0D, this.hammerTarget.z - getZ());
                double len = flat.length();
                Vec3 step = len > 1.2D ? flat.scale(1.2D / len) : flat;
                this.move(MoverType.SELF, step);
            }
            this.setDeltaMovement(0.0D, 0.0D, 0.0D);
        } else if (t < 22) {
            this.setDeltaMovement(0.0D, -1.6D, 0.0D);
        }
        if (t == 22) {
            Vec3 c = new Vec3(getX(), Math.min(getY(), this.hammerTarget.y + 0.5D), getZ());
            for (LivingEntity victim : victimsAround(level, c, 4.0D, 1.5D, 3.0D)) {
                double d = horizontalDist(victim.position(), c);
                double mult = d <= 1.5D ? ZephyrarchConfig.HAMMER_CENTER_MULTIPLIER.get() : ZephyrarchConfig.HAMMER_EDGE_MULTIPLIER.get();
                applyScaledAttackFrom(victim, c, mult, 0.4D, 0.8D);
            }
            level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, c.x, c.y + 0.25D, c.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            playBossSound(ModSounds.HAMMER_SLAM.get(), 2.6F, 1.0F);
        }
    }

    private void tickGaleGrab(ServerLevel level, LivingEntity target) {
        int t = this.attackTick;
        if (t < 10) {
            faceTarget();
            if (t == 3) {
                playBossSound(ModSounds.PUNCH_SWING.get(), 1.4F, 0.7F);
            }
            return;
        }
        if (t == 10) {
            LivingEntity caught = findGrabVictim(level, target);
            if (caught == null) {
                startAttack(ZephyrarchAttack.GRAB_WHIFF);
                return;
            }
            if (caught instanceof Player player && player.isBlocking()
                    && player.getLookAngle().dot(position().subtract(player.position()).normalize()) > 0.0D) {
                player.disableShield();
                playBossSound(SoundEvents.SHIELD_BLOCK, 1.6F, 0.7F);
                playBossSound(ModSounds.STAGGER.get(), 1.6F, 1.0F);
                startAttack(ZephyrarchAttack.STAGGER);
                return;
            }
            this.grabbedId = caught.getId();
            this.entityData.set(DATA_GRABBED_ID, this.grabbedId);
            playBossSound(ModSounds.GRAB_CATCH.get(), 1.8F, 1.0F);
        }
        Entity held = this.grabbedId >= 0 ? level.getEntity(this.grabbedId) : null;
        if (!(held instanceof LivingEntity victim) || !victim.isAlive()) {
            if (t <= 32) {
                releaseGrab();
            }
            return;
        }
        if (t < 32) {
            Vec3 hand = position().add(forward().scale(1.6D)).add(right().scale(0.9D)).add(0.0D, 3.2D - victim.getBbHeight() * 0.5D, 0.0D);
            victim.teleportTo(hand.x, hand.y, hand.z);
            victim.setDeltaMovement(Vec3.ZERO);
            victim.fallDistance = 0.0F;
            victim.hurtMarked = true;
            if (t % 5 == 0) {
                ZephyrarchCombatEvents.applyAntiHealIfEligible(this, victim);
                victim.hurt(this.damageSources().mobAttack(this), (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * ZephyrarchConfig.GRAB_SQUEEZE_MULTIPLIER.get()));
            }
        } else if (t == 32) {
            Vec3 back = forward().scale(-1.6D);
            double resist = Math.max(0.0D, 1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            double s = Math.max(0.35D, resist) * ZephyrarchConfig.KNOCKBACK_SCALE.get();
            victim.setDeltaMovement(back.x * s, 0.9D * s, back.z * s);
            victim.hurtMarked = true;
            playBossSound(ModSounds.GRAB_THROW.get(), 1.8F, 1.0F);
            releaseGrab();
        }
    }

    private LivingEntity findGrabVictim(ServerLevel level, LivingEntity preferred) {
        Vec3 look = forward();
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : victimsInRadius(level, 4.5D, 3.0D)) {
            if (e.getBbWidth() > getBbWidth() || e.getBbHeight() > getBbHeight() || e instanceof ZephyrarchEntity) {
                continue;
            }
            Vec3 flat = new Vec3(e.getX() - getX(), 0.0D, e.getZ() - getZ());
            double d = flat.length() - e.getBbWidth() * 0.5D;
            if (d > 3.5D || (flat.lengthSqr() > 0.25D && flat.normalize().dot(look) < 0.866D)) {
                continue;
            }
            if (e == preferred) {
                return e;
            }
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private void releaseGrab() {
        this.grabbedId = -1;
        this.entityData.set(DATA_GRABBED_ID, -1);
    }

    private void tickVortexStomp(ServerLevel level) {
        int t = this.attackTick;
        if (t == 2) {
            playBossSound(ModSounds.STOMP_PULL.get(), 1.8F, 1.0F);
        }
        if (t >= 8 && t <= 28) {
            double kbScale = ZephyrarchConfig.KNOCKBACK_SCALE.get();
            for (LivingEntity victim : victimsAround(level, position(), 6.0D, 1.0D, 4.0D)) {
                double resist = Math.max(0.0D, 1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                double pull = 0.08D * resist * kbScale * (victim.isSprinting() ? 0.35D : 1.0D);
                Vec3 to = new Vec3(getX() - victim.getX(), 0.0D, getZ() - victim.getZ());
                if (to.lengthSqr() > 1.0D && pull > 0.002D) {
                    to = to.normalize().scale(pull);
                    victim.push(to.x, 0.0D, to.z);
                    victim.hurtMarked = true;
                }
            }
        }
        if (t == 30) {
            for (LivingEntity victim : victimsAround(level, position(), 3.5D, 1.0D, 3.5D)) {
                applyScaledAttack(victim, ZephyrarchConfig.STOMP_MULTIPLIER.get(), 1.4D, 0.5D);
            }
            level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, getX(), getY() + 0.5D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            playBossSound(ModSounds.STOMP_BURST.get(), 2.4F, 1.0F);
        }
    }

    private void tickPhaseShift(ServerLevel level) {
        this.getNavigation().stop();
        this.setDeltaMovement(0.0D, 0.02D, 0.0D);
        if (this.attackTick == 21) {
            level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.8D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            for (LivingEntity victim : victimsAround(level, position(), 6.0D, 1.5D, 4.0D)) {
                applyScaledAttack(victim, ZephyrarchConfig.PHASE_SHIFT_BURST_MULTIPLIER.get(), 2.0D, 0.3D);
            }
        }
    }

    private Vec3 handPos(boolean rightHand) {
        return position().add(forward().scale(1.0D)).add(right().scale(rightHand ? 1.3D : -1.3D)).add(0.0D, 2.3D, 0.0D);
    }

    private void tickGaleBolts(ServerLevel level, LivingEntity target) {
        int t = this.attackTick;
        if (target == null) {
            return;
        }
        faceTarget();
        if (t >= 10 && t <= 40 && (t - 10) % 6 == 0) {
            int shot = (t - 10) / 6;
            Vec3 from = handPos(shot % 2 == 0);
            Vec3 aim = target.getBoundingBox().getCenter();
            if (shot >= 3) {
                double flight = aim.distanceTo(from) / 1.4D;
                Vec3 vel = target.getDeltaMovement();
                aim = aim.add(vel.x * flight, 0.0D, vel.z * flight);
            }
            Vec3 dir = aim.subtract(from);
            ZephyrChargeEntity bolt = new ZephyrChargeEntity(level, this)
                    .configure(ZephyrarchConfig.BOLT_MULTIPLIER.get(), 1.2D, 0.5D, 40, 0.6F);
            bolt.setPos(from.x, from.y, from.z);
            bolt.shoot(dir.x, dir.y, dir.z, 1.4F, 0.0F);
            level.addFreshEntity(bolt);
            level.playSound(null, from.x, from.y, from.z, ModSounds.BOLT_SHOOT.get(), SoundSource.HOSTILE, 1.5F, 0.9F + this.random.nextFloat() * 0.2F);
        }
    }

    private void tickTempestLance(ServerLevel level, LivingEntity target) {
        int t = this.attackTick;
        if (t == 2) {
            playBossSound(ModSounds.LANCE_CHARGE.get(), 2.0F, 1.0F);
        }
        if (t <= 30) {
            faceTarget();
            Vec3 origin = getLanceOrigin();
            Vec3 aim = target != null ? target.getBoundingBox().getCenter() : origin.add(forward().scale(LANCE_RANGE));
            Vec3 dir = aim.subtract(origin);
            this.lanceDir = dir.lengthSqr() > 1.0E-4D ? dir.normalize() : forward();
            this.lanceLength = traceLance(origin, this.lanceDir);
            this.entityData.set(DATA_LANCE_STATE, t == 30 ? 2 : 1);
            syncLanceVector();
        }
        if (t == 30) {
            playBossSound(SoundEvents.BEACON_ACTIVATE, 1.4F, 1.6F);
        }
        if (t == 36) {
            Vec3 origin = getLanceOrigin();
            this.lanceLength = traceLance(origin, this.lanceDir);
            syncLanceVector();
            this.entityData.set(DATA_LANCE_STATE, 3);
            Vec3 end = origin.add(this.lanceDir.scale(this.lanceLength));
            AABB box = new AABB(origin, end).inflate(1.5D);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, this::canAffectVictim)) {
                Vec3 c = victim.getBoundingBox().getCenter();
                double along = Mth.clamp(c.subtract(origin).dot(this.lanceDir), 0.0D, this.lanceLength);
                Vec3 nearest = origin.add(this.lanceDir.scale(along));
                if (c.distanceTo(nearest) <= 0.5D + victim.getBbWidth() * 0.5D + 0.3D) {
                    applyScaledAttackFrom(victim, nearest.subtract(this.lanceDir), ZephyrarchConfig.LANCE_MULTIPLIER.get(), 0.8D, 0.15D);
                }
            }
            ZephyrWorldVfxPayload.sendPoint(this, ZephyrWorldVfxPayload.KIND_CHARGE_BURST, end, 1.75F, 11);
            playBossSound(ModSounds.LANCE_FIRE.get(), 2.6F, 1.0F);
        }
        if (t == 44) {
            this.entityData.set(DATA_LANCE_STATE, 0);
        }
    }

    private double traceLance(Vec3 origin, Vec3 dir) {
        HitResult hit = this.level().clip(new ClipContext(origin, origin.add(dir.scale(LANCE_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? LANCE_RANGE : hit.getLocation().distanceTo(origin);
    }

    private void syncLanceVector() {
        Vec3 v = this.lanceDir.scale(this.lanceLength);
        this.entityData.set(DATA_LANCE_VEC, new Vector3f((float) v.x, (float) v.y, (float) v.z));
    }

    private void tickStormEye(ServerLevel level, LivingEntity target) {
        int t = this.attackTick;
        if (t == 1) {
            this.stormEyeMarks.clear();
            if (target != null) {
                this.stormEyeMarks.add(target.position());
                for (int i = 0; i < 2; i++) {
                    double a = this.random.nextDouble() * Math.PI * 2.0D;
                    double r = 3.0D + this.random.nextDouble() * 3.0D;
                    this.stormEyeMarks.add(target.position().add(Math.cos(a) * r, 0.0D, Math.sin(a) * r));
                }
                for (Vec3 mark : this.stormEyeMarks) {
                    ZephyrWorldVfxPayload.sendPoint(this, ZephyrWorldVfxPayload.KIND_STORM_EYE_MARK, mark, 1.5F, 20);
                }
            }
            playBossSound(ModSounds.RUSH_CHARGE.get(), 1.4F, 1.3F);
        }
        if (target != null && t < 20) {
            faceTarget();
        }
        if (t == 20) {
            for (Vec3 mark : this.stormEyeMarks) {
                level.addFreshEntity(new ZephyrTwisterEntity(level, this, mark.add(0.0D, 0.2D, 0.0D)));
                level.playSound(null, mark.x, mark.y, mark.z, ModSounds.TWISTER_SPAWN.get(), SoundSource.HOSTILE, 1.6F, 0.9F + this.random.nextFloat() * 0.2F);
            }
            this.stormEyeMarks.clear();
        }
    }

    private void tickDowndraftCast(ServerLevel level, LivingEntity target) {
        if (target != null && this.attackTick < 12) {
            faceTarget();
        }
        if (this.attackTick == 4) {
            playBossSound(ModSounds.HAMMER_RISE.get(), 1.6F, 1.3F);
        }
        if (this.attackTick == 12) {
            this.downdraftLeft = 8;
            this.downdraftTimer = 0;
        }
    }

    private void tickDowndrafts(ServerLevel level) {
        if (this.downdraftLeft > 0) {
            if (this.downdraftTimer-- <= 0) {
                this.downdraftTimer = 6;
                LivingEntity target = getTarget();
                if (target == null || !target.isAlive()) {
                    this.downdraftLeft = 0;
                } else {
                    boolean direct = this.downdraftLeft > 6;
                    Vec3 p = target.position();
                    if (!direct) {
                        double a = this.random.nextDouble() * Math.PI * 2.0D;
                        double r = this.random.nextDouble() * 6.0D;
                        p = p.add(Math.cos(a) * r, 0.0D, Math.sin(a) * r);
                    }
                    this.downdrafts.add(new Downdraft(p));
                    ZephyrWorldVfxPayload.sendPoint(this, ZephyrWorldVfxPayload.KIND_DOWNDRAFT_COLUMN, p, 2.0F, 28);
                    this.downdraftLeft--;
                }
            }
        }
        Iterator<Downdraft> it = this.downdrafts.iterator();
        while (it.hasNext()) {
            Downdraft d = it.next();
            d.warn--;
            if (d.warn > 0) {
                continue;
            }
            for (LivingEntity victim : victimsAround(level, d.pos, 2.0D, 1.0D, 4.5D)) {
                if (applyScaledAttackFrom(victim, d.pos, ZephyrarchConfig.DOWNDRAFT_MULTIPLIER.get(), 0.15D, 0.0D)) {
                    victim.push(0.0D, -0.6D, 0.0D);
                    victim.hurtMarked = true;
                }
            }
            level.playSound(null, d.pos.x, d.pos.y, d.pos.z, ModSounds.DOWNDRAFT_IMPACT.get(), SoundSource.HOSTILE, 1.6F, 0.9F + this.random.nextFloat() * 0.2F);
            it.remove();
        }
    }

    private void tickBoomerangBlades(ServerLevel level, LivingEntity target) {
        int t = this.attackTick;
        if (target != null && t <= 12) {
            faceTarget();
        }
        if (t == 12 && target != null) {
            double hDist = horizontalDist(position(), target.position());
            Vec3 vel = target.getDeltaMovement();
            Vec3 lead = new Vec3(vel.x, 0.0D, vel.z).scale(Mth.clamp(hDist * 0.85D, 2.0D, 9.5D));
            if (lead.length() > 3.2D) {
                lead = lead.normalize().scale(3.2D);
            }
            double hitY = target.getY() + Mth.clamp(target.getBbHeight() * 0.52D, 0.45D, 1.55D);
            Vec3 crossPoint = new Vec3(target.getX() + lead.x, hitY, target.getZ() + lead.z);
            for (int side = -1; side <= 1; side += 2) {
                Vec3 start = position().add(right().scale(1.25D * side)).add(0.0D, 1.55D, 0.0D);
                level.addFreshEntity(new ZephyrBladeEntity(level, this, start, crossPoint, target.getId(), side));
            }
            playBossSound(ModSounds.BLADE_THROW.get(), 2.0F, 1.0F);
        }
    }

    private void tickCycloneCage(ServerLevel level, LivingEntity target) {
        int t = this.attackTick;
        if (t == 1) {
            playBossSound(ModSounds.CAGE_FORM.get(), 2.4F, 1.0F);
        }
        if (target != null) {
            faceTarget();
        }
        if (t == 20 && target != null) {
            this.cageCenter = target.position();
            this.cageTicks = CAGE_DURATION;
            this.cageInside.clear();
            this.cageLastHit.clear();
            for (LivingEntity e : victimsAround(level, this.cageCenter, CAGE_RADIUS, 2.0D, CAGE_HEIGHT)) {
                this.cageInside.add(e.getId());
            }
            this.entityData.set(DATA_CAGE_CENTER, new Vector3f((float) cageCenter.x, (float) cageCenter.y, (float) cageCenter.z));
            this.entityData.set(DATA_CAGE_TICKS, this.cageTicks);
        }
        if ((t == 28 || t == 40 || t == 52) && target != null && this.cageTicks > 0) {
            Vec3 from = t == 28 ? handPos(true) : (t == 40 ? handPos(false) : position().add(forward().scale(0.95D)).add(0.0D, 2.15D, 0.0D));
            Vec3 dir = target.getBoundingBox().getCenter().subtract(from);
            ZephyrChargeEntity orb = new ZephyrChargeEntity(level, this)
                    .configure(ZephyrarchConfig.CAGE_ORB_MULTIPLIER.get(), 2.5D, 0.6D, 80, 1.5F)
                    .homing(target, 4.0F);
            orb.setPos(from.x, from.y, from.z);
            orb.shoot(dir.x, dir.y, dir.z, 0.35F, 0.0F);
            level.addFreshEntity(orb);
            playBossSound(ModSounds.ORB_SHOOT.get(), 1.8F, 1.0F);
        }
    }

    private void tickCage(ServerLevel level) {
        if (this.cageTicks <= 0) {
            return;
        }
        this.cageTicks--;
        if (this.cageTicks % 10 == 0 || this.cageTicks == 0) {
            this.entityData.set(DATA_CAGE_TICKS, this.cageTicks);
        }
        float damage = (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * ZephyrarchConfig.CAGE_WALL_MULTIPLIER.get());
        Iterator<Integer> it = this.cageInside.iterator();
        while (it.hasNext()) {
            Entity e = level.getEntity(it.next());
            if (!(e instanceof LivingEntity victim) || !victim.isAlive() || !canAffectVictim(victim)) {
                it.remove();
                continue;
            }
            Vec3 off = new Vec3(victim.getX() - cageCenter.x, 0.0D, victim.getZ() - cageCenter.z);
            double d = off.length();
            if (d < CAGE_RADIUS - 0.6D || d > CAGE_RADIUS + 4.0D) {
                continue;
            }
            Vec3 in = d > 1.0E-3D ? off.scale(-1.0D / d) : Vec3.ZERO;
            victim.setDeltaMovement(in.x * 0.6D, Math.max(victim.getDeltaMovement().y, 0.1D), in.z * 0.6D);
            victim.hurtMarked = true;
            Integer last = this.cageLastHit.get(victim.getId());
            if (last == null || last - this.cageTicks >= 10) {
                this.cageLastHit.put(victim.getId(), this.cageTicks);
                ZephyrarchCombatEvents.applyAntiHealIfEligible(this, victim);
                victim.hurt(this.damageSources().mobAttack(this), damage);
            }
        }
        if (this.cageTicks == 0) {
            this.cageInside.clear();
        }
    }

    private void planWindDash(LivingEntity target) {
        double desired = ZephyrarchConfig.WIND_DASH_TARGET_DISTANCE.get();
        Vec3 awayBase;
        double curDist;
        double closingBonus = 0.0D;
        if (target != null && target.isAlive()) {
            Vec3 fromTarget = new Vec3(getX() - target.getX(), 0.0D, getZ() - target.getZ());
            curDist = fromTarget.length();
            awayBase = curDist > 1.0E-3D ? fromTarget.scale(1.0D / curDist) : forward().scale(-1.0D);
            Vec3 targetHorizVel = new Vec3(target.getDeltaMovement().x, 0.0D, target.getDeltaMovement().z);
            double closing = Math.max(0.0D, targetHorizVel.dot(awayBase));
            closingBonus = Mth.clamp(closing * 6.0D, 0.0D, 2.5D);
        } else {
            curDist = 3.0D;
            awayBase = forward().scale(-1.0D);
        }

        double idealDash = Mth.clamp((desired - curDist) + closingBonus, 3.8D, 10.5D);
        double sidePref = this.random.nextBoolean() ? 1.0D : -1.0D;
        double[] candidateAngles = {
                0.0D,
                32.0D * sidePref, -32.0D * sidePref,
                65.0D * sidePref, -65.0D * sidePref,
                100.0D * sidePref, -100.0D * sidePref,
                135.0D * sidePref, -135.0D * sidePref
        };

        Vec3 bestVec = awayBase.scale(idealDash);
        double bestScore = -1.0E9D;
        double chosenAngle = 0.0D;

        for (double deg : candidateAngles) {
            double rad = deg * Mth.DEG_TO_RAD;
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            Vec3 dir = new Vec3(awayBase.x * cos - awayBase.z * sin, 0.0D, awayBase.x * sin + awayBase.z * cos);

            double clear = probeClearDistance(dir, idealDash);
            if (clear < 1.2D) {
                continue;
            }
            Vec3 endPos = position().add(dir.scale(clear));
            double newDist = target != null ? horizontalDist(endPos, target.position()) : clear;
            double score = newDist - Math.abs(newDist - desired) * 0.45D - Math.abs(deg) * 0.022D
                    + (clear >= idealDash * 0.75D ? 1.4D : 0.0D);
            if (score > bestScore) {
                bestScore = score;
                bestVec = dir.scale(clear);
                chosenAngle = deg;
            }
        }

        double groundAtEnd = findGroundYAt(position().add(bestVec));
        double wantEndY = groundAtEnd + ZephyrarchConfig.HOVER_HEIGHT.get();
        double dy = Mth.clamp(wantEndY - getY(), -1.5D, 2.0D);
        this.windDashTotalVec = new Vec3(bestVec.x, dy, bestVec.z);
        setSkillVec(this.windDashTotalVec);

        LOGGER.debug("Zephyrarch [id={}] planned Wind Dash: curDist={}, idealDash={}, actualLen={}, angleDeg={}",
                this.getId(), String.format("%.2f", curDist), String.format("%.2f", idealDash),
                String.format("%.2f", bestVec.length()), chosenAngle);
    }

    private double probeClearDistance(Vec3 dir, double maxDist) {
        AABB baseBox = this.getBoundingBox().deflate(0.05D);
        double clear = 0.0D;
        for (double d = 0.75D; d <= maxDist + 0.35D; d += 0.75D) {
            double testD = Math.min(d, maxDist);
            if (this.level().noCollision(this, baseBox.move(dir.scale(testD)))
                    || this.level().noCollision(this, baseBox.move(dir.x * testD, 0.75D, dir.z * testD))) {
                clear = testD;
            } else {
                break;
            }
        }
        return clear;
    }

    private double findGroundYAt(Vec3 pos) {
        Vec3 from = pos.add(0.0D, 1.5D, 0.0D);
        HitResult hit = this.level().clip(new ClipContext(from, from.add(0.0D, -14.0D, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        return hit.getType() == HitResult.Type.MISS ? getY() - ZephyrarchConfig.HOVER_HEIGHT.get() : hit.getLocation().y;
    }

    private void tickWindDash(ServerLevel level, LivingEntity target) {
        int t = this.attackTick;
        if (t == 1) {
            planWindDash(target);
            playBossSound(ModSounds.WIND_DASH.get(), 1.8F, 1.0F);
            ZephyrWorldVfxPayload.sendDirected(this, ZephyrWorldVfxPayload.KIND_WIND_DASH_TRAIL, position(), this.windDashTotalVec, 1.0F, 12);
        }
        if (t >= 1 && t <= WIND_DASH_STEP_WEIGHTS.length) {
            Vec3 step = this.windDashTotalVec.scale(WIND_DASH_STEP_WEIGHTS[t - 1]);
            this.move(MoverType.SELF, step);
        }
        this.setDeltaMovement(Vec3.ZERO);
        if (target != null && target.isAlive()) {
            faceTarget();
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
        RawAnimation anim = ACTION_ANIMS[current.id];
        if (anim == null) {
            anim = isPhase2() ? ANIM_HOVER : ANIM_IDLE;
        }
        return state.setAndContinue(anim);
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
            return canUse();
        }

        @Override
        public void stop() {
            ZephyrarchEntity.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = ZephyrarchEntity.this.getTarget();
            if (target == null || !target.isAlive() || getAttack() != ZephyrarchAttack.NONE) {
                return;
            }
            ZephyrarchEntity.this.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double horizDist = horizontalDist(position(), target.position());

            if (isPhase2()) {
                ZephyrarchEntity.this.getNavigation().stop();
                if (horizDist < 6.0D && skillCooldowns[ZephyrarchAttack.WIND_DASH.id] <= 0) {
                    useSkill(ZephyrarchAttack.WIND_DASH);
                    return;
                }
                if (globalAttackDelay <= 0) {
                    selectRanged(target, horizDist);
                }
                return;
            }

            if (horizDist > 3.5D) {
                double moveMult = isRapidCombat() ? 1.35D : 1.08D;
                ZephyrarchEntity.this.getNavigation().moveTo(target, moveMult);
            } else {
                ZephyrarchEntity.this.getNavigation().stop();
            }
            farFromTargetTicks = horizDist > 10.0D ? farFromTargetTicks + 1 : 0;
            if (globalAttackDelay <= 0) {
                selectMelee(target, horizDist);
            }
        }

        private void selectMelee(LivingEntity target, double dist) {
            if (farFromTargetTicks > 120 && ready(ZephyrarchAttack.GALE_RUSH)) {
                farFromTargetTicks = 0;
                useSkill(ZephyrarchAttack.GALE_RUSH);
                return;
            }
            List<ZephyrarchAttack> pool = new ArrayList<>();
            List<Integer> weights = new ArrayList<>();
            if (dist <= 4.0D) {
                Vec3 look = Vec3.directionFromRotation(0.0F, yBodyRot);
                Vec3 to = new Vec3(target.getX() - getX(), 0.0D, target.getZ() - getZ());
                boolean behind = to.lengthSqr() > 0.01D && to.normalize().dot(look) < -0.5D;
                if (behind) {
                    add(pool, weights, ZephyrarchAttack.ARM_SWEEP, 50);
                    add(pool, weights, ZephyrarchAttack.VORTEX_STOMP, 50);
                }
                if (pool.isEmpty()) {
                    add(pool, weights, ZephyrarchAttack.GALE_COMBO, 30);
                    add(pool, weights, ZephyrarchAttack.ARM_SWEEP, 25);
                    add(pool, weights, ZephyrarchAttack.GALE_GRAB, 20);
                    add(pool, weights, ZephyrarchAttack.VORTEX_STOMP, 25);
                }
            } else if (dist <= 10.0D) {
                add(pool, weights, ZephyrarchAttack.GALE_RUSH, 50);
                add(pool, weights, ZephyrarchAttack.SKY_HAMMER, 50);
            }
            pick(pool, weights);
        }

        private void selectRanged(LivingEntity target, double horizDist) {
            List<ZephyrarchAttack> pool = new ArrayList<>();
            List<Integer> weights = new ArrayList<>();
            boolean cageUnlocked = getHealth() <= getMaxHealth() * ZephyrarchConfig.CAGE_HEALTH_RATIO.get();

            if (horizDist < 8.0D) {
                add(pool, weights, ZephyrarchAttack.GALE_BOLTS, 28);
                add(pool, weights, ZephyrarchAttack.BOOMERANG_BLADES, 22);
                add(pool, weights, ZephyrarchAttack.STORM_EYE, 22);
                add(pool, weights, ZephyrarchAttack.DOWNDRAFT, 18);
                add(pool, weights, ZephyrarchAttack.TEMPEST_LANCE, 10);
                if (cageUnlocked) {
                    add(pool, weights, ZephyrarchAttack.CYCLONE_CAGE, 14);
                }
            } else if (horizDist <= 16.0D) {
                add(pool, weights, ZephyrarchAttack.GALE_BOLTS, 24);
                add(pool, weights, ZephyrarchAttack.TEMPEST_LANCE, 20);
                add(pool, weights, ZephyrarchAttack.STORM_EYE, 16);
                add(pool, weights, ZephyrarchAttack.DOWNDRAFT, 16);
                add(pool, weights, ZephyrarchAttack.BOOMERANG_BLADES, 16);
                if (cageUnlocked) {
                    add(pool, weights, ZephyrarchAttack.CYCLONE_CAGE, 12);
                }
            } else if (horizDist <= 38.0D) {
                add(pool, weights, ZephyrarchAttack.TEMPEST_LANCE, 38);
                add(pool, weights, ZephyrarchAttack.DOWNDRAFT, 34);
                add(pool, weights, ZephyrarchAttack.STORM_EYE, 18);
                add(pool, weights, ZephyrarchAttack.GALE_BOLTS, 10);
            }

            if (pool.isEmpty() && lastSkill != ZephyrarchAttack.NONE && isPhase2Skill(lastSkill) && ready(lastSkill)) {
                pool.add(lastSkill);
                weights.add(10);
            }
            pick(pool, weights);
        }

        private boolean isPhase2Skill(ZephyrarchAttack attack) {
            return switch (attack) {
                case GALE_BOLTS, TEMPEST_LANCE, STORM_EYE, DOWNDRAFT, BOOMERANG_BLADES, CYCLONE_CAGE -> true;
                default -> false;
            };
        }

        private boolean ready(ZephyrarchAttack attack) {
            return skillCooldowns[attack.id] <= 0;
        }

        private void add(List<ZephyrarchAttack> pool, List<Integer> weights, ZephyrarchAttack attack, int weight) {
            if (ready(attack) && attack != lastSkill) {
                pool.add(attack);
                weights.add(weight);
            }
        }

        private void pick(List<ZephyrarchAttack> pool, List<Integer> weights) {
            if (pool.isEmpty()) {
                return;
            }
            int total = 0;
            for (int w : weights) {
                total += w;
            }
            int roll = random.nextInt(total);
            for (int i = 0; i < pool.size(); i++) {
                roll -= weights.get(i);
                if (roll < 0) {
                    useSkill(pool.get(i));
                    return;
                }
            }
        }

        private void useSkill(ZephyrarchAttack attack) {
            double scale = isRapidCombat() ? ZephyrarchConfig.NO_PLAYER_COOLDOWN_SCALE.get() : 1.0D;
            skillCooldowns[attack.id] = (int) Math.round(ZephyrarchConfig.cooldown(attack) * scale);
            if (attack != ZephyrarchAttack.WIND_DASH) {
                lastSkill = attack;
            }
            startAttack(attack);
        }
    }
}
