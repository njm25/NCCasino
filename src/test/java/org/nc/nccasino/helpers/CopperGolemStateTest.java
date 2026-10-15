package org.nc.nccasino.helpers;

import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.CopperGolem.CopperWeatherState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CopperGolemStateTest {
    @Test
    void readsSpigotsWeatherState() {
        CopperGolem golem = mock(CopperGolem.class);
        when(golem.getWeatherState()).thenReturn(CopperWeatherState.WEATHERED);
        assertEquals(CopperWeatherState.WEATHERED, CopperGolemState.get(golem));
    }

    @Test
    void cyclesToTheNextStageAndWrapsAfterOxidized() {
        CopperGolem golem = mock(CopperGolem.class);
        when(golem.getWeatherState()).thenReturn(CopperWeatherState.EXPOSED);
        assertEquals(CopperWeatherState.WEATHERED, CopperGolemState.cycle(golem));
        verify(golem).setWeatherState(CopperWeatherState.WEATHERED);

        when(golem.getWeatherState()).thenReturn(CopperWeatherState.OXIDIZED);
        assertEquals(CopperWeatherState.UNAFFECTED, CopperGolemState.cycle(golem));
        verify(golem).setWeatherState(CopperWeatherState.UNAFFECTED);
    }

    @Test
    void noStateMeansNullNotACrash() {
        CopperGolem golem = mock(CopperGolem.class);
        assertNull(CopperGolemState.get(golem));
        assertNull(CopperGolemState.cycle(golem));
        assertEquals("unknown", VariantNames.id(CopperGolemState.get(golem)));
    }
}
