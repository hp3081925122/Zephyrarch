package com.hp.zephyrarch.registry;

import com.hp.zephyrarch.Zephyrarch;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Zephyrarch.MOD_ID);

    /** 与 assets/zephyrarch/sounds.json、tools/gen_sounds.py 保持一致。 */
    public static final String[] NAMES = {
            "punch_swing", "punch_impact", "sweep_spin", "rush_charge", "rush_dash", "hammer_rise", "hammer_slam",
            "grab_catch", "grab_throw", "stomp_pull", "stomp_burst", "phase_shift", "wind_dash", "stagger",
            "bolt_shoot", "lance_charge", "lance_fire", "twister_spawn", "downdraft_impact", "blade_throw",
            "cage_form", "orb_shoot"
    };

    public static final DeferredHolder<SoundEvent, SoundEvent> PUNCH_SWING = reg("punch_swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUNCH_IMPACT = reg("punch_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWEEP_SPIN = reg("sweep_spin");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUSH_CHARGE = reg("rush_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUSH_DASH = reg("rush_dash");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAMMER_RISE = reg("hammer_rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAMMER_SLAM = reg("hammer_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAB_CATCH = reg("grab_catch");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAB_THROW = reg("grab_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> STOMP_PULL = reg("stomp_pull");
    public static final DeferredHolder<SoundEvent, SoundEvent> STOMP_BURST = reg("stomp_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHASE_SHIFT = reg("phase_shift");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_DASH = reg("wind_dash");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAGGER = reg("stagger");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOLT_SHOOT = reg("bolt_shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_CHARGE = reg("lance_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_FIRE = reg("lance_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> TWISTER_SPAWN = reg("twister_spawn");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOWNDRAFT_IMPACT = reg("downdraft_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_THROW = reg("blade_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAGE_FORM = reg("cage_form");
    public static final DeferredHolder<SoundEvent, SoundEvent> ORB_SHOOT = reg("orb_shoot");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Zephyrarch.MOD_ID, name)));
    }

    private ModSounds() {
    }
}
