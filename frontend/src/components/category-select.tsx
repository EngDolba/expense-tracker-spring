"use client";

import * as Select from "@radix-ui/react-select";
import { Check, ChevronDown } from "lucide-react";
import { buildCategoryTree, type CategoryNode } from "@/lib/categories";

function flatten(
  nodes: ReturnType<typeof buildCategoryTree>,
  depth = 0,
): Array<CategoryNode & { depth: number }> {
  return nodes.flatMap((node) => [
    { ...node, depth },
    ...flatten(node.children, depth + 1),
  ]);
}

export function CategorySelect({
  categories,
  name = "categoryId",
  placeholder = "Select category",
  allowEmpty = true,
  defaultValue,
}: {
  categories: CategoryNode[];
  name?: string;
  placeholder?: string;
  allowEmpty?: boolean;
  defaultValue?: string | null;
}) {
  const items = flatten(buildCategoryTree(categories));

  return (
    <Select.Root name={name} defaultValue={defaultValue ?? undefined}>
      <Select.Trigger className="select-trigger" aria-label={placeholder}>
        <Select.Value placeholder={placeholder} />
        <Select.Icon>
          <ChevronDown size={15} />
        </Select.Icon>
      </Select.Trigger>
      <Select.Portal>
        <Select.Content className="select-content" position="popper" sideOffset={6}>
          <Select.Viewport>
            {allowEmpty ? (
              <Select.Item className="select-item" value="none">
                <Select.ItemText>—</Select.ItemText>
              </Select.Item>
            ) : null}
            {items.map((item) => (
              <Select.Item
                className="select-item"
                key={item.id}
                value={item.id}
              >
                <Select.ItemIndicator>
                  <Check size={14} />
                </Select.ItemIndicator>
                <Select.ItemText>
                  {"\u00A0\u00A0".repeat(item.depth)}
                  {item.depth ? "↳ " : ""}
                  {item.name}
                </Select.ItemText>
              </Select.Item>
            ))}
          </Select.Viewport>
        </Select.Content>
      </Select.Portal>
    </Select.Root>
  );
}
