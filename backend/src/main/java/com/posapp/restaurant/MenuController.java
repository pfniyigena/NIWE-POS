package com.posapp.restaurant;

import com.posapp.restaurant.dto.MenuCategoryDto;
import com.posapp.restaurant.dto.MenuCategoryRequest;
import com.posapp.restaurant.dto.MenuItemDto;
import com.posapp.restaurant.dto.MenuItemRequest;
import com.posapp.restaurant.dto.OptionGroupDto;
import com.posapp.restaurant.dto.OptionGroupRequest;
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
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    public MenuCategoryDto createCategory(@Valid @RequestBody MenuCategoryRequest request) {
        return menuService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    public MenuCategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody MenuCategoryRequest request) {
        return menuService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    public void deleteCategory(@PathVariable Long id) {
        menuService.deleteCategory(id);
    }

    @GetMapping("/items")
    public List<MenuItemDto> listItems(@RequestParam(required = false) Long categoryId) {
        return menuService.listItems(categoryId);
    }

    @PostMapping("/items")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    public MenuItemDto addItem(@Valid @RequestBody MenuItemRequest request) {
        return menuService.addItem(request);
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    public MenuItemDto updateItem(@PathVariable Long id, @Valid @RequestBody MenuItemRequest request) {
        return menuService.updateItem(id, request);
    }

    /** Mark an item sold out or back on — open to any signed-in user. */
    @PostMapping("/items/{id}/sold-out")
    public MenuItemDto soldOut(@PathVariable Long id, @RequestBody com.posapp.restaurant.dto.SoldOutRequest request) {
        return menuService.setSoldOut(id, request.isSoldOut(), request.getReason());
    }

    @GetMapping("/allergens")
    public List<String> allergens() {
        return RestaurantAllergens.ALL;
    }

    @GetMapping("/items/{id}/options")
    public List<OptionGroupDto> listOptions(@PathVariable Long id) {
        return menuService.listOptions(id);
    }

    @PutMapping("/items/{id}/options")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    public List<OptionGroupDto> saveOptions(@PathVariable Long id, @RequestBody List<OptionGroupRequest> groups) {
        return menuService.saveOptions(id, groups);
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    public void removeItem(@PathVariable Long id) {
        menuService.removeItem(id);
    }
}
