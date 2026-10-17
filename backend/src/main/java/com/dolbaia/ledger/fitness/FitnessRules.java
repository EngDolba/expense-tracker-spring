package com.dolbaia.ledger.fitness;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.dolbaia.ledger.category.CategoryTree;

/**
 * The training rules, as pure functions: which session comes next, which
 * variation to start with, the starting target, superset grouping, and the
 * order the runner walks a workout in. Port of the Next.js app's
 * src/lib/fitness.ts; the frontend keeps its own copy for the runner.
 */
public final class FitnessRules {

    private FitnessRules() {}

    private static final Collator NAMES = Collator.getInstance(CategoryTree.GEORGIAN);

    // ---- Session queue -------------------------------------------------------------

    public interface PlanDayQueueItem {
        Object id();
        int planOrder();
        int dayOrder();
    }

    public record CompletedPlanDay(Object planDayId, Instant completedAt) {}

    /**
     * The session after the most recently completed one, wrapping around;
     * the first session when there is no (matching) history.
     */
    public static <T extends PlanDayQueueItem> Optional<T> nextPlanDay(
            List<T> planDays, Collection<CompletedPlanDay> completed) {
        List<T> ordered = planDays.stream()
                .sorted(Comparator.comparingInt(PlanDayQueueItem::planOrder)
                        .thenComparingInt(PlanDayQueueItem::dayOrder))
                .toList();
        if (ordered.isEmpty()) return Optional.empty();
        Optional<CompletedPlanDay> latest = completed.stream()
                .max(Comparator.comparing(CompletedPlanDay::completedAt));
        if (latest.isEmpty()) return Optional.of(ordered.getFirst());

        for (int index = 0; index < ordered.size(); index++) {
            if (Objects.equals(ordered.get(index).id(), latest.get().planDayId())) {
                return Optional.of(ordered.get((index + 1) % ordered.size()));
            }
        }
        return Optional.of(ordered.getFirst());
    }

    public interface LoggedSet {
        BigDecimal weight();
        int reps();
    }

    /** Total load moved: weight x reps over every set, zero-load sets included. */
    public static BigDecimal workoutVolume(Collection<? extends LoggedSet> sets) {
        BigDecimal total = BigDecimal.ZERO;
        for (LoggedSet set : sets) total = total.add(set.weight().multiply(BigDecimal.valueOf(set.reps())));
        return total;
    }

    // ---- Variations and targets ----------------------------------------------------

    public interface VariationChoice {
        Object id();
        String name();
        int order();
    }

    /**
     * The variation to start an exercise with, given the one used last time.
     * Variations rotate in order, so alternating incline/flat needs no extra plan.
     */
    public static <T extends VariationChoice> Optional<T> pickNextVariation(List<T> variations, Object lastVariationId) {
        List<T> ordered = variations.stream()
                .sorted(Comparator.comparingInt(VariationChoice::order)
                        .thenComparing(VariationChoice::name, NAMES::compare))
                .toList();
        if (ordered.isEmpty()) return Optional.empty();
        if (lastVariationId == null) return Optional.of(ordered.getFirst());
        for (int index = 0; index < ordered.size(); index++) {
            if (Objects.equals(ordered.get(index).id(), lastVariationId)) {
                return Optional.of(ordered.get((index + 1) % ordered.size()));
            }
        }
        return Optional.of(ordered.getFirst());
    }

    public record TargetSource(BigDecimal recommendedWeight, int recommendedReps, Integer recommendedSets) {}

    public record Target(BigDecimal weight, int reps, int setCount) {}

    /**
     * A selected variation wins over the parent exercise; the plan's set count
     * applies until a set count has been recorded for that exercise or variation.
     */
    public static Target resolveExerciseTarget(TargetSource exercise, TargetSource variation, int planSetCount) {
        TargetSource source = variation != null ? variation : exercise;
        return new Target(source.recommendedWeight(), source.recommendedReps(),
                source.recommendedSets() != null ? source.recommendedSets() : planSetCount);
    }

    // ---- Supersets -------------------------------------------------------------------

    public interface SupersetSlot {
        /** Consecutive slots sharing a group are one superset; null is a plain slot. */
        Integer supersetGroup();
    }

    /**
     * @param group      null for a slot performed on its own, including a stranded group of one
     * @param startIndex position of the first member in the ordered list the block came from
     */
    public record SupersetBlock<T>(Integer group, int startIndex, List<T> members) {}

    /**
     * Splits ordered slots into the blocks a session is performed in. A group
     * left with a single member is not a superset and comes back as a plain block.
     */
    public static <T extends SupersetSlot> List<SupersetBlock<T>> groupSupersets(List<T> slots) {
        List<SupersetBlock<T>> blocks = new ArrayList<>();
        for (int index = 0; index < slots.size(); index++) {
            T slot = slots.get(index);
            Integer group = slot.supersetGroup();
            SupersetBlock<T> last = blocks.isEmpty() ? null : blocks.getLast();
            if (last != null && group != null && group.equals(last.group())) {
                last.members().add(slot);
                continue;
            }
            List<T> members = new ArrayList<>();
            members.add(slot);
            blocks.add(new SupersetBlock<>(group, index, members));
        }
        return blocks.stream()
                .map(block -> block.members().size() > 1
                        ? new SupersetBlock<>(block.group(), block.startIndex(), List.copyOf(block.members()))
                        : new SupersetBlock<>(null, block.startIndex(), List.copyOf(block.members())))
                .toList();
    }

    /** A, B, C... for superset members; numbers past Z. */
    public static String supersetLetter(int offset) {
        return offset < 26 ? String.valueOf((char) ('A' + offset)) : String.valueOf(offset + 1);
    }

    /** The block that contains the slot at {@code index}. */
    public static <T> Optional<SupersetBlock<T>> supersetBlockAt(List<SupersetBlock<T>> blocks, int index) {
        return blocks.stream()
                .filter(block -> index >= block.startIndex() && index < block.startIndex() + block.members().size())
                .findFirst();
    }

    /** How a target reads everywhere in the training UI: "60 kg x 8". */
    public static String formatTarget(BigDecimal weight, int reps) {
        String kilograms = weight.signum() == 0 ? "0" : weight.stripTrailingZeros().toPlainString();
        return kilograms + " kg x " + reps;
    }

    /** {@code links[i]} is true when slot i is performed together with slot i - 1. */
    public static boolean[] supersetLinks(List<? extends SupersetSlot> slots) {
        boolean[] links = new boolean[slots.size()];
        for (int index = 1; index < slots.size(); index++) {
            Integer group = slots.get(index).supersetGroup();
            links[index] = group != null && group.equals(slots.get(index - 1).supersetGroup());
        }
        return links;
    }

    /** Links back to groups, renumbered from 1 in slot order; an unlinked slot has none. */
    public static List<Integer> normalizeSupersetGroups(boolean[] links) {
        Integer[] groups = new Integer[links.length];
        int nextGroup = 1;
        for (int index = 1; index < links.length; index++) {
            if (!links[index]) continue;
            if (groups[index - 1] == null) groups[index - 1] = nextGroup++;
            groups[index] = groups[index - 1];
        }
        return java.util.Arrays.asList(groups);
    }

    /**
     * Flips whether the slot at {@code index} is a superset with the one before
     * it. Linking into a pair extends it (a tri-set); unlinking a middle splits it.
     */
    public static List<Integer> toggleSupersetLink(List<? extends SupersetSlot> slots, int index) {
        if (index <= 0 || index >= slots.size()) {
            throw new IllegalArgumentException("Only a slot with another one above it can be paired.");
        }
        boolean[] links = supersetLinks(slots);
        links[index] = !links[index];
        return normalizeSupersetGroups(links);
    }

    // ---- Walking a workout -------------------------------------------------------------

    public interface StepSlot extends SupersetSlot {
        int setCount();
    }

    /** One stop of the runner: a set index, or {@code setCount} for the end-of-exercise step. */
    public record WorkoutStep(int exerciseIndex, int step) {}

    /**
     * Supersets alternate set by set (A1, B1, A2, B2, ...) and are rated one
     * after the other once the last round is done; every other exercise runs its
     * sets, then its wrap-up.
     */
    public static List<WorkoutStep> buildWorkoutSteps(List<? extends StepSlot> exercises) {
        List<WorkoutStep> steps = new ArrayList<>();
        for (SupersetBlock<? extends StepSlot> block : groupSupersets(exercises)) {
            int rounds = block.members().stream().mapToInt(StepSlot::setCount).max().orElse(0);
            for (int round = 0; round < rounds; round++) {
                for (int offset = 0; offset < block.members().size(); offset++) {
                    if (round < block.members().get(offset).setCount()) {
                        steps.add(new WorkoutStep(block.startIndex() + offset, round));
                    }
                }
            }
            for (int offset = 0; offset < block.members().size(); offset++) {
                steps.add(new WorkoutStep(block.startIndex() + offset, block.members().get(offset).setCount()));
            }
        }
        return steps;
    }

    public interface ProgressSlot extends StepSlot {
        boolean wrappedUp();
        /** One entry per set: whether it has been logged. */
        List<Boolean> completedSets();

        @Override
        default int setCount() {
            return completedSets().size();
        }
    }

    /** An exercise counts as performed once a single set is logged. */
    public static boolean exerciseWasPerformed(List<Boolean> completedSets) {
        return completedSets.contains(Boolean.TRUE);
    }

    /**
     * Open while there is still something to do at it: a set not logged yet or
     * the rating of an exercise not wrapped up yet. Every step of a wrapped-up
     * exercise is closed, including sets it never got to.
     */
    public static boolean stepIsOpen(List<? extends ProgressSlot> exercises, WorkoutStep step) {
        if (step.exerciseIndex() < 0 || step.exerciseIndex() >= exercises.size()) return false;
        ProgressSlot exercise = exercises.get(step.exerciseIndex());
        if (exercise.wrappedUp()) return false;
        return step.step() >= exercise.setCount() || !exercise.completedSets().get(step.step());
    }

    /** The first open step after position {@code fromIndex} of {@code steps}. */
    public static Optional<WorkoutStep> nextOpenStep(List<? extends ProgressSlot> exercises,
                                                     List<WorkoutStep> steps, int fromIndex) {
        for (int index = fromIndex + 1; index < steps.size(); index++) {
            if (stepIsOpen(exercises, steps.get(index))) return Optional.of(steps.get(index));
        }
        return Optional.empty();
    }

    /**
     * Where a resumed workout picks up: the first open step in performance
     * order, so a superset interrupted after A2 resumes on B2 rather than A3.
     */
    public static WorkoutStep resumeCursor(List<? extends ProgressSlot> exercises) {
        List<WorkoutStep> steps = buildWorkoutSteps(exercises);
        return nextOpenStep(exercises, steps, -1)
                .orElse(steps.isEmpty() ? new WorkoutStep(0, 0) : steps.getLast());
    }

    /**
     * Fileable once one set is logged and every exercise that was performed is
     * wrapped up; untouched exercises are dropped rather than blocking the finish.
     */
    public static boolean workoutIsFinishable(List<? extends ProgressSlot> exercises) {
        List<? extends ProgressSlot> performed = exercises.stream()
                .filter(exercise -> exerciseWasPerformed(exercise.completedSets()))
                .toList();
        return !performed.isEmpty() && performed.stream().allMatch(ProgressSlot::wrappedUp);
    }
}
