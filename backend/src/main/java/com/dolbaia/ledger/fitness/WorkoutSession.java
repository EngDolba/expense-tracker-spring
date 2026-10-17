package com.dolbaia.ledger.fitness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.dolbaia.ledger.common.BaseEntity;

/** A finished workout in history: only the work that was actually performed. */
@Entity
@Table(name = "workout_session")
public class WorkoutSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_day_id", nullable = false)
    private WorkoutPlanDay planDay;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(nullable = false)
    private String note = "";

    @OneToMany(mappedBy = "workout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<WorkoutExercise> exercises = new ArrayList<>();

    protected WorkoutSession() {}

    public WorkoutSession(WorkoutPlanDay planDay, Instant startedAt, Instant completedAt, String note) {
        this.planDay = planDay;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.note = note;
    }

    public WorkoutPlanDay getPlanDay() { return planDay; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getNote() { return note; }
    public List<WorkoutExercise> getExercises() { return exercises; }
}
