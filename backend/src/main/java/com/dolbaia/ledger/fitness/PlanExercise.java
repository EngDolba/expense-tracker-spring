package com.dolbaia.ledger.fitness;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.dolbaia.ledger.common.BaseEntity;

/**
 * One slot of a session. Consecutive slots sharing a superset group are
 * performed together: their sets alternate and both are rated afterwards.
 */
@Entity
@Table(name = "plan_exercise")
public class PlanExercise extends BaseEntity implements FitnessRules.SupersetSlot {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_day_id", nullable = false)
    private WorkoutPlanDay planDay;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "set_count", nullable = false)
    private int setCount;

    @Column(name = "superset_group")
    private Integer supersetGroup;

    @Column(nullable = false)
    private String note = "";

    protected PlanExercise() {}

    public PlanExercise(WorkoutPlanDay planDay, Exercise exercise, int sortOrder, int setCount, String note) {
        this.planDay = planDay;
        this.exercise = exercise;
        this.sortOrder = sortOrder;
        this.setCount = setCount;
        this.note = note;
    }

    public WorkoutPlanDay getPlanDay() { return planDay; }
    public Exercise getExercise() { return exercise; }
    public int getSortOrder() { return sortOrder; }
    public int getSetCount() { return setCount; }
    public String getNote() { return note; }
    public Integer getSupersetGroup() { return supersetGroup; }
    public void setSupersetGroup(Integer supersetGroup) { this.supersetGroup = supersetGroup; }

    @Override
    public Integer supersetGroup() {
        return supersetGroup;
    }
}
