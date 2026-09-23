package com.example.CompetencyHub.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "job_run")
public class JobRun {

    public enum Status { RUNNING, SUCCEEDED, FAILED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "records_processed", nullable = false)
    private int recordsProcessed;

    @Column(columnDefinition = "text")
    private String detail;

    protected JobRun() { }   // JPA only

    public JobRun(String jobName) {
        this.jobName = jobName;
        this.startedAt = Instant.now();
        this.status = Status.RUNNING;
    }

    /** Behavior on the entity rather than setters called from the service -- same rule we
     *  used for Course.reserveSeat(). The entity decides what a valid state change is. */
    public void succeeded(int recordsProcessed) {
        this.status = Status.SUCCEEDED;
        this.recordsProcessed = recordsProcessed;
        this.finishedAt = Instant.now();
    }

    public void failed(String detail) {
        this.status = Status.FAILED;
        this.detail = truncate(detail);
        this.finishedAt = Instant.now();
    }

    public void skipped(String detail) {
        this.status = Status.SKIPPED;
        this.detail = truncate(detail);
        this.finishedAt = Instant.now();
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 4000 ? s : s.substring(0, 4000);
    }

    public Long getId() { return id; }
    public String getJobName() { return jobName; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Status getStatus() { return status; }
    public int getRecordsProcessed() { return recordsProcessed; }
    public String getDetail() { return detail; }
}
