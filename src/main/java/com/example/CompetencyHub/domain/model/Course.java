package com.example.CompetencyHub.domain.model;

import com.example.CompetencyHub.common.exception.CourseFullException;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "course")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int capacity;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<Competency> competencies = new ArrayList<>();

    /**
     * Seats still open. Decremented on enrollment, restored on withdrawal.
     *
     * <p>Kept as its own column rather than computed as
     * {@code capacity - count(enrollments)} because the check happens on every
     * enrollment attempt, and a counter read is cheaper than an aggregate. The
     * trade-off is that it can drift if anything bypasses the service layer — which
     * is exactly why the decrement lives inside the entity, below.
     */
    @Column(name = "seats_available", nullable = false)
    private int seatsAvailable;

    /**
     * Optimistic locking counter. Never set this by hand.
     *
     * <p>Hibernate adds {@code AND version = ?} to every UPDATE and increments the
     * value. If another transaction has already changed the row, the WHERE clause
     * matches zero rows and Hibernate throws OptimisticLockingFailureException rather
     * than silently overwriting.
     *
     * <p>The alternative, pessimistic locking (SELECT ... FOR UPDATE), holds a database
     * lock for the whole transaction. Optimistic is the right default: conflicts on a
     * given course are rare, and it costs nothing when there is no contention.
     */
    @Version
    @Column(nullable = false)
    private long version;

    public Course() {
    }

    public Course(String code, String title, String description, int capacity) {
        this.code = code;
        this.title = title;
        this.description = description;
        this.capacity = capacity;
        this.seatsAvailable = capacity;
    }

    public List<Competency> getCompetencies() {
        return Collections.unmodifiableList(competencies);
    }

    public void addCompetency(Competency competency) {
        competencies.add(competency);
        competency.setCourse(this);
    }

    public void removeCompetency(Competency competency) {
        competencies.remove(competency);
        competency.setCourse(null);
    }

    /**
     * Claims one seat.
     *
     * <p>The rule lives on the entity, not in the service, so there is no way to
     * decrement seats without passing the check. A service method could forget;
     * a method that owns the field cannot.
     *
     * @throws CourseFullException if no seats remain
     */
    public void reserveSeat() {
        if (seatsAvailable <= 0) {
            throw new CourseFullException("Course " + code + " has no seats available");
        }
        seatsAvailable--;
    }

    /** Returns a seat on withdrawal. Guarded so it can never exceed capacity. */
    public void releaseSeat() {
        if (seatsAvailable < capacity) {
            seatsAvailable++;
        }
    }

    public int getSeatsAvailable() {
        return seatsAvailable;
    }

    public long getVersion() {
        return version;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public int getCapacity() {
        return capacity;
    }
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }
}
