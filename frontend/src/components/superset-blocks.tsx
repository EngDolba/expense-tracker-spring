import type { ReactNode } from "react";
import { groupSupersets, supersetLetter, type SupersetSlot } from "@/lib/fitness";

export type SupersetPosition = {
  /** Position in the ordered list the blocks were built from. */
  index: number;
  /** Letter of this member inside its superset; null for a plain slot. */
  letter: string | null;
  /** True when the slot is performed together with the one above it. */
  pairedWithPrevious: boolean;
};

/**
 * Renders an ordered list of slots in the blocks a session is performed in:
 * members of a superset are lettered and wrapped in one frame, every other
 * slot is rendered on its own. Used by the plan builder, workout history and
 * the runner's set rail so the three never disagree about what a pair is.
 */
export function SupersetBlocks<T extends SupersetSlot & { id: string }>({
  items,
  frameClassName,
  label,
  renderItem,
}: {
  items: T[];
  frameClassName: string;
  label: ReactNode;
  renderItem: (item: T, position: SupersetPosition) => ReactNode;
}) {
  return (
    <>
      {groupSupersets(items).map((block) => {
        const paired = block.group !== null;
        const rows = block.members.map((item, offset) =>
          renderItem(item, {
            index: block.startIndex + offset,
            letter: paired ? supersetLetter(offset) : null,
            pairedWithPrevious: paired && offset > 0,
          }),
        );
        if (!paired) return rows;
        return (
          <div
            className={frameClassName}
            role="group"
            aria-label="Superset"
            key={`superset-${block.members[0].id}`}
          >
            {label}
            {rows}
          </div>
        );
      })}
    </>
  );
}
