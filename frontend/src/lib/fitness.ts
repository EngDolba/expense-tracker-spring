import { z } from "zod";

const identifier = z.string().trim().min(1);
const weight = z.number().finite().min(0).max(2_000);
const reps = z.number().int().min(1).max(200);
const setCount = z.number().int().min(1).max(20);
const perceivedEffort = z.number().int().min(1).max(3);

export const startWorkoutSchema = z.object({
  planDayId: identifier,
});

export const activeWorkoutIdSchema = z.object({
  activeWorkoutId: identifier,
});

export const saveActiveSetSchema = z.object({
  activeSetId: identifier,
  weight,
  reps,
  completed: z.boolean(),
});

export const selectVariationSchema = z.object({
  activeExerciseId: identifier,
  variationId: identifier.nullable(),
});

/// The end-of-exercise step: how hard it felt plus the targets the next
/// workout should start from. Every field is entered by hand; nothing is
/// derived from the logged work.
export const wrapUpExerciseSchema = z.object({
  activeExerciseId: identifier,
  perceivedEffort,
  nextWeight: weight,
  nextReps: reps,
  nextSets: setCount,
});

export const saveWorkoutNoteSchema = z.object({
  activeWorkoutId: identifier,
  note: z.string().trim().max(2_000),
});

export type SaveActiveSetPayload = z.infer<typeof saveActiveSetSchema>;
export type WrapUpExercisePayload = z.infer<typeof wrapUpExerciseSchema>;

export type PlanDayQueueItem = {
  id: string;
  planOrder: number;
  dayOrder: number;
};

export type CompletedPlanDay = {
  planDayId: string;
  completedAt: Date | string;
};

export function getNextPlanDay<T extends PlanDayQueueItem>(
  planDays: T[],
  completedWorkouts: CompletedPlanDay[],
) {
  const ordered = [...planDays].sort(
    (a, b) => a.planOrder - b.planOrder || a.dayOrder - b.dayOrder,
  );
  if (!ordered.length) return null;
  if (!completedWorkouts.length) return ordered[0];

  const latest = [...completedWorkouts].sort(
    (a, b) =>
      new Date(b.completedAt).getTime() - new Date(a.completedAt).getTime(),
  )[0];
  const completedIndex = ordered.findIndex((day) => day.id === latest.planDayId);
  if (completedIndex < 0) return ordered[0];
  return ordered[(completedIndex + 1) % ordered.length];
}

export function workoutVolume(sets: Array<{ weight: number; reps: number }>) {
  return sets.reduce((total, set) => total + set.weight * set.reps, 0);
}

export type VariationChoice = {
  id: string;
  name: string;
  order: number;
};

/**
 * Picks the variation to start an exercise with, given the one used last time.
 * Variations rotate in order so alternating incline/flat needs no extra plan;
 * the runner still lets the choice be overridden before the first set.
 */
export function pickNextVariation<T extends VariationChoice>(
  variations: T[],
  lastVariationId: string | null | undefined,
): T | null {
  const ordered = [...variations].sort(
    (a, b) => a.order - b.order || a.name.localeCompare(b.name),
  );
  if (!ordered.length) return null;
  if (!lastVariationId) return ordered[0];
  const lastIndex = ordered.findIndex(
    (variation) => variation.id === lastVariationId,
  );
  if (lastIndex < 0) return ordered[0];
  return ordered[(lastIndex + 1) % ordered.length];
}

export type ExerciseTargetSource = {
  recommendedWeight: number;
  recommendedReps: number;
  recommendedSets?: number | null;
};

/**
 * Resolves the starting target for one slot of a workout. A selected variation
 * wins over the parent exercise, and the plan's set count is the fallback until
 * a set count has been recorded for that exercise or variation.
 */
export function resolveExerciseTarget({
  exercise,
  variation,
  planSetCount,
}: {
  exercise: ExerciseTargetSource;
  variation?: ExerciseTargetSource | null;
  planSetCount: number;
}) {
  const source = variation ?? exercise;
  return {
    weight: source.recommendedWeight,
    reps: source.recommendedReps,
    setCount: source.recommendedSets ?? planSetCount,
  };
}

export type SupersetSlot = {
  /** Consecutive slots sharing a group are one superset; null is a plain slot. */
  supersetGroup?: number | null;
};

export type SupersetBlock<T> = {
  /** null for a slot performed on its own, including a stranded group of one. */
  group: number | null;
  /** Position of the first member in the ordered list the block came from. */
  startIndex: number;
  members: T[];
};

/**
 * Splits an ordered list of slots into the blocks a session is performed in.
 * Consecutive slots that share a superset group form one block; every other
 * slot is a block on its own. A group left with a single member is not a
 * superset, so it comes back as a plain block.
 */
export function groupSupersets<T extends SupersetSlot>(slots: T[]): SupersetBlock<T>[] {
  const blocks: SupersetBlock<T>[] = [];
  for (const [index, slot] of slots.entries()) {
    const group = slot.supersetGroup ?? null;
    const last = blocks[blocks.length - 1];
    if (last && group !== null && last.group === group) {
      last.members.push(slot);
      continue;
    }
    blocks.push({ group, startIndex: index, members: [slot] });
  }
  return blocks.map((block) =>
    block.members.length > 1 ? block : { ...block, group: null },
  );
}

/** Letter used to label the members of a superset: A, B, C... (numbers past Z). */
export function supersetLetter(offset: number) {
  return offset < 26 ? String.fromCharCode(65 + offset) : String(offset + 1);
}

/** The block of `blocks` that contains the slot at `index`, if any. */
export function supersetBlockAt<T>(blocks: SupersetBlock<T>[], index: number) {
  return (
    blocks.find(
      (block) =>
        index >= block.startIndex && index < block.startIndex + block.members.length,
    ) ?? null
  );
}

/** How a target reads everywhere in the training UI: "60 kg x 8". */
export function formatTarget(weight: number, reps: number) {
  return `${weight} kg x ${reps}`;
}

/**
 * `links[i]` is true when slot i is performed together with slot i - 1. This is
 * the editable form of the grouping: a plan toggles one link at a time.
 */
export function supersetLinks<T extends SupersetSlot>(slots: T[]): boolean[] {
  return slots.map((slot, index) => {
    if (index === 0) return false;
    const group = slot.supersetGroup ?? null;
    return group !== null && (slots[index - 1].supersetGroup ?? null) === group;
  });
}

/**
 * Turns links back into group numbers. Groups are renumbered from 1 in slot
 * order, and a slot that is linked to nothing carries no group at all.
 */
export function normalizeSupersetGroups(links: boolean[]): Array<number | null> {
  const groups: Array<number | null> = links.map(() => null);
  let nextGroup = 1;
  for (let index = 1; index < links.length; index++) {
    if (!links[index]) continue;
    if (groups[index - 1] === null) groups[index - 1] = nextGroup++;
    groups[index] = groups[index - 1];
  }
  return groups;
}

/**
 * Flips whether the slot at `index` is a superset with the slot before it and
 * returns the renumbered group for every slot. Linking into an existing pair
 * extends it (a tri-set); unlinking the middle of one splits it.
 */
export function toggleSupersetLink<T extends SupersetSlot>(slots: T[], index: number) {
  if (index <= 0 || index >= slots.length) {
    throw new RangeError("Only a slot with another one above it can be paired.");
  }
  const links = supersetLinks(slots);
  links[index] = !links[index];
  return normalizeSupersetGroups(links);
}

export type WorkoutStep = {
  exerciseIndex: number;
  /** A set index, or `sets.length` for the end-of-exercise step. */
  step: number;
};

/**
 * The order the runner walks a workout in. Exercises in a superset alternate
 * set by set (A1, B1, A2, B2, ...) and are rated one after the other once the
 * last round is done. Every other exercise runs its sets, then its wrap-up.
 */
export function buildWorkoutSteps<
  T extends SupersetSlot & { sets: ReadonlyArray<unknown> },
>(exercises: T[]): WorkoutStep[] {
  const steps: WorkoutStep[] = [];
  for (const block of groupSupersets(exercises)) {
    const rounds = Math.max(0, ...block.members.map((member) => member.sets.length));
    for (let round = 0; round < rounds; round++) {
      block.members.forEach((member, offset) => {
        if (round < member.sets.length) {
          steps.push({ exerciseIndex: block.startIndex + offset, step: round });
        }
      });
    }
    block.members.forEach((member, offset) => {
      steps.push({
        exerciseIndex: block.startIndex + offset,
        step: member.sets.length,
      });
    });
  }
  return steps;
}

export type WorkoutProgressExercise = SupersetSlot & {
  wrappedUp: boolean;
  sets: Array<{ completed: boolean }>;
};

/**
 * An exercise counts as performed once a single set is logged. Sets that were
 * never confirmed are cut when the workout is filed, so an exercise with none
 * of them never reaches history.
 */
export function exerciseWasPerformed(exercise: {
  sets: Array<{ completed: boolean }>;
}) {
  return exercise.sets.some((set) => set.completed);
}

/**
 * Where a resumed workout should pick up: the first step, in performance
 * order, that is still open. That is the first unlogged set, or the
 * end-of-exercise step when every set of that exercise is already logged. An
 * exercise that was wrapped up early is skipped — its unlogged sets were left
 * behind on purpose. Inside a superset this follows the alternating order, so a
 * session interrupted after A2 resumes on B2 rather than A3.
 */
export function resumeCursor(exercises: WorkoutProgressExercise[]): WorkoutStep {
  const steps = buildWorkoutSteps(exercises);
  return (
    nextOpenStep(exercises, steps, -1) ??
    steps[steps.length - 1] ?? { exerciseIndex: 0, step: 0 }
  );
}

/**
 * A step is open while there is still something to do at it: a set not logged
 * yet, or the rating of an exercise not wrapped up yet. Every step of an
 * exercise that was wrapped up is closed, including sets it never got to.
 */
export function stepIsOpen(
  exercises: WorkoutProgressExercise[],
  step: WorkoutStep,
) {
  const exercise = exercises[step.exerciseIndex];
  if (!exercise || exercise.wrappedUp) return false;
  return step.step >= exercise.sets.length || !exercise.sets[step.step].completed;
}

/**
 * The first open step after position `fromIndex` of `steps`, or null when
 * nothing after it is open. This is what confirming a set advances to, so a
 * superset whose partner was closed early moves on to the next round instead
 * of walking onto the partner's abandoned sets.
 */
export function nextOpenStep(
  exercises: WorkoutProgressExercise[],
  steps: WorkoutStep[],
  fromIndex: number,
): WorkoutStep | null {
  for (let index = fromIndex + 1; index < steps.length; index++) {
    if (stepIsOpen(exercises, steps[index])) return steps[index];
  }
  return null;
}

/**
 * A workout can be filed as soon as one set is logged. Only the exercises that
 * were actually performed need a rating and a next target; untouched ones are
 * dropped rather than blocking the finish.
 */
export function workoutIsFinishable(exercises: WorkoutProgressExercise[]) {
  const performed = exercises.filter(exerciseWasPerformed);
  return performed.length > 0 && performed.every((exercise) => exercise.wrappedUp);
}
