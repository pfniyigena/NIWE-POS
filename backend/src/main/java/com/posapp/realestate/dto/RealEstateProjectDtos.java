package com.posapp.realestate.dto;

import com.posapp.installment.dto.InstallmentDtos.PlanDetailDto;
import com.posapp.realestate.ProjectStatus;
import com.posapp.realestate.UnitKind;
import com.posapp.realestate.UnitSaleStage;
import com.posapp.realestate.UnitStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the developer-project screens read: projects, their units and price list, and the unit sales. */
public final class RealEstateProjectDtos {

    private RealEstateProjectDtos() {
    }

    public record ProjectRow(Long id, String code, String name, String area, String developerName, ProjectStatus status, LocalDate launchDate, LocalDate handoverDate,
                             int totalUnits, int available, int reserved, int sold, int withheld, BigDecimal stockValue, BigDecimal soldValue) {
    }

    public record ProjectStats(BigDecimal stockValue, BigDecimal soldValue, BigDecimal collected, BigDecimal outstanding, BigDecimal minPrice, BigDecimal maxPrice) {
    }

    public record ProjectDto(Long id, String code, String name, String area, String address, String developerName, ProjectStatus status, LocalDate launchDate,
                             LocalDate handoverDate, String description, String createdByName, Instant createdAt, int totalUnits, int available, int reserved, int sold,
                             int withheld, ProjectStats stats, List<String> blocks, List<UnitRow> units) {
    }

    public record UnitRow(Long id, Long projectId, String projectCode, String projectName, String unitNo, String block, UnitKind kind, Integer floor, Integer bedrooms,
                          Integer bathrooms, BigDecimal sizeSqm, BigDecimal listPrice, BigDecimal pricePerSqm, UnitStatus status, Long saleId, String saleCode, String buyerName) {
    }

    public record PriceChangeDto(Long id, Instant at, BigDecimal oldPrice, BigDecimal newPrice, String reason, String changedByName) {
    }

    public record UnitDto(Long id, Long projectId, String projectCode, String projectName, String unitNo, String block, UnitKind kind, Integer floor, Integer bedrooms,
                          Integer bathrooms, BigDecimal sizeSqm, BigDecimal listPrice, BigDecimal pricePerSqm, UnitStatus status, String notes, Instant createdAt,
                          boolean priceLocked, List<UnitSaleRow> sales, List<PriceChangeDto> priceHistory) {
    }

    /** One line of a price adjustment: shown as a preview first, then applied. */
    public record PriceLineDto(Long unitId, String unitNo, String block, BigDecimal oldPrice, BigDecimal newPrice) {
    }

    public record PriceAdjustResult(boolean applied, int changed, List<PriceLineDto> lines) {
    }

    public record BulkResult(int created, List<String> unitNos) {
    }

    public record UnitSaleRow(Long id, String code, Long unitId, String unitNo, Long projectId, String projectCode, String projectName, String buyerName, Long buyerCustomerId,
                              String agentName, UnitSaleStage stage, BigDecimal listPrice, BigDecimal agreedPrice, BigDecimal paid, BigDecimal outstanding, LocalDate reservedOn,
                              LocalDate reservationExpiry, LocalDate agreementDate, LocalDate completedDate, String invoiceNumber, boolean overdue, Instant createdAt) {
    }

    public record SaleInvoiceDto(Long id, String invoiceNumber, String status, BigDecimal total, BigDecimal paid, BigDecimal outstanding) {
    }

    public record UnitSaleDto(Long id, String code, Long unitId, String unitNo, String unitKind, String block, Long projectId, String projectCode, String projectName, Long leadId,
                              String leadName, Long buyerCustomerId, String buyerName, String buyerPhone, String buyerEmail, Long agentUserId, String agentName,
                              UnitSaleStage stage, BigDecimal listPrice, BigDecimal discount, BigDecimal agreedPrice, LocalDate reservedOn, LocalDate reservationExpiry,
                              LocalDate agreementDate, LocalDate completedDate, String notes, String cancelReason, String createdByName, Instant createdAt,
                              SaleInvoiceDto invoice, PlanDetailDto plan, boolean overdue, boolean canEdit) {
    }
}
