package com.posapp.helpdesk;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.helpdesk.dto.HelpDeskCategoryDto;
import com.posapp.helpdesk.dto.HelpDeskCategoryRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Admin > Help Desk > Categories — same simple CRUD shape as CatalogService's own category handling. */
@Service
public class HelpDeskCategoryService {

    private final HelpDeskCategoryRepository repository;
    private final HelpDeskTicketRepository ticketRepository;
    private final AuditLogService auditLogService;

    public HelpDeskCategoryService(HelpDeskCategoryRepository repository, HelpDeskTicketRepository ticketRepository,
                                    AuditLogService auditLogService) {
        this.repository = repository;
        this.ticketRepository = ticketRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<HelpDeskCategoryDto> listAll() {
        return repository.findAllByOrderByNameAsc().stream().map(HelpDeskCategoryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<HelpDeskCategoryDto> listActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream().map(HelpDeskCategoryDto::from).toList();
    }

    @Transactional
    public HelpDeskCategoryDto create(HelpDeskCategoryRequest request) {
        if (repository.existsByNameIgnoreCase(request.getName())) {
            throw ApiException.conflict("A category named '" + request.getName() + "' already exists");
        }
        HelpDeskCategory category = new HelpDeskCategory();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setActive(request.isActive());
        HelpDeskCategory saved = repository.save(category);
        auditLogService.record("HELPDESK_CATEGORY_CREATED", "Created Help Desk category '" + saved.getName() + "'",
                "HelpDeskCategory", saved.getId());
        return HelpDeskCategoryDto.from(saved);
    }

    @Transactional
    public HelpDeskCategoryDto update(Long id, HelpDeskCategoryRequest request) {
        HelpDeskCategory category = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category not found: " + id));
        if (!category.getName().equalsIgnoreCase(request.getName()) && repository.existsByNameIgnoreCase(request.getName())) {
            throw ApiException.conflict("A category named '" + request.getName() + "' already exists");
        }
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setActive(request.isActive());
        HelpDeskCategory saved = repository.save(category);
        auditLogService.record("HELPDESK_CATEGORY_UPDATED", "Updated Help Desk category '" + saved.getName() + "'",
                "HelpDeskCategory", saved.getId());
        return HelpDeskCategoryDto.from(saved);
    }

    /** Hard-deleted (not soft-retired) only while unused — a category already on a ticket is toggled inactive instead via update(). */
    @Transactional
    public void delete(Long id) {
        HelpDeskCategory category = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category not found: " + id));
        if (ticketRepository.existsByCategoryId(id)) {
            throw ApiException.conflict("Cannot delete '" + category.getName() + "' — tickets still use this category. Mark it inactive instead.");
        }
        repository.delete(category);
        auditLogService.record("HELPDESK_CATEGORY_DELETED", "Deleted Help Desk category '" + category.getName() + "'",
                "HelpDeskCategory", id);
    }

    @Transactional(readOnly = true)
    public HelpDeskCategory getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category not found: " + id));
    }
}
