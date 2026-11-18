package com.hp.zephyrarch.client;

import com.hp.zephyrarch.Zephyrarch;
import com.hp.zephyrarch.entity.ZephyrarchEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Zephyrarch.MOD_ID, value = Dist.CLIENT)
public final class ZephyrarchBossBar {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(Zephyrarch.MOD_ID, "textures/gui/zephyrarch_bossbar.png");
    private static final int TW = 256;
    private static final int TH = 96;
    private static final int W = 210;
    private static final int H = 28;
    private static final int WIN_X = 14;
    private static final int WIN_Y = 11;
    private static final int WIN_W = 182;
    private static final int WIN_H = 5;
    private static final int TILE = 128;

    private static final float CHIP_HOLD = 10.0F;
    private static final float CHIP_SPEED = 0.012F;

    private static final Map<UUID, float[]> STATE = new HashMap<>();

    private ZephyrarchBossBar() {
    }

    @SubscribeEvent
    public static void onBossBarRender(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (!(boss.getName().getContents() instanceof TranslatableContents tc) || !ZephyrarchEntity.BAR_KEY.equals(tc.getKey())) {
            return;
        }
        event.setCanceled(true);
        event.setIncrement(H + 11);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        float time = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        float p = Mth.clamp(boss.getProgress(), 0.0F, 1.0F);
        boolean phase2 = boss.getColor() == BossEvent.BossBarColor.PURPLE;
        float phaseFlag = phase2 ? 2.0F : 1.0F;

        STATE.values().removeIf(s -> time - s[3] > 200.0F || time < s[3]);
        float[] s = STATE.computeIfAbsent(boss.getId(), id -> new float[]{p, p, time, time, -100.0F, phaseFlag, -100.0F});

        if (phaseFlag > s[5] + 0.5F) {
            s[0] = p;
            s[6] = time;
        }
        s[5] = phaseFlag;

        if (p < s[1] - 1.0E-4F) {
            s[2] = time + CHIP_HOLD;
            s[4] = time;
        }
        if (p >= s[0]) {
            s[0] = p;
        } else if (time > s[2]) {
            float dt = time - Math.max(s[3], s[2]);
            s[0] = Math.max(p, s[0] - dt * CHIP_SPEED);
        }
        s[1] = p;
        s[3] = time;

        float hurtFlash = 1.0F - Mth.clamp((time - s[4]) / 4.0F, 0.0F, 1.0F);
        float rebirthFlash = 1.0F - Mth.clamp((time - s[6]) / 18.0F, 0.0F, 1.0F);

        int x = g.guiWidth() / 2 - W / 2;
        int y = event.getY() + 3;
        int wx = x + WIN_X;
        int wy = y + WIN_Y;
        int fw = Math.round(WIN_W * p);
        int cw = Math.round(WIN_W * s[0]);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        g.blit(TEX, wx, wy, 0, 58, WIN_W, WIN_H, TW, TH);

        if (cw > fw) {
            float k = time < s[2] ? 1.0F : 0.76F;
            g.fill(wx + fw, wy, wx + cw, wy + 1, argb(k, 255, 242, 214));
            g.fill(wx + fw, wy + 1, wx + cw, wy + 2, argb(k, 246, 154, 76));
            g.fill(wx + fw, wy + 2, wx + cw, wy + 4, argb(k, 224, 108, 50));
            g.fill(wx + fw, wy + 4, wx + cw, wy + WIN_H, argb(k, 152, 58, 28));
        }

        int scrollOff = phase2 ? (int) (time * 0.85F) % TILE : (int) (time * 0.45F) % TILE;
        int barV = phase2 ? 72 : 65;
        tile(g, wx, wy, barV, fw, scrollOff);

        if (hurtFlash > 0.0F && fw > 0) {
            g.fill(wx, wy, wx + fw, wy + WIN_H, argb(0.55F * hurtFlash, 236, 254, 255));
        }
        if (rebirthFlash > 0.0F && fw > 0) {
            g.fill(wx, wy, wx + fw, wy + WIN_H, argb(0.68F * rebirthFlash, 255, 246, 184));
        }

        if (fw > 0 && fw < WIN_W) {
            int frame = ((int) (time / (phase2 ? 2.0F : 3.5F))) & 1;
            int edgeU = phase2 ? (frame == 0 ? 152 : 160) : (frame == 0 ? 136 : 144);
            g.blit(TEX, wx + fw - 2, wy - 2, edgeU, 64, 5, 9, TW, TH);
        }

        int overlayV = phase2 ? 28 : 0;
        g.blit(TEX, x, y, 0, overlayV, W, H, TW, TH);

        RenderSystem.disableBlend();

        Component name = boss.getName();
        int textColor = phase2 ? 0xFFFFECA6 : 0xFFD6F6FF;
        g.drawString(mc.font, name, g.guiWidth() / 2 - mc.font.width(name) / 2, event.getY() - 6, textColor, true);
    }

    private static void tile(GuiGraphics g, int x, int y, int v, int width, int off) {
        int drawn = 0;
        while (drawn < width) {
            int u = Math.floorMod(off + drawn, TILE);
            int w = Math.min(TILE - u, width - drawn);
            g.blit(TEX, x + drawn, y, u, v, w, WIN_H, TW, TH);
            drawn += w;
        }
    }

    private static int argb(float a, int r, int g, int b) {
        return (Math.round(Mth.clamp(a, 0.0F, 1.0F) * 255.0F) << 24) | (r << 16) | (g << 8) | b;
    }
}
