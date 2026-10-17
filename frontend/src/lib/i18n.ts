import { useSyncExternalStore } from "react";

export type Locale = "ka" | "en";

const dictionaries = {
  ka: {
    appName: "ფინანსური ჟურნალი",
    dashboard: "მიმოხილვა",
    expenses: "ხარჯები",
    obligations: "ვალდებულებები",
    accounts: "ანგარიშები",
    categories: "კატეგორიები",
    imports: "იმპორტი",
    settings: "პარამეტრები",
    logout: "გასვლა",
    add: "დამატება",
    save: "შენახვა",
    date: "თარიღი",
    amount: "თანხა",
    category: "კატეგორია",
    note: "შენიშვნა",
    recipient: "მიმღები",
    noData: "ჩანაწერები ჯერ არ არის",
    databaseUnavailable: "მონაცემთა ბაზასთან კავშირი არ არის",
    allTime: "სრული პერიოდი",
  },
  en: {
    appName: "Financial Ledger",
    dashboard: "Overview",
    expenses: "Expenses",
    obligations: "Obligations",
    accounts: "Accounts",
    categories: "Categories",
    imports: "Import",
    settings: "Settings",
    logout: "Log out",
    add: "Add",
    save: "Save",
    date: "Date",
    amount: "Amount",
    category: "Category",
    note: "Note",
    recipient: "Recipient",
    noData: "No records yet",
    databaseUnavailable: "Database connection is unavailable",
    allTime: "All time",
  },
} as const;

/** Georgian is the default; the choice lives in the same cookie the Next.js app used. */
const COOKIE = "ledger-locale";
const listeners = new Set<() => void>();

function readLocale(): Locale {
  const value = document.cookie
    .split("; ")
    .find((part) => part.startsWith(`${COOKIE}=`))
    ?.slice(COOKIE.length + 1);
  return value === "en" ? "en" : "ka";
}

let current: Locale = typeof document === "undefined" ? "ka" : readLocale();

export function getLocale(): Locale {
  return current;
}

export function setLocale(locale: Locale) {
  current = locale;
  document.cookie = `${COOKIE}=${locale}; path=/; max-age=${60 * 60 * 24 * 365}; samesite=lax`;
  document.documentElement.lang = locale;
  listeners.forEach((listener) => listener());
}

export function useLocale(): Locale {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => current,
    () => "ka" as Locale,
  );
}

export function useDictionary() {
  const locale = useLocale();
  return { locale, t: dictionaries[locale] };
}
