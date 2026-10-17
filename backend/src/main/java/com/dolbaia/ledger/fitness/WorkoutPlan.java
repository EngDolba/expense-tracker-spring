package com.dolbaia.ledger.fitness;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.dolbaia.ledger.common.BaseEntity;

/** A training plan: an ordered rotation of sessions (days). */
@Entity
@Table(name = "workout_plan")
public class WorkoutPlan extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String description = "";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean archived;

    @OneToMany(mappedBy = "plan")
    @OrderBy("sortOrder")
    private List<WorkoutPlanDay> days = new ArrayList<>();

    protected WorkoutPlan() {}

    public WorkoutPlan(String name, String description, int sortOrder) {
        this.name = name;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public List<WorkoutPlanDay> getDays() { return days; }
}
