package com.dolbaia.ledger.fitness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.dolbaia.ledger.fitness.FitnessRules.CompletedPlanDay;
import com.dolbaia.ledger.fitness.FitnessRules.LoggedSet;
import com.dolbaia.ledger.fitness.FitnessRules.PlanDayQueueItem;
import com.dolbaia.ledger.fitness.FitnessRules.ProgressSlot;
import com.dolbaia.ledger.fitness.FitnessRules.StepSlot;
import com.dolbaia.ledger.fitness.FitnessRules.SupersetBlock;
import com.dolbaia.ledger.fitness.FitnessRules.SupersetSlot;
import com.dolbaia.ledger.fitness.FitnessRules.Target;
import com.dolbaia.ledger.fitness.FitnessRules.TargetSource;
import com.dolbaia.ledger.fitness.FitnessRules.VariationChoice;
import com.dolbaia.ledger.fitness.FitnessRules.WorkoutStep;

/** Port of fitness.test.ts (the payload-schema cases live in the fitness DTO tests). */
class FitnessRulesTest {

    record Day(String id, int planOrder, int dayOrder) implements PlanDayQueueItem {}

    record Variation(String id, String name, int order) implements VariationChoice {}

    record Slot(String id, Integer supersetGroup) implements SupersetSlot {}

    record Steps(Integer supersetGroup, int setCount) implements StepSlot {}

    record Progress(Integer supersetGroup, boolean wrappedUp, List<Boolean> completedSets) implements ProgressSlot {}

    record LoggedWork(BigDecimal weight, int reps) implements LoggedSet {}

    static Progress p(boolean wrappedUp, Boolean... sets) {
        return new Progress(null, wrappedUp, List.of(sets));
    }

    static Progress p(Integer group, boolean wrappedUp, Boolean... sets) {
        return new Progress(group, wrappedUp, List.of(sets));
    }

    static WorkoutStep step(int exerciseIndex, int step) {
        return new WorkoutStep(exerciseIndex, step);
    }

    static List<Integer> groups(Integer... values) {
        return Arrays.asList(values);
    }

    @Nested
    class NextPlanDay {
        final List<Day> planDays = List.of(new Day("push", 0, 0), new Day("pull", 0, 1), new Day("legs", 1, 0));

        @Test
        void startsWithTheFirstOrderedSessionWhenThereIsNoHistory() {
            assertThat(FitnessRules.nextPlanDay(planDays, List.of())).map(Day::id).contains("push");
        }

        @Test
        void movesForwardFromTheMostRecentlyCompletedSessionAndWraps() {
            assertThat(FitnessRules.nextPlanDay(planDays, List.of(
                    new CompletedPlanDay("push", Instant.parse("2026-07-20T09:00:00Z")),
                    new CompletedPlanDay("pull", Instant.parse("2026-07-22T09:00:00Z")))))
                    .map(Day::id).contains("legs");
            assertThat(FitnessRules.nextPlanDay(planDays, List.of(
                    new CompletedPlanDay("legs", Instant.parse("2026-07-23T09:00:00Z")))))
                    .map(Day::id).contains("push");
        }

        @Test
        void hasNothingToOfferWithoutSessions() {
            assertThat(FitnessRules.nextPlanDay(List.<Day>of(), List.of())).isEmpty();
        }
    }

    @Nested
    class PickNextVariation {
        final List<Variation> variations = List.of(new Variation("flat", "Flat", 0), new Variation("incline", "Incline", 1));

        @Test
        void returnsNothingWhenTheExerciseHasNoVariations() {
            assertThat(FitnessRules.pickNextVariation(List.<Variation>of(), null)).isEmpty();
        }

        @Test
        void startsWithTheFirstVariationWhenNothingWasLoggedBefore() {
            assertThat(FitnessRules.pickNextVariation(variations, null)).map(Variation::id).contains("flat");
        }

        @Test
        void alternatesToTheNextVariationAndWrapsAround() {
            assertThat(FitnessRules.pickNextVariation(variations, "flat")).map(Variation::id).contains("incline");
            assertThat(FitnessRules.pickNextVariation(variations, "incline")).map(Variation::id).contains("flat");
        }

        @Test
        void fallsBackToTheFirstVariationWhenTheLastOneWasArchivedAway() {
            assertThat(FitnessRules.pickNextVariation(variations, "decline")).map(Variation::id).contains("flat");
        }

        @Test
        void rotatesThroughThreeVariationsInDeclaredOrder() {
            List<Variation> three = List.of(variations.get(0), variations.get(1), new Variation("decline", "Decline", 2));
            assertThat(FitnessRules.pickNextVariation(three, "incline")).map(Variation::id).contains("decline");
            assertThat(FitnessRules.pickNextVariation(three, "decline")).map(Variation::id).contains("flat");
        }
    }

    @Nested
    class ResolveExerciseTarget {
        final TargetSource exercise = new TargetSource(new BigDecimal("60"), 8, null);

        @Test
        void usesThePlanSetCountUntilASetCountHasBeenRecorded() {
            assertThat(FitnessRules.resolveExerciseTarget(exercise, null, 3))
                    .isEqualTo(new Target(new BigDecimal("60"), 8, 3));
        }

        @Test
        void prefersTheRecordedSetCountOverThePlanDefault() {
            assertThat(FitnessRules.resolveExerciseTarget(new TargetSource(new BigDecimal("60"), 8, 4), null, 3)
                    .setCount()).isEqualTo(4);
        }

        @Test
        void letsASelectedVariationOverrideTheParentExercise() {
            assertThat(FitnessRules.resolveExerciseTarget(exercise, new TargetSource(new BigDecimal("45"), 10, 2), 3))
                    .isEqualTo(new Target(new BigDecimal("45"), 10, 2));
        }
    }

    @Nested
    class ResumeCursor {
        @Test
        void returnsTheFirstUnloggedSet() {
            assertThat(FitnessRules.resumeCursor(List.of(p(true, true, true), p(false, true, false))))
                    .isEqualTo(step(1, 1));
        }

        @Test
        void returnsTheEndOfExerciseStepWhenEverySetIsLoggedButUnrated() {
            assertThat(FitnessRules.resumeCursor(List.of(p(false, true, true), p(false, false))))
                    .isEqualTo(step(0, 2));
        }

        @Test
        void parksOnTheLastExerciseOnceEverythingIsDone() {
            assertThat(FitnessRules.resumeCursor(List.of(p(true, true)))).isEqualTo(step(0, 1));
        }

        @Test
        void skipsTheUnloggedSetsOfAnExerciseThatWasClosedEarly() {
            assertThat(FitnessRules.resumeCursor(List.of(p(true, true, false), p(false, false))))
                    .isEqualTo(step(1, 0));
        }

        @Test
        void resumesOnThePartnerSetRatherThanTheNextSetOfTheSameExercise() {
            assertThat(FitnessRules.resumeCursor(List.of(p(1, false, true, false), p(1, false, false, false))))
                    .isEqualTo(step(1, 0));
        }

        @Test
        void movesToTheSecondWrapUpOnceTheFirstMemberOfAPairIsRated() {
            assertThat(FitnessRules.resumeCursor(List.of(p(1, true, true), p(1, false, true))))
                    .isEqualTo(step(1, 1));
        }
    }

    @Nested
    class WorkoutIsFinishable {
        @Test
        void requiresAtLeastOneLoggedSet() {
            assertThat(FitnessRules.workoutIsFinishable(List.<Progress>of())).isFalse();
            assertThat(FitnessRules.workoutIsFinishable(List.of(p(true, false)))).isFalse();
        }

        @Test
        void requiresAWrapUpOnEveryExerciseThatWasLogged() {
            assertThat(FitnessRules.workoutIsFinishable(List.of(p(false, true)))).isFalse();
            assertThat(FitnessRules.workoutIsFinishable(List.of(p(true, true), p(false, true)))).isFalse();
            assertThat(FitnessRules.workoutIsFinishable(List.of(p(true, true)))).isTrue();
        }

        @Test
        void finishesEarlyPartialSetsCountUntouchedExercisesAreIgnored() {
            assertThat(FitnessRules.workoutIsFinishable(List.of(p(true, true, false)))).isTrue();
            assertThat(FitnessRules.workoutIsFinishable(List.of(p(true, true), p(false, false, false)))).isTrue();
        }
    }

    @Test
    void workoutVolumeAddsEveryLoggedSetIncludingZeroLoadSets() {
        assertThat(FitnessRules.workoutVolume(List.of(
                new LoggedWork(new BigDecimal("60"), 8),
                new LoggedWork(new BigDecimal("62.5"), 7),
                new LoggedWork(BigDecimal.ZERO, 10))))
                .isEqualByComparingTo("917.5");
    }

    @Nested
    class GroupSupersets {
        @Test
        void pairsConsecutiveSlotsThatShareAGroupAndLeavesTheRestAlone() {
            List<SupersetBlock<Slot>> blocks = FitnessRules.groupSupersets(List.of(
                    new Slot("a", null), new Slot("b", 1), new Slot("c", 1), new Slot("d", 2), new Slot("e", null)));

            assertThat(blocks).extracting(SupersetBlock::group).containsExactly(null, 1, null, null);
            assertThat(blocks).extracting(SupersetBlock::startIndex).containsExactly(0, 1, 3, 4);
            assertThat(blocks.get(1).members()).extracting(Slot::id).containsExactly("b", "c");
        }

        @Test
        void keepsTwoDifferentGroupsThatTouchApart() {
            assertThat(FitnessRules.groupSupersets(List.of(new Slot("a", 1), new Slot("b", 2))))
                    .extracting(SupersetBlock::group).containsExactly(null, null);
        }
    }

    @Nested
    class SupersetLinks {
        @Test
        void roundTripsGroupsThroughThePerSlotLinks() {
            List<Slot> slots = List.of(new Slot("a", null), new Slot("b", 1), new Slot("c", 1), new Slot("d", null));
            assertThat(FitnessRules.supersetLinks(slots)).containsExactly(false, false, true, false);
            assertThat(FitnessRules.normalizeSupersetGroups(FitnessRules.supersetLinks(slots)))
                    .isEqualTo(groups(null, 1, 1, null));
        }

        @Test
        void renumbersFromOneAndIgnoresALinkOnTheFirstSlot() {
            assertThat(FitnessRules.normalizeSupersetGroups(new boolean[] {false, false, false, true, true}))
                    .isEqualTo(groups(null, null, 1, 1, 1));
            assertThat(FitnessRules.normalizeSupersetGroups(new boolean[] {true, false})).isEqualTo(groups(null, null));
            assertThat(FitnessRules.normalizeSupersetGroups(new boolean[] {false, true, false, true}))
                    .isEqualTo(groups(1, 1, 2, 2));
        }

        @Test
        void togglesAPairOnAndOffAndExtendsAPairIntoATriSet() {
            List<Slot> plain = List.of(new Slot("a", null), new Slot("b", null), new Slot("c", null));
            assertThat(FitnessRules.toggleSupersetLink(plain, 1)).isEqualTo(groups(1, 1, null));
            List<Slot> paired = List.of(new Slot("a", 1), new Slot("b", 1), new Slot("c", null));
            assertThat(FitnessRules.toggleSupersetLink(paired, 2)).isEqualTo(groups(1, 1, 1));
            assertThat(FitnessRules.toggleSupersetLink(paired, 1)).isEqualTo(groups(null, null, null));
            assertThatThrownBy(() -> FitnessRules.toggleSupersetLink(paired, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class BuildWorkoutSteps {
        @Test
        void runsAPlainExerciseSetBySetThenItsWrapUp() {
            assertThat(FitnessRules.buildWorkoutSteps(List.of(new Steps(null, 2))))
                    .containsExactly(step(0, 0), step(0, 1), step(0, 2));
        }

        @Test
        void alternatesTheSetsOfASupersetAndRatesItsMembersAfterwards() {
            assertThat(FitnessRules.buildWorkoutSteps(List.of(new Steps(1, 3), new Steps(1, 2), new Steps(null, 1))))
                    .containsExactly(step(0, 0), step(1, 0), step(0, 1), step(1, 1), step(0, 2),
                            step(0, 3), step(1, 2), step(2, 0), step(2, 1));
        }
    }

    @Nested
    class NextOpenStep {
        // A and B are a pair with three sets each; A was closed early after one set.
        final List<Progress> exercises = List.of(p(1, true, true, false, false), p(1, false, true, false, false));
        // Steps: A1 B1 A2 B2 A3 B3 A-wrap B-wrap (indexes 0..7).
        final List<WorkoutStep> steps = FitnessRules.buildWorkoutSteps(exercises);

        @Test
        void skipsTheAbandonedSetsOfAPartnerThatWasClosedEarly() {
            assertThat(FitnessRules.nextOpenStep(exercises, steps, 1)).contains(step(1, 1));
            assertThat(FitnessRules.nextOpenStep(exercises, steps, 3)).contains(step(1, 2));
        }

        @Test
        void skipsTheWrapUpOfAnExerciseThatIsAlreadyRated() {
            assertThat(FitnessRules.nextOpenStep(exercises, steps, 5)).contains(step(1, 3));
        }

        @Test
        void returnsNothingOnceNothingAfterThePositionIsOpen() {
            assertThat(FitnessRules.nextOpenStep(exercises, steps, 7)).isEmpty();
            List<Progress> done = List.of(p(true, true));
            assertThat(FitnessRules.nextOpenStep(done, FitnessRules.buildWorkoutSteps(done), -1)).isEmpty();
        }

        @Test
        void isWhatResumeCursorStartsFrom() {
            assertThat(FitnessRules.nextOpenStep(exercises, steps, -1)).contains(FitnessRules.resumeCursor(exercises));
        }
    }

    @Nested
    class SupersetBlockAt {
        final List<SupersetBlock<Slot>> blocks =
                FitnessRules.groupSupersets(List.of(new Slot("a", null), new Slot("b", 1), new Slot("c", 1)));

        @Test
        void findsTheBlockThatContainsASlot() {
            assertThat(FitnessRules.supersetBlockAt(blocks, 0)).map(SupersetBlock::startIndex).contains(0);
            assertThat(FitnessRules.supersetBlockAt(blocks, 2)).map(SupersetBlock::startIndex).contains(1);
            assertThat(FitnessRules.supersetBlockAt(blocks, 1)).isEqualTo(FitnessRules.supersetBlockAt(blocks, 2));
        }

        @Test
        void returnsNothingOutsideTheList() {
            assertThat(FitnessRules.supersetBlockAt(blocks, 3)).isEmpty();
            assertThat(FitnessRules.supersetBlockAt(blocks, -1)).isEmpty();
        }
    }

    @Test
    void lettersSupersetMembersAndFallsBackToNumbersPastZ() {
        assertThat(FitnessRules.supersetLetter(0)).isEqualTo("A");
        assertThat(FitnessRules.supersetLetter(1)).isEqualTo("B");
        assertThat(FitnessRules.supersetLetter(25)).isEqualTo("Z");
        assertThat(FitnessRules.supersetLetter(26)).isEqualTo("27");
    }

    @Test
    void formatsATargetTheSameWayEverywhere() {
        assertThat(FitnessRules.formatTarget(new BigDecimal("62.50"), 8)).isEqualTo("62.5 kg x 8");
        assertThat(FitnessRules.formatTarget(new BigDecimal("0.00"), 12)).isEqualTo("0 kg x 12");
        assertThat(FitnessRules.formatTarget(new BigDecimal("60.00"), 5)).isEqualTo("60 kg x 5");
    }
}
