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
import com.posapp.restaurant.dto.OptionGroupDto;
import com.posapp.restaurant.dto.OptionGroupRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Admin > Restaurant > Menu (Fourier: "Menu & menu categories," reusing the existing product catalog — see MenuItem's own javadoc for why this never duplicates product data). */
@Service
public class MenuService {

    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;
    private final CatalogService catalogService;
    private final AuditLogService auditLogService;
    private final BomService bomService;
    private final RestaurantOptionGroupRepository groupRepository;

    public MenuService(MenuCategoryRepository categoryRepository, MenuItemRepository itemRepository,
                        CatalogService catalogService, AuditLogService auditLogService, BomService bomService,
                        RestaurantOptionGroupRepository groupRepository) {
        this.groupRepository = groupRepository;
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
        Map<Long, List<OptionGroupDto>> groups = new HashMap<>();
        if (!items.isEmpty()) {
            for (RestaurantOptionGroup g : groupRepository.findByMenuItemIdInOrderBySortOrderAscIdAsc(items.stream().map(MenuItem::getId).toList())) {
                groups.computeIfAbsent(g.getMenuItem().getId(), k -> new ArrayList<>()).add(OptionGroupDto.from(g));
            }
        }
        return items.stream()
                .map(item -> MenuItemDto.from(item, item.getProduct() != null && bomService.findActiveForProduct(item.getProduct().getId()) != null,
                        groups.getOrDefault(item.getId(), List.of())))
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
        applyKitchenFields(item, request);
        MenuItem saved = itemRepository.save(item);
        auditLogService.record("RESTAURANT_MENU_ITEM_ADDED", "Added " + product.getName() + " to menu category " + category.getName(), "MenuItem", saved.getId());
        return MenuItemDto.from(saved, bomService.findActiveForProduct(product.getId()) != null, groupDtos(saved.getId()));
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
        applyKitchenFields(item, request);
        MenuItem saved = itemRepository.save(item);
        auditLogService.record("RESTAURANT_MENU_ITEM_UPDATED", "Updated menu item " + product.getName(), "MenuItem", saved.getId());
        return MenuItemDto.from(saved, bomService.findActiveForProduct(product.getId()) != null, groupDtos(saved.getId()));
    }

    @Transactional
    public void removeItem(Long id) {
        MenuItem item = itemRepository.findById(id).orElseThrow(() -> ApiException.notFound("Menu item not found: " + id));
        itemRepository.delete(item);
        auditLogService.record("RESTAURANT_MENU_ITEM_REMOVED", "Removed menu item " + id, "MenuItem", id);
    }

    private void applyKitchenFields(MenuItem item, MenuItemRequest request) {
        if (request.getAllergens() != null) item.setAllergens(RestaurantAllergens.normalize(request.getAllergens()));
        int course = request.getDefaultCourse() == null ? (item.getDefaultCourse() <= 0 ? 1 : item.getDefaultCourse()) : request.getDefaultCourse();
        if (course < 1 || course > RestaurantOrderService.MAX_COURSE) {
            throw ApiException.badRequest("Course must be between 1 and " + RestaurantOrderService.MAX_COURSE);
        }
        item.setDefaultCourse(course);
        if (request.getPrepMinutes() != null) {
            int m = request.getPrepMinutes();
            if (m == 0) item.setPrepMinutes(null);
            else if (m < 1 || m > 240) throw ApiException.badRequest("Preparation time must be between 1 and 240 minutes");
            else item.setPrepMinutes(m);
        }
    }

    /** Switches an item to sold out ("86") or back on. Anyone signed in may do it — the kitchen is the first to know. */
    @Transactional
    public MenuItemDto setSoldOut(Long id, boolean soldOut, String reason) {
        MenuItem item = itemRepository.findById(id).orElseThrow(() -> ApiException.notFound("Menu item not found: " + id));
        String why = reason == null ? "" : reason.trim();
        if (why.length() > 120) throw ApiException.badRequest("The reason is too long (120 characters at most)");
        item.setSoldOut(soldOut);
        item.setSoldOutReason(soldOut && !why.isEmpty() ? why : null);
        item.setSoldOutAt(soldOut ? java.time.Instant.now() : null);
        item.setSoldOutBy(soldOut ? RestaurantAccess.username() : null);
        MenuItem saved = itemRepository.save(item);
        auditLogService.record(soldOut ? "RESTAURANT_ITEM_SOLD_OUT" : "RESTAURANT_ITEM_BACK_ON",
                (soldOut ? "Marked sold out: " : "Back on the menu: ") + item.getProduct().getName() + (soldOut && !why.isEmpty() ? " (" + why + ")" : ""), "MenuItem", id);
        return MenuItemDto.from(saved, bomService.findActiveForProduct(item.getProduct().getId()) != null, groupDtos(saved.getId()));
    }

    private List<OptionGroupDto> groupDtos(Long itemId) {
        return groupRepository.findByMenuItemIdOrderBySortOrderAscIdAsc(itemId).stream().map(OptionGroupDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OptionGroupDto> listOptions(Long itemId) {
        itemRepository.findById(itemId).orElseThrow(() -> ApiException.notFound("Menu item not found: " + itemId));
        return groupDtos(itemId);
    }

    /**
     * Saves the whole set of option groups of one item in one go. Groups and options that already exist are matched by id and
     * updated in place (so their ids stay the same), new ones are created, and any that are no longer in the list are removed.
     * Lines already ordered keep their own copy of what was picked, so removing an option never changes an existing bill.
     */
    @Transactional
    public List<OptionGroupDto> saveOptions(Long itemId, List<OptionGroupRequest> requested) {
        MenuItem item = itemRepository.findById(itemId).orElseThrow(() -> ApiException.notFound("Menu item not found: " + itemId));
        List<OptionGroupRequest> in = requested == null ? List.of() : requested;
        if (in.size() > 12) throw ApiException.badRequest("An item can have at most 12 option groups");
        Map<Long, RestaurantOptionGroup> existing = groupRepository.findByMenuItemIdOrderBySortOrderAscIdAsc(itemId).stream()
                .collect(Collectors.toMap(RestaurantOptionGroup::getId, g -> g));
        List<RestaurantOptionGroup> keep = new ArrayList<>();
        int groupOrder = 0;
        for (OptionGroupRequest gr : in) {
            String name = gr.getName() == null ? "" : gr.getName().trim();
            if (name.isEmpty()) throw ApiException.badRequest("Every option group needs a name");
            if (name.length() > 100) throw ApiException.badRequest("A group name can be at most 100 characters");
            if (gr.getMaxSelect() < 0 || gr.getMaxSelect() > 20) throw ApiException.badRequest("\"" + name + "\": the most that can be picked must be between 0 and 20");
            List<OptionGroupRequest.OptionRequest> opts = gr.getOptions() == null ? List.of() : gr.getOptions();
            if (opts.isEmpty()) throw ApiException.badRequest("\"" + name + "\" needs at least one option");
            if (opts.size() > 40) throw ApiException.badRequest("\"" + name + "\" has too many options (40 at most)");
            RestaurantOptionGroup g;
            if (gr.getId() != null) {
                g = existing.get(gr.getId());
                if (g == null) throw ApiException.badRequest("Option group " + gr.getId() + " does not belong to this item");
            } else {
                g = new RestaurantOptionGroup();
                g.setMenuItem(item);
            }
            g.setName(name);
            g.setRequired(gr.isRequired());
            g.setMaxSelect(gr.getMaxSelect());
            g.setActive(gr.isActive());
            g.setSortOrder(groupOrder++);
            Map<Long, RestaurantOption> oldOptions = g.getOptions().stream().collect(Collectors.toMap(RestaurantOption::getId, o -> o));
            List<RestaurantOption> newList = new ArrayList<>();
            int optOrder = 0;
            for (OptionGroupRequest.OptionRequest or : opts) {
                String on = or.getName() == null ? "" : or.getName().trim();
                if (on.isEmpty()) throw ApiException.badRequest("Every option in \"" + name + "\" needs a name");
                if (on.length() > 100) throw ApiException.badRequest("An option name can be at most 100 characters");
                BigDecimal delta = or.getPriceDelta() == null ? BigDecimal.ZERO : or.getPriceDelta();
                if (delta.abs().compareTo(BigDecimal.valueOf(10_000_000)) > 0) throw ApiException.badRequest("The price of \"" + on + "\" is too large");
                RestaurantOption o;
                if (or.getId() != null) {
                    o = oldOptions.get(or.getId());
                    if (o == null) throw ApiException.badRequest("Option " + or.getId() + " does not belong to \"" + name + "\"");
                } else {
                    o = new RestaurantOption();
                    o.setGroup(g);
                }
                o.setName(on);
                o.setPriceDelta(delta.setScale(2, java.math.RoundingMode.HALF_UP));
                o.setActive(or.isActive());
                o.setSortOrder(optOrder++);
                newList.add(o);
            }
            g.getOptions().clear();
            g.getOptions().addAll(newList);
            keep.add(g);
        }
        for (RestaurantOptionGroup old : existing.values()) {
            if (!keep.contains(old)) groupRepository.delete(old);
        }
        groupRepository.saveAll(keep);
        auditLogService.record("RESTAURANT_MENU_OPTIONS_SAVED", "Saved the options of " + (item.getProduct() != null ? item.getProduct().getName() : "menu item " + itemId)
                + " (" + keep.size() + " group" + (keep.size() == 1 ? "" : "s") + ")", "MenuItem", itemId);
        return groupDtos(itemId);
    }
}
