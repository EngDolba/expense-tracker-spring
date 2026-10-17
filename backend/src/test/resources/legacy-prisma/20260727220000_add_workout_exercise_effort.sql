ALTER TABLE "WorkoutExercise"
ADD COLUMN "perceivedEffort" INTEGER NOT NULL DEFAULT 2;

ALTER TABLE "WorkoutExercise"
ADD CONSTRAINT "WorkoutExercise_perceivedEffort_check"
CHECK ("perceivedEffort" BETWEEN 1 AND 3);
