package com.dolbaia.ledger.fitness;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.dolbaia.ledger.common.BaseEntity;

/**
 * A variation of the same movement (e.g. incline vs flat chest press). It shares
 * the plan slot but keeps its own next-workout target and logged history.
 */
@Entity
@Table(name = "exercise_variation")
public class ExerciseVariation extends BaseEntity implements HasTarget {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String note = "";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "recommended_weight", nullable = false, precision = 8, scale = 2)
    private BigDecimal recommendedWeight = BigDecimal.ZERO;

    @Column(name = "recommended_reps", nullable = false)
    private int recommendedReps = 10;

    @Column(name = "recommended_sets")
    private Integer recommendedSets;

    protected ExerciseVariation() {}

    public ExerciseVariation(Exercise exercise, String name, String note, int sortOrder) {
        this.exercise = exercise;
        this.name = name;
        this.note = note;
        this.sortOrder = sortOrder;
    }

    public Exercise getExercise() { return exercise; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public int getSortOrder() { return sortOrder; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    @Override public BigDecimal getRecommendedWeight() { return recommendedWeight; }
    @Override public void setRecommendedWeight(BigDecimal recommendedWeight) { this.recommendedWeight = recommendedWeight; }
    @Override public int getRecommendedReps() { return recommendedReps; }
    @Override public void setRecommendedReps(int recommendedReps) { this.recommendedReps = recommendedReps; }
    @Override public Integer getRecommendedSets() { return recommendedSets; }
    @Override public void setRecommendedSets(Integer recommendedSets) { this.recommendedSets = recommendedSets; }
}
