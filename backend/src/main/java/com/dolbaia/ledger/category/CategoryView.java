package com.dolbaia.ledger.category;

import java.util.UUID;

/** A category as every page sees it. */
public record CategoryView(UUID id, String name, UUID parentId, int order, boolean archived) {

    public static CategoryView of(Category category) {
        return category == null ? null : new CategoryView(category.getId(), category.getName(),
                category.getParentId(), category.getSortOrder(), category.isArchived());
    }

    public CategoryTree.Node node() {
        return new CategoryTree.Node(id, name, parentId, order, archived);
    }
}
