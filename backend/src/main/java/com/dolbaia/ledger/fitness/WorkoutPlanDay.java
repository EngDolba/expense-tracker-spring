package com.dolbaia.ledger.fitness;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.dolbaia.ledger.common.BaseEntity;

/** One session of a plan, such as "Push" or "Gym Routine A". */
@Entity
@Table(name = "workout_plan_day")
public class WorkoutPlanDay extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private WorkoutPlan plan;

    @Column(nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "planDay")
    @OrderBy("sortOrder")
    private List<PlanExercise> planExercises = new ArrayList<>();

    protected WorkoutPlanDay() {}

    public WorkoutPlanDay(WorkoutPlan plan, String name, int sortOrder) {
        this.plan = plan;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public WorkoutPlan getPlan() { return plan; }
    public String getName() { return name; }
    public int getSortOrder() { return sortOrder; }
    public List<PlanExercise> getPlanExercises() { return planExercises; }
}
