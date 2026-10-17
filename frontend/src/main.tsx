import "@fontsource-variable/space-grotesk";
import "@fontsource-variable/noto-sans-georgian";
import "@fontsource-variable/jetbrains-mono";
import "@/app/globals.css";

import { QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router";
import { getLocale } from "@/lib/i18n";
import { queryClient } from "@/lib/query-client";
import { router } from "@/router";

document.documentElement.lang = getLocale();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
);
