import { useQuery } from "@tanstack/react-query";
import { X } from "lucide-react";
import { useEffect } from "react";
import { Navigate, Outlet, useLocation } from "react-router";
import { AppShell } from "@/components/app-shell";
import { api, ApiError } from "@/lib/api";
import { useLocale } from "@/lib/i18n";
import { clearNotices, dismissNotice, useNotices } from "@/lib/notices";

export function useSession() {
  return useQuery({
    queryKey: ["session"],
    queryFn: () => api<{ username: string }>("/api/auth/me"),
    staleTime: 5 * 60_000,
  });
}

/** Every page but /login: needs a session, renders inside the app shell. */
export function ProtectedLayout() {
  const session = useSession();
  const locale = useLocale();
  const location = useLocation();

  // Errors belong to the page they happened on.
  useEffect(() => clearNotices(), [location.pathname]);

  if (session.isPending) return <div className="app-loading" aria-busy="true" />;
  if (session.error instanceof ApiError && session.error.status === 401) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }

  return (
    <AppShell locale={locale} username={session.data?.username ?? ""}>
      <Notices />
      <Outlet />
    </AppShell>
  );
}

function Notices() {
  const notices = useNotices();
  if (!notices.length) return null;
  return (
    <div className="grid gap-2" role="alert">
      {notices.map((notice) => (
        <div className="status status-error" key={notice.id}>
          <span className="flex-1">{notice.message}</span>
          <button className="icon-button" type="button" aria-label="Dismiss" onClick={() => dismissNotice(notice.id)}>
            <X size={15} />
          </button>
        </div>
      ))}
    </div>
  );
}
