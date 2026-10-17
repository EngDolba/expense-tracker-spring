package com.dolbaia.ledger.fitness;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.dolbaia.ledger.common.BaseEntity;

/** One set of an active workout, saved as soon as it is logged. */
@Entity
@Table(name = "active_workout_set")
public class ActiveWorkoutSet extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "active_workout_exercise_id", nullable = false)
    private ActiveWorkoutExercise activeWorkoutExercise;

    @Column(name = "set_number", nullable = false)
    private int setNumber;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal weight;

    @Column(nullable = false)
    private int reps;

    @Column(nullable = false)
    private boolean completed;

    protected ActiveWorkoutSet() {}

    public ActiveWorkoutSet(ActiveWorkoutExercise activeWorkoutExercise, int setNumber, BigDecimal weight, int reps) {
        this.activeWorkoutExercise = activeWorkoutExercise;
        this.setNumber = setNumber;
        this.weight = weight;
        this.reps = reps;
    }

    public void log(BigDecimal weight, int reps, boolean completed) {
        this.weight = weight;
        this.reps = reps;
        this.completed = completed;
    }

    public ActiveWorkoutExercise getActiveWorkoutExercise() { return activeWorkoutExercise; }
    public int getSetNumber() { return setNumber; }
    public BigDecimal getWeight() { return weight; }
    public int getReps() { return reps; }
    public boolean isCompleted() { return completed; }
}
