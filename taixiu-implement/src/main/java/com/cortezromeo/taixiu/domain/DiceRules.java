package com.cortezromeo.taixiu.domain;

import com.cortezromeo.taixiu.api.TaiXiuResult;

public final class DiceRules {

    private DiceRules() {
    }

    public static DiceOutcome resolve(int dice1, int dice2, int dice3, boolean disableSpecial) {
        validateDie(dice1);
        validateDie(dice2);
        validateDie(dice3);

        int total = dice1 + dice2 + dice3;
        if (disableSpecial && total == 3) {
            dice3++;
            total++;
        } else if (disableSpecial && total == 18) {
            dice3--;
            total--;
        }

        TaiXiuResult result = total == 3 || total == 18
                ? TaiXiuResult.SPECIAL
                : total <= 10 ? TaiXiuResult.XIU : TaiXiuResult.TAI;
        return new DiceOutcome(dice1, dice2, dice3, total, result);
    }

    private static void validateDie(int die) {
        if (die < 1 || die > 6) throw new IllegalArgumentException("Dice must be between 1 and 6");
    }

    public record DiceOutcome(int dice1, int dice2, int dice3, int total, TaiXiuResult result) {
    }
}
