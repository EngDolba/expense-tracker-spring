/**
 * The Next.js app's server actions, re-implemented as client functions over
 * the Spring Boot API with the same names and signatures. Form actions take
 * the submitted FormData (React 19 form actions); after any change every page's
 * data is refetched, which is what revalidatePath did. A failed form action
 * shows its error above the page instead of crashing it.
 */

import { api, ApiError, errorMessage, formJson } from "@/lib/api";
import { setLocale } from "@/lib/i18n";
import { navigate } from "@/lib/navigation";
import { notifyError } from "@/lib/notices";
import { queryClient } from "@/lib/query-client";
import type { RunnerWorkout } from "@/components/fitness-workout-runner";

async function refresh() {
  await queryClient.invalidateQueries();
}

/**
 * Runs a form action: reports a failure instead of throwing, refetches, and
 * then follows the path the action returned. The form action is a React
 * transition that keeps the current page on screen until it ends, so the
 * navigation happens after it.
 */
function formAction(
  run: (form: Record<string, string | null>, formData: FormData) => Promise<unknown>,
) {
  return async (formData: FormData) => {
    let next: unknown;
    try {
      next = await run(formJson(formData), formData);
    } catch (error) {
      notifyError(errorMessage(error));
    }
    await refresh();
    // Navigate once the action's transition has ended, so the URL and the
    // new page change together instead of the old page lingering under it.
    if (typeof next === "string") setTimeout(() => navigate(next), 0);
  };
}

/** For actions that end on another page: the path formAction navigates to. */
const goTo = (path: string) => path;

const id = (form: Record<string, string | null>, key = "id") => form[key] ?? "";

// ---- Session -----------------------------------------------------------------------

export type LoginState = { error?: string };

/** Only same-site paths: "//evil.example" and "/\evil" would leave the app. */
export function safeNext(next: string | null | undefined) {
  return next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\")
    ? next
    : "/dashboard";
}

export async function loginAction(_state: LoginState, formData: FormData): Promise<LoginState> {
  const username = String(formData.get("username") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  if (!username || !password) return { error: "Enter your username and password." };
  try {
    await api("/api/auth/login", { json: { username, password } });
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return { error: "The username or password is incorrect." };
    }
    return { error: errorMessage(error) };
  }
  await queryClient.invalidateQueries({ queryKey: ["session"] });
  navigate(safeNext(formData.get("next") as string | null), { replace: true });
  return {};
}

export async function logoutAction() {
  try {
    await api("/api/auth/logout", { method: "POST" });
  } finally {
    queryClient.clear();
    navigate("/login", { replace: true });
  }
}

export async function setLocaleAction(formData: FormData) {
  setLocale(formData.get("locale") === "en" ? "en" : "ka");
}

// ---- Categories and expenses ----------------------------------------------------------

export const createCategoryAction = formAction((form) => api("/api/categories", { json: form }));

export const updateCategoryAction = formAction((form) =>
  api(`/api/categories/${id(form)}`, { method: "PUT", json: { name: form.name, parentId: form.parentId } }),
);

export const archiveCategoryAction = formAction((form) =>
  api(`/api/categories/${id(form)}/archive`, { method: "POST" }),
);

export const createExpenseAction = formAction((form) => api("/api/expenses", { json: form }));

export const deleteExpenseAction = formAction((form) => api(`/api/expenses/${id(form)}`, { method: "DELETE" }));

export const createRuleAction = formAction((form) => api("/api/rules", { json: form }));

export const deleteRuleAction = formAction((form) => api(`/api/rules/${id(form)}`, { method: "DELETE" }));

// ---- Obligations ------------------------------------------------------------------------

export const createObligationPartyAction = formAction((form) => api("/api/obligations/parties", { json: form }));

export const createObligationCaseAction = formAction((form) => api("/api/obligations/cases", { json: form }));

export const createObligationEntryAction = formAction((form) => api("/api/obligations/entries", { json: form }));

export const deleteObligationEntryAction = formAction((form) =>
  api(`/api/obligations/entries/${id(form)}`, { method: "DELETE" }),
);

// ---- Accounts ------------------------------------------------------------------------------

export const createAccountAction = formAction((form) => api("/api/accounts", { json: form }));

export const createAccountEventAction = formAction((form) => api("/api/accounts/events", { json: form }));

export const createTransferAction = formAction((form) => api("/api/accounts/transfers", { json: form }));

export const reconcileAccountAction = formAction((form) => api("/api/accounts/reconcile", { json: form }));

export const upsertExchangeRateAction = formAction((form) =>
  api("/api/exchange-rates", { method: "PUT", json: form }),
);

// ---- Imports --------------------------------------------------------------------------------

/** Stages a workbook and opens its preview; a failure returns to the import page with the reason. */
export async function uploadWorkbookAction(formData: FormData) {
  try {
    const result = await api<{ id: string; duplicate: boolean }>("/api/imports", { form: formData });
    await refresh();
    navigate(`/imports/${result.id}${result.duplicate ? "?duplicate=1" : ""}`);
  } catch (error) {
    navigate(`/imports?error=${encodeURIComponent(errorMessage(error))}`);
  }
}

export const commitImportAction = formAction(async (form) => {
  await api(`/api/imports/${id(form, "batchId")}/commit`, { method: "POST" });
  return goTo(`/imports/${id(form, "batchId")}?committed=1`);
});

// ---- Fitness library and plans ---------------------------------------------------------------

export const createExerciseAction = formAction((form) => api("/api/fitness/exercises", { json: form }));

export const updateExerciseAction = formAction((form) =>
  api(`/api/fitness/exercises/${id(form)}`, { method: "PUT", json: form }),
);

export const archiveExerciseAction = formAction((form) =>
  api(`/api/fitness/exercises/${id(form)}/archive`, { method: "POST" }),
);

export const createExerciseVariationAction = formAction((form) => api("/api/fitness/variations", { json: form }));

export const updateExerciseVariationAction = formAction((form) =>
  api(`/api/fitness/variations/${id(form)}`, { method: "PUT", json: form }),
);

export const archiveExerciseVariationAction = formAction((form) =>
  api(`/api/fitness/variations/${id(form)}/archive`, { method: "POST" }),
);

export const createWorkoutPlanAction = formAction((form) => api("/api/fitness/plans", { json: form }));

export const archiveWorkoutPlanAction = formAction((form) =>
  api(`/api/fitness/plans/${id(form)}/archive`, { method: "POST" }),
);

export const createPlanDayAction = formAction((form) => api("/api/fitness/days", { json: form }));

export const addPlanExerciseAction = formAction((form) =>
  api("/api/fitness/plan-exercises", {
    json: { ...form, supersetWithPrevious: form.supersetWithPrevious === "1" },
  }),
);

export const togglePlanSupersetAction = formAction((form) =>
  api(`/api/fitness/plan-exercises/${id(form)}/superset`, { method: "POST" }),
);

export const removePlanExerciseAction = formAction((form) =>
  api(`/api/fitness/plan-exercises/${id(form)}`, { method: "DELETE" }),
);

// ---- The workout runner ------------------------------------------------------------------------

/** Opens the session's workout, resuming one already in progress. */
export const startWorkoutAction = formAction(async (form) => {
  const started = await api<{ id: string }>("/api/fitness/workouts", { json: { planDayId: form.planDayId } });
  return goTo(`/fitness/workouts/${started.id}`);
});

export const discardActiveWorkoutAction = formAction(async (form) => {
  await api(`/api/fitness/workouts/${id(form)}`, { method: "DELETE" });
  return goTo("/fitness");
});

export async function saveActiveSetAction(payload: {
  activeSetId: string;
  weight: number;
  reps: number;
  completed: boolean;
}) {
  const { activeSetId, ...set } = payload;
  return api<{ ok: true }>(`/api/fitness/active-sets/${activeSetId}`, { method: "PUT", json: set });
}

export async function selectActiveVariationAction(payload: {
  activeExerciseId: string;
  variationId: string | null;
}) {
  return api<{ workout: RunnerWorkout | null }>(
    `/api/fitness/active-exercises/${payload.activeExerciseId}/variation`,
    { method: "PUT", json: { variationId: payload.variationId } },
  );
}

export async function wrapUpActiveExerciseAction(payload: {
  activeExerciseId: string;
  perceivedEffort: number;
  nextWeight: number;
  nextReps: number;
  nextSets: number;
}) {
  const { activeExerciseId, ...wrapUp } = payload;
  return api<{ ok: true }>(`/api/fitness/active-exercises/${activeExerciseId}/wrap-up`, {
    method: "PUT",
    json: wrapUp,
  });
}

export async function saveActiveWorkoutNoteAction(payload: { activeWorkoutId: string; note: string }) {
  await api(`/api/fitness/workouts/${payload.activeWorkoutId}/note`, {
    method: "PUT",
    json: { note: payload.note },
  });
  return { ok: true as const };
}

export async function completeWorkoutAction(payload: { activeWorkoutId: string }) {
  const result = await api<{ id: string }>(`/api/fitness/workouts/${payload.activeWorkoutId}/complete`, {
    method: "POST",
  });
  await refresh();
  return result;
}
