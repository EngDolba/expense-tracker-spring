export type CategoryNode = {
  id: string;
  name: string;
  parentId: string | null;
  order?: number;
  archived?: boolean;
};

export type CategoryTreeNode = CategoryNode & {
  children: CategoryTreeNode[];
};

export function buildCategoryTree(categories: CategoryNode[]) {
  const nodes = new Map<string, CategoryTreeNode>();
  categories.forEach((category) =>
    nodes.set(category.id, { ...category, children: [] }),
  );

  const roots: CategoryTreeNode[] = [];
  for (const node of nodes.values()) {
    const parent = node.parentId ? nodes.get(node.parentId) : null;
    if (parent) parent.children.push(node);
    else roots.push(node);
  }

  const sort = (items: CategoryTreeNode[]) => {
    items.sort(
      (a, b) =>
        (a.order ?? 0) - (b.order ?? 0) ||
        a.name.localeCompare(b.name, "ka"),
    );
    items.forEach((item) => sort(item.children));
  };
  sort(roots);
  return roots;
}

export function descendantIds(categories: CategoryNode[], categoryId: string) {
  const children = new Map<string, string[]>();
  for (const category of categories) {
    if (!category.parentId) continue;
    const existing = children.get(category.parentId) ?? [];
    existing.push(category.id);
    children.set(category.parentId, existing);
  }

  const descendants = new Set<string>();
  const visit = (id: string) => {
    for (const child of children.get(id) ?? []) {
      if (descendants.has(child)) continue;
      descendants.add(child);
      visit(child);
    }
  };
  visit(categoryId);
  return descendants;
}

export function canMoveCategory(
  categories: CategoryNode[],
  categoryId: string,
  nextParentId: string | null,
) {
  if (!nextParentId) return true;
  if (categoryId === nextParentId) return false;
  return !descendantIds(categories, categoryId).has(nextParentId);
}
