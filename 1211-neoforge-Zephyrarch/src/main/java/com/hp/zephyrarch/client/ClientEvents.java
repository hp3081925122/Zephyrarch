package com.hp.zephyrarch.client;

import com.hp.zephyrarch.registry.ModRegistry;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

public final class ClientEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.ZEPHYRARCH.get(), ZephyrarchRenderer::new);
        event.registerEntityRenderer(ModRegistry.ZEPHYR_CHARGE.get(), ZephyrChargeRenderer::new);
    }

    public static GeoRenderProvider createGauntletRenderProvider() {
        return new GeoRenderProvider() {
            private SovereignGauntletRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = new SovereignGauntletRenderer();
                }
                return this.renderer;
            }
        };
    }

    private ClientEvents() {
    }
}
