import { Link2, Trash2, Unlink2 } from "lucide-react";
import { removePlanExerciseAction, togglePlanSupersetAction } from "@/app/actions";
import { formatTarget } from "@/lib/fitness";

export type PlanExerciseRowEntry = {
  id: string;
  note: string;
  target: { weight: number; reps: number; setCount: number };
  exercise: { name: string; variations: Array<{ id: string; name: string }> };
};

/**
 * One slot of a plan session. `letter` is set when the slot belongs to a
 * superset; `paired` says whether it is linked to the slot above it, which is
 * what the link toggle flips. The first slot of a session has nothing above it
 * and gets no toggle.
 */
export function PlanExerciseRow({
  entry,
  letter,
  canPair,
  paired,
}: {
  entry: PlanExerciseRowEntry;
  letter: string | null;
  canPair: boolean;
  paired: boolean;
}) {
  return (
    <div className="fitness-plan-exercise">
      <div>
        <strong>
          {letter ? <span className="fitness-plan-exercise-letter">{letter}</span> : null}
          {entry.exercise.name}
        </strong>
        <span>
          {entry.target.setCount} sets · next {formatTarget(entry.target.weight, entry.target.reps)}
        </span>
        {entry.exercise.variations.length ? (
          <small>
            Variations: {entry.exercise.variations.map((variation) => variation.name).join(" / ")}
          </small>
        ) : null}
        {entry.note ? <small>{entry.note}</small> : null}
      </div>
      <div className="fitness-plan-exercise-actions">
        {canPair ? (
          <form action={togglePlanSupersetAction}>
            <input type="hidden" name="id" value={entry.id} />
            <button
              className={`icon-button${paired ? " is-linked" : ""}`}
              type="submit"
              aria-label={
                paired
                  ? `Split ${entry.exercise.name} from the exercise above`
                  : `Superset ${entry.exercise.name} with the exercise above`
              }
              title={paired ? "Split this superset" : "Superset with the exercise above"}
            >
              {paired ? <Unlink2 size={14} /> : <Link2 size={14} />}
            </button>
          </form>
        ) : null}
        <form action={removePlanExerciseAction}>
          <input type="hidden" name="id" value={entry.id} />
          <button
            className="icon-button"
            type="submit"
            aria-label={`Remove ${entry.exercise.name}`}
            title="Remove from session"
          >
            <Trash2 size={14} />
          </button>
        </form>
      </div>
    </div>
  );
}
