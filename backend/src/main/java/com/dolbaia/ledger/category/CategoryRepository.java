package com.dolbaia.ledger.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("select c from Category c where c.archived = false order by c.sortOrder, c.name")
    List<Category> findActive();

    long countByParentIsNull();

    long countByParent_Id(UUID parentId);

    Optional<Category> findFirstByParentIsNullAndNameOrderByCreatedAt(String name);

    Optional<Category> findFirstByParent_IdAndNameOrderByCreatedAt(UUID parentId, String name);

    /** How many categories share this parent (null: the roots); a new one goes last. */
    default long countSiblings(UUID parentId) {
        return parentId == null ? countByParentIsNull() : countByParent_Id(parentId);
    }

    default Optional<Category> findChild(UUID parentId, String name) {
        return parentId == null
                ? findFirstByParentIsNullAndNameOrderByCreatedAt(name)
                : findFirstByParent_IdAndNameOrderByCreatedAt(parentId, name);
    }
}
