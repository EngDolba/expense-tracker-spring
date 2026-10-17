package com.dolbaia.ledger.category;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dolbaia.ledger.common.Ids;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    public record CategoryForm(@NotBlank @Size(max = 120) String name, String parentId) {}

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    CategoryService.CategoriesPage page() {
        return service.page();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CategoryView create(@Valid @RequestBody CategoryForm form) {
        return service.create(form.name().trim(), Ids.optional(form.parentId()));
    }

    @PutMapping("/{id}")
    CategoryView update(@PathVariable UUID id, @Valid @RequestBody CategoryForm form) {
        return service.update(id, form.name().trim(), Ids.optional(form.parentId()));
    }

    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable UUID id) {
        service.archive(id);
    }
}
