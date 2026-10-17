import { describe, expect, it } from "vitest";
import {
  buildCategoryTree,
  canMoveCategory,
  descendantIds,
} from "@/lib/categories";

const categories = [
  { id: "food", name: "Food", parentId: null, order: 0 },
  { id: "dining", name: "Dining", parentId: "food", order: 1 },
  { id: "lunch", name: "Lunch", parentId: "dining", order: 0 },
  { id: "travel", name: "Travel", parentId: null, order: 1 },
];

describe("category hierarchy", () => {
  it("builds an unlimited-depth ordered tree", () => {
    const tree = buildCategoryTree(categories);
    expect(tree.map((node) => node.id)).toEqual(["food", "travel"]);
    expect(tree[0].children[0].children[0].id).toBe("lunch");
  });

  it("finds all recursive descendants", () => {
    expect([...descendantIds(categories, "food")].sort()).toEqual([
      "dining",
      "lunch",
    ]);
  });

  it("prevents moving a category into itself or a descendant", () => {
    expect(canMoveCategory(categories, "food", "food")).toBe(false);
    expect(canMoveCategory(categories, "food", "lunch")).toBe(false);
    expect(canMoveCategory(categories, "food", "travel")).toBe(true);
    expect(canMoveCategory(categories, "food", null)).toBe(true);
  });
});
