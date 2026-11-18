package com.hp.zephyrarch.client;

import com.hp.zephyrarch.Zephyrarch;
import com.hp.zephyrarch.entity.ZephyrarchAttack;
import com.hp.zephyrarch.entity.ZephyrarchEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public final class ZephyrarchModel extends GeoModel<ZephyrarchEntity> {
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
            head.setRotY(head.getRotY() + Mth.clamp(data.netHeadYaw(), -45.0F, 45.0F) * Mth.DEG_TO_RAD);
            head.setRotX(head.getRotX() + Mth.clamp(data.headPitch(), -25.0F, 25.0F) * Mth.DEG_TO_RAD);
        });
    }
}
