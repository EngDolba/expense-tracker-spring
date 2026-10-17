import { AlertTriangle, Database } from "lucide-react";
import { cn } from "@/lib/utils";

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  actions?: React.ReactNode;
}) {
  return (
    <header className="page-header">
      <div>
        {eyebrow ? <p className="eyebrow">{eyebrow}</p> : null}
        <h1>{title}</h1>
        {description ? <p>{description}</p> : null}
      </div>
      {actions ? <div className="page-actions">{actions}</div> : null}
    </header>
  );
}

export function StatCard({
  label,
  value,
  detail,
  tone = "green",
}: {
  label: string;
  value: string;
  detail?: string;
  tone?: "green" | "amber" | "rust" | "blue";
}) {
  return (
    <article className={cn("stat-card", `stat-${tone}`)}>
      <p>{label}</p>
      <strong>{value}</strong>
      {detail ? <span>{detail}</span> : null}
    </article>
  );
}

export function SummaryTape({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return <section className={cn("summary-tape", className)}>{children}</section>;
}

export function ActivitySpine({
  label,
  value,
  detail,
  tone = "green",
}: {
  label: string;
  value: string;
  detail?: string;
  tone?: "green" | "amber" | "rust" | "blue";
}) {
  return (
    <div className={cn("activity-spine", `spine-${tone}`)}>
      <span>{label}</span>
      <strong>{value}</strong>
      {detail ? <small>{detail}</small> : null}
    </div>
  );
}

export function DatabaseNotice() {
  return (
    <div className="status status-warning">
      <Database size={17} />
      <span>
        The ledger could not be loaded. The server or its database is
        unavailable; reload once it is back.
      </span>
    </div>
  );
}

export function WarningNotice({ children }: { children: React.ReactNode }) {
  return (
    <div className="status status-warning">
      <AlertTriangle size={17} />
      <span>{children}</span>
    </div>
  );
}
