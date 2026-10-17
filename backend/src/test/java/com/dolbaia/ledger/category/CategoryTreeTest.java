package com.dolbaia.ledger.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dolbaia.ledger.category.CategoryTree.Node;
import com.dolbaia.ledger.category.CategoryTree.TreeNode;

/** Port of categories.test.ts "category hierarchy". */
class CategoryTreeTest {

    static UUID id(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    static final List<Node> CATEGORIES = List.of(
            new Node(id("food"), "Food", null, 0, false),
            new Node(id("dining"), "Dining", id("food"), 1, false),
            new Node(id("lunch"), "Lunch", id("dining"), 0, false),
            new Node(id("travel"), "Travel", null, 1, false));

    @Test
    void buildsAnUnlimitedDepthOrderedTree() {
        List<TreeNode> tree = CategoryTree.build(CATEGORIES);

        assertThat(tree).extracting(TreeNode::id).containsExactly(id("food"), id("travel"));
        assertThat(tree.getFirst().children().getFirst().children().getFirst().id()).isEqualTo(id("lunch"));
    }

    @Test
    void ordersSiblingsByOrderThenName() {
        List<TreeNode> tree = CategoryTree.build(List.of(
                new Node(id("b"), "ბ", null, 0, false),
                new Node(id("a"), "ა", null, 0, false),
                new Node(id("z"), "z-first", null, -1, false)));

        assertThat(tree).extracting(TreeNode::name).containsExactly("z-first", "ა", "ბ");
    }

    @Test
    void findsAllRecursiveDescendants() {
        assertThat(CategoryTree.descendantIds(CATEGORIES, id("food")))
                .containsExactlyInAnyOrder(id("dining"), id("lunch"));
    }

    @Test
    void preventsMovingACategoryIntoItselfOrADescendant() {
        assertThat(CategoryTree.canMove(CATEGORIES, id("food"), id("food"))).isFalse();
        assertThat(CategoryTree.canMove(CATEGORIES, id("food"), id("lunch"))).isFalse();
        assertThat(CategoryTree.canMove(CATEGORIES, id("food"), id("travel"))).isTrue();
        assertThat(CategoryTree.canMove(CATEGORIES, id("food"), null)).isTrue();
    }
}
