package com.dolbaia.ledger.category;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The category hierarchy: unlimited depth, ordered by sort order then Georgian name order. */
public final class CategoryTree {

    private CategoryTree() {}

    public static final Locale GEORGIAN = Locale.forLanguageTag("ka");

    public record Node(UUID id, String name, UUID parentId, int order, boolean archived) {}

    public record TreeNode(UUID id, String name, UUID parentId, int order, boolean archived,
                           List<TreeNode> children) {}

    private static final Comparator<TreeNode> ORDER;
    static {
        Collator collator = Collator.getInstance(GEORGIAN);
        ORDER = Comparator.comparingInt(TreeNode::order)
                .thenComparing(TreeNode::name, collator::compare);
    }

    public static List<TreeNode> build(Collection<Node> categories) {
        Map<UUID, TreeNode> nodes = new LinkedHashMap<>();
        for (Node category : categories) {
            nodes.put(category.id(), new TreeNode(category.id(), category.name(), category.parentId(),
                    category.order(), category.archived(), new ArrayList<>()));
        }
        List<TreeNode> roots = new ArrayList<>();
        for (TreeNode node : nodes.values()) {
            TreeNode parent = node.parentId() == null ? null : nodes.get(node.parentId());
            if (parent != null) parent.children().add(node);
            else roots.add(node);
        }
        sort(roots);
        return roots;
    }

    private static void sort(List<TreeNode> items) {
        items.sort(ORDER);
        for (TreeNode item : items) sort(item.children());
    }

    /** Every category below {@code categoryId}, at any depth. */
    public static Set<UUID> descendantIds(Collection<Node> categories, UUID categoryId) {
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (Node category : categories) {
            if (category.parentId() == null) continue;
            children.computeIfAbsent(category.parentId(), key -> new ArrayList<>()).add(category.id());
        }
        Set<UUID> descendants = new LinkedHashSet<>();
        List<UUID> pending = new ArrayList<>(children.getOrDefault(categoryId, List.of()));
        while (!pending.isEmpty()) {
            UUID child = pending.removeLast();
            if (descendants.add(child)) pending.addAll(children.getOrDefault(child, List.of()));
        }
        return descendants;
    }

    /** A category can move under any parent except itself and its own descendants. */
    public static boolean canMove(Collection<Node> categories, UUID categoryId, UUID nextParentId) {
        if (nextParentId == null) return true;
        if (categoryId.equals(nextParentId)) return false;
        return !descendantIds(categories, categoryId).contains(nextParentId);
    }
}
