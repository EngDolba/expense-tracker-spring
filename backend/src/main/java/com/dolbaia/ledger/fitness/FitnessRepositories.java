package com.dolbaia.ledger.fitness;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repositories for the training workspace. Nested so the module stays in a
 * few files; {@code PersistenceConfig} enables scanning of nested repositories.
 */
public final class FitnessRepositories {

    private FitnessRepositories() {}

    public interface Exercises extends JpaRepository<Exercise, UUID> {
        @Query("select e from Exercise e where e.archived = false order by e.name")
        List<Exercise> findActive();

        Optional<Exercise> findByIdAndArchivedFalse(UUID id);
    }

    public interface Variations extends JpaRepository<ExerciseVariation, UUID> {
        long countByExercise_Id(UUID exerciseId);

        Optional<ExerciseVariation> findByIdAndExercise_IdAndArchivedFalse(UUID id, UUID exerciseId);
    }

    public interface Plans extends JpaRepository<WorkoutPlan, UUID> {
        @Query("select p from WorkoutPlan p where p.archived = false order by p.sortOrder, p.name")
        List<WorkoutPlan> findActive();
    }

    public interface Days extends JpaRepository<WorkoutPlanDay, UUID> {
        long countByPlan_Id(UUID planId);
    }

    public interface Slots extends JpaRepository<PlanExercise, UUID> {
        @Query("select s from PlanExercise s where s.planDay.id = :planDayId order by s.sortOrder")
        List<PlanExercise> findForDay(UUID planDayId);

        @Query("select coalesce(max(s.sortOrder), -1) from PlanExercise s where s.planDay.id = :planDayId")
        int maxOrder(UUID planDayId);
    }

    public interface Sessions extends JpaRepository<WorkoutSession, UUID> {
        @EntityGraph(attributePaths = {"planDay", "planDay.plan"})
        @Query("select s from WorkoutSession s order by s.completedAt desc")
        List<WorkoutSession> findLatest(Limit limit);
    }

    public interface History extends JpaRepository<WorkoutExercise, UUID> {
        /** Newest first: the first row per slot is the variation it was last performed with. */
        @Query("select w from WorkoutExercise w where w.planExercise.id in :slotIds and w.variation is not null"
                + " order by w.createdAt desc")
        List<WorkoutExercise> findWithVariationForSlots(Collection<UUID> slotIds);
    }

    public interface Actives extends JpaRepository<ActiveWorkout, UUID> {
        Optional<ActiveWorkout> findByPlanDay_Id(UUID planDayId);

        @EntityGraph(attributePaths = {"planDay", "planDay.plan"})
        @Query("select a from ActiveWorkout a order by a.startedAt desc")
        List<ActiveWorkout> findAllLatestFirst();
    }

    public interface ActiveExercises extends JpaRepository<ActiveWorkoutExercise, UUID> {}

    public interface ActiveSets extends JpaRepository<ActiveWorkoutSet, UUID> {}
}
