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

/**
 * A workout that has been started but not finished. It lives in the database
 * so a session survives a closed window and can be resumed from any device.
 * At most one per session (plan day).
 */
@Entity
@Table(name = "active_workout")
public class ActiveWorkout extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_day_id", nullable = false, unique = true)
    private WorkoutPlanDay planDay;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private String note = "";

    @OneToMany(mappedBy = "activeWorkout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<ActiveWorkoutExercise> exercises = new ArrayList<>();

    protected ActiveWorkout() {}

    public ActiveWorkout(WorkoutPlanDay planDay, Instant startedAt) {
        this.planDay = planDay;
        this.startedAt = startedAt;
    }

    public WorkoutPlanDay getPlanDay() { return planDay; }
    public Instant getStartedAt() { return startedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public List<ActiveWorkoutExercise> getExercises() { return exercises; }
}
