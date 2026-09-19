package com.example.CompetencyHub.domain.model;

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

    public Course() {
    }

    public Course(String code, String title, String description, int capacity) {
        this.code = code;
        this.title = title;
        this.description = description;
        this.capacity = capacity;
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
