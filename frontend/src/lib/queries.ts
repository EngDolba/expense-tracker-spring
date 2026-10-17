import { useQuery } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api";
import type {
  AccountsPage,
  CategoriesPage,
  DashboardData,
  ExpensesPage,
  FitnessPageData,
  ImportBatch,
  ImportDetail,
  ObligationsPage,
} from "@/lib/api-types";
import type { RunnerWorkout as ActiveWorkoutData } from "@/components/fitness-workout-runner";

/**
 * One page's data. Like the Next.js app's safeQuery, a failed load renders
 * the page with empty data and `unavailable` set, instead of crashing.
 */
function usePage<T>(key: readonly unknown[], path: string, fallback: T) {
  const query = useQuery({ queryKey: key, queryFn: () => api<T>(path) });
  return {
    data: query.data ?? fallback,
    loading: query.isPending,
    unavailable: query.isError,
    notFound: query.error instanceof ApiError && query.error.status === 404,
  };
}

export const useDashboardData = () =>
  usePage<DashboardData>(["dashboard"], "/api/dashboard", {
    expenseTotal: 0,
    incomeTotal: 0,
    expenseCount: 0,
    outstanding: 0,
    accountSummaries: [],
    accountGelTotal: 0,
    missingRates: [],
    categories: [],
    categoryTotals: {},
    trend: [],
  });

export const useExpensePageData = () =>
  usePage<ExpensesPage>(["expenses"], "/api/expenses", { entries: [], categories: [], rules: [] });

export const useObligationPageData = () =>
  usePage<ObligationsPage>(["obligations"], "/api/obligations", {
    parties: [],
    cases: [],
    entries: [],
    categories: [],
  });

export const useAccountPageData = () =>
  usePage<AccountsPage>(["accounts"], "/api/accounts", { accounts: [], rates: [] });

export const useCategoriesPageData = () =>
  usePage<CategoriesPage>(["categories"], "/api/categories", { categories: [], codes: [] });

export const useImportBatches = () => usePage<ImportBatch[]>(["imports"], "/api/imports", []);

export const useImportDetail = (id: string) =>
  usePage<ImportDetail | null>(["imports", id], `/api/imports/${id}`, null);

export const useFitnessPageData = () =>
  usePage<FitnessPageData>(["fitness"], "/api/fitness", {
    exercises: [],
    plans: [],
    nextPlanDayId: null,
    activeWorkouts: [],
    workouts: [],
  });

export const useActiveWorkout = (id: string) =>
  usePage<ActiveWorkoutData | null>(["fitness", "workouts", id], `/api/fitness/workouts/${id}`, null);
