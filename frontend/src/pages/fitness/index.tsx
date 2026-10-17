import Link from "next/link";
import { Archive, Dumbbell, Layers, Link2, Play, Plus, Trash2 } from "lucide-react";
import {
  addPlanExerciseAction,
  archiveExerciseAction,
  archiveExerciseVariationAction,
  archiveWorkoutPlanAction,
  createExerciseAction,
  createExerciseVariationAction,
  createPlanDayAction,
  createWorkoutPlanAction,
  discardActiveWorkoutAction,
  startWorkoutAction,
  updateExerciseAction,
  updateExerciseVariationAction,
} from "@/app/actions";
import { PlanExerciseRow } from "@/components/plan-exercise-row";
import { SubmitButton } from "@/components/submit-button";
import { SupersetBlocks } from "@/components/superset-blocks";
import { DatabaseNotice, PageHeader, StatCard, SummaryTape } from "@/components/ui";
import { formatTarget } from "@/lib/fitness";
import { useFitnessPageData } from "@/lib/queries";
import { useTitle } from "@/lib/use-title";
import { PageLoading } from "@/pages/not-found";

function StartSessionButton({
  planDayId,
  label,
  className = "button button-primary",
}: {
  planDayId: string;
  label: string;
  className?: string;
}) {
  return (
    <form action={startWorkoutAction}>
      <input type="hidden" name="planDayId" value={planDayId} />
      <SubmitButton className={className} pendingText="Opening...">
        <Play size={16} />
        {label}
      </SubmitButton>
    </form>
  );
}

export default function FitnessPage() {
  useTitle("Fitness");
  const { data, loading, unavailable } = useFitnessPageData();
  if (loading) return <PageLoading />;
  const nextPlanDay = data.plans
    .flatMap((plan) =>
      plan.days.map((day) => ({ ...day, planName: plan.name })),
    )
    .find((day) => day.id === data.nextPlanDayId);
  const totalVolume = data.workouts.reduce((sum, workout) => sum + workout.volume, 0);
  const activeWorkoutFor = (planDayId: string) =>
    data.activeWorkouts.find((active) => active.planDayId === planDayId);
  const nextPlanDayActive = nextPlanDay ? activeWorkoutFor(nextPlanDay.id) : undefined;

  return (
    <div lang="en" className="contents">
      <PageHeader
        eyebrow="Training log"
        title="Fitness"
        description="Reusable training plans, live set targets, and a record of every completed set."
        actions={
          <a href="#exercise-library" className="button button-primary">
            <Plus size={17} />
            New exercise
          </a>
        }
      />
      {unavailable ? <DatabaseNotice /> : null}

      <SummaryTape>
        <StatCard
          label="Exercises"
          value={String(data.exercises.length)}
          detail="Active library"
        />
        <StatCard
          label="Plan sessions"
          value={String(data.plans.reduce((sum, plan) => sum + plan.days.length, 0))}
          detail="Across active plans"
          tone="blue"
        />
        <StatCard
          label="Workouts completed"
          value={String(data.workouts.length)}
          detail="Latest 50 sessions"
          tone="amber"
        />
        <StatCard
          label="Logged volume"
          value={`${Math.round(totalVolume).toLocaleString()} kg`}
          detail="From recorded working sets"
          tone="rust"
        />
      </SummaryTape>

      {data.activeWorkouts.length ? (
        <section className="panel resume-panel" aria-labelledby="resume-heading">
          <div className="panel-header">
            <div>
              <h2 id="resume-heading">Workout in progress</h2>
              <p>
                These sessions were started but not finished. They are stored on
                the server, so you can carry on from any device.
              </p>
            </div>
          </div>
          <div className="resume-list">
            {data.activeWorkouts.map((active) => (
              <article className="resume-row" key={active.id}>
                <div>
                  <strong>
                    {active.planName} / {active.planDayName}
                  </strong>
                  <span>
                    Started {new Date(active.startedAt).toLocaleString()} ·{" "}
                    {active.completedSets} of {active.totalSets} sets logged
                  </span>
                </div>
                <div className="resume-actions">
                  <Link
                    className="button button-primary"
                    href={`/fitness/workouts/${active.id}`}
                  >
                    <Play size={16} />
                    Resume
                  </Link>
                  <form action={discardActiveWorkoutAction}>
                    <input type="hidden" name="id" value={active.id} />
                    <button
                      className="icon-button"
                      type="submit"
                      aria-label={`Discard ${active.planDayName}`}
                      title="Discard workout"
                    >
                      <Trash2 size={15} />
                    </button>
                  </form>
                </div>
              </article>
            ))}
          </div>
        </section>
      ) : null}

      <section className="fitness-next-section" aria-labelledby="next-workout-heading">
        <div>
          <p className="eyebrow">Up next</p>
          <h2 id="next-workout-heading">
            {nextPlanDay ? `${nextPlanDay.planName} / ${nextPlanDay.name}` : "Build your first session"}
          </h2>
          <p>
            {nextPlanDay
              ? `${nextPlanDay.planExercises.length} exercises are ready with each exercise's latest recommended target.`
              : "Add exercises, then place them into a session to begin guided workouts."}
          </p>
        </div>
        {nextPlanDay && nextPlanDay.planExercises.length ? (
          nextPlanDayActive ? (
            <Link
              className="button button-primary"
              href={`/fitness/workouts/${nextPlanDayActive.id}`}
            >
              <Play size={17} />
              Resume {nextPlanDay.name}
            </Link>
          ) : (
            <StartSessionButton
              planDayId={nextPlanDay.id}
              label={`Start ${nextPlanDay.name}`}
            />
          )
        ) : null}
      </section>

      <div className="fitness-workspace">
        <section className="panel" id="plan-builder">
          <div className="panel-header">
            <div>
              <h2>Plan builder</h2>
              <p>Plans hold the order and working-set count. The next weight and reps live on each exercise.</p>
            </div>
          </div>

          <form action={createWorkoutPlanAction} className="form-grid">
            <label className="field field-wide">
              <span>Plan name</span>
              <input name="name" placeholder="Strength A" required />
            </label>
            <label className="field field-wide">
              <span>Plan note</span>
              <input name="description" placeholder="Optional focus or schedule" />
            </label>
            <div className="field field-full items-start">
              <SubmitButton pendingText="Creating plan...">
                <Plus size={16} />
                Create plan
              </SubmitButton>
            </div>
          </form>

          <div className="fitness-plan-list">
            {data.plans.map((plan) => (
              <article className="fitness-plan" key={plan.id}>
                <div className="fitness-plan-heading">
                  <div>
                    <h3>{plan.name}</h3>
                    {plan.description ? <p>{plan.description}</p> : null}
                  </div>
                  <form action={archiveWorkoutPlanAction}>
                    <input type="hidden" name="id" value={plan.id} />
                    <button
                      type="submit"
                      className="icon-button"
                      aria-label={`Archive ${plan.name}`}
                      title="Archive plan"
                    >
                      <Archive size={15} />
                    </button>
                  </form>
                </div>

                <form action={createPlanDayAction} className="fitness-inline-form">
                  <input type="hidden" name="planId" value={plan.id} />
                  <label>
                    <span>Session name</span>
                    <input name="name" placeholder="Day 1 - Upper" required />
                  </label>
                  <SubmitButton className="button button-secondary" pendingText="Adding...">
                    <Plus size={15} />
                    Add session
                  </SubmitButton>
                </form>

                <div className="fitness-session-list">
                  {plan.days.map((day) => {
                    const active = activeWorkoutFor(day.id);
                    return (
                      <section className="fitness-session" key={day.id}>
                        <div className="fitness-session-heading">
                          <div>
                            <h4>{day.name}</h4>
                            <span>{day.planExercises.length} exercises</span>
                          </div>
                          {day.planExercises.length ? (
                            active ? (
                              <Link
                                className="button button-secondary"
                                href={`/fitness/workouts/${active.id}`}
                              >
                                <Play size={15} />
                                Resume {day.name}
                              </Link>
                            ) : (
                              <StartSessionButton
                                planDayId={day.id}
                                label={`Start ${day.name}`}
                                className="button button-secondary"
                              />
                            )
                          ) : null}
                        </div>

                        <div className="fitness-plan-exercises">
                          <SupersetBlocks
                            items={day.planExercises}
                            frameClassName="fitness-superset"
                            label={
                              <span className="fitness-superset-label">
                                <Link2 size={12} />
                                Superset · alternate sets, rest after each round
                              </span>
                            }
                            renderItem={(entry, position) => (
                              <PlanExerciseRow
                                key={entry.id}
                                entry={entry}
                                letter={position.letter}
                                canPair={position.index > 0}
                                paired={position.pairedWithPrevious}
                              />
                            )}
                          />
                        </div>

                        <form action={addPlanExerciseAction} className="fitness-add-exercise">
                          <input type="hidden" name="planDayId" value={day.id} />
                          <label>
                            <span>Exercise to add</span>
                            <select name="exerciseId" defaultValue="" required>
                              <option value="" disabled>Choose exercise</option>
                              {data.exercises.map((exercise) => (
                                <option value={exercise.id} key={exercise.id}>
                                  {exercise.name}
                                </option>
                              ))}
                            </select>
                          </label>
                          <label>
                            <span>Working sets</span>
                            <input name="setCount" type="number" min="1" max="20" defaultValue="3" required />
                          </label>
                          <label>
                            <span>Session note</span>
                            <input name="note" placeholder="Optional cue" />
                          </label>
                          <SubmitButton className="button button-secondary" pendingText="Adding...">
                            <Plus size={15} />
                            Add to session
                          </SubmitButton>
                          {day.planExercises.length ? (
                            <label className="fitness-add-superset">
                              <input type="checkbox" name="supersetWithPrevious" value="1" />
                              <span>Superset with the exercise above</span>
                            </label>
                          ) : null}
                        </form>
                      </section>
                    );
                  })}
                  {!plan.days.length ? <div className="empty-state">Add a session to this plan.</div> : null}
                </div>
              </article>
            ))}
            {!data.plans.length ? <div className="empty-state">Create a reusable plan, then add its sessions.</div> : null}
          </div>
        </section>

        <section className="panel" id="exercise-library">
          <div className="panel-header">
            <div>
              <h2>Exercise library</h2>
              <p>Recommended weight, reps and sets become the starting target for every new workout.</p>
            </div>
            <Dumbbell size={20} color="var(--training-rack)" />
          </div>

          <form action={createExerciseAction} className="form-grid">
            <label className="field field-wide">
              <span>Exercise name</span>
              <input name="name" placeholder="Barbell bench press" required />
            </label>
            <label className="field field-wide">
              <span>Muscle group</span>
              <input name="muscleGroup" placeholder="Chest" />
            </label>
            <label className="field">
              <span>Next weight (kg)</span>
              <input name="recommendedWeight" type="number" min="0" max="2000" step="0.25" defaultValue="0" required />
            </label>
            <label className="field">
              <span>Next reps</span>
              <input name="recommendedReps" type="number" min="1" max="200" defaultValue="10" required />
            </label>
            <label className="field field-wide">
              <span>Exercise note</span>
              <input name="note" placeholder="Optional setup cue" />
            </label>
            <div className="field field-full items-start">
              <SubmitButton pendingText="Adding exercise...">
                <Plus size={16} />
                Add exercise
              </SubmitButton>
            </div>
          </form>

          <div className="fitness-exercise-list">
            {data.exercises.map((exercise) => (
              <article className="fitness-exercise-row" key={exercise.id}>
                <div className="fitness-exercise-summary">
                  <div>
                    <strong>{exercise.name}</strong>
                    {exercise.muscleGroup ? <span className="tag">{exercise.muscleGroup}</span> : null}
                    {exercise.note ? <p>{exercise.note}</p> : null}
                  </div>
                  <div className="fitness-target">
                    <span>Next target</span>
                    <strong>{formatTarget(exercise.recommendedWeight, exercise.recommendedReps)}</strong>
                  </div>
                </div>

                {exercise.variations.length ? (
                  <ul className="variation-summary">
                    {exercise.variations.map((variation) => (
                      <li key={variation.id}>
                        <span>{variation.name}</span>
                        <strong>
                          {formatTarget(variation.recommendedWeight, variation.recommendedReps)}
                          {variation.recommendedSets ? ` · ${variation.recommendedSets} sets` : ""}
                        </strong>
                      </li>
                    ))}
                  </ul>
                ) : null}

                <details className="fitness-exercise-details">
                  <summary>Adjust target</summary>
                  <form action={updateExerciseAction} className="form-grid">
                    <input type="hidden" name="id" value={exercise.id} />
                    <label className="field field-wide">
                      <span>Exercise name</span>
                      <input name="name" defaultValue={exercise.name} required />
                    </label>
                    <label className="field field-wide">
                      <span>Muscle group</span>
                      <input name="muscleGroup" defaultValue={exercise.muscleGroup} />
                    </label>
                    <label className="field">
                      <span>Next weight (kg)</span>
                      <input name="recommendedWeight" type="number" min="0" max="2000" step="0.25" defaultValue={exercise.recommendedWeight} required />
                    </label>
                    <label className="field">
                      <span>Next reps</span>
                      <input name="recommendedReps" type="number" min="1" max="200" defaultValue={exercise.recommendedReps} required />
                    </label>
                    <label className="field">
                      <span>Next sets</span>
                      <input name="recommendedSets" type="number" min="1" max="20" defaultValue={exercise.recommendedSets ?? ""} placeholder="Plan default" />
                    </label>
                    <label className="field field-wide">
                      <span>Exercise note</span>
                      <input name="note" defaultValue={exercise.note} />
                    </label>
                    <div className="field field-full items-start">
                      <SubmitButton className="button button-secondary" pendingText="Saving...">Save target</SubmitButton>
                    </div>
                  </form>
                  <form action={archiveExerciseAction} className="fitness-archive-form">
                    <input type="hidden" name="id" value={exercise.id} />
                    <button
                      className="icon-button"
                      type="submit"
                      aria-label={`Archive ${exercise.name}`}
                      title="Archive exercise"
                    >
                      <Archive size={15} />
                    </button>
                    <span>Archive exercise</span>
                  </form>
                </details>

                <details className="fitness-exercise-details variation-editor">
                  <summary>
                    <Layers size={13} /> Variations ({exercise.variations.length})
                  </summary>
                  <p className="variation-hint">
                    Same movement, different angle — incline and flat chest press,
                    for example. Each variation keeps its own next target and history,
                    and the runner alternates them automatically.
                  </p>

                  {exercise.variations.map((variation) => (
                    <form
                      action={updateExerciseVariationAction}
                      className="form-grid variation-form"
                      key={variation.id}
                    >
                      <input type="hidden" name="id" value={variation.id} />
                      <label className="field field-wide">
                        <span>Variation name</span>
                        <input name="name" defaultValue={variation.name} required />
                      </label>
                      <label className="field field-wide">
                        <span>Variation note</span>
                        <input name="note" defaultValue={variation.note} />
                      </label>
                      <label className="field">
                        <span>Next weight (kg)</span>
                        <input name="recommendedWeight" type="number" min="0" max="2000" step="0.25" defaultValue={variation.recommendedWeight} required />
                      </label>
                      <label className="field">
                        <span>Next reps</span>
                        <input name="recommendedReps" type="number" min="1" max="200" defaultValue={variation.recommendedReps} required />
                      </label>
                      <label className="field">
                        <span>Next sets</span>
                        <input name="recommendedSets" type="number" min="1" max="20" defaultValue={variation.recommendedSets ?? ""} placeholder="Plan default" />
                      </label>
                      <div className="field field-full items-start variation-form-actions">
                        <SubmitButton className="button button-secondary" pendingText="Saving...">
                          Save {variation.name}
                        </SubmitButton>
                        <button
                          className="icon-button"
                          type="submit"
                          formAction={archiveExerciseVariationAction}
                          aria-label={`Archive ${variation.name}`}
                          title="Archive variation"
                        >
                          <Archive size={14} />
                        </button>
                      </div>
                    </form>
                  ))}

                  <form action={createExerciseVariationAction} className="fitness-inline-form">
                    <input type="hidden" name="exerciseId" value={exercise.id} />
                    <label>
                      <span>New variation</span>
                      <input name="name" placeholder="Incline" required />
                    </label>
                    <SubmitButton className="button button-secondary" pendingText="Adding...">
                      <Plus size={15} />
                      Add variation
                    </SubmitButton>
                  </form>
                </details>
              </article>
            ))}
            {!data.exercises.length ? <div className="empty-state">Add your working exercises here.</div> : null}
          </div>
        </section>
      </div>

      <section className="panel fitness-history" aria-labelledby="workout-history-heading">
        <div className="panel-header">
          <div>
            <h2 id="workout-history-heading">Workout history</h2>
            <p>Each completed set is kept with the target that started the exercise.</p>
          </div>
        </div>
        {data.workouts.length ? (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Completed</th>
                  <th>Plan / session</th>
                  <th>Exercises</th>
                  <th className="numeric">Volume</th>
                  <th>Logged sets</th>
                </tr>
              </thead>
              <tbody>
                {data.workouts.map((workout) => (
                  <tr key={workout.id}>
                    <td>{new Date(workout.completedAt).toLocaleDateString()}</td>
                    <td>
                      <strong>{workout.planDay.planName}</strong>
                      <div className="meta">{workout.planDay.name}</div>
                    </td>
                    <td>{workout.exercises.length}</td>
                    <td className="numeric">{Math.round(workout.volume).toLocaleString()} kg</td>
                    <td>
                      <details className="fitness-history-details">
                        <summary>View sets</summary>
                        <div className="fitness-history-sets">
                          <SupersetBlocks
                            items={workout.exercises}
                            frameClassName="fitness-history-superset"
                            label={<em>Superset</em>}
                            renderItem={(entry, position) => (
                              <div key={entry.id}>
                                <strong>
                                  {position.letter ? `${position.letter} · ` : ""}
                                  {entry.exercise.name}
                                  {entry.variationName ? ` · ${entry.variationName}` : ""}
                                </strong>
                                <span>Target {formatTarget(entry.targetWeight, entry.targetReps)}</span>
                                <p>{entry.sets.map((set) => `${set.weight} kg x ${set.reps}`).join(" · ")}</p>
                              </div>
                            )}
                          />
                        </div>
                      </details>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty-state">Completed workouts will appear here.</div>
        )}
      </section>
    </div>
  );
}
