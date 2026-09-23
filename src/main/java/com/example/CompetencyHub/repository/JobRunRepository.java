package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.JobRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobRunRepository extends JpaRepository<JobRun, Long> {
    Page<JobRun> findByJobNameOrderByStartedAtDesc(String jobName, Pageable pageable);
    Optional<JobRun> findFirstByJobNameAndStatusOrderByStartedAtDesc(String jobName, JobRun.Status status);
}
