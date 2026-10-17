import { Languages } from "lucide-react";
import { setLocaleAction } from "@/app/actions";
import type { Locale } from "@/lib/i18n";

export function LanguageMenu({ locale }: { locale: Locale }) {
  const target = locale === "ka" ? "en" : "ka";
  return (
    <form action={setLocaleAction}>
      <input type="hidden" name="locale" value={target} />
      <button className="icon-button" type="submit" aria-label="Change language">
        <Languages size={17} />
        <span>{target.toUpperCase()}</span>
      </button>
    </form>
  );
}
