package com.dolbaia.ledger.fitness;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dolbaia.ledger.common.ApiException;
import com.dolbaia.ledger.fitness.FitnessViews.ActiveSummary;
import com.dolbaia.ledger.fitness.FitnessViews.ExerciseView;
import com.dolbaia.ledger.fitness.FitnessViews.FitnessPage;
import com.dolbaia.ledger.fitness.FitnessViews.PlanView;
import com.dolbaia.ledger.fitness.FitnessViews.WorkoutView;

/** The training workspace: exercise library, plans and their sessions, history. Not part of any ledger. */
@Service
public class FitnessService {

    static final int HISTORY_SIZE = 50;

    public record ExerciseForm(String name, String muscleGroup, String note, BigDecimal recommendedWeight,
                               int recommendedReps, Integer recommendedSets) {}

    public record VariationForm(String name, String note, BigDecimal recommendedWeight, int recommendedReps,
                                Integer recommendedSets) {}

    private final FitnessRepositories.Exercises exercises;
    private final FitnessRepositories.Variations variations;
    private final FitnessRepositories.Plans plans;
    private final FitnessRepositories.Days days;
    private final FitnessRepositories.Slots slots;
    private final FitnessRepositories.Sessions sessions;
    private final FitnessRepositories.Actives actives;

    public FitnessService(FitnessRepositories.Exercises exercises, FitnessRepositories.Variations variations,
                          FitnessRepositories.Plans plans, FitnessRepositories.Days days,
                          FitnessRepositories.Slots slots, FitnessRepositories.Sessions sessions,
                          FitnessRepositories.Actives actives) {
        this.exercises = exercises;
        this.variations = variations;
        this.plans = plans;
        this.days = days;
        this.slots = slots;
        this.sessions = sessions;
        this.actives = actives;
    }

    record QueueDay(UUID id, int planOrder, int dayOrder) implements FitnessRules.PlanDayQueueItem {}

    @Transactional(readOnly = true)
    public FitnessPage page() {
        List<WorkoutPlan> activePlans = plans.findActive();
        List<WorkoutSession> history = sessions.findLatest(Limit.of(HISTORY_SIZE));

        List<QueueDay> queue = activePlans.stream()
                .flatMap(plan -> plan.getDays().stream()
                        .map(day -> new QueueDay(day.getId(), plan.getSortOrder(), day.getSortOrder())))
                .toList();
        UUID next = FitnessRules.nextPlanDay(queue, history.stream()
                        .map(session -> new FitnessRules.CompletedPlanDay(session.getPlanDay().getId(),
                                session.getCompletedAt()))
                        .toList())
                .map(QueueDay::id)
                .orElse(null);

        return new FitnessPage(
                exercises.findActive().stream().map(ExerciseView::of).toList(),
                activePlans.stream().map(PlanView::of).toList(),
                next,
                actives.findAllLatestFirst().stream().map(ActiveSummary::of).toList(),
                history.stream().map(WorkoutView::of).toList());
    }

    // ---- Library ------------------------------------------------------------------------

    @Transactional
    public UUID createExercise(ExerciseForm form) {
        Exercise exercise = new Exercise(form.name());
        apply(exercise, form);
        return exercises.save(exercise).getId();
    }

    @Transactional
    public void updateExercise(UUID id, ExerciseForm form) {
        apply(exercise(id), form);
    }

    private static void apply(Exercise exercise, ExerciseForm form) {
        exercise.setName(form.name());
        exercise.setMuscleGroup(form.muscleGroup());
        exercise.setNote(form.note());
        exercise.applyNextTarget(form.recommendedWeight(), form.recommendedReps(), form.recommendedSets());
    }

    @Transactional
    public void archiveExercise(UUID id) {
        exercise(id).setArchived(true);
    }

    /** A new variation starts from the parent's current target, so its first workout is not from zero. */
    @Transactional
    public UUID createVariation(UUID exerciseId, String name, String note) {
        Exercise exercise = exercise(exerciseId);
        ExerciseVariation variation = new ExerciseVariation(exercise, name, note,
                (int) variations.countByExercise_Id(exerciseId));
        variation.applyNextTarget(exercise.getRecommendedWeight(), exercise.getRecommendedReps(),
                exercise.getRecommendedSets());
        return variations.save(variation).getId();
    }

    @Transactional
    public void updateVariation(UUID id, VariationForm form) {
        ExerciseVariation variation = variation(id);
        variation.setName(form.name());
        variation.setNote(form.note());
        variation.applyNextTarget(form.recommendedWeight(), form.recommendedReps(), form.recommendedSets());
    }

    @Transactional
    public void archiveVariation(UUID id) {
        variation(id).setArchived(true);
    }

    // ---- Plans ---------------------------------------------------------------------------

    @Transactional
    public UUID createPlan(String name, String description) {
        return plans.save(new WorkoutPlan(name, description, (int) plans.count())).getId();
    }

    @Transactional
    public void archivePlan(UUID id) {
        plans.findById(id).orElseThrow(() -> notFound("plan_not_found")).setArchived(true);
    }

    @Transactional
    public UUID createDay(UUID planId, String name) {
        WorkoutPlan plan = plans.findById(planId).orElseThrow(() -> notFound("plan_not_found"));
        return days.save(new WorkoutPlanDay(plan, name, (int) days.countByPlan_Id(planId))).getId();
    }

    /**
     * Adds an exercise as the last slot of a session, optionally as a superset
     * with the slot above it.
     */
    @Transactional
    public UUID addSlot(UUID planDayId, UUID exerciseId, int setCount, String note, boolean supersetWithPrevious) {
        WorkoutPlanDay day = days.findById(planDayId)
                .orElseThrow(() -> ApiException.notFound("plan_day_not_found", "Workout session was not found."));
        Exercise exercise = exercises.findByIdAndArchivedFalse(exerciseId)
                .orElseThrow(() -> ApiException.badRequest("exercise_not_available",
                        "Choose an active exercise from the library."));
        // Removing a slot leaves a gap, so "next after the highest" keeps a new slot last.
        int order = slots.maxOrder(planDayId) + 1;
        PlanExercise slot = slots.saveAndFlush(new PlanExercise(day, exercise, order, setCount, note));
        if (supersetWithPrevious && order > 0) {
            relink(planDayId, ordered -> FitnessRules.toggleSupersetLink(ordered, indexOf(ordered, slot.getId())));
        }
        return slot.getId();
    }

    /** Pairs a slot with the one above it as a superset, or splits them again. */
    @Transactional
    public void toggleSuperset(UUID slotId) {
        PlanExercise slot = slot(slotId);
        relink(slot.getPlanDay().getId(), ordered -> {
            int index = indexOf(ordered, slotId);
            if (index <= 0) {
                throw ApiException.badRequest("nothing_to_pair",
                        "The first exercise of a session has nothing above it to pair with.");
            }
            return FitnessRules.toggleSupersetLink(ordered, index);
        });
    }

    /** Removes a slot; a superset left with one member stops being one, the rest are renumbered. */
    @Transactional
    public void removeSlot(UUID slotId) {
        PlanExercise slot = slot(slotId);
        UUID planDayId = slot.getPlanDay().getId();
        slots.delete(slot);
        slots.flush();
        relink(planDayId, ordered -> FitnessRules.normalizeSupersetGroups(FitnessRules.supersetLinks(ordered)));
    }

    /** Re-derives the superset group of every slot of a session from {@code edit}. */
    private void relink(UUID planDayId, Function<List<PlanExercise>, List<Integer>> edit) {
        List<PlanExercise> ordered = slots.findForDay(planDayId);
        List<Integer> groups = edit.apply(ordered);
        for (int index = 0; index < ordered.size(); index++) ordered.get(index).setSupersetGroup(groups.get(index));
    }

    private static int indexOf(List<PlanExercise> ordered, UUID id) {
        for (int index = 0; index < ordered.size(); index++) if (ordered.get(index).getId().equals(id)) return index;
        return -1;
    }

    private Exercise exercise(UUID id) {
        return exercises.findById(id).orElseThrow(() -> notFound("exercise_not_found"));
    }

    private ExerciseVariation variation(UUID id) {
        return variations.findById(id).orElseThrow(() -> notFound("variation_not_found"));
    }

    private PlanExercise slot(UUID id) {
        return slots.findById(id).orElseThrow(() -> ApiException.notFound("slot_not_found",
                "That exercise is no longer in this session."));
    }

    private static ApiException notFound(String code) {
        return ApiException.notFound(code, "That record is no longer available.");
    }
}
