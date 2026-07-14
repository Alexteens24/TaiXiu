package com.cortezromeo.taixiu.domain;

import com.cortezromeo.taixiu.api.TaiXiuResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiceRulesTest {

    @Test
    void resolvesNormalResults() {
        assertEquals(TaiXiuResult.XIU, DiceRules.resolve(2, 3, 4, false).result());
        assertEquals(TaiXiuResult.TAI, DiceRules.resolve(4, 4, 4, false).result());
    }

    @Test
    void preservesOrDisablesSpecialResults() {
        assertEquals(TaiXiuResult.SPECIAL, DiceRules.resolve(1, 1, 1, false).result());
        assertEquals(TaiXiuResult.XIU, DiceRules.resolve(1, 1, 1, true).result());
        assertEquals(TaiXiuResult.TAI, DiceRules.resolve(6, 6, 6, true).result());
    }

    @Test
    void rejectsInvalidDice() {
        assertThrows(IllegalArgumentException.class, () -> DiceRules.resolve(0, 1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> DiceRules.resolve(7, 1, 1, false));
    }
}
