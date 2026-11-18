package com.hp.zephyrarch.item;

import com.hp.zephyrarch.entity.ZephyrarchEntity;
import com.hp.zephyrarch.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class TempestCrestItem extends DescItem {
    public TempestCrestItem(Properties properties) {
        super(properties, "item.zephyrarch.tempest_crest.desc");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        ServerLevel serverLevel = (ServerLevel) level;
        Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(4.0D);
        BlockPos spawnPos = BlockPos.containing(player.getX() + forward.x, player.getY() + 0.5D, player.getZ() + forward.z);

        ZephyrarchEntity boss = ModRegistry.ZEPHYRARCH.get().create(serverLevel);
        if (boss == null) {
            return InteractionResultHolder.fail(stack);
        }

        boss.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, player.getYRot() + 180.0F, 0.0F);
        if (!serverLevel.noCollision(boss)) {
            boss.moveTo(player.getX(), player.getY() + 1.0D, player.getZ(), player.getYRot() + 180.0F, 0.0F);
            if (!serverLevel.noCollision(boss)) {
                player.displayClientMessage(Component.translatable("message.zephyrarch.summon_blocked").withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
        }

        serverLevel.addFreshEntity(boss);
        serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, boss.getX(), boss.getY() + 1.5D, boss.getZ(), 3, 0.6D, 0.6D, 0.6D, 0.0D);
        serverLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 2.0F, 0.7F);
        player.displayClientMessage(Component.translatable("message.zephyrarch.summoned").withStyle(ChatFormatting.AQUA), true);

        player.getCooldowns().addCooldown(this, 60);
        stack.consume(1, player);
        return InteractionResultHolder.consume(stack);
    }
}
