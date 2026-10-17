package com.dolbaia.ledger.category;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dolbaia.ledger.common.ApiException;

@Service
public class CategoryService {

    public record CodeView(UUID id, String code, CategoryView category) {}

    public record CategoriesPage(List<CategoryView> categories, List<CodeView> codes) {}

    private final CategoryRepository categories;
    private final CategoryCodeRepository codes;

    public CategoryService(CategoryRepository categories, CategoryCodeRepository codes) {
        this.categories = categories;
        this.codes = codes;
    }

    /** The categories every form picks from: not archived, in tree order. */
    @Transactional(readOnly = true)
    public List<CategoryView> active() {
        return categories.findActive().stream().map(CategoryView::of).toList();
    }

    @Transactional(readOnly = true)
    public CategoriesPage page() {
        return new CategoriesPage(active(), codes.findAllWithCategory().stream()
                .map(code -> new CodeView(code.getId(), code.getCode(), CategoryView.of(code.getCategory())))
                .toList());
    }

    @Transactional
    public CategoryView create(String name, UUID parentId) {
        Category parent = findOptional(parentId);
        int order = (int) categories.countSiblings(parentId);
        return CategoryView.of(categories.save(new Category(name, parent, order)));
    }

    /** Renames and/or moves a category; it may not move under itself or its own descendants. */
    @Transactional
    public CategoryView update(UUID id, String name, UUID parentId) {
        Category category = find(id);
        List<CategoryTree.Node> all = categories.findAll().stream().map(c -> CategoryView.of(c).node()).toList();
        if (!CategoryTree.canMove(all, id, parentId)) {
            throw ApiException.badRequest("category_cycle",
                    "A category cannot be moved inside its own descendant.");
        }
        category.setName(name);
        category.setParent(findOptional(parentId));
        return CategoryView.of(category);
    }

    @Transactional
    public void archive(UUID id) {
        find(id).setArchived(true);
    }

    public Category find(UUID id) {
        return categories.findById(id)
                .orElseThrow(() -> ApiException.notFound("category_not_found", "The category no longer exists."));
    }

    /** The category for an optional id, or null. */
    public Category findOptional(UUID id) {
        return id == null ? null : find(id);
    }
}
