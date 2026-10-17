package com.dolbaia.ledger.fitness;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.dolbaia.ledger.common.BaseEntity;

@Entity
@Table(name = "workout_set")
public class WorkoutSet extends BaseEntity implements FitnessRules.LoggedSet {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_exercise_id", nullable = false)
    private WorkoutExercise workoutExercise;

    @Column(name = "set_number", nullable = false)
    private int setNumber;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal weight;

    @Column(nullable = false)
    private int reps;

    protected WorkoutSet() {}

    public WorkoutSet(WorkoutExercise workoutExercise, int setNumber, BigDecimal weight, int reps) {
        this.workoutExercise = workoutExercise;
        this.setNumber = setNumber;
        this.weight = weight;
        this.reps = reps;
    }

    public int getSetNumber() { return setNumber; }
    public BigDecimal getWeight() { return weight; }
    public int getReps() { return reps; }

    @Override public BigDecimal weight() { return weight; }
    @Override public int reps() { return reps; }
}
