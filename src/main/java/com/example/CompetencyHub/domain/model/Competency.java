package com.example.CompetencyHub.domain.model;

import jakarta.persistence.*;

@Entity
@Table(name = "competency")
public class Competency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private int weight;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    protected Competency() {
    }

    public Competency(String title, int weight, int orderIndex) {
        this.title = title;
        this.weight = weight;
        this.orderIndex = orderIndex;
    }

    /**
     * Replaces the editable fields. The owning course is deliberately not among them:
     * moving a competency to another course would silently move its assessments and every
     * student's submissions with it. That is a different operation, not an edit.
     */
    public void updateDetails(String title, int weight, int orderIndex) {
        this.title = title;
        this.weight = weight;
        this.orderIndex = orderIndex;
    }

    public Long getId() { return id; }
    public Course getCourse() { return course; }
    void setCourse(Course course) { this.course = course; }
    public String getTitle() { return title; }
    public int getWeight() { return weight; }
    public int getOrderIndex() { return orderIndex; }
}
