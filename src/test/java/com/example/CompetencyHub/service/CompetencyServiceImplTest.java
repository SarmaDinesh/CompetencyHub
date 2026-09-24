package com.example.CompetencyHub.service;

import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.common.exception.NotFoundException;
import com.example.CompetencyHub.domain.model.Competency;
import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.repository.CompetencyRepository;
import com.example.CompetencyHub.repository.CourseRepository;
import com.example.CompetencyHub.repository.SubmissionRepository;
import com.example.CompetencyHub.service.impl.CompetencyServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.example.CompetencyHub.TestFixtures.course;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompetencyServiceImplTest {

    @Mock private CompetencyRepository competencyRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private SubmissionRepository submissionRepository;

    @InjectMocks private CompetencyServiceImpl service;

    @Test
    void createWithoutOrderIndexGoesAfterTheLastOne() {
        Course course = course();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(competencyRepository.findMaxOrderIndexByCourseId(1L)).thenReturn(3);
        when(competencyRepository.save(any(Competency.class))).thenAnswer(inv -> inv.getArgument(0));

        Competency created = service.create(1L, "Transactions", 25, null);

        assertThat(created.getOrderIndex()).isEqualTo(4);
        // Both sides of the relationship are set -- the fix for the stale-parent bug.
        assertThat(created.getCourse()).isSameAs(course);
        assertThat(course.getCompetencies()).contains(created);
    }

    @Test
    void createWithAnExplicitOrderIndexKeepsIt() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course()));
        when(competencyRepository.save(any(Competency.class))).thenAnswer(inv -> inv.getArgument(0));

        Competency created = service.create(1L, "Transactions", 25, 2);

        assertThat(created.getOrderIndex()).isEqualTo(2);
        // No reason to ask the database for the max when the caller already said.
        verify(competencyRepository, never()).findMaxOrderIndexByCourseId(any());
    }

    @Test
    void creatingInAnUnknownCourseIs404() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(99L, "Transactions", 25, null))
                .isInstanceOf(NotFoundException.class);
        verify(competencyRepository, never()).save(any());
    }

    @Test
    void listingAnUnknownCourseIs404NotAnEmptyList() {
        when(courseRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByCourse(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemovesTheCompetencyFromItsCourse() {
        Course course = course();
        Competency competency = new Competency("Transactions", 25, 1);
        course.addCompetency(competency);
        when(competencyRepository.findById(7L)).thenReturn(Optional.of(competency));
        when(submissionRepository.existsByAssessmentCompetencyId(7L)).thenReturn(false);

        service.delete(7L);

        // Removed from the parent's list; orphanRemoval turns that into the DELETE.
        assertThat(course.getCompetencies()).doesNotContain(competency);
    }

    @Test
    void aCompetencyWithSubmissionsCannotBeDeleted() {
        Course course = course();
        Competency competency = new Competency("Transactions", 25, 1);
        course.addCompetency(competency);
        when(competencyRepository.findById(7L)).thenReturn(Optional.of(competency));
        when(submissionRepository.existsByAssessmentCompetencyId(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("submissions");
        assertThat(course.getCompetencies()).contains(competency);
    }
}
