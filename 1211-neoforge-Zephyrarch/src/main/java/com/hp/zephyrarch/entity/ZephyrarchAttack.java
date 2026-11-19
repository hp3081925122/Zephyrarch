package com.hp.zephyrarch.entity;

public enum ZephyrarchAttack {
    NONE(0, null),
    GALE_COMBO(34, "combo_punch"),
    ARM_SWEEP(36, "arm_sweep"),
    GALE_RUSH(40, "gale_rush"),
    SKY_HAMMER(44, "sky_hammer"),
    VORTEX_STOMP(46, "vortex_stomp"),
    PHASE_SHIFT(40, "phase_shift"),
    GALE_BOLTS(48, "gale_bolts"),
    TEMPEST_LANCE(54, "tempest_lance"),
    STORM_EYE(40, "storm_eye"),
    DOWNDRAFT(30, "downdraft"),
    BOOMERANG_BLADES(40, "boomerang_blades"),
    CYCLONE_CAGE(60, "cyclone_cage"),
    WIND_DASH(10, "wind_dash"),
    STAGGER(30, "stagger");

    private static final ZephyrarchAttack[] BY_ID = values();

    public final int id;
    public final int duration;
    public final String animation;

    ZephyrarchAttack(int duration, String animation) {
        this.id = this.ordinal();
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
