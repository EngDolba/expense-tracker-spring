package com.dolbaia.ledger.fitness;

import java.math.BigDecimal;

/** An exercise or a variation: anything that carries the target the next workout starts from. */
public interface HasTarget {

    BigDecimal getRecommendedWeight();
    void setRecommendedWeight(BigDecimal weight);
    int getRecommendedReps();
    void setRecommendedReps(int reps);
    Integer getRecommendedSets();
    void setRecommendedSets(Integer sets);

    default FitnessRules.TargetSource targetSource() {
        return new FitnessRules.TargetSource(getRecommendedWeight(), getRecommendedReps(), getRecommendedSets());
    }

    default void applyNextTarget(BigDecimal weight, int reps, Integer sets) {
        setRecommendedWeight(weight);
        setRecommendedReps(reps);
        setRecommendedSets(sets);
    }
}
