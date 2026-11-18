package com.hp.zephyrarch.registry;

import com.hp.zephyrarch.Zephyrarch;
import com.hp.zephyrarch.entity.ZephyrBladeEntity;
import com.hp.zephyrarch.entity.ZephyrChargeEntity;
import com.hp.zephyrarch.entity.ZephyrTwisterEntity;
import com.hp.zephyrarch.entity.ZephyrarchEntity;
import com.hp.zephyrarch.item.DescItem;
import com.hp.zephyrarch.item.SovereignGalebladeItem;
import com.hp.zephyrarch.item.TempestCrestItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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

    public static final DeferredHolder<EntityType<?>, EntityType<ZephyrTwisterEntity>> ZEPHYR_TWISTER = ENTITIES.register("zephyr_twister",
            () -> EntityType.Builder.<ZephyrTwisterEntity>of(ZephyrTwisterEntity::new, MobCategory.MISC)
                    .sized(1.2F, 2.6F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(Zephyrarch.MOD_ID + ":zephyr_twister"));

    public static final DeferredHolder<EntityType<?>, EntityType<ZephyrBladeEntity>> ZEPHYR_BLADE = ENTITIES.register("zephyr_blade",
            () -> EntityType.Builder.<ZephyrBladeEntity>of(ZephyrBladeEntity::new, MobCategory.MISC)
                    .sized(1.2F, 0.4F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(Zephyrarch.MOD_ID + ":zephyr_blade"));

    public static final DeferredHolder<Item, Item> ZEPHYRARCH_SPAWN_EGG = ITEMS.register("zephyrarch_spawn_egg",
            () -> new DeferredSpawnEggItem(ZEPHYRARCH, 0x576C9D, 0x8AE6FF, new Item.Properties()));

    public static final DeferredHolder<Item, Item> TEMPEST_CREST = ITEMS.register("tempest_crest",
            () -> new TempestCrestItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    public static final DeferredHolder<Item, Item> ZEPHYR_CORE = ITEMS.register("zephyr_core",
            () -> new DescItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant(), "item.zephyrarch.zephyr_core.desc"));

    public static final DeferredHolder<Item, Item> SOVEREIGN_GALEBLADE = ITEMS.register("sovereign_galeblade",
            () -> new SovereignGalebladeItem(
                    Tiers.NETHERITE,
                    new Item.Properties()
                            .rarity(Rarity.EPIC)
                            .fireResistant()
                            .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 4, -2.2F))
            ));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.zephyrarch"))
                    .icon(() -> ZEPHYR_CORE.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(TEMPEST_CREST.get());
                        output.accept(ZEPHYR_CORE.get());
                        output.accept(SOVEREIGN_GALEBLADE.get());
                        output.accept(ZEPHYRARCH_SPAWN_EGG.get());
                    })
                    .build());

    private ModRegistry() {
    }
}
