package com.hp.zephyrarch.entity;

public enum ZephyrarchAttack {
    NONE(0, 0, null),
    // 一阶段近战
    GALE_COMBO(1, 34, "combo_punch"),
    ARM_SWEEP(2, 36, "arm_sweep"),
    GALE_RUSH(3, 40, "gale_rush"),
    SKY_HAMMER(4, 44, "sky_hammer"),
    GALE_GRAB(5, 52, "gale_grab"),
    VORTEX_STOMP(6, 46, "vortex_stomp"),
    // 转阶段
    PHASE_SHIFT(7, 40, "phase_shift"),
    // 二阶段远程
    GALE_BOLTS(8, 48, "gale_bolts"),
    TEMPEST_LANCE(9, 54, "tempest_lance"),
    STORM_EYE(10, 40, "storm_eye"),
    DOWNDRAFT(11, 30, "downdraft"),
    BOOMERANG_BLADES(12, 40, "boomerang_blades"),
    CYCLONE_CAGE(13, 60, "cyclone_cage"),
    // 辅助动作
    WIND_DASH(14, 10, "wind_dash"),
    GRAB_WHIFF(15, 20, "grab_whiff"),
    STAGGER(16, 30, "stagger");

    private static final ZephyrarchAttack[] BY_ID = values();

    public final int id;
    public final int duration;
    public final String animation;

    ZephyrarchAttack(int id, int duration, String animation) {
        this.id = id;
        this.duration = duration;
        this.animation = animation == null ? null : "animation.zephyrarch." + animation;
    }

    public static ZephyrarchAttack byId(int id) {
        if (id < 0 || id >= BY_ID.length) {
            return NONE;
        }
        return BY_ID[id];
    }
}
