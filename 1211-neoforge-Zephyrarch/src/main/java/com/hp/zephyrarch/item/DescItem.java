package com.hp.zephyrarch.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class DescItem extends Item {
    private final String descKey;

    public DescItem(Properties properties, String descKey) {
        super(properties);
        this.descKey = descKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(this.descKey).withStyle(ChatFormatting.AQUA));
    }
}
