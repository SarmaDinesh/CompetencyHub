package com.example.CompetencyHub.web;

import com.example.CompetencyHub.domain.model.Mentor;
import com.example.CompetencyHub.service.MentorService;
import com.example.CompetencyHub.web.dto.request.CreateMentorRequest;
import com.example.CompetencyHub.web.dto.response.MentorResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/mentors")
public class MentorController {

    private final MentorService mentorService;

    public MentorController(MentorService mentorService) {
        this.mentorService = mentorService;
    }

    @PostMapping
    public ResponseEntity<MentorResponse> create(@Valid @RequestBody CreateMentorRequest request,
                                                 UriComponentsBuilder uriBuilder) {
        Mentor created = mentorService.create(
                request.firstName(), request.lastName(), request.email(), request.specialization());
        return ResponseEntity
                .created(uriBuilder.path("/api/mentors/{id}").buildAndExpand(created.getId()).toUri())
                .body(MentorResponse.from(created));
    }

    @GetMapping
    public List<MentorResponse> list() {
        return mentorService.findAll().stream().map(MentorResponse::from).toList();
    }

    @GetMapping("/{id}")
    public MentorResponse findById(@PathVariable Long id) {
        return MentorResponse.from(mentorService.findById(id));
    }
}
