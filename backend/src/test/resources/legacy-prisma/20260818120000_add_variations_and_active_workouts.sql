-- AlterTable
ALTER TABLE "Exercise" ADD COLUMN "recommendedSets" INTEGER;

-- AlterTable
ALTER TABLE "WorkoutExercise" ADD COLUMN "variationId" TEXT,
ADD COLUMN "nextWeight" DECIMAL(8,2),
ADD COLUMN "nextReps" INTEGER,
ADD COLUMN "nextSets" INTEGER;

-- CreateTable
CREATE TABLE "ExerciseVariation" (
    "id" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "note" TEXT NOT NULL DEFAULT '',
    "order" INTEGER NOT NULL DEFAULT 0,
    "archived" BOOLEAN NOT NULL DEFAULT false,
    "recommendedWeight" DECIMAL(8,2) NOT NULL DEFAULT 0,
    "recommendedReps" INTEGER NOT NULL DEFAULT 10,
    "recommendedSets" INTEGER,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ExerciseVariation_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ActiveWorkout" (
    "id" TEXT NOT NULL,
    "planDayId" TEXT NOT NULL,
    "startedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "note" TEXT NOT NULL DEFAULT '',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ActiveWorkout_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ActiveWorkoutExercise" (
    "id" TEXT NOT NULL,
    "activeWorkoutId" TEXT NOT NULL,
    "planExerciseId" TEXT,
    "exerciseId" TEXT NOT NULL,
    "variationId" TEXT,
    "order" INTEGER NOT NULL,
    "targetWeight" DECIMAL(8,2) NOT NULL,
    "targetReps" INTEGER NOT NULL,
    "perceivedEffort" INTEGER,
    "nextWeight" DECIMAL(8,2),
    "nextReps" INTEGER,
    "nextSets" INTEGER,
    "wrappedUpAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ActiveWorkoutExercise_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ActiveWorkoutSet" (
    "id" TEXT NOT NULL,
    "activeWorkoutExerciseId" TEXT NOT NULL,
    "setNumber" INTEGER NOT NULL,
    "weight" DECIMAL(8,2) NOT NULL,
    "reps" INTEGER NOT NULL,
    "completed" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ActiveWorkoutSet_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "ExerciseVariation_exerciseId_archived_order_idx" ON "ExerciseVariation"("exerciseId", "archived", "order");

-- CreateIndex
CREATE UNIQUE INDEX "ExerciseVariation_exerciseId_name_key" ON "ExerciseVariation"("exerciseId", "name");

-- CreateIndex
CREATE UNIQUE INDEX "ActiveWorkout_planDayId_key" ON "ActiveWorkout"("planDayId");

-- CreateIndex
CREATE INDEX "ActiveWorkout_startedAt_idx" ON "ActiveWorkout"("startedAt");

-- CreateIndex
CREATE INDEX "ActiveWorkoutExercise_exerciseId_idx" ON "ActiveWorkoutExercise"("exerciseId");

-- CreateIndex
CREATE INDEX "ActiveWorkoutExercise_variationId_idx" ON "ActiveWorkoutExercise"("variationId");

-- CreateIndex
CREATE INDEX "ActiveWorkoutExercise_planExerciseId_idx" ON "ActiveWorkoutExercise"("planExerciseId");

-- CreateIndex
CREATE UNIQUE INDEX "ActiveWorkoutExercise_activeWorkoutId_order_key" ON "ActiveWorkoutExercise"("activeWorkoutId", "order");

-- CreateIndex
CREATE UNIQUE INDEX "ActiveWorkoutSet_activeWorkoutExerciseId_setNumber_key" ON "ActiveWorkoutSet"("activeWorkoutExerciseId", "setNumber");

-- CreateIndex
CREATE INDEX "WorkoutExercise_variationId_idx" ON "WorkoutExercise"("variationId");

-- CreateIndex
CREATE INDEX "WorkoutExercise_planExerciseId_idx" ON "WorkoutExercise"("planExerciseId");

-- AddForeignKey
ALTER TABLE "ExerciseVariation" ADD CONSTRAINT "ExerciseVariation_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "Exercise"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "WorkoutExercise" ADD CONSTRAINT "WorkoutExercise_variationId_fkey" FOREIGN KEY ("variationId") REFERENCES "ExerciseVariation"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ActiveWorkout" ADD CONSTRAINT "ActiveWorkout_planDayId_fkey" FOREIGN KEY ("planDayId") REFERENCES "WorkoutPlanDay"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ActiveWorkoutExercise" ADD CONSTRAINT "ActiveWorkoutExercise_activeWorkoutId_fkey" FOREIGN KEY ("activeWorkoutId") REFERENCES "ActiveWorkout"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ActiveWorkoutExercise" ADD CONSTRAINT "ActiveWorkoutExercise_planExerciseId_fkey" FOREIGN KEY ("planExerciseId") REFERENCES "PlanExercise"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ActiveWorkoutExercise" ADD CONSTRAINT "ActiveWorkoutExercise_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "Exercise"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ActiveWorkoutExercise" ADD CONSTRAINT "ActiveWorkoutExercise_variationId_fkey" FOREIGN KEY ("variationId") REFERENCES "ExerciseVariation"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ActiveWorkoutSet" ADD CONSTRAINT "ActiveWorkoutSet_activeWorkoutExerciseId_fkey" FOREIGN KEY ("activeWorkoutExerciseId") REFERENCES "ActiveWorkoutExercise"("id") ON DELETE CASCADE ON UPDATE CASCADE;
