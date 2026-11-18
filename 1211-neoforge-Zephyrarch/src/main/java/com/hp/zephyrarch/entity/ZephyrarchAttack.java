package com.hp.zephyrarch.entity;

public enum ZephyrarchAttack {
    NONE(0, 0),
    COMBO_PUNCH(1, 34),
    TEMPEST_SLAM(2, 40),
    GALE_BARRAGE(3, 44),
    CYCLONE_VORTEX(4, 60),
    PHASE_SHIFT(5, 40);

    private static final ZephyrarchAttack[] BY_ID = values();

    public final int id;
    public final int duration;

    ZephyrarchAttack(int id, int duration) {
        this.id = id;
        this.duration = duration;
    }

    public static ZephyrarchAttack byId(int id) {
        if (id < 0 || id >= BY_ID.length) {
            return NONE;
        }
        return BY_ID[id];
    }
}
