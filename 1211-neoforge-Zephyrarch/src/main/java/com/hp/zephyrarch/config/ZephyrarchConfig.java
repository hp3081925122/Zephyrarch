package com.hp.zephyrarch.config;

import com.hp.zephyrarch.entity.ZephyrarchAttack;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ZephyrarchConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue ATTACK_DAMAGE;
    public static final ModConfigSpec.DoubleValue ARMOR;
    public static final ModConfigSpec.DoubleValue MOVEMENT_SPEED;
    public static final ModConfigSpec.DoubleValue FOLLOW_RANGE;

    public static final ModConfigSpec.DoubleValue COMBO_FIRST_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue COMBO_SECOND_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARM_SWEEP_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue RUSH_PATH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue RUSH_FINISH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HAMMER_CENTER_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HAMMER_EDGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue GRAB_SQUEEZE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue STOMP_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue PHASE_SHIFT_BURST_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BOLT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LANCE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue TWISTER_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue DOWNDRAFT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BLADE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CAGE_WALL_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CAGE_ORB_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CAGE_HEALTH_RATIO;
    public static final ModConfigSpec.DoubleValue HOVER_HEIGHT;
    public static final ModConfigSpec.DoubleValue WIND_DASH_TARGET_DISTANCE;
    public static final ModConfigSpec.DoubleValue KNOCKBACK_SCALE;
    private static final ModConfigSpec.IntValue[] COOLDOWNS = new ModConfigSpec.IntValue[ZephyrarchAttack.values().length];

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
                .defineInRange("movementSpeed", 0.32D, 0.05D, 1.0D);

        FOLLOW_RANGE = builder
                .comment("罡风君主索敌追踪距离 / Follow range of Zephyrarch")
                .defineInRange("followRange", 44.0D, 16.0D, 128.0D);

        builder.pop();

        builder.comment("罡风君主技能伤害倍率 (第一条命死亡后回满血进入二阶段：一阶段只用近战，二阶段只用远程) / Skill damage multipliers (Phase 1 fatal blow restores full HP and enters Phase 2; Phase 1 is melee only, Phase 2 is ranged only)")
                .push("combat");

        COMBO_FIRST_MULTIPLIER = mult(builder, "comboFirstMultiplier", "裂风连拳第一拳 / Gale Combo first punch", 1.0D);
        COMBO_SECOND_MULTIPLIER = mult(builder, "comboSecondMultiplier", "裂风连拳第二拳 / Gale Combo second punch", 1.3D);
        ARM_SWEEP_MULTIPLIER = mult(builder, "armSweepMultiplier", "旋臂横扫 / Arm Sweep", 1.2D);
        RUSH_PATH_MULTIPLIER = mult(builder, "rushPathMultiplier", "罡风冲拳冲刺路径 / Gale Rush dash path", 1.4D);
        RUSH_FINISH_MULTIPLIER = mult(builder, "rushFinishMultiplier", "罡风冲拳终点拳 / Gale Rush finishing punch", 0.8D);
        HAMMER_CENTER_MULTIPLIER = mult(builder, "hammerCenterMultiplier", "天坠锤击中心 1.5 格 / Sky Hammer center", 1.8D);
        HAMMER_EDGE_MULTIPLIER = mult(builder, "hammerEdgeMultiplier", "天坠锤击外圈 / Sky Hammer outer ring", 0.9D);
        GRAB_SQUEEZE_MULTIPLIER = mult(builder, "grabSqueezeMultiplier", "擒风摔投每次挤压 / Gale Grab squeeze per pulse", 0.2D);
        STOMP_MULTIPLIER = mult(builder, "stompMultiplier", "旋涡踏震冲击 / Vortex Stomp burst", 1.5D);
        PHASE_SHIFT_BURST_MULTIPLIER = mult(builder, "phaseShiftBurstMultiplier", "风暴相变爆发 / Phase Shift burst", 0.8D);
        BOLT_MULTIPLIER = mult(builder, "boltMultiplier", "风弹连射每颗 / Gale Bolts per bolt", 0.45D);
        LANCE_MULTIPLIER = mult(builder, "lanceMultiplier", "罡风长枪 / Tempest Lance", 2.0D);
        TWISTER_MULTIPLIER = mult(builder, "twisterMultiplier", "风暴之眼小龙卷接触 / Storm Eye twister contact", 0.35D);
        DOWNDRAFT_MULTIPLIER = mult(builder, "downdraftMultiplier", "天压风柱每根 / Downdraft per column", 0.9D);
        BLADE_MULTIPLIER = mult(builder, "bladeMultiplier", "回旋风刃每次 / Boomerang Blades per hit", 0.7D);
        CAGE_WALL_MULTIPLIER = mult(builder, "cageWallMultiplier", "气旋囚笼风墙 / Cyclone Cage wall", 0.6D);
        CAGE_ORB_MULTIPLIER = mult(builder, "cageOrbMultiplier", "气旋囚笼风暴球 / Cyclone Cage storm orb", 1.1D);
        CAGE_HEALTH_RATIO = builder
                .comment("二阶段血量低于该比例才会使用气旋囚笼 / Cyclone Cage is unlocked below this Phase 2 health ratio")
                .defineInRange("cageHealthRatio", 0.35D, 0.0D, 1.0D);
        HOVER_HEIGHT = builder
                .comment("二阶段悬浮离地高度 (格) / Phase 2 hover height in blocks")
                .defineInRange("hoverHeight", 1.5D, 0.0D, 6.0D);

        WIND_DASH_TARGET_DISTANCE = builder
                .comment("二阶段风遁后撤期望拉开到的目标水平距离 (格，会根据当前距离与地形障碍动态计算后撤步长) / Desired horizontal distance to target after Phase 2 Wind Dash (dash distance adapts dynamically to current distance and terrain)")
                .defineInRange("windDashTargetDistance", 11.0D, 6.0D, 20.0D);

        KNOCKBACK_SCALE = builder
                .comment("技能击退力度全局缩放系数 (实际击退还会受目标击退抗性减免) / Global knockback scale for skills (actual knockback is also reduced by target knockback resistance)")
                .defineInRange("knockbackScale", 1.00D, 0.0D, 3.0D);

        builder.pop();

        builder.comment("技能冷却 (刻，20 刻 = 1 秒) / Skill cooldowns in ticks").push("cooldowns");
        cd(builder, ZephyrarchAttack.GALE_COMBO, "galeCombo", 60);
        cd(builder, ZephyrarchAttack.ARM_SWEEP, "armSweep", 100);
        cd(builder, ZephyrarchAttack.GALE_RUSH, "galeRush", 120);
        cd(builder, ZephyrarchAttack.SKY_HAMMER, "skyHammer", 140);
        cd(builder, ZephyrarchAttack.GALE_GRAB, "galeGrab", 160);
        cd(builder, ZephyrarchAttack.VORTEX_STOMP, "vortexStomp", 140);
        cd(builder, ZephyrarchAttack.GALE_BOLTS, "galeBolts", 80);
        cd(builder, ZephyrarchAttack.TEMPEST_LANCE, "tempestLance", 140);
        cd(builder, ZephyrarchAttack.STORM_EYE, "stormEye", 220);
        cd(builder, ZephyrarchAttack.DOWNDRAFT, "downdraft", 180);
        cd(builder, ZephyrarchAttack.BOOMERANG_BLADES, "boomerangBlades", 120);
        cd(builder, ZephyrarchAttack.CYCLONE_CAGE, "cycloneCage", 600);
        cd(builder, ZephyrarchAttack.WIND_DASH, "windDash", 60);
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

    private static ModConfigSpec.DoubleValue mult(ModConfigSpec.Builder b, String key, String name, double def) {
        return b.comment(name + " 伤害倍率 (乘以基础攻击力) / damage multiplier (times base attack damage)")
                .defineInRange(key, def, 0.0D, 20.0D);
    }

    private static void cd(ModConfigSpec.Builder b, ZephyrarchAttack attack, String key, int def) {
        COOLDOWNS[attack.id] = b.defineInRange(key, def, 0, 12000);
    }

    public static int cooldown(ZephyrarchAttack attack) {
        ModConfigSpec.IntValue v = COOLDOWNS[attack.id];
        return v == null ? 0 : v.get();
    }

    private ZephyrarchConfig() {
    }
}
