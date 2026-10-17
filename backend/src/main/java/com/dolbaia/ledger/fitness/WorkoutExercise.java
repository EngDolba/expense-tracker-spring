package com.dolbaia.ledger.fitness;

import java.math.BigDecimal;
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

/** One exercise of a finished workout, with its rating and the next target set at the time. */
@Entity
@Table(name = "workout_exercise")
public class WorkoutExercise extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_id", nullable = false)
    private WorkoutSession workout;

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

    @Column(name = "perceived_effort", nullable = false)
    private int perceivedEffort = 2;

    @Column(name = "next_weight", precision = 8, scale = 2)
    private BigDecimal nextWeight;

    @Column(name = "next_reps")
    private Integer nextReps;

    @Column(name = "next_sets")
    private Integer nextSets;

    @OneToMany(mappedBy = "workoutExercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("setNumber")
    private List<WorkoutSet> sets = new ArrayList<>();

    protected WorkoutExercise() {}

    /** Files one performed exercise of an active workout into history. */
    public WorkoutExercise(WorkoutSession workout, ActiveWorkoutExercise performed, Integer supersetGroup) {
        this.workout = workout;
        this.planExercise = performed.getPlanExercise();
        this.exercise = performed.getExercise();
        this.variation = performed.getVariation();
        this.sortOrder = performed.getSortOrder();
        this.supersetGroup = supersetGroup;
        this.targetWeight = performed.getTargetWeight();
        this.targetReps = performed.getTargetReps();
        this.perceivedEffort = performed.getPerceivedEffort() == null ? 2 : performed.getPerceivedEffort();
        this.nextWeight = performed.getNextWeight();
        this.nextReps = performed.getNextReps();
        this.nextSets = performed.getNextSets();
        for (ActiveWorkoutSet set : performed.getSets()) {
            if (set.isCompleted()) sets.add(new WorkoutSet(this, set.getSetNumber(), set.getWeight(), set.getReps()));
        }
    }

    public PlanExercise getPlanExercise() { return planExercise; }
    public Exercise getExercise() { return exercise; }
    public ExerciseVariation getVariation() { return variation; }
    public int getSortOrder() { return sortOrder; }
    public Integer getSupersetGroup() { return supersetGroup; }
    public BigDecimal getTargetWeight() { return targetWeight; }
    public int getTargetReps() { return targetReps; }
    public int getPerceivedEffort() { return perceivedEffort; }
    public BigDecimal getNextWeight() { return nextWeight; }
    public Integer getNextReps() { return nextReps; }
    public Integer getNextSets() { return nextSets; }
    public List<WorkoutSet> getSets() { return sets; }
}
