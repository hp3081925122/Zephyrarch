package com.hp.zephyrarch.client;

import com.hp.zephyrarch.registry.ModRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ClientEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.ZEPHYRARCH.get(), ZephyrarchRenderer::new);
        event.registerEntityRenderer(ModRegistry.ZEPHYR_CHARGE.get(), ZephyrChargeRenderer::new);
    }

    private ClientEvents() {
    }
}
