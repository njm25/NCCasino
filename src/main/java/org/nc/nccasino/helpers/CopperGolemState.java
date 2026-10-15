package org.nc.nccasino.helpers;

import java.lang.reflect.Method;

import org.bukkit.entity.CopperGolem;

/**
 * A Copper Golem's oxidation stage. Spigot calls it getWeatherState() with
 * CopperGolem.CopperWeatherState; Paper renamed it getWeatheringState() with
 * its own WeatheringCopperState, so calling Spigot's method on Paper throws
 * NoSuchMethodError. Both enums list the same four stages in the same order.
 */
public final class CopperGolemState {
    private static final String[] STATE_NAMES = {"WeatherState", "WeatheringState"};

    private CopperGolemState() {
    }

    /** The golem's current stage, or null if this server has neither method. */
    public static Enum<?> get(CopperGolem golem) {
        for (String name : STATE_NAMES) {
            Enum<?> state = read(golem, name);
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    /** Moves the golem to its next stage, wrapping after the last; returns that stage, or null if it couldn't. */
    public static Enum<?> cycle(CopperGolem golem) {
        for (String name : STATE_NAMES) {
            Enum<?> current = read(golem, name);
            if (current == null) {
                continue;
            }
            Enum<?>[] stages = current.getDeclaringClass().getEnumConstants();
            Enum<?> next = stages[(current.ordinal() + 1) % stages.length];
            try {
                Method setter = CopperGolem.class.getMethod("set" + name, current.getDeclaringClass());
                setter.invoke(golem, next);
                return next;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private static Enum<?> read(CopperGolem golem, String name) {
        try {
            return CopperGolem.class.getMethod("get" + name).invoke(golem) instanceof Enum<?> state ? state : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
