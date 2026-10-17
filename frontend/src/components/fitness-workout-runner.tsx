"use client";

import {
  Check,
  ChevronLeft,
  ChevronRight,
  CloudOff,
  Flag,
  Layers,
  Link2,
  Save,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState, useTransition } from "react";
import {
  completeWorkoutAction,
  saveActiveSetAction,
  saveActiveWorkoutNoteAction,
  selectActiveVariationAction,
  wrapUpActiveExerciseAction,
} from "@/app/actions";
import {
  NumberField,
  REPS_BOUNDS,
  SET_COUNT_BOUNDS,
  WEIGHT_BOUNDS,
} from "@/components/number-field";
import { SupersetBlocks } from "@/components/superset-blocks";
import {
  buildWorkoutSteps,
  exerciseWasPerformed,
  groupSupersets,
  nextOpenStep,
  resumeCursor,
  supersetBlockAt,
  supersetLetter,
  workoutIsFinishable,
} from "@/lib/fitness";

export type RunnerVariation = {
  id: string;
  name: string;
  note: string;
  order: number;
  recommendedWeight: number;
  recommendedReps: number;
  recommendedSets: number | null;
};

export type RunnerSet = {
  id: string;
  setNumber: number;
  weight: number;
  reps: number;
  completed: boolean;
};

export type RunnerExercise = {
  id: string;
  order: number;
  /** Consecutive exercises sharing a group alternate their sets. */
  supersetGroup: number | null;
  exerciseId: string;
  name: string;
  muscleGroup: string;
  note: string;
  variationId: string | null;
  variations: RunnerVariation[];
  targetWeight: number;
  targetReps: number;
  perceivedEffort: number | null;
  nextWeight: number | null;
  nextReps: number | null;
  nextSets: number | null;
  wrappedUp: boolean;
  sets: RunnerSet[];
};

export type RunnerWorkout = {
  id: string;
  planDayId: string;
  planDayName: string;
  planName: string;
  startedAt: string;
  note: string;
  exercises: RunnerExercise[];
};

const EFFORT_OPTIONS = [
  [1, "Easy"],
  [2, "Moderate"],
  [3, "Hard"],
] as const;

export function FitnessWorkoutRunner({ workout }: { workout: RunnerWorkout }) {
  const router = useRouter();
  const [exercises, setExercises] = useState(workout.exercises);
  const [note, setNote] = useState(workout.note);
  const [cursor, setCursor] = useState(() => resumeCursor(workout.exercises));
  const [error, setError] = useState<string | null>(null);
  const [isSyncing, startSync] = useTransition();
  const [isFinishing, startFinishing] = useTransition();

  const activeExercise = exercises[cursor.exerciseIndex];
  // Selecting a variation can change the set count, so keep the cursor in range.
  const activeStep = Math.min(cursor.step, activeExercise.sets.length);
  const isWrapUpStep = activeStep >= activeExercise.sets.length;
  const activeSet = isWrapUpStep ? null : activeExercise.sets[activeStep];

  const completedSets = exercises.reduce(
    (total, exercise) => total + exercise.sets.filter((set) => set.completed).length,
    0,
  );
  const totalSets = exercises.reduce(
    (total, exercise) => total + exercise.sets.length,
    0,
  );
  const canFinish = workoutIsFinishable(exercises);
  const pendingWrapUps = exercises.filter(
    (exercise) => !exercise.wrappedUp && exerciseWasPerformed(exercise),
  ).length;

  const setsLeft = totalSets - completedSets;
  const wrapUpHint = pendingWrapUps
    ? `${pendingWrapUps} exercise${pendingWrapUps === 1 ? " still needs" : "s still need"} a rating and next target.`
    : null;
  const finishHint =
    completedSets === 0
      ? "Log at least one set to finish this workout."
      : setsLeft > 0
        ? `${setsLeft} set${setsLeft === 1 ? "" : "s"} left to log. ${
            wrapUpHint ?? "Finishing now saves only what you logged."
          }`
        : (wrapUpHint ?? "All sets are logged and every next target is set.");

  // Supersets alternate their sets, so the walk is not simply exercise by
  // exercise: the step list is the single source of "what comes next".
  const blocks = groupSupersets(exercises);
  const flatSteps = buildWorkoutSteps(exercises);
  const currentStepIndex = flatSteps.findIndex(
    (entry) =>
      entry.exerciseIndex === cursor.exerciseIndex && entry.step === activeStep,
  );
  // Where confirming the current set leads: the next step that still has
  // something to do. An exercise closed early is skipped entirely.
  const nextStep = nextOpenStep(exercises, flatSteps, currentStepIndex);
  const activeBlock = supersetBlockAt(blocks, cursor.exerciseIndex);
  const activeSuperset =
    activeBlock && activeBlock.group !== null ? activeBlock : null;

  // What a superset asks for after this set: straight on to the partner, or a
  // rest before the next round.
  let supersetHint: string | null = null;
  if (
    activeSuperset &&
    !isWrapUpStep &&
    nextStep &&
    supersetBlockAt(blocks, nextStep.exerciseIndex) === activeSuperset
  ) {
    const partner = exercises[nextStep.exerciseIndex];
    const setNumber = nextStep.step + 1;
    if (nextStep.step >= partner.sets.length) {
      supersetHint = "Last round. Rest, then rate the paired exercises.";
    } else if (nextStep.exerciseIndex === cursor.exerciseIndex) {
      // The partner has no sets left, so the round is this exercise alone.
      supersetHint = `Rest after this set, then set ${setNumber} of this exercise.`;
    } else if (nextStep.exerciseIndex > cursor.exerciseIndex) {
      supersetHint = `No rest yet: ${partner.name} set ${setNumber} comes straight after this one.`;
    } else {
      supersetHint = `Rest after this set, then back to ${partner.name} for set ${setNumber}.`;
    }
  }

  // The exercise list as of the last render, for callbacks that resume after
  // an await and must not act on the list they were created with.
  const latestExercises = useRef(exercises);
  useEffect(() => {
    latestExercises.current = exercises;
  }, [exercises]);

  const noteTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => () => {
    if (noteTimer.current) clearTimeout(noteTimer.current);
  }, []);

  const failed = useCallback((caught: unknown, fallback: string) => {
    setError(caught instanceof Error ? caught.message : fallback);
  }, []);

  function updateExercise(id: string, patch: Partial<RunnerExercise>) {
    setExercises((current) =>
      current.map((exercise) =>
        exercise.id === id ? { ...exercise, ...patch } : exercise,
      ),
    );
  }

  function goTo(exerciseIndex: number, step: number) {
    setCursor({ exerciseIndex, step });
    setError(null);
  }

  function updateActiveSet(field: "weight" | "reps", value: number) {
    if (!activeSet) return;
    updateExercise(activeExercise.id, {
      sets: activeExercise.sets.map((set) =>
        set.id === activeSet.id ? { ...set, [field]: value } : set,
      ),
    });
  }

  function confirmSet() {
    if (!activeSet) return;
    const saved = { ...activeSet, completed: true };
    updateExercise(activeExercise.id, {
      sets: activeExercise.sets.map((set) => (set.id === saved.id ? saved : set)),
    });
    setError(null);
    // The set is written immediately; a closed window loses nothing.
    startSync(async () => {
      try {
        await saveActiveSetAction({
          activeSetId: saved.id,
          weight: saved.weight,
          reps: saved.reps,
          completed: true,
        });
      } catch (caught) {
        failed(caught, "This set could not be saved. Confirm it again.");
      }
    });
    // Inside a superset the next open step is the partner set, not this
    // exercise's next one; after the last round it is the wrap-up. When nothing
    // after this point is open (the set was logged out of order), fall back to
    // the first open step anywhere so the cursor never parks on finished work.
    const patched = exercises.map((exercise) =>
      exercise.id === activeExercise.id
        ? {
            ...exercise,
            sets: exercise.sets.map((set) => (set.id === saved.id ? saved : set)),
          }
        : exercise,
    );
    const next =
      nextOpenStep(patched, flatSteps, currentStepIndex) ??
      nextOpenStep(patched, flatSteps, -1);
    if (next) goTo(next.exerciseIndex, next.step);
  }

  function chooseVariation(variationId: string | null) {
    if (activeExercise.variationId === variationId) return;
    const targetId = activeExercise.id;
    setError(null);
    startSync(async () => {
      try {
        const result = await selectActiveVariationAction({
          activeExerciseId: targetId,
          variationId,
        });
        // Only this exercise was re-seeded on the server. The rest of the
        // snapshot may predate a set save still in flight, so it is left alone.
        const fresh = result?.workout?.exercises.find(
          (exercise) => exercise.id === targetId,
        );
        if (fresh) updateExercise(targetId, fresh);
      } catch (caught) {
        failed(caught, "That variation could not be selected.");
      }
    });
  }

  function saveWrapUp(draft: WrapUpDraft) {
    const targetId = activeExercise.id;
    setError(null);
    startSync(async () => {
      try {
        await wrapUpActiveExerciseAction({
          activeExerciseId: targetId,
          ...draft,
        });
        const patch = { ...draft, wrappedUp: true };
        updateExercise(targetId, patch);
        // Carry on from the first step still open, in performance order: inside
        // a superset that is the partner's wrap-up. An exercise closed early
        // stays closed; only unrated ones are next. Sets logged while the save
        // was in flight count, hence the latest list rather than the closure.
        const patched = latestExercises.current.map((exercise) =>
          exercise.id === targetId ? { ...exercise, ...patch } : exercise,
        );
        if (patched.some((exercise) => !exercise.wrappedUp)) {
          const next = resumeCursor(patched);
          goTo(next.exerciseIndex, next.step);
        }
      } catch (caught) {
        failed(caught, "This exercise could not be saved.");
      }
    });
  }

  function updateNote(value: string) {
    setNote(value);
    if (noteTimer.current) clearTimeout(noteTimer.current);
    noteTimer.current = setTimeout(() => {
      void saveActiveWorkoutNoteAction({
        activeWorkoutId: workout.id,
        note: value,
      }).catch(() => {
        setError("The workout note could not be saved.");
      });
    }, 700);
  }

  function finishWorkout() {
    if (!canFinish) return;
    setError(null);
    startFinishing(async () => {
      try {
        if (noteTimer.current) clearTimeout(noteTimer.current);
        await saveActiveWorkoutNoteAction({
          activeWorkoutId: workout.id,
          note,
        });
        await completeWorkoutAction({ activeWorkoutId: workout.id });
        router.replace("/fitness");
        router.refresh();
      } catch (caught) {
        failed(caught, "Unable to save this workout.");
      }
    });
  }

  function navigate(direction: -1 | 1) {
    const next = flatSteps[currentStepIndex + direction];
    if (next) goTo(next.exerciseIndex, next.step);
  }

  const activeVariation = activeExercise.variations.find(
    (variation) => variation.id === activeExercise.variationId,
  );

  function renderRailExercise(
    exercise: RunnerExercise,
    exerciseIndex: number,
    letter: string | null,
  ) {
    return (
      <div className="set-rail-exercise" key={exercise.id}>
        <span>
          {letter ? <b className="set-rail-letter">{letter}</b> : null}
          {exercise.name}
        </span>
        <div>
          {exercise.sets.map((set, setIndex) => {
            const isActive =
              exerciseIndex === cursor.exerciseIndex && setIndex === activeStep;
            // The exercise was closed before this set was logged.
            const isSkipped = !set.completed && exercise.wrappedUp;
            return (
              <button
                type="button"
                key={set.id}
                className={`set-rail-step${isActive ? " is-active" : ""}${set.completed ? " is-complete" : ""}${isSkipped ? " is-skipped" : ""}`}
                onClick={() => goTo(exerciseIndex, setIndex)}
                aria-label={`${exercise.name}, set ${set.setNumber}${set.completed ? ", complete" : isSkipped ? ", skipped" : ""}`}
              >
                {set.completed ? <Check size={13} /> : set.setNumber}
              </button>
            );
          })}
          <button
            type="button"
            className={`set-rail-step set-rail-wrap${
              exerciseIndex === cursor.exerciseIndex &&
              activeStep >= exercise.sets.length
                ? " is-active"
                : ""
            }${exercise.wrappedUp ? " is-complete" : ""}`}
            onClick={() => goTo(exerciseIndex, exercise.sets.length)}
            aria-label={`${exercise.name}, rating and next target${exercise.wrappedUp ? ", complete" : ""}`}
            title="Rating and next target"
          >
            <Flag size={12} />
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="workout-runner">
      <aside className="set-rail" aria-label="Workout set progress">
        <div className="set-rail-heading">
          <span>Set rail</span>
          <strong>
            {completedSets} / {totalSets}
          </strong>
        </div>
        <div className="set-rail-list">
          <SupersetBlocks
            items={exercises}
            frameClassName="set-rail-superset"
            label={
              <span className="set-rail-superset-label">
                <Link2 size={11} />
                Superset
              </span>
            }
            renderItem={(exercise, position) =>
              renderRailExercise(exercise, position.index, position.letter)
            }
          />
        </div>
      </aside>

      <section className="workout-stage">
        <div className="workout-stage-topline">
          <span>{workout.planName}</span>
          <span>{workout.planDayName}</span>
        </div>
        <div className="workout-current-heading">
          <div>
            <p>Current exercise</p>
            <h2>{activeExercise.name}</h2>
            {activeExercise.muscleGroup ? (
              <span className="tag">{activeExercise.muscleGroup}</span>
            ) : null}
            {activeSuperset ? (
              <span className="tag tag-superset">
                <Link2 size={11} />
                Superset {supersetLetter(cursor.exerciseIndex - activeSuperset.startIndex)}
                {" · with "}
                {activeSuperset.members
                  .filter((member) => member.id !== activeExercise.id)
                  .map((member) => member.name)
                  .join(" and ")}
              </span>
            ) : null}
          </div>
          <div className="workout-current-set">
            <span>{isWrapUpStep ? "Exercise" : "Set"}</span>
            <strong>
              {isWrapUpStep
                ? "Wrap up"
                : `${activeSet?.setNumber} / ${activeExercise.sets.length}`}
            </strong>
          </div>
        </div>

        {activeExercise.variations.length ? (
          <div
            className="variation-picker"
            role="group"
            aria-label="Which variation are you doing?"
          >
            <span>
              <Layers size={13} />
              Which variation are you doing?
            </span>
            <div className="variation-options">
              <button
                type="button"
                className={`variation-option${activeExercise.variationId === null ? " is-selected" : ""}`}
                aria-pressed={activeExercise.variationId === null}
                disabled={activeExercise.wrappedUp}
                onClick={() => chooseVariation(null)}
              >
                Standard
              </button>
              {activeExercise.variations.map((variation) => (
                <button
                  type="button"
                  key={variation.id}
                  className={`variation-option${activeExercise.variationId === variation.id ? " is-selected" : ""}`}
                  aria-pressed={activeExercise.variationId === variation.id}
                  disabled={activeExercise.wrappedUp}
                  onClick={() => chooseVariation(variation.id)}
                >
                  {variation.name}
                </button>
              ))}
            </div>
            <small>
              {activeVariation
                ? `${activeVariation.name} is pre-selected because you did the other one last time. Its weight and reps are tracked separately.`
                : "No variation selected — this logs against the exercise itself."}
            </small>
          </div>
        ) : null}

        <p className="workout-target">
          Started with a target of {activeExercise.targetWeight} kg x{" "}
          {activeExercise.targetReps}.
          {activeExercise.note ? ` ${activeExercise.note}` : ""}
        </p>
        {supersetHint ? (
          <p className="workout-superset-hint">{supersetHint}</p>
        ) : null}

        {isWrapUpStep ? (
          <WrapUpCard
            key={activeExercise.id}
            exercise={activeExercise}
            variationName={activeVariation?.name ?? null}
            isSaving={isSyncing}
            canGoBack={currentStepIndex > 0}
            canGoForward={currentStepIndex < flatSteps.length - 1}
            onNavigate={navigate}
            onSave={saveWrapUp}
          />
        ) : (
          <>
            <div className="workout-input-grid">
              {/* Keyed per set so the draft text resets when the cursor moves. */}
              <NumberField
                key={`${activeSet?.id}-weight`}
                label="Weight (kg)"
                value={activeSet?.weight ?? 0}
                bounds={WEIGHT_BOUNDS}
                step="0.25"
                onCommit={(next) => updateActiveSet("weight", next)}
              />
              <NumberField
                key={`${activeSet?.id}-reps`}
                label="Reps"
                value={activeSet?.reps ?? 1}
                bounds={REPS_BOUNDS}
                onCommit={(next) => updateActiveSet("reps", next)}
              />
            </div>

            <div className="workout-controls">
              <button
                type="button"
                className="icon-button"
                onClick={() => navigate(-1)}
                disabled={currentStepIndex <= 0}
                aria-label="Previous set"
                title="Previous set"
              >
                <ChevronLeft size={18} />
              </button>
              <button
                type="button"
                className="button button-primary"
                onClick={confirmSet}
              >
                <Check size={17} />
                {activeSet?.completed ? "Update set" : "Confirm set"}
              </button>
              {activeExercise.sets.some((set) => !set.completed) ? (
                // Cutting an exercise short: jump straight to its wrap-up.
                <button
                  type="button"
                  className="button button-secondary"
                  onClick={() =>
                    goTo(cursor.exerciseIndex, activeExercise.sets.length)
                  }
                >
                  <Flag size={17} />
                  Finish this exercise
                </button>
              ) : null}
              <button
                type="button"
                className="icon-button"
                onClick={() => navigate(1)}
                disabled={currentStepIndex >= flatSteps.length - 1}
                aria-label="Next set"
                title="Next set"
              >
                <ChevronRight size={18} />
              </button>
            </div>
          </>
        )}

        <label className="workout-note">
          <span>Workout note</span>
          <textarea
            value={note}
            onChange={(event) => updateNote(event.target.value)}
            placeholder="Optional note for this session"
          />
        </label>

        {error ? (
          <div className="status status-error">
            <CloudOff size={16} />
            <span>{error}</span>
          </div>
        ) : null}

        <div className="workout-finish-row">
          <span>{finishHint}</span>
          <button
            type="button"
            className="button button-primary"
            disabled={!canFinish || isFinishing}
            onClick={finishWorkout}
          >
            {isFinishing ? <Save size={17} /> : <Flag size={17} />}
            {isFinishing
              ? "Saving workout..."
              : completedSets < totalSets
                ? "Finish early"
                : "Finish workout"}
          </button>
        </div>
        <p className="workout-autosave">
          {isSyncing
            ? "Saving..."
            : "This workout is saved as you go — close the tab and pick it up anywhere."}
        </p>
      </section>
    </div>
  );
}

type WrapUpDraft = {
  perceivedEffort: number;
  nextWeight: number;
  nextReps: number;
  nextSets: number;
};

/**
 * The step that runs after the last set of an exercise. It is a separate,
 * keyed component so its draft resets cleanly when a different exercise comes
 * into view. Nothing here is auto-calculated: whatever is typed is exactly what
 * the next workout will open with.
 */
function WrapUpCard({
  exercise,
  variationName,
  isSaving,
  canGoBack,
  canGoForward,
  onNavigate,
  onSave,
}: {
  exercise: RunnerExercise;
  variationName: string | null;
  isSaving: boolean;
  canGoBack: boolean;
  canGoForward: boolean;
  onNavigate: (direction: -1 | 1) => void;
  onSave: (draft: WrapUpDraft) => void;
}) {
  // Seed from the last set actually logged, not the last one planned.
  const logged = exercise.sets.filter((set) => set.completed);
  const lastSet = logged[logged.length - 1];
  const loggedSets = logged.length;
  const unloggedSets = exercise.sets.length - loggedSets;
  const [perceivedEffort, setPerceivedEffort] = useState<number | null>(
    exercise.perceivedEffort,
  );
  const [nextWeight, setNextWeight] = useState(
    exercise.nextWeight ?? lastSet?.weight ?? exercise.targetWeight,
  );
  const [nextReps, setNextReps] = useState(
    exercise.nextReps ?? lastSet?.reps ?? exercise.targetReps,
  );
  const [nextSets, setNextSets] = useState(
    exercise.nextSets ?? exercise.sets.length,
  );

  return (
    <div className="wrap-up-card">
      <div className="wrap-up-heading">
        <h3>Finish {exercise.name}</h3>
        <p>
          Rate the exercise, then write the target you want next time. The next
          workout opens with exactly these numbers.
        </p>
        {unloggedSets ? (
          <p className="wrap-up-warning">
            {loggedSets
              ? `${unloggedSets} set${unloggedSets === 1 ? "" : "s"} left unlogged — ${unloggedSets === 1 ? "it won't" : "they won't"} be saved to this session.`
              : "No sets logged — this exercise won't be saved to this session."}
          </p>
        ) : null}
      </div>

      <div
        className="effort-selector"
        role="group"
        aria-label="How hard did this exercise feel?"
      >
        <span>How hard did this exercise feel?</span>
        <div className="effort-options">
          {EFFORT_OPTIONS.map(([effort, label]) => (
            <button
              type="button"
              key={effort}
              className={`effort-option${perceivedEffort === effort ? " is-selected" : ""}`}
              onClick={() => setPerceivedEffort(effort)}
              aria-pressed={perceivedEffort === effort}
            >
              <b>{effort}</b>
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="wrap-up-grid">
        <NumberField
          label="Next weight (kg)"
          value={nextWeight}
          bounds={WEIGHT_BOUNDS}
          step="0.25"
          onCommit={setNextWeight}
        />
        <NumberField
          label="Next reps"
          value={nextReps}
          bounds={REPS_BOUNDS}
          onCommit={setNextReps}
        />
        <NumberField
          label="Next sets"
          value={nextSets}
          bounds={SET_COUNT_BOUNDS}
          onCommit={setNextSets}
        />
      </div>

      <p className="wrap-up-destination">
        {perceivedEffort === null
          ? "Choose how hard this exercise felt to continue."
          : null}
        {perceivedEffort !== null ? (
          <>
            Saved to{" "}
            <strong>
              {variationName ? `${exercise.name} · ${variationName}` : exercise.name}
            </strong>
            .
          </>
        ) : null}
      </p>

      <div className="workout-controls">
        <button
          type="button"
          className="icon-button"
          onClick={() => onNavigate(-1)}
          disabled={!canGoBack}
          aria-label="Previous step"
          title="Previous step"
        >
          <ChevronLeft size={18} />
        </button>
        <button
          type="button"
          className="button button-primary"
          disabled={isSaving || perceivedEffort === null}
          onClick={() =>
            perceivedEffort !== null &&
            onSave({ perceivedEffort, nextWeight, nextReps, nextSets })
          }
        >
          <Check size={17} />
          {exercise.wrappedUp ? "Update next target" : "Save and continue"}
        </button>
        <button
          type="button"
          className="icon-button"
          onClick={() => onNavigate(1)}
          disabled={!canGoForward}
          aria-label="Next step"
          title="Next step"
        >
          <ChevronRight size={18} />
        </button>
      </div>
    </div>
  );
}
