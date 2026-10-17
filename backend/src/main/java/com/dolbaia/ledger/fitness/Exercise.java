package com.dolbaia.ledger.fitness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.dolbaia.ledger.common.BaseEntity;

/** A movement in the library, with the target its next workout starts from. */
@Entity
@Table(name = "exercise")
public class Exercise extends BaseEntity implements HasTarget {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "muscle_group", nullable = false)
    private String muscleGroup = "";

    @Column(nullable = false)
    private String note = "";

    @Column(name = "recommended_weight", nullable = false, precision = 8, scale = 2)
    private BigDecimal recommendedWeight = BigDecimal.ZERO;

    @Column(name = "recommended_reps", nullable = false)
    private int recommendedReps = 10;

    @Column(name = "recommended_sets")
    private Integer recommendedSets;

    @Column(nullable = false)
    private boolean archived;

    @OneToMany(mappedBy = "exercise")
    @OrderBy("sortOrder, name")
    private List<ExerciseVariation> variations = new ArrayList<>();

    protected Exercise() {}

    public Exercise(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMuscleGroup() { return muscleGroup; }
    public void setMuscleGroup(String muscleGroup) { this.muscleGroup = muscleGroup; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    @Override public BigDecimal getRecommendedWeight() { return recommendedWeight; }
    @Override public void setRecommendedWeight(BigDecimal recommendedWeight) { this.recommendedWeight = recommendedWeight; }
    @Override public int getRecommendedReps() { return recommendedReps; }
    @Override public void setRecommendedReps(int recommendedReps) { this.recommendedReps = recommendedReps; }
    @Override public Integer getRecommendedSets() { return recommendedSets; }
    @Override public void setRecommendedSets(Integer recommendedSets) { this.recommendedSets = recommendedSets; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    /** The variations that are not archived, in their declared order. */
    public List<ExerciseVariation> activeVariations() {
        return variations.stream().filter(variation -> !variation.isArchived()).toList();
    }
}
