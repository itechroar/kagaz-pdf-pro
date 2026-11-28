package kagaz.software.SPDF.controller.api.misc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompressStartingLevelTest {

    @Test
    void withoutAMinimumTheTargetDecides() {
        assertThat(CompressController.startingLevel(3, null)).isEqualTo(3);
    }

    @Test
    void aStrongerMinimumRaisesTheLevel() {
        assertThat(CompressController.startingLevel(2, 6)).isEqualTo(6);
    }

    @Test
    void aWeakerMinimumNeverLowersTheLevel() {
        assertThat(CompressController.startingLevel(7, 4)).isEqualTo(7);
    }

    @Test
    void outOfRangeMinimumsAreClamped() {
        assertThat(CompressController.startingLevel(1, 42)).isEqualTo(9);
        assertThat(CompressController.startingLevel(1, -5)).isEqualTo(1);
    }
}
