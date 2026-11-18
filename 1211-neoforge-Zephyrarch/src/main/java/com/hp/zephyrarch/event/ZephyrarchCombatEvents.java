package com.hp.zephyrarch.event;

import com.hp.zephyrarch.config.ZephyrarchConfig;
import com.hp.zephyrarch.entity.ZephyrarchEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.slf4j.Logger;

import java.util.UUID;

public final class ZephyrarchCombatEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String NBT_ANTI_HEAL_UNTIL = "zephyrarch_anti_heal_until";

    private ZephyrarchCombatEvents() {
    }

    public static void applyAntiHealIfEligible(Entity attacker, LivingEntity victim) {
        if (!ZephyrarchConfig.CRICKET_MODE_ENABLED.get()) {
            return;
        }
        if (!(attacker instanceof ZephyrarchEntity boss) || victim instanceof Player || !victim.isAlive() || victim == boss) {
            return;
        }
        if (victim.level().isClientSide) {
            return;
        }
        int duration = ZephyrarchConfig.ANTI_HEAL_DURATION_TICKS.get();
        long until = victim.level().getGameTime() + duration;
        victim.getPersistentData().putLong(NBT_ANTI_HEAL_UNTIL, until);
        LOGGER.debug("Zephyrarch [id={}] applied anti-heal to non-player entity {} for {} ticks", boss.getId(), victim.getName().getString(), duration);
    }

    public static boolean isNonPlayerSourcedDamage(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        Entity culprit = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (culprit == null && direct == null) {
            return false;
        }
        return !isPlayerOrOwnedByPlayer(culprit) && !isPlayerOrOwnedByPlayer(direct);
    }

    private static boolean isPlayerOrOwnedByPlayer(Entity entity) {
        Entity current = entity;
        for (int depth = 0; depth < 6 && current != null; depth++) {
            if (current instanceof Player) {
                return true;
            }
            if (current instanceof OwnableEntity ownable) {
                LivingEntity owner = ownable.getOwner();
                if (owner != null) {
                    current = owner;
                    continue;
                }
                UUID ownerId = ownable.getOwnerUUID();
                if (ownerId != null && current.level() instanceof ServerLevel serverLevel) {
                    if (serverLevel.getServer().getPlayerList().getPlayer(ownerId) != null) {
                        return true;
                    }
                    current = serverLevel.getEntity(ownerId);
                    continue;
                }
                return false;
            }
            if (current instanceof TraceableEntity traceable) {
                current = traceable.getOwner();
                continue;
            }
            return false;
        }
        return false;
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ZephyrarchEntity boss) || boss.level().isClientSide) {
            return;
        }
        if (!ZephyrarchConfig.CRICKET_MODE_ENABLED.get()) {
            return;
        }
        if (isNonPlayerSourcedDamage(event.getSource())) {
            float cap = boss.getNonPlayerDamageCap();
            if (event.getAmount() > cap * 6.0F) {
                event.setAmount(cap * 6.0F);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ZephyrarchEntity boss) || boss.level().isClientSide) {
            return;
        }
        if (!ZephyrarchConfig.CRICKET_MODE_ENABLED.get()) {
            return;
        }
        if (isNonPlayerSourcedDamage(event.getSource())) {
            float cap = boss.getNonPlayerDamageCap();
            if (event.getNewDamage() > cap) {
                LOGGER.debug("Zephyrarch [id={}] cricket damage cap clamped non-player damage {} -> {}", boss.getId(), event.getNewDamage(), cap);
                event.setNewDamage(cap);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || victim instanceof Player) {
            return;
        }
        Entity culprit = event.getSource().getEntity();
        if (culprit instanceof ZephyrarchEntity) {
            applyAntiHealIfEligible(culprit, victim);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ZephyrarchEntity boss) || boss.level().isClientSide) {
            return;
        }
        if (!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !boss.isPhase2()) {
            event.setCanceled(true);
            boss.triggerPhase2Rebirth();
        }
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        boolean hasNbtLock = ZephyrarchConfig.CRICKET_MODE_ENABLED.get()
                && entity.getPersistentData().getLong(NBT_ANTI_HEAL_UNTIL) > entity.level().getGameTime();
        if (hasNbtLock) {
            event.setAmount(0.0F);
            event.setCanceled(true);
        }
    }
}
