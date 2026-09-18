package com.example.CompetencyHub.web;

import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.service.CourseService;
import com.example.CompetencyHub.web.dto.response.CourseResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public List<CourseResponse> findAll() {
        return courseService.findAll()
                .stream()
                .map(CourseResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public CourseResponse findById(@PathVariable Long id) {
        return CourseResponse.from(courseService.findById(id));
    }

    @PostMapping
    public CourseResponse create(@RequestBody Course course) {
        return CourseResponse.from(courseService.create(course));
    }
}
