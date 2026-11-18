package com.hp.zephyrarch.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ZephyrarchConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue ATTACK_DAMAGE;
    public static final ModConfigSpec.DoubleValue ARMOR;
    public static final ModConfigSpec.DoubleValue MOVEMENT_SPEED;
    public static final ModConfigSpec.DoubleValue FOLLOW_RANGE;

    public static final ModConfigSpec.DoubleValue COMBO_PUNCH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue COMBO_FINISHER_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue TEMPEST_SLAM_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue GALE_CHARGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CYCLONE_TICK_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CYCLONE_BURST_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue PHASE_SHIFT_BURST_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue KNOCKBACK_SCALE;

    public static final ModConfigSpec.BooleanValue CRICKET_MODE_ENABLED;
    public static final ModConfigSpec.DoubleValue NON_PLAYER_DAMAGE_CAP_RATIO;
    public static final ModConfigSpec.IntValue ANTI_HEAL_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue SURVIVAL_PLAYER_CHECK_RANGE;
    public static final ModConfigSpec.DoubleValue NO_PLAYER_COOLDOWN_SCALE;
    public static final ModConfigSpec.DoubleValue NO_PLAYER_ACTION_SPEED;

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

        builder.comment("罡风君主战斗与技能倍率配置 (第一条命死亡回满血进入二阶段，无三阶段) / Combat and skill damage multipliers (Phase 1 fatal blow restores full HP and enters Phase 2, no Phase 3)")
                .push("combat");

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
                .comment("第一条命死亡满血觉醒二阶段时的风暴震波伤害倍率 / Damage multiplier for Phase 2 rebirth storm burst")
                .defineInRange("phaseShiftBurstMultiplier", 1.25D, 0.10D, 10.0D);

        KNOCKBACK_SCALE = builder
                .comment("技能击退力度全局缩放系数 (实际击退还会受目标击退抗性减免) / Global knockback scale for skills (actual knockback is also reduced by target knockback resistance)")
                .defineInRange("knockbackScale", 1.00D, 0.0D, 3.0D);

        builder.pop();

        builder.comment("斗蛐蛐模式配置 (非玩家限伤、对非玩家禁疗、周围无生存玩家时高频出招) / Cricket (Mob Arena) mode settings (non-player damage cap, anti-heal on non-players, rapid attack frequency without survival players nearby)")
                .push("cricketMode");

        CRICKET_MODE_ENABLED = builder
                .comment("是否开启斗蛐蛐模式 (默认开启) / Enable Cricket Mode (enabled by default)")
                .define("enabled", true);

        NON_PLAYER_DAMAGE_CAP_RATIO = builder
                .comment("来自非玩家且主人不是玩家的有源伤害单次上限占最大生命值的比例 (0.04 = 单次最多扣除4%最大生命值；无源伤害不受限制) / Max single-hit damage ratio of max health from non-player / non-player-owned sources (0.04 = 4% max HP; sourceless damage is unaffected)")
                .defineInRange("nonPlayerDamageCapRatio", 0.04D, 0.005D, 1.00D);

        ANTI_HEAL_DURATION_TICKS = builder
                .comment("攻击非玩家生物时施加禁疗效果的持续时间 (刻，20刻=1秒) / Duration in ticks of the anti-heal effect applied when hitting non-player entities")
                .defineInRange("antiHealDurationTicks", 120, 20, 6000);

        SURVIVAL_PLAYER_CHECK_RANGE = builder
                .comment("检测周围是否存在生存/冒险模式玩家的半径 (格)，范围内无此类玩家时加快攻击频率 / Radius in blocks to check for survival/adventure players; attack frequency increases when none are present")
                .defineInRange("survivalPlayerCheckRange", 48.0D, 8.0D, 128.0D);

        NO_PLAYER_COOLDOWN_SCALE = builder
                .comment("周围没有生存模式玩家时的技能冷却与攻击间隔缩放系数 (越小越快，0.40 = 原冷却的40%) / Skill cooldown and attack interval scale when no survival player is nearby (lower is faster, 0.40 = 40% cooldown)")
                .defineInRange("noPlayerCooldownScale", 0.40D, 0.10D, 1.00D);

        NO_PLAYER_ACTION_SPEED = builder
                .comment("周围没有生存模式玩家时的攻击动作帧推进速率倍率 (1.45 = 1.45倍出招速度) / Attack action speed multiplier when no survival player is nearby (1.45 = 1.45x speed)")
                .defineInRange("noPlayerActionSpeed", 1.45D, 1.00D, 3.00D);

        builder.pop();

        SPEC = builder.build();
    }

    private ZephyrarchConfig() {
    }
}
