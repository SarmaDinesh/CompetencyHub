package com.example.CompetencyHub.controller;

import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.common.exception.NotFoundException;
import com.example.CompetencyHub.service.CourseService;
import com.example.CompetencyHub.web.CourseController;
import com.example.CompetencyHub.domain.model.Course;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.example.CompetencyHub.security.WebSecurityTestConfig;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static com.example.CompetencyHub.security.SecurityTestSupport.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest loads the web layer only: controllers, the exception handler, converters,
 * validation. No JPA, no Kafka, no scheduler, no database. It starts in about a second
 * because of everything it leaves out.
 *
 * CourseService returns entities, so these mocks return Course. The mapping to
 * CourseResponse happens in the controller -- which means these tests are exercising that
 * mapping, and a broken mapper shows up here rather than in production.
 */
@WebMvcTest(CourseController.class)
@Import(WebSecurityTestConfig.class)
class CourseControllerTest {

    @Autowired private MockMvc mockMvc;

    /*
     * @MockitoBean, not @MockBean. The old one is gone in Boot 4; the replacement lives in
     * Spring Framework 7 (org.springframework.test.context.bean.override.mockito), so it
     * works in plain Spring tests too, not only Boot ones.
     */
    @MockitoBean
    private CourseService courseService;

    /**
     * Builds the Course entity the service would return.
     *
     * Seats are reduced by calling the real reserveSeat() rather than by setting the field,
     * so the fixture cannot represent a state the domain would refuse to produce. The id is
     * set reflectively because @GeneratedValue means a constructed Course has none, and the
     * Location header assertion needs one.
     */
    private static Course savedCourse(int capacity, int seatsTaken) {
        Course course = new Course(
                "CS544",
                "Enterprise Architecture",
                "Enterprise patterns with Spring Boot",
                capacity);
        for (int i = 0; i < seatsTaken; i++) {
            course.reserveSeat();
        }
        ReflectionTestUtils.setField(course, "id", 1L);
        return course;
    }

    @Test
    void returnsTheCourseWhenItExists() throws Exception {
        when(courseService.findById(1L)).thenReturn(savedCourse(30, 2));

        mockMvc.perform(get("/api/courses/1").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.code").value("CS544"))
                .andExpect(jsonPath("$.title").value("Enterprise Architecture"))
                .andExpect(jsonPath("$.description").value("Enterprise patterns with Spring Boot"))
                .andExpect(jsonPath("$.capacity").value(30))
                .andExpect(jsonPath("$.seatsAvailable").value(28));
    }

    /**
     * The entity must NOT leak. Course has a `version` field and a `competencies` list that
     * CourseResponse deliberately omits; if either appears in the JSON, the controller is
     * serialising the entity directly instead of mapping to the DTO.
     *
     * Worth its own test because that bug is invisible -- everything the client asked for is
     * still there, just with extra internals attached.
     */
    @Test
    void doesNotLeakEntityInternalsIntoTheResponse() throws Exception {
        when(courseService.findById(1L)).thenReturn(savedCourse(30, 2));

        mockMvc.perform(get("/api/courses/1").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.competencies").doesNotExist());
    }

    @Test
    void returns404WhenCourseDoesNotExist() throws Exception {
        when(courseService.findById(99L)).thenThrow(new NotFoundException("Course 99 not found"));

        mockMvc.perform(get("/api/courses/99").with(asAdmin()))
                .andExpect(status().isNotFound())
                // Asserting the error CONTRACT, not just the status code. This is the
                // RFC 9457 ProblemDetail shape from Module 8, and clients parse it -- so
                // changing it is a breaking change and deserves a failing test.
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void returns201WithLocationHeaderOnCreate() throws Exception {
        /*
         * eq() on every argument rather than any(), and this is the important part.
         *
         * create(String, String, String, Integer) has three adjacent String parameters, so
         * a controller that passes title where code belongs compiles cleanly and fails
         * silently. any() would happily match that. Naming the expected values means the
         * test catches the swap.
         *
         * Matchers cannot be mixed with raw values -- once one argument uses a matcher,
         * all of them must.
         */
        when(courseService.create(
                eq("CS544"),
                eq("Enterprise Architecture"),
                eq("Enterprise patterns with Spring Boot"),
                eq(30)))
                .thenReturn(savedCourse(30, 0));

        mockMvc.perform(post("/api/courses").with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        /*
                         * Literal JSON, not the slides' asJsonString(someDto) helper.
                         * Serializing your own DTO to build the request means a broken DTO
                         * produces a matching broken request and the test still passes.
                         * A literal tests the contract a real client would send.
                         */
                        .content("""
                                {
                                  "code": "CS544",
                                  "title": "Enterprise Architecture",
                                  "description": "Enterprise patterns with Spring Boot",
                                  "capacity": 30
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/courses/1")))
                // A new course has every seat free: the Course constructor sets
                // seatsAvailable = capacity.
                .andExpect(jsonPath("$.seatsAvailable").value(30));
    }

    @Test
    void returns400WithFieldErrorsWhenTitleIsBlank() throws Exception {
        mockMvc.perform(post("/api/courses").with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "CS544",
                                  "title": "",
                                  "description": "Enterprise patterns with Spring Boot",
                                  "capacity": 30
                                }
                                """))
                .andExpect(status().isBadRequest())
                // fieldErrors is an object keyed by field name, so assert the KEY. That is
                // what a client reads to highlight the offending input; the message text is
                // copy and will get reworded, so pinning it would make this test brittle
                // for no gain.
                .andExpect(jsonPath("$.fieldErrors.title").exists());

        // Validation must reject before the service is reached. Without this the test
        // would pass even if @Valid were removed and the service rejected it instead --
        // same outcome, wrong layer, and a much more expensive round trip.
        verifyNoInteractions(courseService);
    }

    @Test
    void returns400WhenCapacityIsNegative() throws Exception {
        mockMvc.perform(post("/api/courses").with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "CS544",
                                  "title": "Enterprise Architecture",
                                  "description": "Enterprise patterns with Spring Boot",
                                  "capacity": -5
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(courseService);
    }

    /**
     * `code` is UNIQUE in the schema, so a second course with the same code is a genuine
     * conflict: the request is well formed but collides with existing state, which is what
     * 409 means.
     *
     * Adjust the exception if your service throws something more specific for a duplicate
     * code, and make sure @RestControllerAdvice maps it to CONFLICT.
     */
    @Test
    void returns409WhenCourseCodeAlreadyExists() throws Exception {
        when(courseService.create(anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(new BusinessRuleException("Course code CS544 already exists"));

        mockMvc.perform(post("/api/courses").with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "CS544",
                                  "title": "Enterprise Architecture",
                                  "description": "Enterprise patterns with Spring Boot",
                                  "capacity": 30
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ---- security ------------------------------------------------------------------

    @Test
    void anonymousRequestsAre401WithAProblemBody() throws Exception {
        mockMvc.perform(get("/api/courses/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                // The standard challenge header, from BearerTokenAuthenticationEntryPoint.
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")));
        verifyNoInteractions(courseService);
    }

    @Test
    void anyLoggedInUserCanReadTheCatalog() throws Exception {
        when(courseService.findById(1L)).thenReturn(savedCourse(30, 0));

        mockMvc.perform(get("/api/courses/1").with(asStudent(7L)))
                .andExpect(status().isOk());
    }

    @Test
    void aStudentCannotCreateACourse() throws Exception {
        mockMvc.perform(post("/api/courses").with(asStudent(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "CS544", "title": "Enterprise Architecture", "capacity": 30 }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        // Denied BEFORE the method body: the service was never reached.
        verifyNoInteractions(courseService);
    }
}
