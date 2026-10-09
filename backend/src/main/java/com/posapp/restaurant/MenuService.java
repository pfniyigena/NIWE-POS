package com.posapp.restaurant;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.manufacturing.BomService;
import com.posapp.restaurant.dto.MenuCategoryDto;
import com.posapp.restaurant.dto.MenuCategoryRequest;
import com.posapp.restaurant.dto.MenuItemDto;
import com.posapp.restaurant.dto.MenuItemRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Admin > Restaurant > Menu (Fourier: "Menu & menu categories," reusing the existing product catalog — see MenuItem's own javadoc for why this never duplicates product data). */
@Service
public class MenuService {

    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;
    private final CatalogService catalogService;
    private final AuditLogService auditLogService;
    private final BomService bomService;

    public MenuService(MenuCategoryRepository categoryRepository, MenuItemRepository itemRepository,
                        CatalogService catalogService, AuditLogService auditLogService, BomService bomService) {
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.catalogService = catalogService;
        this.auditLogService = auditLogService;
        this.bomService = bomService;
    }

    @Transactional(readOnly = true)
    public List<MenuCategoryDto> listCategories() {
        return categoryRepository.findAllByOrderBySortOrderAscNameAsc().stream().map(MenuCategoryDto::from).toList();
    }

    @Transactional
    public MenuCategoryDto createCategory(MenuCategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw ApiException.conflict("A menu category named '" + request.getName() + "' already exists");
        }
        MenuCategory category = new MenuCategory();
        applyCategory(category, request);
        MenuCategory saved = categoryRepository.save(category);
        auditLogService.record("RESTAURANT_MENU_CATEGORY_CREATED", "Added menu category " + saved.getName(), "MenuCategory", saved.getId());
        return MenuCategoryDto.from(saved);
    }

    @Transactional
    public MenuCategoryDto updateCategory(Long id, MenuCategoryRequest request) {
        MenuCategory category = categoryRepository.findById(id).orElseThrow(() -> ApiException.notFound("Menu category not found: " + id));
        if (!category.getName().equalsIgnoreCase(request.getName()) && categoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw ApiException.conflict("A menu category named '" + request.getName() + "' already exists");
        }
        applyCategory(category, request);
        MenuCategory saved = categoryRepository.save(category);
        auditLogService.record("RESTAURANT_MENU_CATEGORY_UPDATED", "Updated menu category " + saved.getName(), "MenuCategory", saved.getId());
        return MenuCategoryDto.from(saved);
    }

    @Transactional
    public void deleteCategory(Long id) {
        MenuCategory category = categoryRepository.findById(id).orElseThrow(() -> ApiException.notFound("Menu category not found: " + id));
        if (!itemRepository.findByCategoryIdOrderBySortOrderAsc(id).isEmpty()) {
            throw ApiException.conflict("Cannot delete '" + category.getName() + "' — it still has menu items");
        }
        categoryRepository.delete(category);
        auditLogService.record("RESTAURANT_MENU_CATEGORY_DELETED", "Deleted menu category " + category.getName(), "MenuCategory", id);
    }

    private void applyCategory(MenuCategory category, MenuCategoryRequest request) {
        category.setName(request.getName());
        category.setSortOrder(request.getSortOrder());
        category.setActive(request.isActive());
    }

    @Transactional(readOnly = true)
    public List<MenuItemDto> listItems(Long categoryId) {
        List<MenuItem> items = categoryId != null
                ? itemRepository.findByCategoryIdOrderBySortOrderAsc(categoryId)
                : itemRepository.findAllByOrderBySortOrderAsc();
        return items.stream()
                .map(item -> MenuItemDto.from(item, item.getProduct() != null && bomService.findActiveForProduct(item.getProduct().getId()) != null))
                .toList();
    }

    @Transactional
    public MenuItemDto addItem(MenuItemRequest request) {
        MenuCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> ApiException.notFound("Menu category not found: " + request.getCategoryId()));
        Product product = catalogService.getProductOrThrow(request.getProductId());
        if (itemRepository.existsByCategoryIdAndProductId(request.getCategoryId(), request.getProductId())) {
            throw ApiException.conflict("'" + product.getName() + "' is already on this menu category");
        }
        MenuItem item = new MenuItem();
        item.setCategory(category);
        item.setProduct(product);
        item.setSortOrder(request.getSortOrder());
        item.setActive(request.isActive());
        item.setPriceOverride(request.getPriceOverride());
        item.setStation(request.getStation());
        MenuItem saved = itemRepository.save(item);
        auditLogService.record("RESTAURANT_MENU_ITEM_ADDED", "Added " + product.getName() + " to menu category " + category.getName(), "MenuItem", saved.getId());
        return MenuItemDto.from(saved, bomService.findActiveForProduct(product.getId()) != null);
    }

    @Transactional
    public MenuItemDto updateItem(Long id, MenuItemRequest request) {
        MenuItem item = itemRepository.findById(id).orElseThrow(() -> ApiException.notFound("Menu item not found: " + id));
        MenuCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> ApiException.notFound("Menu category not found: " + request.getCategoryId()));
        Product product = catalogService.getProductOrThrow(request.getProductId());
        item.setCategory(category);
        item.setProduct(product);
        item.setSortOrder(request.getSortOrder());
        item.setActive(request.isActive());
        item.setPriceOverride(request.getPriceOverride());
        item.setStation(request.getStation());
        MenuItem saved = itemRepository.save(item);
        auditLogService.record("RESTAURANT_MENU_ITEM_UPDATED", "Updated menu item " + product.getName(), "MenuItem", saved.getId());
        return MenuItemDto.from(saved, bomService.findActiveForProduct(product.getId()) != null);
    }

    @Transactional
    public void removeItem(Long id) {
        MenuItem item = itemRepository.findById(id).orElseThrow(() -> ApiException.notFound("Menu item not found: " + id));
        itemRepository.delete(item);
        auditLogService.record("RESTAURANT_MENU_ITEM_REMOVED", "Removed menu item " + id, "MenuItem", id);
    }
}
