package com.hp.zephyrarch;

import com.hp.zephyrarch.client.ClientEvents;
import com.hp.zephyrarch.config.ZephyrarchConfig;
import com.hp.zephyrarch.entity.ZephyrarchEntity;
import com.hp.zephyrarch.item.SovereignGalebladeItem;
import com.hp.zephyrarch.registry.ModRegistry;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(Zephyrarch.MOD_ID)
public final class Zephyrarch {
    public static final String MOD_ID = "zephyrarch";

    public Zephyrarch(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, ZephyrarchConfig.SPEC);

        ModRegistry.ENTITIES.register(modEventBus);
        ModRegistry.ITEMS.register(modEventBus);
        ModRegistry.TABS.register(modEventBus);

        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::fillCreativeTabs);

        NeoForge.EVENT_BUS.register(SovereignGalebladeItem.Events.class);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.register(ClientEvents.class);
        }
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModRegistry.ZEPHYRARCH.get(), ZephyrarchEntity.createAttributes().build());
    }

    private void fillCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModRegistry.ZEPHYRARCH_SPAWN_EGG.get());
        } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModRegistry.SOVEREIGN_GALEBLADE.get());
        }
    }
}
