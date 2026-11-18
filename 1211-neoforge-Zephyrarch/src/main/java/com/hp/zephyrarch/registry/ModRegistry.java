package com.hp.zephyrarch.registry;

import com.hp.zephyrarch.Zephyrarch;
import com.hp.zephyrarch.effect.GaleSeveranceEffect;
import com.hp.zephyrarch.entity.ZephyrChargeEntity;
import com.hp.zephyrarch.entity.ZephyrarchEntity;
import com.hp.zephyrarch.item.DescItem;
import com.hp.zephyrarch.item.SovereignGauntletItem;
import com.hp.zephyrarch.item.TempestCrestItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Zephyrarch.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Zephyrarch.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Zephyrarch.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Zephyrarch.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ZephyrarchEntity>> ZEPHYRARCH = ENTITIES.register("zephyrarch",
            () -> EntityType.Builder.of(ZephyrarchEntity::new, MobCategory.MONSTER)
                    .sized(1.8F, 3.4F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(Zephyrarch.MOD_ID + ":zephyrarch"));

    public static final DeferredHolder<EntityType<?>, EntityType<ZephyrChargeEntity>> ZEPHYR_CHARGE = ENTITIES.register("zephyr_charge",
            () -> EntityType.Builder.<ZephyrChargeEntity>of(ZephyrChargeEntity::new, MobCategory.MISC)
                    .sized(0.55F, 0.55F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(Zephyrarch.MOD_ID + ":zephyr_charge"));

    public static final DeferredHolder<MobEffect, MobEffect> GALE_SEVERANCE = EFFECTS.register("gale_severance", GaleSeveranceEffect::new);

    public static final DeferredHolder<Item, Item> ZEPHYRARCH_SPAWN_EGG = ITEMS.register("zephyrarch_spawn_egg",
            () -> new DeferredSpawnEggItem(ZEPHYRARCH, 0x576C9D, 0x8AE6FF, new Item.Properties()));

    public static final DeferredHolder<Item, Item> TEMPEST_CREST = ITEMS.register("tempest_crest",
            () -> new TempestCrestItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    public static final DeferredHolder<Item, Item> ZEPHYR_CORE = ITEMS.register("zephyr_core",
            () -> new DescItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant(), "item.zephyrarch.zephyr_core.desc"));

    public static final DeferredHolder<Item, Item> SOVEREIGN_GAUNTLET = ITEMS.register("sovereign_gauntlet",
            () -> new SovereignGauntletItem(
                    Tiers.NETHERITE,
                    new Item.Properties()
                            .rarity(Rarity.EPIC)
                            .fireResistant()
                            .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 5, -2.0F))
            ));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.zephyrarch"))
                    .icon(() -> SOVEREIGN_GAUNTLET.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(TEMPEST_CREST.get());
                        output.accept(ZEPHYR_CORE.get());
                        output.accept(SOVEREIGN_GAUNTLET.get());
                        output.accept(ZEPHYRARCH_SPAWN_EGG.get());
                    })
                    .build());

    private ModRegistry() {
    }
}
