package com.dolbaia.ledger.fitness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The JSON the fitness pages and the workout runner read; field names match the Next.js app. */
public final class FitnessViews {

    private FitnessViews() {}

    public record VariationView(UUID id, String name, String note, int order, BigDecimal recommendedWeight,
                                int recommendedReps, Integer recommendedSets) {

        static VariationView of(ExerciseVariation variation) {
            return new VariationView(variation.getId(), variation.getName(), variation.getNote(),
                    variation.getSortOrder(), variation.getRecommendedWeight(), variation.getRecommendedReps(),
                    variation.getRecommendedSets());
        }

        static List<VariationView> of(Exercise exercise) {
            return exercise.activeVariations().stream().map(VariationView::of).toList();
        }
    }

    public record ExerciseView(UUID id, String name, String muscleGroup, String note, BigDecimal recommendedWeight,
                               int recommendedReps, Integer recommendedSets, List<VariationView> variations) {

        static ExerciseView of(Exercise exercise) {
            return new ExerciseView(exercise.getId(), exercise.getName(), exercise.getMuscleGroup(),
                    exercise.getNote(), exercise.getRecommendedWeight(), exercise.getRecommendedReps(),
                    exercise.getRecommendedSets(), VariationView.of(exercise));
        }
    }

    public record SlotExercise(UUID id, String name, String muscleGroup, BigDecimal recommendedWeight,
                               int recommendedReps, Integer recommendedSets, List<VariationView> variations) {}

    public record SlotView(UUID id, int setCount, String note, Integer supersetGroup, FitnessRules.Target target,
                           SlotExercise exercise) {

        static SlotView of(PlanExercise slot) {
            Exercise exercise = slot.getExercise();
            return new SlotView(slot.getId(), slot.getSetCount(), slot.getNote(), slot.getSupersetGroup(),
                    FitnessRules.resolveExerciseTarget(exercise.targetSource(), null, slot.getSetCount()),
                    new SlotExercise(exercise.getId(), exercise.getName(), exercise.getMuscleGroup(),
                            exercise.getRecommendedWeight(), exercise.getRecommendedReps(),
                            exercise.getRecommendedSets(), VariationView.of(exercise)));
        }
    }

    public record DayView(UUID id, String name, int order, List<SlotView> planExercises) {}

    public record PlanView(UUID id, String name, String description, int order, List<DayView> days) {

        static PlanView of(WorkoutPlan plan) {
            return new PlanView(plan.getId(), plan.getName(), plan.getDescription(), plan.getSortOrder(),
                    plan.getDays().stream()
                            .map(day -> new DayView(day.getId(), day.getName(), day.getSortOrder(),
                                    day.getPlanExercises().stream().map(SlotView::of).toList()))
                            .toList());
        }
    }

    public record ActiveSummary(UUID id, UUID planDayId, String planDayName, String planName, Instant startedAt,
                                int totalSets, int completedSets) {

        static ActiveSummary of(ActiveWorkout active) {
            int total = 0;
            int completed = 0;
            for (ActiveWorkoutExercise exercise : active.getExercises()) {
                total += exercise.getSets().size();
                completed += (int) exercise.getSets().stream().filter(ActiveWorkoutSet::isCompleted).count();
            }
            WorkoutPlanDay day = active.getPlanDay();
            return new ActiveSummary(active.getId(), day.getId(), day.getName(), day.getPlan().getName(),
                    active.getStartedAt(), total, completed);
        }
    }

    public record PlanDayRef(UUID id, String name, String planName) {}

    public record ExerciseRef(UUID id, String name) {}

    public record HistorySet(UUID id, int setNumber, BigDecimal weight, int reps) {}

    public record HistoryExercise(UUID id, int order, Integer supersetGroup, BigDecimal targetWeight, int targetReps,
                                  int perceivedEffort, ExerciseRef exercise, String variationName,
                                  List<HistorySet> sets) {}

    public record WorkoutView(UUID id, String note, Instant startedAt, Instant completedAt, PlanDayRef planDay,
                              BigDecimal volume, List<HistoryExercise> exercises) {

        static WorkoutView of(WorkoutSession session) {
            BigDecimal volume = BigDecimal.ZERO;
            List<HistoryExercise> exercises = new java.util.ArrayList<>();
            for (WorkoutExercise entry : session.getExercises()) {
                volume = volume.add(FitnessRules.workoutVolume(entry.getSets()));
                exercises.add(new HistoryExercise(entry.getId(), entry.getSortOrder(), entry.getSupersetGroup(),
                        entry.getTargetWeight(), entry.getTargetReps(), entry.getPerceivedEffort(),
                        new ExerciseRef(entry.getExercise().getId(), entry.getExercise().getName()),
                        entry.getVariation() == null ? null : entry.getVariation().getName(),
                        entry.getSets().stream()
                                .map(set -> new HistorySet(set.getId(), set.getSetNumber(), set.getWeight(), set.getReps()))
                                .toList()));
            }
            WorkoutPlanDay day = session.getPlanDay();
            return new WorkoutView(session.getId(), session.getNote(), session.getStartedAt(),
                    session.getCompletedAt(), new PlanDayRef(day.getId(), day.getName(), day.getPlan().getName()),
                    volume, exercises);
        }
    }

    public record FitnessPage(List<ExerciseView> exercises, List<PlanView> plans, UUID nextPlanDayId,
                              List<ActiveSummary> activeWorkouts, List<WorkoutView> workouts) {}

    public record ActiveSet(UUID id, int setNumber, BigDecimal weight, int reps, boolean completed) {}

    public record ActiveExercise(UUID id, int order, Integer supersetGroup, UUID exerciseId, String name,
                                 String muscleGroup, String note, UUID variationId, List<VariationView> variations,
                                 BigDecimal targetWeight, int targetReps, Integer perceivedEffort,
                                 BigDecimal nextWeight, Integer nextReps, Integer nextSets, boolean wrappedUp,
                                 List<ActiveSet> sets) {}

    /** Everything the runner needs to resume a started workout on any device. */
    public record ActiveWorkoutView(UUID id, UUID planDayId, String planDayName, String planName, Instant startedAt,
                                    String note, List<ActiveExercise> exercises) {

        static ActiveWorkoutView of(ActiveWorkout active) {
            WorkoutPlanDay day = active.getPlanDay();
            return new ActiveWorkoutView(active.getId(), day.getId(), day.getName(), day.getPlan().getName(),
                    active.getStartedAt(), active.getNote(), active.getExercises().stream()
                    .map(entry -> new ActiveExercise(entry.getId(), entry.getSortOrder(), entry.getSupersetGroup(),
                            entry.getExercise().getId(), entry.getExercise().getName(),
                            entry.getExercise().getMuscleGroup(), entry.getExercise().getNote(),
                            entry.getVariation() == null ? null : entry.getVariation().getId(),
                            VariationView.of(entry.getExercise()), entry.getTargetWeight(), entry.getTargetReps(),
                            entry.getPerceivedEffort(), entry.getNextWeight(), entry.getNextReps(),
                            entry.getNextSets(), entry.getWrappedUpAt() != null,
                            entry.getSets().stream()
                                    .map(set -> new ActiveSet(set.getId(), set.getSetNumber(), set.getWeight(),
                                            set.getReps(), set.isCompleted()))
                                    .toList()))
                    .toList());
        }
    }
}
