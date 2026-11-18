package com.hp.zephyrarch.entity;

import com.hp.zephyrarch.config.ZephyrarchConfig;
import com.hp.zephyrarch.event.ZephyrarchCombatEvents;
import com.hp.zephyrarch.registry.ModRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ZephyrBladeEntity extends Entity {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int FLIGHT_TICKS = 42;
    private static final EntityDataAccessor<Integer> DATA_SIDE = SynchedEntityData.defineId(ZephyrBladeEntity.class, EntityDataSerializers.INT);

    public final Deque<Vec3> clientTrail = new ArrayDeque<>(14);
    private int ownerId = -1;
    private int targetId = -1;
    private Vec3 start = Vec3.ZERO;
    private Vec3 crossPoint = Vec3.ZERO;
    private Vec3 turnPoint = Vec3.ZERO;
    private int side = 1;
    private int life;
    private final Set<Integer> hitThisLeg = new HashSet<>();

    public ZephyrBladeEntity(EntityType<? extends ZephyrBladeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public ZephyrBladeEntity(Level level, LivingEntity owner, Vec3 start, Vec3 crossPoint, int targetId, int side) {
        this(ModRegistry.ZEPHYR_BLADE.get(), level);
        this.ownerId = owner.getId();
        this.targetId = targetId;
        this.start = start;
        this.crossPoint = crossPoint;
        this.side = side >= 0 ? 1 : -1;
        this.entityData.set(DATA_SIDE, this.side);
        setPos(start.x, start.y, start.z);
    }

    public int getBladeSide() {
        return this.entityData.get(DATA_SIDE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SIDE, 1);
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (clientTrail.size() >= 12) {
                clientTrail.removeLast();
            }
            clientTrail.addFirst(position().add(0.0D, 0.15D, 0.0D));
            return;
        }
        life++;
        Entity ownerEntity = level().getEntity(ownerId);
        if (life > FLIGHT_TICKS || !(ownerEntity instanceof ZephyrarchEntity owner) || !owner.isAlive()) {
            discard();
            return;
        }
        int half = FLIGHT_TICKS / 2;
        if (life == half + 1) {
            hitThisLeg.clear();
            turnPoint = position();
        }

        Vec3 prev = position();
        Vec3 home = owner.position().add(0.0D, 1.55D, 0.0D);
        Vec3 next;
        boolean outbound = life <= half;
        if (outbound) {
            next = outboundAt(life / (double) half);
        } else {
            Entity tEnt = targetId >= 0 ? level().getEntity(targetId) : null;
            LivingEntity liveTarget = (tEnt instanceof LivingEntity le && le.isAlive()) ? le : owner.getTarget();
            next = returnAt((life - half) / (double) (FLIGHT_TICKS - half), home, liveTarget);
        }

        Vec3 delta = next.subtract(prev);
        setDeltaMovement(delta);
        if (delta.horizontalDistanceSqr() > 1.0E-5D) {
            setYRot((float) (Mth.atan2(delta.x, delta.z) * (180.0D / Math.PI)));
        }
        setPos(next.x, next.y, next.z);

        AABB sweepArea = new AABB(prev, next).inflate(1.1D, 0.65D, 1.1D);
        List<LivingEntity> victims = level().getEntitiesOfClass(LivingEntity.class, sweepArea,
                e -> e != owner && e.isAlive() && !owner.isAlliedTo(e) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())));
        float damage = (float) (owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * ZephyrarchConfig.BLADE_MULTIPLIER.get());
        for (LivingEntity victim : victims) {
            AABB hitBox = victim.getBoundingBox().inflate(0.80D, 0.40D, 0.80D);
            if (!hitBox.contains(prev) && !hitBox.contains(next) && hitBox.clip(prev, next).isEmpty()) {
                continue;
            }
            if (!hitThisLeg.add(victim.getId())) {
                continue;
            }
            ZephyrarchCombatEvents.applyAntiHealIfEligible(owner, victim);
            if (victim.hurt(damageSources().mobAttack(owner), damage)) {
                LOGGER.debug("ZephyrBlade [side={}, leg={}] hit entity {}", side, outbound ? "outbound" : "return", victim.getName().getString());
                Vec3 push = delta.multiply(1.0D, 0.0D, 1.0D);
                if (push.lengthSqr() < 1.0E-4D) {
                    push = victim.position().subtract(next).multiply(1.0D, 0.0D, 1.0D);
                }
                if (push.lengthSqr() > 1.0E-4D) {
                    double resist = Math.max(0.0D, 1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                    push = push.normalize().scale(0.4D * resist * ZephyrarchConfig.KNOCKBACK_SCALE.get());
                    victim.push(push.x, 0.1D * resist, push.z);
                    victim.hurtMarked = true;
                }
            }
        }
    }

    private Vec3 outboundAt(double s) {
        Vec3 toCross = crossPoint.subtract(start);
        Vec3 flat = new Vec3(toCross.x, 0.0D, toCross.z);
        double dist = Math.max(2.4D, flat.length());
        Vec3 dir = flat.lengthSqr() > 1.0E-4D ? flat.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 perp = new Vec3(-dir.z, 0.0D, dir.x);

        double arcWidth = Mth.clamp(dist * 0.21D, 1.15D, 2.45D);
        double overshoot = Mth.clamp(dist * 0.23D, 1.8D, 3.1D);

        Vec3 p0 = start;
        Vec3 p1 = new Vec3(start.x, Mth.lerp(0.58D, start.y, crossPoint.y), start.z)
                .add(dir.scale(dist * 0.35D))
                .add(perp.scale(side * arcWidth * 1.42D));
        Vec3 p2 = crossPoint
                .add(dir.scale(dist * 0.08D))
                .add(perp.scale(side * 0.18D));
        Vec3 p3 = crossPoint
                .add(dir.scale(overshoot))
                .subtract(perp.scale(side * 0.72D));

        return cubicBezier(p0, p1, p2, p3, s);
    }

    private Vec3 returnAt(double s, Vec3 home, LivingEntity liveTarget) {
        Vec3 base = turnPoint.lengthSqr() > 1.0E-4D ? turnPoint : crossPoint;
        Vec3 toHome = home.subtract(base);
        Vec3 flat = new Vec3(toHome.x, 0.0D, toHome.z);
        double dist = Math.max(2.0D, flat.length());
        Vec3 dir = flat.lengthSqr() > 1.0E-4D ? flat.normalize() : new Vec3(0.0D, 0.0D, -1.0D);
        Vec3 perp = new Vec3(-dir.z, 0.0D, dir.x);

        double retArc = Mth.clamp(dist * 0.16D, 0.9D, 1.95D);
        Vec3 midLine = base.lerp(home, 0.45D);
        if (liveTarget != null && liveTarget.isAlive()) {
            Vec3 targetCenter = liveTarget.position().add(0.0D, Mth.clamp(liveTarget.getBbHeight() * 0.52D, 0.45D, 1.55D), 0.0D);
            if (targetCenter.distanceToSqr(midLine) <= 36.0D) {
                midLine = midLine.lerp(targetCenter, 0.55D);
            }
        }

        Vec3 p0 = base;
        Vec3 p1 = base.add(dir.scale(dist * 0.22D)).add(perp.scale(side * retArc));
        Vec3 p2 = midLine.add(dir.scale(dist * 0.18D)).subtract(perp.scale(side * 0.25D));
        Vec3 p3 = home;

        return cubicBezier(p0, p1, p2, p3, s);
    }

    private static Vec3 cubicBezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double u = 1.0D - t;
        double b0 = u * u * u;
        double b1 = 3.0D * u * u * t;
        double b2 = 3.0D * u * t * t;
        double b3 = t * t * t;
        return new Vec3(
                b0 * p0.x + b1 * p1.x + b2 * p2.x + b3 * p3.x,
                b0 * p0.y + b1 * p1.y + b2 * p2.y + b3 * p3.y,
                b0 * p0.z + b1 * p1.z + b2 * p2.z + b3 * p3.z
        );
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
