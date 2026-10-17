package com.dolbaia.ledger.fitness;

import java.math.BigDecimal;
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

/** One exercise of an active workout: its sets, and once wrapped up, its rating and next target. */
@Entity
@Table(name = "active_workout_exercise")
public class ActiveWorkoutExercise extends BaseEntity implements FitnessRules.SupersetSlot {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "active_workout_id", nullable = false)
    private ActiveWorkout activeWorkout;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_exercise_id")
    private PlanExercise planExercise;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variation_id")
    private ExerciseVariation variation;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "superset_group")
    private Integer supersetGroup;

    @Column(name = "target_weight", nullable = false, precision = 8, scale = 2)
    private BigDecimal targetWeight;

    @Column(name = "target_reps", nullable = false)
    private int targetReps;

    @Column(name = "perceived_effort")
    private Integer perceivedEffort;

    @Column(name = "next_weight", precision = 8, scale = 2)
    private BigDecimal nextWeight;

    @Column(name = "next_reps")
    private Integer nextReps;

    @Column(name = "next_sets")
    private Integer nextSets;

    @Column(name = "wrapped_up_at")
    private Instant wrappedUpAt;

    @OneToMany(mappedBy = "activeWorkoutExercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("setNumber")
    private List<ActiveWorkoutSet> sets = new ArrayList<>();

    protected ActiveWorkoutExercise() {}

    public ActiveWorkoutExercise(ActiveWorkout activeWorkout, PlanExercise planExercise, Exercise exercise,
                                 ExerciseVariation variation, int sortOrder, Integer supersetGroup) {
        this.activeWorkout = activeWorkout;
        this.planExercise = planExercise;
        this.exercise = exercise;
        this.variation = variation;
        this.sortOrder = sortOrder;
        this.supersetGroup = supersetGroup;
    }

    /** Points the exercise at a target; the sets are (re)seeded separately. */
    public void target(ExerciseVariation variation, FitnessRules.Target target) {
        this.variation = variation;
        this.targetWeight = target.weight();
        this.targetReps = target.reps();
    }

    /** Replaces every set with {@code count} fresh ones at the target. */
    public void seedSets(int count) {
        sets.clear();
        for (int number = 1; number <= count; number++) {
            sets.add(new ActiveWorkoutSet(this, number, targetWeight, targetReps));
        }
    }

    public void wrapUp(int perceivedEffort, BigDecimal nextWeight, int nextReps, int nextSets, Instant at) {
        this.perceivedEffort = perceivedEffort;
        this.nextWeight = nextWeight;
        this.nextReps = nextReps;
        this.nextSets = nextSets;
        this.wrappedUpAt = at;
    }

    public boolean wasPerformed() {
        return sets.stream().anyMatch(ActiveWorkoutSet::isCompleted);
    }

    public ActiveWorkout getActiveWorkout() { return activeWorkout; }
    public PlanExercise getPlanExercise() { return planExercise; }
    public Exercise getExercise() { return exercise; }
    public ExerciseVariation getVariation() { return variation; }
    public int getSortOrder() { return sortOrder; }
    public Integer getSupersetGroup() { return supersetGroup; }
    public BigDecimal getTargetWeight() { return targetWeight; }
    public int getTargetReps() { return targetReps; }
    public Integer getPerceivedEffort() { return perceivedEffort; }
    public BigDecimal getNextWeight() { return nextWeight; }
    public Integer getNextReps() { return nextReps; }
    public Integer getNextSets() { return nextSets; }
    public Instant getWrappedUpAt() { return wrappedUpAt; }
    public List<ActiveWorkoutSet> getSets() { return sets; }

    @Override
    public Integer supersetGroup() {
        return supersetGroup;
    }
}
