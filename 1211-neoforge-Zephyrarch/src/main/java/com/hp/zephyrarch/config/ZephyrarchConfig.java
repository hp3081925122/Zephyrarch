package com.hp.zephyrarch.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ZephyrarchConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue ATTACK_DAMAGE;
    public static final ModConfigSpec.DoubleValue ARMOR;
    public static final ModConfigSpec.DoubleValue MOVEMENT_SPEED;
    public static final ModConfigSpec.DoubleValue FOLLOW_RANGE;

    public static final ModConfigSpec.DoubleValue PHASE2_HEALTH_RATIO;
    public static final ModConfigSpec.DoubleValue LOW_HEALTH_BURST_RATIO;

    public static final ModConfigSpec.DoubleValue COMBO_PUNCH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue COMBO_FINISHER_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue TEMPEST_SLAM_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue GALE_CHARGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CYCLONE_TICK_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CYCLONE_BURST_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue PHASE_SHIFT_BURST_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue KNOCKBACK_SCALE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("罡风君主基础属性配置 (仅在实体首次生成时应用一次，不会覆盖后续指令或KubeJS修改) / Zephyrarch base attribute settings (applied once on initial spawn, preserving subsequent command/KubeJS edits)")
                .push("attributes");

        MAX_HEALTH = builder
                .comment("罡风君主基础最大生命值 / Base max health of Zephyrarch")
                .defineInRange("maxHealth", 320.0D, 20.0D, 20000.0D);

        ATTACK_DAMAGE = builder
                .comment("罡风君主基础攻击力 (所有技能伤害均以此基础攻击力乘以倍率计算) / Base attack damage of Zephyrarch (all skill damages scale with this value)")
                .defineInRange("attackDamage", 12.0D, 1.0D, 2000.0D);

        ARMOR = builder
                .comment("罡风君主基础护甲值 / Base armor of Zephyrarch")
                .defineInRange("armor", 10.0D, 0.0D, 100.0D);

        MOVEMENT_SPEED = builder
                .comment("罡风君主基础移动速度 / Base movement speed of Zephyrarch")
                .defineInRange("movementSpeed", 0.29D, 0.05D, 1.0D);

        FOLLOW_RANGE = builder
                .comment("罡风君主索敌追踪距离 / Follow range of Zephyrarch")
                .defineInRange("followRange", 44.0D, 16.0D, 128.0D);

        builder.pop();

        builder.comment("罡风君主阶段阈值与技能倍率配置 / Phase thresholds and skill damage multipliers")
                .push("combat");

        PHASE2_HEALTH_RATIO = builder
                .comment("触发狂飙二阶段的最大生命值百分比阈值 (0.50 = 50%血量) / Max health percentage threshold to enter Phase 2 (0.50 = 50% HP)")
                .defineInRange("phase2HealthRatio", 0.50D, 0.05D, 0.95D);

        LOW_HEALTH_BURST_RATIO = builder
                .comment("触发低血量强化狂风攻势的最大生命值百分比阈值 (0.25 = 25%血量) / Max health percentage threshold for low-health tempest overdrive (0.25 = 25% HP)")
                .defineInRange("lowHealthBurstRatio", 0.25D, 0.05D, 0.90D);

        COMBO_PUNCH_MULTIPLIER = builder
                .comment("连环重拳第一击伤害倍率 / Damage multiplier for the first strike of Combo Punch")
                .defineInRange("comboPunchMultiplier", 1.00D, 0.10D, 10.0D);

        COMBO_FINISHER_MULTIPLIER = builder
                .comment("连环重拳第二击横扫伤害倍率 / Damage multiplier for the second sweep strike of Combo Punch")
                .defineInRange("comboFinisherMultiplier", 1.25D, 0.10D, 10.0D);

        TEMPEST_SLAM_MULTIPLIER = builder
                .comment("裂空震地重砸范围伤害倍率 / Damage multiplier for Tempest Slam shockwave")
                .defineInRange("tempestSlamMultiplier", 1.45D, 0.10D, 10.0D);

        GALE_CHARGE_MULTIPLIER = builder
                .comment("罡风爆弹命中伤害倍率 / Damage multiplier for Zephyr Charge projectile")
                .defineInRange("galeChargeMultiplier", 0.85D, 0.10D, 10.0D);

        CYCLONE_TICK_MULTIPLIER = builder
                .comment("暴风眼牵引期间每段切割风伤倍率 / Damage multiplier per pulse during Cyclone Vortex pull")
                .defineInRange("cycloneTickMultiplier", 0.35D, 0.05D, 5.0D);

        CYCLONE_BURST_MULTIPLIER = builder
                .comment("暴风眼终段破空脉冲爆发伤害倍率 / Damage multiplier for Cyclone Vortex final burst")
                .defineInRange("cycloneBurstMultiplier", 1.60D, 0.10D, 10.0D);

        PHASE_SHIFT_BURST_MULTIPLIER = builder
                .comment("半血转阶段风暴震波伤害倍率 / Damage multiplier for Phase Shift awakening burst")
                .defineInRange("phaseShiftBurstMultiplier", 1.15D, 0.10D, 10.0D);

        KNOCKBACK_SCALE = builder
                .comment("技能击退力度全局缩放系数 (实际击退还会受目标击退抗性减免) / Global knockback scale for skills (actual knockback is also reduced by target knockback resistance)")
                .defineInRange("knockbackScale", 1.00D, 0.0D, 3.0D);

        builder.pop();

        SPEC = builder.build();
    }

    private ZephyrarchConfig() {
    }
}
