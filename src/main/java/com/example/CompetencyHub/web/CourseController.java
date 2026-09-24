package com.example.CompetencyHub.web;

import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.service.CourseService;
import com.example.CompetencyHub.web.dto.request.CreateCourseRequest;
import com.example.CompetencyHub.web.dto.request.UpdateCourseRequest;
import com.example.CompetencyHub.web.dto.response.CourseResponse;
import com.example.CompetencyHub.web.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Course catalog endpoints.
 *
 * <p>The path names a resource ({@code /api/courses}) and the HTTP method says what to
 * do with it. No verbs in paths — no /getCourses, no /createCourse. That convention is
 * what makes an API predictable without reading its documentation.
 */

@Tag(name = "Courses")
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    /**
     * Lists courses, paginated, or searches by title when {@code search} is supplied.
     *
     * <p>{@code Pageable} is resolved from ?page=, ?size= and ?sort= automatically.
     * {@code @PageableDefault} caps the default size — without it a client can request
     * ?size=1000000 and pull the whole table in one query.
     *
     * <p>Filters belong in the query string, not the path: /api/courses?search=spring
     * is the same collection, narrowed. /api/courses/search would imply a different one.
     */
    @Operation(summary = "List courses, or search by title",
            description = "Paged with ?page=, ?size= (default 20) and ?sort= (default code). ?search= does a case-insensitive title match.")
    @ApiResponse(responseCode = "200", description = "A page of courses; with ?search=, all matches in one page")
    @GetMapping
    public PageResponse<CourseResponse> list(
            @Parameter(description = "Case-insensitive title fragment; when present, paging is ignored")
            @RequestParam(required = false) String search,
            // @ParameterObject: without it, Swagger UI shows Pageable as one JSON-object query
            // parameter nobody knows how to fill. With it, page, size and sort appear as the
            // three separate query parameters Spring actually reads.
            @ParameterObject @PageableDefault(size = 20, sort = "code") Pageable pageable) {

        if (search != null && !search.isBlank()) {
            List<CourseResponse> matches = courseService.search(search).stream()
                    .map(CourseResponse::from)
                    .toList();
            return new PageResponse<>(matches, 0, matches.size(), matches.size(), 1, true, true);
        }

        return PageResponse.from(courseService.findAll(pageable), CourseResponse::from);
    }

    /** 200 with the course, or 404 from the exception handler if it does not exist. */
    @Operation(summary = "Get a course")
    @ApiResponse(responseCode = "200", description = "The course")
    @ApiResponse(responseCode = "404", description = "No course with this id")
    @GetMapping("/{id}")
    public CourseResponse findById(@PathVariable Long id) {
        return CourseResponse.from(courseService.findById(id));
    }

    /**
     * Creates a course.
     *
     * <p>{@code @Valid} triggers the constraints on the request record before this method
     * body runs. A violation throws MethodArgumentNotValidException, which the advice
     * turns into a 400 listing every offending field.
     *
     * <p>Returns <b>201 Created</b> with a {@code Location} header pointing at the new
     * resource. 200 would be wrong: it says "here is the result", not "something now
     * exists at this address".
     */
    @Operation(summary = "Create a course",
            description = "capacity is optional and defaults to competencyhub.default-course-capacity.")
    @ApiResponse(responseCode = "201", description = "Created; Location header points at the new course")
    @ApiResponse(responseCode = "400", description = "Invalid body, e.g. a code not like CS544")
    @ApiResponse(responseCode = "409", description = "A course with this code already exists")
    @PostMapping
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CreateCourseRequest request,
                                                 UriComponentsBuilder uriBuilder) {
        // The controller unpacks the request — translating HTTP into domain terms is
        // exactly the web layer's job.
        Course created = courseService.create(
                request.code(),
                request.title(),
                request.description(),
                request.capacity()
        );

        URI location = uriBuilder.path("/api/courses/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(CourseResponse.from(created));
    }

    /**
     * Replaces the editable fields of a course. 200 with the updated resource.
     *
     * <p>PUT is idempotent: sending the same request twice leaves the same state, so a
     * client that times out can safely retry. POST is not — which is exactly why create
     * uses POST and update uses PUT.
     */
    @Operation(summary = "Replace a course's editable fields")
    @ApiResponse(responseCode = "200", description = "The updated course")
    @ApiResponse(responseCode = "400", description = "Invalid body")
    @ApiResponse(responseCode = "404", description = "No course with this id")
    @ApiResponse(responseCode = "409", description = "Capacity below current enrollment, or modified concurrently")
    @PutMapping("/{id}")
    public CourseResponse update(@PathVariable Long id,
                                 @Valid @RequestBody UpdateCourseRequest request) {
        Course updated = courseService.update(
                id, request.title(), request.description(), request.capacity());
        return CourseResponse.from(updated);
    }

    /**
     * Deletes a course. <b>204 No Content</b> — the operation succeeded and there is
     * nothing to return. Returning 200 with an empty body, or a "deleted: true" object,
     * is noise the status code already conveys.
     */
    @Operation(summary = "Delete a course")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No course with this id")
    @ApiResponse(responseCode = "409", description = "The course has active enrollments")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
