"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronRight } from "lucide-react";
import { buildCategoryTree, type CategoryNode } from "@/lib/categories";
import { formatMoney } from "@/lib/money";

type Totals = Record<string, { amount: number; count: number }>;
type RolledTotal = { amount: number; count: number };

function readExpanded(storageKey: string) {
  try {
    const stored = JSON.parse(localStorage.getItem(storageKey) ?? "[]");
    return new Set<string>(Array.isArray(stored) ? stored : []);
  } catch {
    return new Set<string>();
  }
}

export function BranchingLedger({
  categories,
  totals,
  storageKey,
  currency = "GEL",
  locale = "ka-GE",
  emptyLabel,
}: {
  categories: CategoryNode[];
  totals: Totals;
  storageKey: string;
  currency?: string;
  locale?: string;
  emptyLabel?: string;
}) {
  const tree = useMemo(() => buildCategoryTree(categories), [categories]);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const restoredKey = useRef<string | null>(null);

  useEffect(() => {
    if (restoredKey.current === storageKey) return;
    const restored = readExpanded(storageKey);
    restoredKey.current = storageKey;
    // This state mirrors the expansion set persisted outside React.
    setExpanded(restored);
  }, [storageKey]);

  const toggle = (id: string) => {
    const storageWasRestored = restoredKey.current === storageKey;
    restoredKey.current = storageKey;
    setExpanded((current) => {
      const next = new Set(
        storageWasRestored ? current : readExpanded(storageKey),
      );
      if (next.has(id)) next.delete(id);
      else next.add(id);
      localStorage.setItem(storageKey, JSON.stringify([...next]));
      return next;
    });
  };

  const rollups = useMemo(() => {
    const result = new Map<string, RolledTotal>();
    const visit = (node: (typeof tree)[number]): RolledTotal => {
      const own = totals[node.id] ?? { amount: 0, count: 0 };
      const total = node.children.reduce(
        (acc, child) => {
          const childTotal = visit(child);
          return {
            amount: acc.amount + childTotal.amount,
            count: acc.count + childTotal.count,
          };
        },
        { ...own },
      );
      result.set(node.id, total);
      return total;
    };
    tree.forEach(visit);
    return result;
  }, [tree, totals]);

  const grandTotal = tree.reduce(
    (sum, node) => sum + (rollups.get(node.id)?.amount ?? 0),
    0,
  );

  const renderNodes = (
    nodes: typeof tree,
    depth = 0,
  ): React.ReactNode =>
    nodes.map((node) => {
      const total = rollups.get(node.id) ?? { amount: 0, count: 0 };
      const isOpen = expanded.has(node.id);
      const share = grandTotal ? (total.amount / grandTotal) * 100 : 0;
      return (
        <div key={node.id}>
          <div className="ledger-branch" style={{ paddingLeft: depth * 18 }}>
            <button
              className="branch-toggle"
              type="button"
              onClick={() => toggle(node.id)}
              aria-expanded={isOpen}
              aria-label={`Toggle ${node.name}`}
              disabled={!node.children.length}
            >
              <ChevronRight
                size={15}
                className={isOpen ? "rotate-90" : ""}
              />
            </button>
            <div className="min-w-0 flex-1">
              <div className="flex items-baseline justify-between gap-4">
                <span className="truncate font-medium">{node.name}</span>
                <span className="money">
                  {formatMoney(total.amount, currency, locale)}
                </span>
              </div>
              <div className="mt-1 flex items-center gap-3">
                <div className="share-track">
                  <span style={{ width: `${Math.min(100, share)}%` }} />
                </div>
                <span className="meta whitespace-nowrap">
                  {total.count} · {share.toFixed(1)}%
                </span>
              </div>
            </div>
          </div>
          {isOpen && node.children.length
            ? renderNodes(node.children, depth + 1)
            : null}
        </div>
      );
    });

  return tree.length ? (
    <div className="branching-ledger">{renderNodes(tree)}</div>
  ) : (
    <EmptyState>{emptyLabel ?? (locale.startsWith("ka") ? "ჩანაწერები ჯერ არ არის" : "No records yet")}</EmptyState>
  );
}

export function EmptyState({ children = "No records yet" }: { children?: React.ReactNode }) {
  return <div className="empty-state">{children}</div>;
}
