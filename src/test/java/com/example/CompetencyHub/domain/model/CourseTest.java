package com.example.CompetencyHub.domain.model;

import com.example.CompetencyHub.common.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * No @RunWith, no @ExtendWith, no annotations at all. JUnit 5 dropped runners, and this
 * class needs nothing from Spring -- it constructs a Course and calls methods on it.
 *
 * Package-private class and methods: JUnit 5 does not require public.
 */
class CourseTest {

    private Course course;

    @BeforeEach   // was @Before
    void setUp() {
        course = new Course("CS544", "Enterprise Architecture", "This course helps learn Java", 2);
    }

    @Test
    void newCourseHasAllSeatsAvailable() {
        assertThat(course.getSeatsAvailable()).isEqualTo(2);
    }

    @Test
    void reservingASeatDecrementsAvailability() {
        course.reserveSeat();
        assertThat(course.getSeatsAvailable()).isEqualTo(1);
    }

    @Test
    void theLastSeatCanBeReserved() {
        course.reserveSeat();
        course.reserveSeat();
        assertThat(course.getSeatsAvailable()).isZero();
    }

    /*
     * Named for the PASS condition. "reservingBeyondCapacityIsRejected" states a guarantee
     * the system makes; "testReserveSeat3" states nothing. When this fails in CI six months
     * from now, the name is the entire error message you get at a glance.
     */
    @Test
    void reservingBeyondCapacityIsRejected() {
        course.reserveSeat();
        course.reserveSeat();

        assertThatThrownBy(() -> course.reserveSeat())
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void releasingASeatRestoresAvailability() {
        course.reserveSeat();
        course.releaseSeat();
        assertThat(course.getSeatsAvailable()).isEqualTo(2);
    }
}