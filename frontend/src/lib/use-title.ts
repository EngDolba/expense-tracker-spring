import { useEffect } from "react";

/** The Next.js metadata title template: "Expenses · Financial Ledger". */
export function useTitle(title: string) {
  useEffect(() => {
    document.title = `${title} · Financial Ledger`;
  }, [title]);
}
