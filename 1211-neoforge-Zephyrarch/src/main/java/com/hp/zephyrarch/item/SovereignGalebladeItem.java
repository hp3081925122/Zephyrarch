package com.hp.zephyrarch.item;

import com.hp.zephyrarch.entity.ZephyrChargeEntity;
import com.hp.zephyrarch.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

import java.util.List;

public final class SovereignGalebladeItem extends SwordItem {
    public SovereignGalebladeItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Vec3 look = player.getLookAngle();
        player.setDeltaMovement(look.x * 1.15D, Math.max(0.35D, look.y * 0.75D + 0.2D), look.z * 1.15D);
        player.fallDistance = 0.0F;

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            ZephyrChargeEntity charge = new ZephyrChargeEntity(serverLevel, player).configure(0.85D, 2.8D, 0.45D, 100, 1.0F);
            charge.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.55F, 0.5F);
            serverLevel.addFreshEntity(charge);
            serverLevel.sendParticles(ParticleTypes.GUST, player.getX(), player.getY() + 0.8D, player.getZ(), 2, 0.3D, 0.2D, 0.3D, 0.0D);
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.2F, 1.1F);
            EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(1, player, slot);
        }

        player.getCooldowns().addCooldown(this, 45);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.is(ModRegistry.ZEPHYR_CORE.get()) || super.isValidRepairItem(toRepair, repair);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.zephyrarch.sovereign_galeblade.desc").withStyle(ChatFormatting.AQUA));
    }

    public static final class Events {
        @SubscribeEvent
        public static void onLivingFall(LivingFallEvent event) {
            LivingEntity entity = event.getEntity();
            if (entity.getMainHandItem().is(ModRegistry.SOVEREIGN_GALEBLADE.get())
                    || entity.getOffhandItem().is(ModRegistry.SOVEREIGN_GALEBLADE.get())) {
                event.setDistance(0.0F);
                event.setCanceled(true);
            }
        }
    }
}
