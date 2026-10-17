import Link from "next/link";
import { BookOpenText, LogOut } from "lucide-react";
import { logoutAction } from "@/app/actions";
import { AppNav } from "@/components/app-nav";
import { LanguageMenu } from "@/components/language-menu";
import type { Locale } from "@/lib/i18n";

export function AppShell({
  children,
  locale,
  username,
}: {
  children: React.ReactNode;
  locale: Locale;
  username: string;
}) {
  return (
    <div className="app-frame" lang={locale}>
      <aside className="sidebar">
        <Link href="/dashboard" className="brand">
          <span className="brand-mark">
            <BookOpenText size={19} />
          </span>
          <span>
            <strong>{locale === "ka" ? "ფინანსური" : "Financial"}</strong>
            <small>{locale === "ka" ? "ჟურნალი" : "Ledger"}</small>
          </span>
        </Link>
        <AppNav locale={locale} />
        <div className="sidebar-foot">
          <div className="user-block">
            <span className="user-avatar">{username.slice(0, 1).toUpperCase()}</span>
            <span className="min-w-0 flex-1 truncate">{username}</span>
            <LanguageMenu locale={locale} />
          </div>
          <form action={logoutAction}>
            <button type="submit" className="nav-link w-full">
              <LogOut size={17} />
              <span>{locale === "ka" ? "გასვლა" : "Log out"}</span>
            </button>
          </form>
        </div>
      </aside>
      <main className="content">
        <div className="mobile-actions">
          <LanguageMenu locale={locale} />
          <form action={logoutAction}>
            <button
              type="submit"
              className="icon-button"
              aria-label={locale === "ka" ? "გასვლა" : "Log out"}
            >
              <LogOut size={17} />
            </button>
          </form>
        </div>
        {children}
      </main>
    </div>
  );
}
