import Link from "next/link";
import { ArrowLeft, Trash2 } from "lucide-react";
import { useParams } from "react-router";
import { discardActiveWorkoutAction } from "@/app/actions";
import { FitnessWorkoutRunner } from "@/components/fitness-workout-runner";
import { PageHeader } from "@/components/ui";
import { useActiveWorkout } from "@/lib/queries";
import { useTitle } from "@/lib/use-title";
import { NotFoundPage, PageLoading } from "@/pages/not-found";

export default function ActiveWorkoutPage() {
  useTitle("Guided workout");
  const { id = "" } = useParams();
  const { data: workout, loading } = useActiveWorkout(id);
  if (loading) return <PageLoading />;
  if (!workout) return <NotFoundPage />;

  const startedAt = new Date(workout.startedAt);

  return (
    <div lang="en" className="contents">
      <PageHeader
        eyebrow="Guided workout"
        title={workout.planDayName}
        description={`Started ${startedAt.toLocaleString()}. Every set is saved as you log it, so you can close this and pick up where you left off on any device.`}
        actions={
          <>
            <form action={discardActiveWorkoutAction}>
              <input type="hidden" name="id" value={workout.id} />
              <button className="button button-secondary" type="submit">
                <Trash2 size={16} />
                Discard workout
              </button>
            </form>
            <Link className="button button-secondary" href="/fitness">
              <ArrowLeft size={16} />
              Fitness
            </Link>
          </>
        }
      />
      <FitnessWorkoutRunner key={workout.id} workout={workout} />
    </div>
  );
}
