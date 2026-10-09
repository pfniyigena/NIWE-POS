package com.posapp.restaurant;

import com.posapp.restaurant.dto.MenuCategoryDto;
import com.posapp.restaurant.dto.MenuCategoryRequest;
import com.posapp.restaurant.dto.MenuItemDto;
import com.posapp.restaurant.dto.MenuItemRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin > Restaurant > Menu — same "reads open, writes admin-only" shape as DiningTableController. */
@RestController
@RequestMapping("/api/restaurant/menu")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/categories")
    public List<MenuCategoryDto> listCategories() {
        return menuService.listCategories();
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public MenuCategoryDto createCategory(@Valid @RequestBody MenuCategoryRequest request) {
        return menuService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MenuCategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody MenuCategoryRequest request) {
        return menuService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteCategory(@PathVariable Long id) {
        menuService.deleteCategory(id);
    }

    @GetMapping("/items")
    public List<MenuItemDto> listItems(@RequestParam(required = false) Long categoryId) {
        return menuService.listItems(categoryId);
    }

    @PostMapping("/items")
    @PreAuthorize("hasRole('ADMIN')")
    public MenuItemDto addItem(@Valid @RequestBody MenuItemRequest request) {
        return menuService.addItem(request);
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MenuItemDto updateItem(@PathVariable Long id, @Valid @RequestBody MenuItemRequest request) {
        return menuService.updateItem(id, request);
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void removeItem(@PathVariable Long id) {
        menuService.removeItem(id);
    }
}
