package com.hp.zephyrarch.client;

import com.hp.zephyrarch.Zephyrarch;
import com.hp.zephyrarch.entity.ZephyrarchAttack;
import com.hp.zephyrarch.entity.ZephyrarchEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public final class ZephyrarchModel extends GeoModel<ZephyrarchEntity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Zephyrarch.MOD_ID, "geo/zephyrarch.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Zephyrarch.MOD_ID, "textures/entity/zephyrarch.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(Zephyrarch.MOD_ID, "animations/zephyrarch.animation.json");

    @Override
    public ResourceLocation getModelResource(ZephyrarchEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ZephyrarchEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ZephyrarchEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(ZephyrarchEntity animatable, long instanceId, AnimationState<ZephyrarchEntity> state) {
        super.setCustomAnimations(animatable, instanceId, state);
        if (animatable.getAttack() != ZephyrarchAttack.NONE || animatable.isDeadOrDying()) {
            return;
        }
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (data == null) {
            return;
        }
        getBone("head").ifPresent(head -> {
            float clampedPitch = Mth.clamp(data.headPitch(), -25.0F, 25.0F);
            float clampedYaw = Mth.clamp(data.netHeadYaw(), -45.0F, 45.0F);
            float rotX = clampedPitch * Mth.DEG_TO_RAD;
            float rotY = clampedYaw * Mth.DEG_TO_RAD;
            head.setRotX(rotX);
            head.setRotY(rotY);
            head.setRotZ(0.0F);
            if (LOGGER.isDebugEnabled() && animatable.tickCount % 60 == 0 && state.getPartialTick() < 0.05F) {
                LOGGER.debug("Zephyrarch [id={}] head rotation updated: rawPitch={}, rawYaw={}, appliedRotX={}, appliedRotY={}",
                        animatable.getId(), data.headPitch(), data.netHeadYaw(), rotX, rotY);
            }
        });
    }
}
