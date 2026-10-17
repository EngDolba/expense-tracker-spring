package com.dolbaia.ledger.fitness;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dolbaia.ledger.common.ApiException;
import com.dolbaia.ledger.fitness.FitnessViews.ActiveWorkoutView;

/**
 * A workout from start to history. Every step is saved as it happens, so a
 * session survives a closed tab and resumes on any device.
 */
@Service
public class WorkoutService {

    public record Started(UUID id, boolean resumed) {}

    private final FitnessRepositories.Days days;
    private final FitnessRepositories.Variations variations;
    private final FitnessRepositories.History history;
    private final FitnessRepositories.Sessions sessions;
    private final FitnessRepositories.Actives actives;
    private final FitnessRepositories.ActiveExercises activeExercises;
    private final FitnessRepositories.ActiveSets activeSets;
    private final Clock clock;

    public WorkoutService(FitnessRepositories.Days days, FitnessRepositories.Variations variations,
                          FitnessRepositories.History history, FitnessRepositories.Sessions sessions,
                          FitnessRepositories.Actives actives, FitnessRepositories.ActiveExercises activeExercises,
                          FitnessRepositories.ActiveSets activeSets, Clock clock) {
        this.days = days;
        this.variations = variations;
        this.history = history;
        this.sessions = sessions;
        this.actives = actives;
        this.activeExercises = activeExercises;
        this.activeSets = activeSets;
        this.clock = clock;
    }

    record Choice(UUID id, String name, int order) implements FitnessRules.VariationChoice {}

    /**
     * Opens a workout for a session. A session already in progress is resumed
     * rather than restarted. Each slot starts with the variation after the one
     * used last time, at that variation's (or the exercise's) target.
     */
    @Transactional
    public Started start(UUID planDayId) {
        Optional<ActiveWorkout> existing = actives.findByPlanDay_Id(planDayId);
        if (existing.isPresent()) return new Started(existing.get().getId(), true);

        WorkoutPlanDay day = days.findById(planDayId).orElse(null);
        if (day == null || day.getPlan().isArchived() || day.getPlanExercises().isEmpty()) {
            throw ApiException.badRequest("session_unavailable", "This workout session is no longer available.");
        }

        List<PlanExercise> slots = day.getPlanExercises();
        Map<UUID, UUID> lastVariationBySlot = new HashMap<>();
        for (WorkoutExercise logged : history.findWithVariationForSlots(slots.stream().map(PlanExercise::getId).toList())) {
            lastVariationBySlot.putIfAbsent(logged.getPlanExercise().getId(), logged.getVariation().getId());
        }

        ActiveWorkout active = new ActiveWorkout(day, clock.instant());
        for (int order = 0; order < slots.size(); order++) {
            PlanExercise slot = slots.get(order);
            Exercise exercise = slot.getExercise();
            List<ExerciseVariation> available = exercise.activeVariations();
            ExerciseVariation variation = FitnessRules.pickNextVariation(
                            available.stream().map(v -> new Choice(v.getId(), v.getName(), v.getSortOrder())).toList(),
                            lastVariationBySlot.get(slot.getId()))
                    .flatMap(choice -> available.stream().filter(v -> v.getId().equals(choice.id())).findFirst())
                    .orElse(null);
            FitnessRules.Target target = FitnessRules.resolveExerciseTarget(exercise.targetSource(),
                    variation == null ? null : variation.targetSource(), slot.getSetCount());

            ActiveWorkoutExercise entry = new ActiveWorkoutExercise(active, slot, exercise, variation, order,
                    slot.getSupersetGroup());
            entry.target(variation, target);
            entry.seedSets(target.setCount());
            active.getExercises().add(entry);
        }
        return new Started(actives.save(active).getId(), false);
    }

    @Transactional(readOnly = true)
    public ActiveWorkoutView view(UUID id) {
        return ActiveWorkoutView.of(active(id));
    }

    @Transactional
    public void discard(UUID id) {
        actives.delete(active(id));
    }

    /** Persists one set as it is entered. */
    @Transactional
    public void saveSet(UUID setId, BigDecimal weight, int reps, boolean completed) {
        activeSets.findById(setId)
                .orElseThrow(() -> ApiException.notFound("set_not_found", "That set is no longer in this workout."))
                .log(weight, reps, completed);
    }

    /**
     * Switches which variation of the movement is performed. Unlogged sets
     * re-seed from the new target; the set count only changes while nothing
     * has been logged for the exercise.
     */
    @Transactional
    public ActiveWorkoutView selectVariation(UUID activeExerciseId, UUID variationId) {
        ActiveWorkoutExercise entry = activeExercise(activeExerciseId);
        if (entry.getWrappedUpAt() != null) {
            throw ApiException.conflict("exercise_finished", "This exercise is already finished for today.");
        }
        ExerciseVariation variation = variationId == null ? null
                : variations.findByIdAndExercise_IdAndArchivedFalse(variationId, entry.getExercise().getId())
                .orElseThrow(() -> ApiException.badRequest("variation_mismatch",
                        "Choose a variation that belongs to this exercise."));

        int planSetCount = entry.getPlanExercise() == null ? entry.getSets().size()
                : entry.getPlanExercise().getSetCount();
        FitnessRules.Target target = FitnessRules.resolveExerciseTarget(entry.getExercise().targetSource(),
                variation == null ? null : variation.targetSource(), planSetCount);
        entry.target(variation, target);

        if (!entry.wasPerformed()) {
            entry.seedSets(target.setCount());
        } else {
            for (ActiveWorkoutSet set : entry.getSets()) {
                if (!set.isCompleted()) set.log(target.weight(), target.reps(), false);
            }
        }
        activeExercises.flush();
        return ActiveWorkoutView.of(entry.getActiveWorkout());
    }

    /**
     * The end-of-exercise step: how hard it felt and the targets the next
     * workout opens with. Unlogged sets may remain: closing early is how a
     * short session ends, and they are dropped when the workout is filed.
     */
    @Transactional
    public void wrapUp(UUID activeExerciseId, int perceivedEffort, BigDecimal nextWeight, int nextReps, int nextSets) {
        activeExercise(activeExerciseId).wrapUp(perceivedEffort, nextWeight, nextReps, nextSets, clock.instant());
    }

    @Transactional
    public void saveNote(UUID id, String note) {
        active(id).setNote(note);
    }

    /**
     * Files the workout into history and applies each performed exercise's
     * next target to the variation performed, or the exercise. Unlogged sets
     * and untouched exercises are dropped.
     */
    @Transactional
    public UUID complete(UUID id) {
        ActiveWorkout active = active(id);
        List<ActiveWorkoutExercise> performed = active.getExercises().stream()
                .filter(ActiveWorkoutExercise::wasPerformed)
                .toList();
        if (performed.isEmpty()) {
            throw ApiException.badRequest("nothing_logged", "Log at least one set before finishing this workout.");
        }
        for (ActiveWorkoutExercise entry : performed) {
            if (entry.getWrappedUpAt() == null || entry.getPerceivedEffort() == null || entry.getNextWeight() == null
                    || entry.getNextReps() == null || entry.getNextSets() == null) {
                throw ApiException.badRequest("exercise_not_rated",
                        "Rate each exercise you logged and set its next target before finishing.");
            }
        }

        // Dropping untouched exercises can strand one member of a superset; history
        // stores the pairing as performed, renumbered from 1.
        List<Integer> groups = FitnessRules.normalizeSupersetGroups(FitnessRules.supersetLinks(performed));
        WorkoutSession session = new WorkoutSession(active.getPlanDay(), active.getStartedAt(), clock.instant(),
                active.getNote());
        for (int index = 0; index < performed.size(); index++) {
            ActiveWorkoutExercise entry = performed.get(index);
            session.getExercises().add(new WorkoutExercise(session, entry, groups.get(index)));
            HasTarget target = entry.getVariation() != null ? entry.getVariation() : entry.getExercise();
            target.applyNextTarget(entry.getNextWeight(), entry.getNextReps(), entry.getNextSets());
        }
        UUID sessionId = sessions.save(session).getId();
        actives.delete(active);
        return sessionId;
    }

    private ActiveWorkout active(UUID id) {
        return actives.findById(id)
                .orElseThrow(() -> ApiException.notFound("workout_not_found", "This workout is no longer available."));
    }

    private ActiveWorkoutExercise activeExercise(UUID id) {
        return activeExercises.findById(id)
                .orElseThrow(() -> ApiException.notFound("exercise_not_in_workout",
                        "That exercise is no longer in this workout."));
    }
}
