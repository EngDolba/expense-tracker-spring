// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  FitnessWorkoutRunner,
  type RunnerExercise,
  type RunnerWorkout,
} from "@/components/fitness-workout-runner";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), refresh: vi.fn() }),
}));

// The runner talks to server actions; here they only need to resolve.
vi.mock("@/app/actions", () => ({
  completeWorkoutAction: vi.fn(async () => ({ id: "workout" })),
  saveActiveSetAction: vi.fn(async () => ({ ok: true })),
  saveActiveWorkoutNoteAction: vi.fn(async () => ({ ok: true })),
  selectActiveVariationAction: vi.fn(async () => ({ workout: null })),
  wrapUpActiveExerciseAction: vi.fn(async () => ({ ok: true })),
}));

afterEach(cleanup);

function exercise(
  id: string,
  name: string,
  setCount: number,
  supersetGroup: number | null,
): RunnerExercise {
  return {
    id,
    order: 0,
    supersetGroup,
    exerciseId: `${id}-exercise`,
    name,
    muscleGroup: "",
    note: "",
    variationId: null,
    variations: [],
    targetWeight: 10,
    targetReps: 10,
    perceivedEffort: null,
    nextWeight: null,
    nextReps: null,
    nextSets: null,
    wrappedUp: false,
    sets: Array.from({ length: setCount }, (_, index) => ({
      id: `${id}-set-${index + 1}`,
      setNumber: index + 1,
      weight: 10,
      reps: 10,
      completed: false,
    })),
  };
}

const workout: RunnerWorkout = {
  id: "active-1",
  planDayId: "day-1",
  planDayName: "Workout A",
  planName: "Gym Routine A/B",
  startedAt: "2026-09-06T10:00:00.000Z",
  note: "",
  exercises: [
    exercise("curl", "Dumbbell Curl", 2, 1),
    exercise("triceps", "Overhead Triceps", 2, 1),
    exercise("bug", "Dead Bug", 1, null),
  ],
};

describe("FitnessWorkoutRunner supersets", () => {
  it("alternates the paired exercises set by set, then rates them in turn", async () => {
    const user = userEvent.setup();
    render(<FitnessWorkoutRunner workout={workout} />);
    const heading = () => screen.getByRole("heading", { level: 2 });
    const confirm = () => user.click(screen.getByRole("button", { name: "Confirm set" }));

    // The rail groups the pair and letters its members.
    const rail = screen.getByLabelText("Superset");
    expect(within(rail).getByText(/Dumbbell Curl/)).toBeInTheDocument();
    expect(within(rail).getByText(/Overhead Triceps/)).toBeInTheDocument();
    expect(within(rail).getByText("A")).toBeInTheDocument();
    expect(within(rail).getByText("B")).toBeInTheDocument();

    expect(heading()).toHaveTextContent("Dumbbell Curl");
    expect(screen.getByText(/Superset A · with Overhead Triceps/)).toBeInTheDocument();
    expect(
      screen.getByText("No rest yet: Overhead Triceps set 1 comes straight after this one."),
    ).toBeInTheDocument();

    // A1 -> B1, not A2.
    await confirm();
    expect(heading()).toHaveTextContent("Overhead Triceps");
    expect(
      screen.getByText("Rest after this set, then back to Dumbbell Curl for set 2."),
    ).toBeInTheDocument();

    // B1 -> A2 -> B2.
    await confirm();
    expect(heading()).toHaveTextContent("Dumbbell Curl");
    expect(screen.getByText("2 / 2")).toBeInTheDocument();
    await confirm();
    expect(heading()).toHaveTextContent("Overhead Triceps");
    expect(
      screen.getByText("Last round. Rest, then rate the paired exercises."),
    ).toBeInTheDocument();

    // After the last round both members are rated before the next exercise.
    await confirm();
    expect(
      screen.getByRole("heading", { name: "Finish Dumbbell Curl" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Moderate/ }));
    await user.click(screen.getByRole("button", { name: "Save and continue" }));
    expect(
      await screen.findByRole("heading", { name: "Finish Overhead Triceps" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Easy/ }));
    await user.click(screen.getByRole("button", { name: "Save and continue" }));

    // Only then does the plain exercise come up, with no superset hint.
    expect(
      await screen.findByRole("heading", { level: 2, name: "Dead Bug" }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Superset .* · with/)).not.toBeInTheDocument();
    expect(screen.queryByText(/No rest yet/)).not.toBeInTheDocument();
  });
});
