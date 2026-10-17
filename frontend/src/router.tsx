import { createBrowserRouter, Navigate } from "react-router";
import { setUnauthenticatedHandler } from "@/lib/api";
import { setNavigator } from "@/lib/navigation";
import { queryClient } from "@/lib/query-client";
import { ProtectedLayout } from "@/pages/protected-layout";
import AccountsPage from "@/pages/accounts";
import CategoriesPage from "@/pages/categories";
import DashboardPage from "@/pages/dashboard";
import ExpensesPage from "@/pages/expenses";
import FitnessPage from "@/pages/fitness";
import ActiveWorkoutPage from "@/pages/fitness/workouts/detail";
import ImportsPage from "@/pages/imports";
import ImportDetailPage from "@/pages/imports/detail";
import LoginPage from "@/pages/login";
import { NotFoundPage } from "@/pages/not-found";
import ObligationsPage from "@/pages/obligations";

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  {
    element: <ProtectedLayout />,
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      { path: "dashboard", element: <DashboardPage /> },
      { path: "expenses", element: <ExpensesPage /> },
      { path: "obligations", element: <ObligationsPage /> },
      { path: "accounts", element: <AccountsPage /> },
      { path: "categories", element: <CategoriesPage /> },
      { path: "imports", element: <ImportsPage /> },
      { path: "imports/:id", element: <ImportDetailPage /> },
      { path: "fitness", element: <FitnessPage /> },
      { path: "fitness/workouts/:id", element: <ActiveWorkoutPage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);

// Actions navigate after their work is done; flushSync commits the new page in
// the same step as the URL change, so the old page never shows under the new URL.
setNavigator((to, options) => void router.navigate(to, { flushSync: true, ...options }));

// An expired session sends the user to sign in, then back to where they were.
setUnauthenticatedHandler(() => {
  const { pathname, search } = router.state.location;
  if (pathname === "/login") return;
  queryClient.clear();
  void router.navigate(`/login?next=${encodeURIComponent(pathname + search)}`, { replace: true });
});
