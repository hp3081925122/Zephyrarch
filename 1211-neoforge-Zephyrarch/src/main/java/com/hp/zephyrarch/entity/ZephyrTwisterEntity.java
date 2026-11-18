package com.hp.zephyrarch.entity;

import com.hp.zephyrarch.config.ZephyrarchConfig;
import com.hp.zephyrarch.event.ZephyrarchCombatEvents;
import com.hp.zephyrarch.registry.ModRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 风暴之眼召唤的小龙卷：慢慢追踪最近的玩家，接触造成伤害并把目标抬起，被玩家打一下就会消散。 */
public final class ZephyrTwisterEntity extends Entity {
    public static final int LIFETIME = 120;
    private static final double SPEED = 0.06D;

    private int ownerId = -1;
    private int life;
    private final Map<Integer, Integer> lastHit = new HashMap<>();

    public ZephyrTwisterEntity(EntityType<? extends ZephyrTwisterEntity> type, Level level) {
        super(type, level);
    }

    public ZephyrTwisterEntity(Level level, LivingEntity owner, Vec3 pos) {
        this(ModRegistry.ZEPHYR_TWISTER.get(), level);
        this.ownerId = owner.getId();
        setPos(pos.x, pos.y, pos.z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    public float getFade(float partialTick) {
        float age = this.tickCount + partialTick;
        return Math.min(1.0F, Math.min(age / 10.0F, (LIFETIME - age) / 15.0F));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                double a = random.nextDouble() * Math.PI * 2.0D;
                level().addParticle(ParticleTypes.SMALL_GUST, getX() + Math.cos(a) * 0.6D, getY() + random.nextDouble() * 2.5D,
                        getZ() + Math.sin(a) * 0.6D, -Math.sin(a) * 0.15D, 0.05D, Math.cos(a) * 0.15D);
            }
            return;
        }
        life++;
        Entity ownerEntity = level().getEntity(ownerId);
        if (life > LIFETIME || !(ownerEntity instanceof ZephyrarchEntity owner) || !owner.isAlive()) {
            dissipate();
            return;
        }

        Player nearest = level().getNearestPlayer(this, 32.0D);
        Vec3 motion = new Vec3(0.0D, getDeltaMovement().y - 0.08D, 0.0D);
        if (nearest != null && !nearest.isCreative() && !nearest.isSpectator()) {
            Vec3 to = nearest.position().subtract(position());
            Vec3 horiz = new Vec3(to.x, 0.0D, to.z);
            if (horiz.lengthSqr() > 0.04D) {
                horiz = horiz.normalize().scale(SPEED);
                motion = new Vec3(horiz.x, motion.y, horiz.z);
            }
        }
        setDeltaMovement(motion);
        move(MoverType.SELF, getDeltaMovement());
        if (onGround()) {
            setDeltaMovement(getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
        }

        List<LivingEntity> victims = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.3D),
                e -> e != owner && e.isAlive() && !owner.isAlliedTo(e) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())));
        float damage = (float) (owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * ZephyrarchConfig.TWISTER_MULTIPLIER.get());
        for (LivingEntity victim : victims) {
            Integer last = lastHit.get(victim.getId());
            if (last != null && life - last < 10) {
                continue;
            }
            lastHit.put(victim.getId(), life);
            ZephyrarchCombatEvents.applyAntiHealIfEligible(owner, victim);
            if (victim.hurt(damageSources().mobAttack(owner), damage)) {
                double resist = Math.max(0.0D, 1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                victim.setDeltaMovement(victim.getDeltaMovement().x * 0.5D, 0.6D * resist * ZephyrarchConfig.KNOCKBACK_SCALE.get() + 0.1D, victim.getDeltaMovement().z * 0.5D);
                victim.hurtMarked = true;
            }
        }
    }

    private void dissipate() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.0D, getZ(), 10, 0.4D, 0.8D, 0.4D, 0.05D);
        }
        discard();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) {
            return false;
        }
        if (!level().isClientSide && source.getEntity() instanceof Player) {
            playSound(SoundEvents.WIND_CHARGE_BURST.value(), 1.0F, 1.3F);
            dissipate();
            return true;
        }
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        life = tag.getInt("Life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Life", life);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
