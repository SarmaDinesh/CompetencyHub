package com.example.CompetencyHub.web;

import com.example.CompetencyHub.domain.model.Mentor;
import com.example.CompetencyHub.service.MentorService;
import com.example.CompetencyHub.web.dto.request.CreateMentorRequest;
import com.example.CompetencyHub.web.dto.response.MentorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Tag(name = "Mentors")
@RestController
@RequestMapping("/api/mentors")
public class MentorController {

    private final MentorService mentorService;

    public MentorController(MentorService mentorService) {
        this.mentorService = mentorService;
    }

    @Operation(summary = "Register a mentor")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Invalid body")
    @ApiResponse(responseCode = "409", description = "A mentor with this email already exists")
    @PostMapping
    public ResponseEntity<MentorResponse> create(@Valid @RequestBody CreateMentorRequest request,
                                                 UriComponentsBuilder uriBuilder) {
        Mentor created = mentorService.create(
                request.firstName(), request.lastName(), request.email(), request.specialization());
        return ResponseEntity
                .created(uriBuilder.path("/api/mentors/{id}").buildAndExpand(created.getId()).toUri())
                .body(MentorResponse.from(created));
    }

    @Operation(summary = "List mentors")
    @ApiResponse(responseCode = "200", description = "All mentors, by last name")
    @GetMapping
    public List<MentorResponse> list() {
        return mentorService.findAll().stream().map(MentorResponse::from).toList();
    }

    @Operation(summary = "Get a mentor")
    @ApiResponse(responseCode = "200", description = "The mentor")
    @ApiResponse(responseCode = "404", description = "No mentor with this id")
    @GetMapping("/{id}")
    public MentorResponse findById(@PathVariable Long id) {
        return MentorResponse.from(mentorService.findById(id));
    }
}
