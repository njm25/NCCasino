package org.nc.nccasino.helpers;

import java.lang.reflect.Method;

import org.bukkit.entity.Entity;

/**
 * A Copper Golem's oxidation stage. Spigot calls it getWeatherState() with
 * CopperGolem.CopperWeatherState; Paper renamed it getWeatheringState() with
 * its own WeatheringCopperState, so calling Spigot's method on Paper throws
 * NoSuchMethodError. Both enums list the same four stages in the same order.
 *
 * <p>Copper Golems arrived in 1.21.9. Naming the CopperGolem class anywhere
 * throws NoClassDefFoundError on an older server, so this class looks it up by
 * name and the menus ask {@link #is} instead of using instanceof.
 */
public final class CopperGolemState {
    private static final String[] STATE_NAMES = {"WeatherState", "WeatheringState"};
    private static final Class<?> COPPER_GOLEM = findCopperGolemClass();

    private CopperGolemState() {
    }

    /** Whether this entity is a Copper Golem; always false before 1.21.9. */
    public static boolean is(Entity entity) {
        return COPPER_GOLEM != null && COPPER_GOLEM.isInstance(entity);
    }

    /** The golem's current stage, or null if it isn't a golem or this server has neither method. */
    public static Enum<?> get(Entity golem) {
        for (String name : STATE_NAMES) {
            Enum<?> state = read(golem, name);
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    /** Moves the golem to its next stage, wrapping after the last; returns that stage, or null if it couldn't. */
    public static Enum<?> cycle(Entity golem) {
        for (String name : STATE_NAMES) {
            Enum<?> current = read(golem, name);
            if (current == null) {
                continue;
            }
            Enum<?>[] stages = current.getDeclaringClass().getEnumConstants();
            Enum<?> next = stages[(current.ordinal() + 1) % stages.length];
            try {
                Method setter = COPPER_GOLEM.getMethod("set" + name, current.getDeclaringClass());
                setter.invoke(golem, next);
                return next;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private static Enum<?> read(Entity golem, String name) {
        if (!is(golem)) {
            return null;
        }
        try {
            return COPPER_GOLEM.getMethod("get" + name).invoke(golem) instanceof Enum<?> state ? state : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Class<?> findCopperGolemClass() {
        try {
            return Class.forName("org.bukkit.entity.CopperGolem");
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}
