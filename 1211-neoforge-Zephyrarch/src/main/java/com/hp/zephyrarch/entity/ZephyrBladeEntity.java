package com.hp.zephyrarch.entity;

import com.hp.zephyrarch.config.ZephyrarchConfig;
import com.hp.zephyrarch.event.ZephyrarchCombatEvents;
import com.hp.zephyrarch.registry.ModRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 回旋风刃：从君主身旁沿弧线飞到目标身后，再沿另一侧弧线飞回君主。
 * 能穿过方块，去程和回程各能命中一次。
 */
public final class ZephyrBladeEntity extends Entity {
    public static final int FLIGHT_TICKS = 40;
    private static final double ARC_RADIUS = 5.0D;

    private int ownerId = -1;
    private Vec3 start = Vec3.ZERO;
    private Vec3 apex = Vec3.ZERO;
    private int side = 1;
    private int life;
    private final Set<Integer> hitThisLeg = new HashSet<>();

    public ZephyrBladeEntity(EntityType<? extends ZephyrBladeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public ZephyrBladeEntity(Level level, LivingEntity owner, Vec3 start, Vec3 apex, int side) {
        this(ModRegistry.ZEPHYR_BLADE.get(), level);
        this.ownerId = owner.getId();
        this.start = start;
        this.apex = apex;
        this.side = side;
        setPos(start.x, start.y, start.z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            level().addParticle(ParticleTypes.SMALL_GUST, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            return;
        }
        life++;
        Entity ownerEntity = level().getEntity(ownerId);
        if (life > FLIGHT_TICKS || !(ownerEntity instanceof ZephyrarchEntity owner) || !owner.isAlive()) {
            discard();
            return;
        }
        if (life == FLIGHT_TICKS / 2) {
            hitThisLeg.clear();
        }

        Vec3 next = pathAt(life / (double) FLIGHT_TICKS, owner.position().add(0.0D, 1.8D, 0.0D));
        setPos(next.x, next.y, next.z);

        List<LivingEntity> victims = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.4D),
                e -> e != owner && e.isAlive() && !owner.isAlliedTo(e) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())));
        float damage = (float) (owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * ZephyrarchConfig.BLADE_MULTIPLIER.get());
        for (LivingEntity victim : victims) {
            if (!hitThisLeg.add(victim.getId())) {
                continue;
            }
            ZephyrarchCombatEvents.applyAntiHealIfEligible(owner, victim);
            if (victim.hurt(damageSources().mobAttack(owner), damage)) {
                Vec3 push = victim.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
                if (push.lengthSqr() > 1.0E-4D) {
                    double resist = Math.max(0.0D, 1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                    push = push.normalize().scale(0.4D * resist * ZephyrarchConfig.KNOCKBACK_SCALE.get());
                    victim.push(push.x, 0.1D, push.z);
                    victim.hurtMarked = true;
                }
            }
        }
    }

    /** u ∈ [0,1]：前半程从起点画弧到 apex，后半程从 apex 沿另一侧画弧回到 home。 */
    private Vec3 pathAt(double u, Vec3 home) {
        boolean outbound = u < 0.5D;
        double s = outbound ? u * 2.0D : (u - 0.5D) * 2.0D;
        Vec3 from = outbound ? start : apex;
        Vec3 to = outbound ? apex : home;
        Vec3 dir = to.subtract(from);
        Vec3 perp = new Vec3(-dir.z, 0.0D, dir.x);
        if (perp.lengthSqr() > 1.0E-4D) {
            perp = perp.normalize();
        }
        double bend = ARC_RADIUS * Math.sin(Math.PI * s) * side;
        return from.add(dir.scale(s)).add(perp.scale(bend));
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
