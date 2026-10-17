import { useLocation, useNavigate } from "react-router";
import { queryClient } from "@/lib/query-client";

/** next/navigation on top of React Router. `refresh` refetches every page's data. */
export function useRouter() {
  const navigate = useNavigate();
  return {
    push: (href: string) => navigate(href),
    replace: (href: string) => navigate(href, { replace: true }),
    refresh: () => void queryClient.invalidateQueries(),
    back: () => navigate(-1),
  };
}

export function usePathname() {
  return useLocation().pathname;
}
