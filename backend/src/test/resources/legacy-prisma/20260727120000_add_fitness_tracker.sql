-- CreateTable
CREATE TABLE "Exercise" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "muscleGroup" TEXT NOT NULL DEFAULT '',
    "note" TEXT NOT NULL DEFAULT '',
    "recommendedWeight" DECIMAL(8,2) NOT NULL DEFAULT 0,
    "recommendedReps" INTEGER NOT NULL DEFAULT 10,
    "archived" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Exercise_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "WorkoutPlan" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL DEFAULT '',
    "order" INTEGER NOT NULL DEFAULT 0,
    "archived" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "WorkoutPlan_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "WorkoutPlanDay" (
    "id" TEXT NOT NULL,
    "planId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "order" INTEGER NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "WorkoutPlanDay_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PlanExercise" (
    "id" TEXT NOT NULL,
    "planDayId" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "order" INTEGER NOT NULL,
    "setCount" INTEGER NOT NULL,
    "note" TEXT NOT NULL DEFAULT '',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "PlanExercise_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "WorkoutSession" (
    "id" TEXT NOT NULL,
    "planDayId" TEXT NOT NULL,
    "startedAt" TIMESTAMP(3) NOT NULL,
    "completedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "note" TEXT NOT NULL DEFAULT '',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "WorkoutSession_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "WorkoutExercise" (
    "id" TEXT NOT NULL,
    "workoutId" TEXT NOT NULL,
    "planExerciseId" TEXT,
    "exerciseId" TEXT NOT NULL,
    "order" INTEGER NOT NULL,
    "targetWeight" DECIMAL(8,2) NOT NULL,
    "targetReps" INTEGER NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "WorkoutExercise_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "WorkoutSet" (
    "id" TEXT NOT NULL,
    "workoutExerciseId" TEXT NOT NULL,
    "setNumber" INTEGER NOT NULL,
    "weight" DECIMAL(8,2) NOT NULL,
    "reps" INTEGER NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "WorkoutSet_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "Exercise_name_key" ON "Exercise"("name");

-- CreateIndex
CREATE INDEX "Exercise_archived_name_idx" ON "Exercise"("archived", "name");

-- CreateIndex
CREATE UNIQUE INDEX "WorkoutPlan_name_key" ON "WorkoutPlan"("name");

-- CreateIndex
CREATE INDEX "WorkoutPlan_archived_order_idx" ON "WorkoutPlan"("archived", "order");

-- CreateIndex
CREATE UNIQUE INDEX "WorkoutPlanDay_planId_order_key" ON "WorkoutPlanDay"("planId", "order");

-- CreateIndex
CREATE INDEX "WorkoutPlanDay_planId_order_idx" ON "WorkoutPlanDay"("planId", "order");

-- CreateIndex
CREATE UNIQUE INDEX "PlanExercise_planDayId_exerciseId_key" ON "PlanExercise"("planDayId", "exerciseId");

-- CreateIndex
CREATE UNIQUE INDEX "PlanExercise_planDayId_order_key" ON "PlanExercise"("planDayId", "order");

-- CreateIndex
CREATE INDEX "PlanExercise_exerciseId_idx" ON "PlanExercise"("exerciseId");

-- CreateIndex
CREATE INDEX "WorkoutSession_planDayId_completedAt_idx" ON "WorkoutSession"("planDayId", "completedAt");

-- CreateIndex
CREATE INDEX "WorkoutSession_completedAt_idx" ON "WorkoutSession"("completedAt");

-- CreateIndex
CREATE INDEX "WorkoutExercise_workoutId_order_idx" ON "WorkoutExercise"("workoutId", "order");

-- CreateIndex
CREATE INDEX "WorkoutExercise_exerciseId_idx" ON "WorkoutExercise"("exerciseId");

-- CreateIndex
CREATE UNIQUE INDEX "WorkoutSet_workoutExerciseId_setNumber_key" ON "WorkoutSet"("workoutExerciseId", "setNumber");

-- AddForeignKey
ALTER TABLE "WorkoutPlanDay" ADD CONSTRAINT "WorkoutPlanDay_planId_fkey" FOREIGN KEY ("planId") REFERENCES "WorkoutPlan"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PlanExercise" ADD CONSTRAINT "PlanExercise_planDayId_fkey" FOREIGN KEY ("planDayId") REFERENCES "WorkoutPlanDay"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PlanExercise" ADD CONSTRAINT "PlanExercise_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "Exercise"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "WorkoutSession" ADD CONSTRAINT "WorkoutSession_planDayId_fkey" FOREIGN KEY ("planDayId") REFERENCES "WorkoutPlanDay"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "WorkoutExercise" ADD CONSTRAINT "WorkoutExercise_workoutId_fkey" FOREIGN KEY ("workoutId") REFERENCES "WorkoutSession"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "WorkoutExercise" ADD CONSTRAINT "WorkoutExercise_planExerciseId_fkey" FOREIGN KEY ("planExerciseId") REFERENCES "PlanExercise"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "WorkoutExercise" ADD CONSTRAINT "WorkoutExercise_exerciseId_fkey" FOREIGN KEY ("exerciseId") REFERENCES "Exercise"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "WorkoutSet" ADD CONSTRAINT "WorkoutSet_workoutExerciseId_fkey" FOREIGN KEY ("workoutExerciseId") REFERENCES "WorkoutExercise"("id") ON DELETE CASCADE ON UPDATE CASCADE;
