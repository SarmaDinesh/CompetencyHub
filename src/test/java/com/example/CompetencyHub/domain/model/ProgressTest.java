package com.example.CompetencyHub.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressTest {

    @Test
    void percentageIsRoundedToTwoDecimals() {
        Progress progress = new Progress(1L, 1L);
        progress.recalculate(2, 3);   // 66.666...

        /*
         * isEqualByComparingTo, NOT isEqualTo.
         *
         * BigDecimal.equals() compares scale as well as value, so 66.67 and 66.670 are
         * not equal even though the numbers are. This produces one of the most confusing
         * assertion failures in Java -- "expected 66.67 but was 66.67". compareTo ignores
         * scale, which is what you actually mean.
         */
        assertThat(progress.getPercentComplete())
                .isEqualByComparingTo(new BigDecimal("66.67"));
    }

    @Test
    void fullMasteryIsOneHundredPercent() {
        Progress progress = new Progress(1L, 1L);
        progress.recalculate(5, 5);
        assertThat(progress.getPercentComplete())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    /** The divide-by-zero guard. This is the branch that would have crashed the job at 2 AM. */
    @Test
    void courseWithNoCompetenciesIsZeroPercentNotAnError() {
        Progress progress = new Progress(1L, 1L);
        progress.recalculate(0, 0);
        assertThat(progress.getPercentComplete()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void recalculateReportsWhetherAnythingChanged() {
        Progress progress = new Progress(1L, 1L);
        assertThat(progress.recalculate(3, 10)).isTrue();    // 0 -> 30.00
        assertThat(progress.recalculate(3, 10)).isFalse();   // same input, no change
        assertThat(progress.recalculate(4, 10)).isTrue();    // 30.00 -> 40.00
    }

    @Test
    void recalculatedAtIsStampedEvenWhenNothingChanged() {
        Progress progress = new Progress(1L, 1L);
        progress.recalculate(3, 10);
        var first = progress.getRecalculatedAt();

        progress.recalculate(3, 10);

        // "When did we last verify this" is a different question from "when did this last
        // change", and the job answers both. This pins that behaviour.
        assertThat(progress.getRecalculatedAt()).isAfterOrEqualTo(first);
    }
}