import { expect, test, type Page } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Username").fill("tester");
  await page.getByLabel("Password").fill("test-password");
  await page.getByRole("button", { name: /sign in/i }).click();
  await expect(page).toHaveURL(/\/dashboard/);
});

async function buildSession(
  page: Page,
  names: { exerciseName: string; planName: string; sessionName: string },
  setCount: string,
) {
  await page.goto("/fitness");
  await page.getByLabel("Exercise name").first().fill(names.exerciseName);
  await page.getByLabel("Muscle group").first().fill("Chest");
  await page.getByLabel("Next weight (kg)").first().fill("60");
  await page.getByLabel("Next reps").first().fill("8");
  await page.getByRole("button", { name: "Add exercise" }).click();
  await expect(
    page.locator(".fitness-exercise-row").filter({ hasText: names.exerciseName }),
  ).toBeVisible();

  await page.getByLabel("Plan name").fill(names.planName);
  await page.getByRole("button", { name: "Create plan" }).click();
  const plan = page.locator(".fitness-plan").filter({ hasText: names.planName });
  await expect(plan).toBeVisible();

  await plan.getByLabel("Session name").fill(names.sessionName);
  await plan.getByRole("button", { name: "Add session" }).click();
  const session = plan
    .locator(".fitness-session")
    .filter({ hasText: names.sessionName });
  await expect(session).toBeVisible();

  await session
    .getByLabel("Exercise to add")
    .selectOption({ label: names.exerciseName });
  await session.getByLabel("Working sets").fill(setCount);
  await session.getByRole("button", { name: "Add to session" }).click();
  await expect(
    session.locator(".fitness-plan-exercise").filter({ hasText: names.exerciseName }),
  ).toBeVisible();
  return session;
}

test("logs a workout, rates each exercise, and stores the hand-entered next target", async ({
  page,
}, testInfo) => {
  const suffix = `${testInfo.project.name}-${Date.now()}`;
  const names = {
    exerciseName: `E2E Bench ${suffix}`,
    planName: `E2E Strength ${suffix}`,
    sessionName: `E2E Upper ${suffix}`,
  };
  const session = await buildSession(page, names, "2");

  await session.getByRole("button", { name: `Start ${names.sessionName}` }).click();
  await expect(page).toHaveURL(/\/fitness\/workouts\/[^/]+$/);
  await expect(page.getByRole("heading", { name: names.exerciseName })).toBeVisible();

  await page.getByLabel("Weight (kg)").fill("62.5");
  await page.getByLabel("Reps").fill("7");
  await page.getByRole("button", { name: "Confirm set" }).click();
  await page.getByLabel("Weight (kg)").fill("65");
  await page.getByLabel("Reps").fill("6");
  await page.getByRole("button", { name: "Confirm set" }).click();

  // The end-of-exercise step must appear on its own instead of being skipped.
  await expect(
    page.getByRole("heading", { name: `Finish ${names.exerciseName}` }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Finish workout" })).toBeDisabled();

  await page.getByRole("button", { name: /^2\s*Moderate$/ }).click();
  await page.getByLabel("Next weight (kg)").fill("67.5");
  await page.getByLabel("Next reps").fill("6");
  await page.getByLabel("Next sets").fill("4");
  await page.getByRole("button", { name: "Save and continue" }).click();

  await expect(
    page.getByText("All sets are logged and every next target is set."),
  ).toBeVisible();
  await page.getByRole("button", { name: "Finish workout" }).click();

  await expect(page).toHaveURL(/\/fitness$/);
  const exercise = page
    .locator(".fitness-exercise-row")
    .filter({ hasText: names.exerciseName });
  await expect(exercise).toContainText("67.5 kg x 6");
  await expect(page.getByRole("heading", { name: "Workout history" })).toBeVisible();
  await expect(page.getByText(names.sessionName).last()).toBeVisible();
});

test("keeps a started workout after the page is closed and reopened", async ({
  page,
}, testInfo) => {
  const suffix = `${testInfo.project.name}-resume-${Date.now()}`;
  const names = {
    exerciseName: `E2E Row ${suffix}`,
    planName: `E2E Resume ${suffix}`,
    sessionName: `E2E Pull ${suffix}`,
  };
  const session = await buildSession(page, names, "3");

  await session.getByRole("button", { name: `Start ${names.sessionName}` }).click();
  await expect(page).toHaveURL(/\/fitness\/workouts\/[^/]+$/);
  const workoutUrl = page.url();

  await page.getByLabel("Weight (kg)").fill("55");
  await page.getByLabel("Reps").fill("9");
  await page.getByRole("button", { name: "Confirm set" }).click();
  await expect(page.getByText("1 / 3")).toBeVisible();

  // Leaving and coming back is the same as closing the window on another device.
  await page.goto("/dashboard");
  await page.goto("/fitness");
  await expect(page.getByRole("heading", { name: "Workout in progress" })).toBeVisible();
  // Scoped to this test's session: other tests may leave workouts in progress.
  const resume = page.locator(".resume-row").filter({ hasText: names.sessionName });
  await expect(resume.getByText("1 of 3 sets logged")).toBeVisible();

  await resume.getByRole("link", { name: "Resume" }).click();
  await expect(page).toHaveURL(workoutUrl);
  await expect(page.getByText("1 / 3")).toBeVisible();
  // The runner resumes on the first unlogged set.
  await expect(page.getByText("2 sets left to log.")).toBeVisible();
  // Stepping back shows the set logged before the page was left.
  await page.getByRole("button", { name: "Previous set" }).click();
  await expect(page.getByLabel("Weight (kg)")).toHaveValue("55");
  await expect(page.getByLabel("Reps")).toHaveValue("9");
});

test("finishes a workout early and stores only the sets that were logged", async ({
  page,
}, testInfo) => {
  const suffix = `${testInfo.project.name}-early-${Date.now()}`;
  const names = {
    exerciseName: `E2E Squat ${suffix}`,
    planName: `E2E Short ${suffix}`,
    sessionName: `E2E Legs ${suffix}`,
  };
  const session = await buildSession(page, names, "3");

  await session.getByRole("button", { name: `Start ${names.sessionName}` }).click();
  await expect(page).toHaveURL(/\/fitness\/workouts\/[^/]+$/);

  await page.getByLabel("Weight (kg)").fill("70");
  await page.getByLabel("Reps").fill("5");
  await page.getByRole("button", { name: "Confirm set" }).click();
  await expect(page.getByText("1 / 3")).toBeVisible();

  // Cutting the exercise short jumps straight to its rating step.
  await page.getByRole("button", { name: "Finish this exercise" }).click();
  await expect(
    page.getByRole("heading", { name: `Finish ${names.exerciseName}` }),
  ).toBeVisible();
  await expect(
    page.getByText("2 sets left unlogged — they won't be saved to this session."),
  ).toBeVisible();

  await page.getByRole("button", { name: /^2\s*Moderate$/ }).click();
  await page.getByLabel("Next weight (kg)").fill("72.5");
  await page.getByLabel("Next reps").fill("5");
  await page.getByLabel("Next sets").fill("3");
  await page.getByRole("button", { name: "Save and continue" }).click();

  await expect(
    page.getByText("Finishing now saves only what you logged."),
  ).toBeVisible();
  await page.getByRole("button", { name: "Finish early" }).click();
  await expect(page).toHaveURL(/\/fitness$/);

  // The next target is the hand-entered one, even though the workout was cut short.
  const exercise = page
    .locator(".fitness-exercise-row")
    .filter({ hasText: names.exerciseName });
  await expect(exercise).toContainText("72.5 kg x 5");

  // Only the single confirmed set reached history.
  const historyRow = page
    .locator(".data-table tbody tr")
    .filter({ hasText: names.sessionName });
  await historyRow.getByText("View sets").click();
  const loggedSets = historyRow
    .locator(".fitness-history-sets > div")
    .filter({ hasText: names.exerciseName });
  await expect(loggedSets.locator("p")).toHaveText("70 kg x 5");
});

test("clears a weight field completely and restores it when left empty", async ({
  page,
}, testInfo) => {
  const suffix = `${testInfo.project.name}-clear-${Date.now()}`;
  const names = {
    exerciseName: `E2E Curl ${suffix}`,
    planName: `E2E Arms ${suffix}`,
    sessionName: `E2E Biceps ${suffix}`,
  };
  const session = await buildSession(page, names, "2");

  await session.getByRole("button", { name: `Start ${names.sessionName}` }).click();
  await expect(page).toHaveURL(/\/fitness\/workouts\/[^/]+$/);

  const weight = page.getByLabel("Weight (kg)");
  const reps = page.getByLabel("Reps");

  // Selecting and deleting must leave the field genuinely empty, not "0".
  await weight.click();
  await weight.press("ControlOrMeta+a");
  await weight.press("Backspace");
  await expect(weight).toHaveValue("");
  await weight.pressSequentially("42.5");
  await expect(weight).toHaveValue("42.5");

  // The same for reps, which used to snap back to 1.
  await reps.click();
  await reps.press("ControlOrMeta+a");
  await reps.press("Backspace");
  await expect(reps).toHaveValue("");
  await reps.pressSequentially("12");
  await expect(reps).toHaveValue("12");

  // Leaving a field empty restores the last good value instead of saving a blank.
  await weight.click();
  await weight.press("ControlOrMeta+a");
  await weight.press("Backspace");
  await reps.click();
  await expect(weight).toHaveValue("42.5");

  await page.getByRole("button", { name: "Confirm set" }).click();
  await page.getByRole("button", { name: "Previous set" }).click();
  await expect(weight).toHaveValue("42.5");
  await expect(reps).toHaveValue("12");
});

test("alternates exercise variations and tracks their targets separately", async ({
  page,
}, testInfo) => {
  const suffix = `${testInfo.project.name}-variation-${Date.now()}`;
  const names = {
    exerciseName: `E2E Press ${suffix}`,
    planName: `E2E Variation ${suffix}`,
    sessionName: `E2E Chest ${suffix}`,
  };
  const session = await buildSession(page, names, "1");

  const exerciseRow = page
    .locator(".fitness-exercise-row")
    .filter({ hasText: names.exerciseName });
  const variationsEditor = exerciseRow.locator(".variation-editor");
  const variationsToggle = variationsEditor.locator("> summary");
  // Adding a variation revalidates the page, but <details> keeps whatever open
  // state it already had, so clicking unconditionally would close it again.
  const openVariations = async () => {
    const isOpen = await variationsEditor.evaluate(
      (element) => (element as HTMLDetailsElement).open,
    );
    if (!isOpen) await variationsToggle.click();
  };

  await openVariations();
  await exerciseRow.getByLabel("New variation").fill("Flat");
  await exerciseRow.getByRole("button", { name: "Add variation" }).click();
  await expect(variationsToggle).toContainText("Variations (1)");
  await openVariations();
  await exerciseRow.getByLabel("New variation").fill("Incline");
  await exerciseRow.getByRole("button", { name: "Add variation" }).click();
  await expect(variationsToggle).toContainText("Variations (2)");

  await session.getByRole("button", { name: `Start ${names.sessionName}` }).click();
  const flat = page.getByRole("button", { name: "Flat", exact: true });
  await expect(flat).toHaveAttribute("aria-pressed", "true");

  await page.getByRole("button", { name: "Incline", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Incline", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");

  await page.getByLabel("Weight (kg)").fill("40");
  await page.getByLabel("Reps").fill("10");
  await page.getByRole("button", { name: "Confirm set" }).click();
  await page.getByRole("button", { name: /^1\s*Easy$/ }).click();
  await page.getByLabel("Next weight (kg)").fill("42.5");
  await page.getByLabel("Next reps").fill("10");
  await page.getByLabel("Next sets").fill("1");
  await page.getByRole("button", { name: "Save and continue" }).click();
  await page.getByRole("button", { name: "Finish workout" }).click();
  await expect(page).toHaveURL(/\/fitness$/);

  // Only the performed variation moves; the other keeps its own target.
  const summary = exerciseRow.locator(".variation-summary");
  await expect(summary).toContainText("42.5 kg x 10");
  await expect(summary.locator("li").filter({ hasText: "Flat" })).toContainText(
    "60 kg x 8",
  );

  // Next time the same slot opens on the variation that was not just performed.
  await session.getByRole("button", { name: `Start ${names.sessionName}` }).click();
  await expect(page.getByRole("button", { name: "Flat", exact: true })).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  await expect(page.getByText("Started with a target of 60 kg x 8.")).toBeVisible();
});

test("runs a superset by alternating sets between the paired exercises", async ({
  page,
}, testInfo) => {
  const suffix = `${testInfo.project.name}-superset-${Date.now()}`;
  const names = {
    exerciseName: `E2E Curl A ${suffix}`,
    planName: `E2E Superset ${suffix}`,
    sessionName: `E2E Arms ${suffix}`,
  };
  const partnerName = `E2E Pushdown B ${suffix}`;
  const session = await buildSession(page, names, "2");

  // A second exercise, added as a superset with the one above it.
  await page.getByLabel("Exercise name").first().fill(partnerName);
  await page.getByLabel("Next weight (kg)").first().fill("20");
  await page.getByLabel("Next reps").first().fill("12");
  await page.getByRole("button", { name: "Add exercise" }).click();
  await expect(
    page.locator(".fitness-exercise-row").filter({ hasText: partnerName }),
  ).toBeVisible();
  await session.getByLabel("Exercise to add").selectOption({ label: partnerName });
  await session.getByLabel("Working sets").fill("2");
  await session.getByLabel("Superset with the exercise above").check();
  await session.getByRole("button", { name: "Add to session" }).click();
  const superset = session.locator(".fitness-superset");
  await expect(superset).toContainText(names.exerciseName);
  await expect(superset).toContainText(partnerName);

  await session.getByRole("button", { name: `Start ${names.sessionName}` }).click();
  await expect(page).toHaveURL(/\/fitness\/workouts\/[^/]+$/);
  const heading = page.locator(".workout-current-heading h2");
  await expect(heading).toHaveText(names.exerciseName);
  await expect(page.getByText(`No rest yet: ${partnerName} set 1`)).toBeVisible();

  // A1 is followed by B1, not by A2.
  await page.getByRole("button", { name: "Confirm set" }).click();
  await expect(heading).toHaveText(partnerName);
  await expect(page.locator(".workout-current-set strong")).toHaveText("1 / 2");
  await page.getByRole("button", { name: "Confirm set" }).click();
  await expect(heading).toHaveText(names.exerciseName);
  await expect(page.locator(".workout-current-set strong")).toHaveText("2 / 2");
  await page.getByRole("button", { name: "Confirm set" }).click();
  await expect(heading).toHaveText(partnerName);
  await page.getByRole("button", { name: "Confirm set" }).click();

  // Both exercises are rated one after the other once the last round is done.
  await expect(
    page.getByRole("heading", { name: `Finish ${names.exerciseName}` }),
  ).toBeVisible();
  await page.getByRole("button", { name: /^2\s*Moderate$/ }).click();
  await page.getByRole("button", { name: "Save and continue" }).click();
  await expect(
    page.getByRole("heading", { name: `Finish ${partnerName}` }),
  ).toBeVisible();
  await page.getByRole("button", { name: /^1\s*Easy$/ }).click();
  await page.getByRole("button", { name: "Save and continue" }).click();
  await page.getByRole("button", { name: "Finish workout" }).click();
  await expect(page).toHaveURL(/\/fitness$/);

  // History keeps the pairing the session was done with.
  const historyRow = page
    .locator(".data-table tbody tr")
    .filter({ hasText: names.sessionName });
  await historyRow.getByText("View sets").click();
  await expect(historyRow.locator(".fitness-history-superset")).toContainText(
    partnerName,
  );

  // Splitting the pair from the plan turns it back into two plain slots.
  await session
    .getByRole("button", { name: `Split ${partnerName} from the exercise above` })
    .click();
  await expect(session.locator(".fitness-superset")).toHaveCount(0);
});
