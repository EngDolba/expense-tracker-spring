"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Building2,
  Dumbbell,
  FileSpreadsheet,
  FolderTree,
  LayoutDashboard,
  Landmark,
  ReceiptText,
} from "lucide-react";
import type { Locale } from "@/lib/i18n";

const nav = [
  { href: "/dashboard", icon: LayoutDashboard, ka: "მიმოხილვა", en: "Overview" },
  { href: "/expenses", icon: ReceiptText, ka: "ხარჯები", en: "Expenses" },
  { href: "/obligations", icon: Building2, ka: "ვალდებულებები", en: "Obligations" },
  { href: "/accounts", icon: Landmark, ka: "ანგარიშები", en: "Accounts" },
  { href: "/categories", icon: FolderTree, ka: "კატეგორიები", en: "Categories" },
  { href: "/imports", icon: FileSpreadsheet, ka: "იმპორტი", en: "Import" },
  { href: "/fitness", icon: Dumbbell, ka: "ფიტნესი", en: "Fitness" },
] as const;

const financeNav = nav.slice(0, 6);
const trainingNav = nav.slice(6);

export function AppNav({ locale }: { locale: Locale }) {
  const pathname = usePathname();
  // Nested routes (/fitness/workouts/[id]) still light up their section.
  const isCurrent = (href: string) =>
    pathname === href || pathname.startsWith(`${href}/`);

  const renderItem = (item: (typeof nav)[number]) => (
    <Link
      href={item.href}
      key={item.href}
      className="nav-link"
      aria-current={isCurrent(item.href) ? "page" : undefined}
    >
      <item.icon size={18} />
      <span>{item[locale]}</span>
    </Link>
  );

  return (
    <nav className="nav-list" aria-label="Main navigation">
      <div className="nav-group">
        <span className="nav-group-label">{locale === "ka" ? "ფინანსები" : "Finance"}</span>
        {financeNav.map(renderItem)}
      </div>
      <div className="nav-group nav-group-training">
        <span className="nav-group-label">{locale === "ka" ? "ვარჯიში" : "Training"}</span>
        {trainingNav.map(renderItem)}
      </div>
    </nav>
  );
}
