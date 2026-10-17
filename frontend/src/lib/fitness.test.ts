import { describe, expect, it } from "vitest";
import {
  buildWorkoutSteps,
  formatTarget,
  getNextPlanDay,
  groupSupersets,
  nextOpenStep,
  normalizeSupersetGroups,
  pickNextVariation,
  resolveExerciseTarget,
  resumeCursor,
  saveActiveSetSchema,
  supersetBlockAt,
  supersetLetter,
  supersetLinks,
  toggleSupersetLink,
  workoutIsFinishable,
  workoutVolume,
  wrapUpExerciseSchema,
} from "@/lib/fitness";

describe("getNextPlanDay", () => {
  const planDays = [
    { id: "push", planOrder: 0, dayOrder: 0 },
    { id: "pull", planOrder: 0, dayOrder: 1 },
    { id: "legs", planOrder: 1, dayOrder: 0 },
  ];

  it("starts with the first ordered session when there is no history", () => {
    expect(getNextPlanDay(planDays, [])?.id).toBe("push");
  });

  it("moves forward from the most recently completed session and wraps", () => {
    expect(
      getNextPlanDay(planDays, [
        { planDayId: "push", completedAt: "2026-07-20T09:00:00.000Z" },
        { planDayId: "pull", completedAt: "2026-07-22T09:00:00.000Z" },
      ])?.id,
    ).toBe("legs");
    expect(
      getNextPlanDay(planDays, [
        { planDayId: "legs", completedAt: "2026-07-23T09:00:00.000Z" },
      ])?.id,
    ).toBe("push");
  });
});

describe("pickNextVariation", () => {
  const variations = [
    { id: "flat", name: "Flat", order: 0 },
    { id: "incline", name: "Incline", order: 1 },
  ];

  it("returns nothing when the exercise has no variations", () => {
    expect(pickNextVariation([], null)).toBeNull();
  });

  it("starts with the first variation when nothing was logged before", () => {
    expect(pickNextVariation(variations, null)?.id).toBe("flat");
    expect(pickNextVariation(variations, undefined)?.id).toBe("flat");
  });

  it("alternates to the next variation and wraps around", () => {
    expect(pickNextVariation(variations, "flat")?.id).toBe("incline");
    expect(pickNextVariation(variations, "incline")?.id).toBe("flat");
  });

  it("falls back to the first variation when the last one was archived away", () => {
    expect(pickNextVariation(variations, "decline")?.id).toBe("flat");
  });

  it("rotates through three variations in declared order", () => {
    const three = [
      ...variations,
      { id: "decline", name: "Decline", order: 2 },
    ];
    expect(pickNextVariation(three, "incline")?.id).toBe("decline");
    expect(pickNextVariation(three, "decline")?.id).toBe("flat");
  });
});

describe("resolveExerciseTarget", () => {
  const exercise = {
    recommendedWeight: 60,
    recommendedReps: 8,
    recommendedSets: null,
  };

  it("uses the plan set count until a set count has been recorded", () => {
    expect(resolveExerciseTarget({ exercise, planSetCount: 3 })).toEqual({
      weight: 60,
      reps: 8,
      setCount: 3,
    });
  });

  it("prefers the recorded set count over the plan default", () => {
    expect(
      resolveExerciseTarget({
        exercise: { ...exercise, recommendedSets: 4 },
        planSetCount: 3,
      }).setCount,
    ).toBe(4);
  });

  it("lets a selected variation override the parent exercise", () => {
    expect(
      resolveExerciseTarget({
        exercise,
        variation: {
          recommendedWeight: 45,
          recommendedReps: 10,
          recommendedSets: 2,
        },
        planSetCount: 3,
      }),
    ).toEqual({ weight: 45, reps: 10, setCount: 2 });
  });
});

describe("resumeCursor", () => {
  it("returns the first unlogged set", () => {
    expect(
      resumeCursor([
        {
          wrappedUp: true,
          sets: [{ completed: true }, { completed: true }],
        },
        {
          wrappedUp: false,
          sets: [{ completed: true }, { completed: false }],
        },
      ]),
    ).toEqual({ exerciseIndex: 1, step: 1 });
  });

  it("returns the end-of-exercise step when every set is logged but unrated", () => {
    expect(
      resumeCursor([
        { wrappedUp: false, sets: [{ completed: true }, { completed: true }] },
        { wrappedUp: false, sets: [{ completed: false }] },
      ]),
    ).toEqual({ exerciseIndex: 0, step: 2 });
  });

  it("parks on the last exercise once everything is done", () => {
    expect(
      resumeCursor([{ wrappedUp: true, sets: [{ completed: true }] }]),
    ).toEqual({ exerciseIndex: 0, step: 1 });
  });

  it("skips the unlogged sets of an exercise that was closed early", () => {
    expect(
      resumeCursor([
        { wrappedUp: true, sets: [{ completed: true }, { completed: false }] },
        { wrappedUp: false, sets: [{ completed: false }] },
      ]),
    ).toEqual({ exerciseIndex: 1, step: 0 });
  });
});

describe("workoutIsFinishable", () => {
  it("requires at least one logged set", () => {
    expect(workoutIsFinishable([])).toBe(false);
    expect(
      workoutIsFinishable([{ wrappedUp: true, sets: [{ completed: false }] }]),
    ).toBe(false);
  });

  it("requires a wrap-up on every exercise that was logged", () => {
    expect(
      workoutIsFinishable([{ wrappedUp: false, sets: [{ completed: true }] }]),
    ).toBe(false);
    expect(
      workoutIsFinishable([
        { wrappedUp: true, sets: [{ completed: true }] },
        { wrappedUp: false, sets: [{ completed: true }] },
      ]),
    ).toBe(false);
    expect(
      workoutIsFinishable([{ wrappedUp: true, sets: [{ completed: true }] }]),
    ).toBe(true);
  });

  it("finishes early: partial sets count, untouched exercises are ignored", () => {
    expect(
      workoutIsFinishable([
        { wrappedUp: true, sets: [{ completed: true }, { completed: false }] },
      ]),
    ).toBe(true);
    expect(
      workoutIsFinishable([
        { wrappedUp: true, sets: [{ completed: true }] },
        { wrappedUp: false, sets: [{ completed: false }, { completed: false }] },
      ]),
    ).toBe(true);
  });
});

describe("workout payload schemas", () => {
  it("accepts a logged set and rejects impossible work", () => {
    expect(
      saveActiveSetSchema.safeParse({
        activeSetId: "set-1",
        weight: 62.5,
        reps: 7,
        completed: true,
      }).success,
    ).toBe(true);
    expect(
      saveActiveSetSchema.safeParse({
        activeSetId: "set-1",
        weight: 62.5,
        reps: 0,
        completed: true,
      }).success,
    ).toBe(false);
  });

  it("requires a hardness rating and complete next targets to wrap up", () => {
    const wrapUp = {
      activeExerciseId: "active-1",
      perceivedEffort: 2,
      nextWeight: 65,
      nextReps: 8,
      nextSets: 3,
    };
    expect(wrapUpExerciseSchema.safeParse(wrapUp).success).toBe(true);
    expect(
      wrapUpExerciseSchema.safeParse({ ...wrapUp, perceivedEffort: 4 }).success,
    ).toBe(false);
    expect(
      wrapUpExerciseSchema.safeParse({ ...wrapUp, nextSets: 0 }).success,
    ).toBe(false);
    expect(
      wrapUpExerciseSchema.safeParse({ ...wrapUp, perceivedEffort: undefined })
        .success,
    ).toBe(false);
  });
});

describe("workoutVolume", () => {
  it("adds every logged set, including bodyweight-style zero-load sets", () => {
    expect(
      workoutVolume([
        { weight: 60, reps: 8 },
        { weight: 62.5, reps: 7 },
        { weight: 0, reps: 10 },
      ]),
    ).toBe(917.5);
  });
});

describe("groupSupersets", () => {
  it("pairs consecutive slots that share a group and leaves the rest alone", () => {
    const blocks = groupSupersets([
      { id: "a", supersetGroup: null },
      { id: "b", supersetGroup: 1 },
      { id: "c", supersetGroup: 1 },
      { id: "d", supersetGroup: 2 },
      { id: "e", supersetGroup: null },
    ]);
    expect(
      blocks.map((block) => [
        block.group,
        block.startIndex,
        block.members.map((member) => member.id),
      ]),
    ).toEqual([
      [null, 0, ["a"]],
      [1, 1, ["b", "c"]],
      // A group left with one member is not a superset.
      [null, 3, ["d"]],
      [null, 4, ["e"]],
    ]);
  });

  it("keeps two different groups that touch apart", () => {
    const blocks = groupSupersets([{ supersetGroup: 1 }, { supersetGroup: 2 }]);
    expect(blocks.map((block) => block.group)).toEqual([null, null]);
  });
});

describe("superset links", () => {
  it("round-trips groups through the per-slot links", () => {
    const slots = [
      { supersetGroup: null },
      { supersetGroup: 1 },
      { supersetGroup: 1 },
      { supersetGroup: null },
    ];
    expect(supersetLinks(slots)).toEqual([false, false, true, false]);
    expect(normalizeSupersetGroups(supersetLinks(slots))).toEqual([null, 1, 1, null]);
  });

  it("renumbers from one and ignores a link on the first slot", () => {
    expect(normalizeSupersetGroups([false, false, false, true, true])).toEqual([
      null, null, 1, 1, 1,
    ]);
    expect(normalizeSupersetGroups([true, false])).toEqual([null, null]);
    expect(normalizeSupersetGroups([false, true, false, true])).toEqual([1, 1, 2, 2]);
  });

  it("toggles a pair on and off and extends a pair into a tri-set", () => {
    const plain = [{ supersetGroup: null }, { supersetGroup: null }, { supersetGroup: null }];
    expect(toggleSupersetLink(plain, 1)).toEqual([1, 1, null]);
    const paired = [{ supersetGroup: 1 }, { supersetGroup: 1 }, { supersetGroup: null }];
    expect(toggleSupersetLink(paired, 2)).toEqual([1, 1, 1]);
    expect(toggleSupersetLink(paired, 1)).toEqual([null, null, null]);
    expect(() => toggleSupersetLink(paired, 0)).toThrow(RangeError);
  });
});

describe("buildWorkoutSteps", () => {
  it("runs a plain exercise set by set, then its wrap-up", () => {
    expect(buildWorkoutSteps([{ supersetGroup: null, sets: [1, 2] }])).toEqual([
      { exerciseIndex: 0, step: 0 },
      { exerciseIndex: 0, step: 1 },
      { exerciseIndex: 0, step: 2 },
    ]);
  });

  it("alternates the sets of a superset and rates its members afterwards", () => {
    expect(
      buildWorkoutSteps([
        { supersetGroup: 1, sets: [1, 2, 3] },
        { supersetGroup: 1, sets: [1, 2] },
        { supersetGroup: null, sets: [1] },
      ]),
    ).toEqual([
      { exerciseIndex: 0, step: 0 },
      { exerciseIndex: 1, step: 0 },
      { exerciseIndex: 0, step: 1 },
      { exerciseIndex: 1, step: 1 },
      { exerciseIndex: 0, step: 2 },
      { exerciseIndex: 0, step: 3 },
      { exerciseIndex: 1, step: 2 },
      { exerciseIndex: 2, step: 0 },
      { exerciseIndex: 2, step: 1 },
    ]);
  });
});

describe("resumeCursor inside a superset", () => {
  it("resumes on the partner set rather than the next set of the same exercise", () => {
    expect(
      resumeCursor([
        { supersetGroup: 1, wrappedUp: false, sets: [{ completed: true }, { completed: false }] },
        { supersetGroup: 1, wrappedUp: false, sets: [{ completed: false }, { completed: false }] },
      ]),
    ).toEqual({ exerciseIndex: 1, step: 0 });
  });

  it("moves to the second wrap-up once the first member of a pair is rated", () => {
    expect(
      resumeCursor([
        { supersetGroup: 1, wrappedUp: true, sets: [{ completed: true }] },
        { supersetGroup: 1, wrappedUp: false, sets: [{ completed: true }] },
      ]),
    ).toEqual({ exerciseIndex: 1, step: 1 });
  });
});

describe("nextOpenStep", () => {
  // A and B are a pair with three sets each; A was closed early after one set.
  const exercises = [
    {
      supersetGroup: 1,
      wrappedUp: true,
      sets: [{ completed: true }, { completed: false }, { completed: false }],
    },
    {
      supersetGroup: 1,
      wrappedUp: false,
      sets: [{ completed: true }, { completed: false }, { completed: false }],
    },
  ];
  // Steps: A1 B1 A2 B2 A3 B3 A-wrap B-wrap (indexes 0..7).
  const steps = buildWorkoutSteps(exercises);

  it("skips the abandoned sets of a partner that was closed early", () => {
    expect(nextOpenStep(exercises, steps, 1)).toEqual({ exerciseIndex: 1, step: 1 });
    expect(nextOpenStep(exercises, steps, 3)).toEqual({ exerciseIndex: 1, step: 2 });
  });

  it("skips the wrap-up of an exercise that is already rated", () => {
    expect(nextOpenStep(exercises, steps, 5)).toEqual({ exerciseIndex: 1, step: 3 });
  });

  it("returns null once nothing after the position is open", () => {
    expect(nextOpenStep(exercises, steps, 7)).toBeNull();
    const done = [{ supersetGroup: null, wrappedUp: true, sets: [{ completed: true }] }];
    expect(nextOpenStep(done, buildWorkoutSteps(done), -1)).toBeNull();
  });

  it("is what resumeCursor starts from", () => {
    expect(resumeCursor(exercises)).toEqual(nextOpenStep(exercises, steps, -1));
  });
});

describe("supersetBlockAt", () => {
  const blocks = groupSupersets([
    { supersetGroup: null },
    { supersetGroup: 1 },
    { supersetGroup: 1 },
  ]);

  it("finds the block that contains a slot", () => {
    expect(supersetBlockAt(blocks, 0)?.startIndex).toBe(0);
    expect(supersetBlockAt(blocks, 2)?.startIndex).toBe(1);
    expect(supersetBlockAt(blocks, 1)).toBe(supersetBlockAt(blocks, 2));
  });

  it("returns null outside the list", () => {
    expect(supersetBlockAt(blocks, 3)).toBeNull();
    expect(supersetBlockAt(blocks, -1)).toBeNull();
  });
});

describe("labels", () => {
  it("letters superset members and falls back to numbers past Z", () => {
    expect(supersetLetter(0)).toBe("A");
    expect(supersetLetter(1)).toBe("B");
    expect(supersetLetter(25)).toBe("Z");
    expect(supersetLetter(26)).toBe("27");
  });

  it("formats a target the same way everywhere", () => {
    expect(formatTarget(62.5, 8)).toBe("62.5 kg x 8");
    expect(formatTarget(0, 12)).toBe("0 kg x 12");
  });
});
