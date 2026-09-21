package com.example.CompetencyHub.service;

import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.common.exception.DuplicateCourseCodeException;
import com.example.CompetencyHub.common.exception.NotFoundException;
import com.example.CompetencyHub.config.AppProperties;
import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.repository.CourseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final AppProperties properties;

    public CourseServiceImpl(CourseRepository courseRepository, AppProperties properties) {
        this.courseRepository = courseRepository;
        this.properties = properties;
    }

    /**
     * readOnly = true lets Hibernate skip dirty-check bookkeeping on loaded entities and
     * signals to the next reader that this method cannot write. Free on every query.
     */
    @Override
    @Transactional(readOnly = true)
    public Course findById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Course not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Course> findAll(Pageable pageable) {
        return courseRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Course> search(String titleFragment) {
        return courseRepository.findByTitleContainingIgnoreCase(titleFragment);
    }

    /**
     * Creates a course.
     *
     * <p>Two things happen here that the request DTO's annotations cannot do:
     * <ul>
     *   <li><b>Uniqueness</b> requires a database lookup, so it cannot be expressed as a
     *       bean-validation constraint.</li>
     *   <li><b>The capacity default</b> comes from configuration, which the DTO has no
     *       access to.</li>
     * </ul>
     * Both are business rules, and business rules live in the service.
     */
    @Override
    @Transactional
    public Course create(String code, String title, String description, Integer capacity) {
        // Checked explicitly so a duplicate becomes a 409 with a clear message, rather
        // than a constraint violation surfacing as a 500 at flush time.
        //
        // Note this is not airtight under concurrency: two simultaneous requests can both
        // pass the check before either inserts. The unique index is the real guarantee;
        // this check exists to give the common case a good error message.
        if (courseRepository.existsByCode(code)) {
            throw new DuplicateCourseCodeException("A course with code " + code + " already exists");
        }

        int seats = (capacity != null)
                ? capacity
                : properties.getDefaultCourseCapacity();

        Course course = new Course(code, title, description, seats);
        return courseRepository.save(course);
    }

    @Override
    @Transactional
    public Course update(Long id, String title, String description, int capacity) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Course not found: " + id));

        // Capacity cannot drop below the number of students already enrolled — that would
        // leave the course holding more people than it has seats for.
        int enrolled = course.getCapacity() - course.getSeatsAvailable();
        if (capacity < enrolled) {
            throw new BusinessRuleException(
                    "Capacity cannot be reduced to " + capacity
                            + "; " + enrolled + " students are already enrolled");
        }

        course.updateDetails(title, description, capacity);

        // No save() call: `course` is managed inside this transaction, so Hibernate's
        // dirty checking issues the UPDATE at commit — carrying the @Version check with it.
        return course;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Course not found: " + id));

        // Refuse rather than cascade. Deleting a course with enrollments would erase
        // students' records of having taken it — data loss disguised as a tidy-up.
        if (course.getSeatsAvailable() < course.getCapacity()) {
            throw new BusinessRuleException("Cannot delete a course with active enrollments");
        }

        courseRepository.delete(course);
    }
}
