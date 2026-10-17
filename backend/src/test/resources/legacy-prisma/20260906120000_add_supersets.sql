-- Superset grouping. Consecutive plan slots that share a group are performed
-- together (A1, B1, A2, B2, ...); the group is copied onto the active workout
-- and into history so a logged session keeps the pairing it was done with.
--
-- IF NOT EXISTS: these columns were added to the production database ahead of
-- this migration being deployed, so applying it there must be a no-op.

-- AlterTable
ALTER TABLE "PlanExercise" ADD COLUMN IF NOT EXISTS "supersetGroup" INTEGER;

-- AlterTable
ALTER TABLE "ActiveWorkoutExercise" ADD COLUMN IF NOT EXISTS "supersetGroup" INTEGER;

-- AlterTable
ALTER TABLE "WorkoutExercise" ADD COLUMN IF NOT EXISTS "supersetGroup" INTEGER;
