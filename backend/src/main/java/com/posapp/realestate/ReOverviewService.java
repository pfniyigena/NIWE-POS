package com.posapp.realestate;

import com.posapp.realestate.dto.RealEstateDtos.Count;
import com.posapp.realestate.dto.RealEstateDtos.LeadRow;
import com.posapp.realestate.dto.RealEstateDtos.Overview;
import com.posapp.realestate.dto.RealEstateDtos.ViewingDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The Real Estate home page: stock, leads, viewings and what needs a call. */
@Service
public class ReOverviewService {

    private final RePropertyRepository propertyRepository;
    private final ReLeadRepository leadRepository;
    private final ReViewingRepository viewingRepository;
    private final ReViewingService viewingService;
    private final ReSettingsService settings;
    private final ReDealService dealService;
    private final ReUnitSaleService unitSaleService;
    private final ReDocumentService documentService;
    private final ReValuationService valuationService;
    private final RealEstateAccess access;

    public ReOverviewService(RePropertyRepository propertyRepository, ReLeadRepository leadRepository, ReViewingRepository viewingRepository, ReViewingService viewingService,
                             ReSettingsService settings, ReDealService dealService, ReUnitSaleService unitSaleService,
                             ReDocumentService documentService, ReValuationService valuationService, RealEstateAccess access) {
        this.documentService = documentService;
        this.valuationService = valuationService;
        this.propertyRepository = propertyRepository;
        this.leadRepository = leadRepository;
        this.viewingRepository = viewingRepository;
        this.viewingService = viewingService;
        this.settings = settings;
        this.dealService = dealService;
        this.unitSaleService = unitSaleService;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public Overview overview(RealEstateAccess.Actor a) {
        access.on();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        List<ReProperty> props = propertyRepository.findAll();
        List<ReLead> leads = leadRepository.findAll();
        Map<PropertyStatus, Long> byStatus = props.stream().collect(Collectors.groupingBy(ReProperty::getStatus, () -> new EnumMap<>(PropertyStatus.class), Collectors.counting()));
        BigDecimal saleValue = props.stream().filter(p -> p.getStatus() == PropertyStatus.AVAILABLE && p.getPurpose() != ListingPurpose.RENT && p.getSalePrice() != null)
                .map(ReProperty::getSalePrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rent = props.stream().filter(p -> p.getStatus() == PropertyStatus.AVAILABLE && p.getPurpose() != ListingPurpose.SALE && p.getRentPrice() != null)
                .map(ReProperty::getRentPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        long newLeads = leads.stream().filter(l -> !l.getCreatedAt().isBefore(monthStart)).count();
        List<ReLead> open = leads.stream().filter(ReLeadService::open).toList();
        Map<LeadStatus, Long> leadStatus = leads.stream().collect(Collectors.groupingBy(ReLead::getStatus, () -> new EnumMap<>(LeadStatus.class), Collectors.counting()));
        Map<LeadSource, Long> bySource = leads.stream().collect(Collectors.groupingBy(ReLead::getSource, () -> new EnumMap<>(LeadSource.class), Collectors.counting()));
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant weekEnd = today.plusDays(8).atStartOfDay(zone).toInstant();
        List<ReViewing> next = viewingRepository.findByStartAtBetweenOrderByStartAtAsc(dayStart, weekEnd).stream().filter(v -> v.getStatus() == ViewingStatus.SCHEDULED).toList();
        long viewingsToday = next.stream().filter(v -> v.getStartAt().isBefore(dayEnd)).count();
        Map<Long, ReProperty> pm = props.stream().collect(Collectors.toMap(ReProperty::getId, Function.identity()));
        List<LeadRow> rows = ReLeadService.rows(open, pm, viewingRepository.findAll(), settings.entity().getStaleLeadDays());
        List<LeadRow> attention = rows.stream().filter(r -> r.followUpOverdue() || r.stale())
                .sorted(Comparator.comparing((LeadRow r) -> r.nextFollowUp() == null ? LocalDate.MAX : r.nextFollowUp())).limit(10).toList();
        List<ViewingDto> upcoming = next.stream().limit(8).map(v -> ReViewingService.dto(v, pm.get(v.getPropertyId()),
                leads.stream().filter(l -> l.getId().equals(v.getLeadId())).findFirst().orElse(null), a.manager())).toList();
        ReDealService.Snapshot snap = dealService.snapshot();
        ReUnitSaleService.Snapshot us = unitSaleService.snapshot();
        List<Count> ps = new ArrayList<>();
        for (PropertyStatus s : PropertyStatus.values()) ps.add(new Count(s.name(), byStatus.getOrDefault(s, 0L)));
        List<Count> ls = new ArrayList<>();
        for (LeadStatus s : LeadStatus.values()) ls.add(new Count(s.name(), leadStatus.getOrDefault(s, 0L)));
        List<Count> src = Arrays.stream(LeadSource.values()).map(s -> new Count(s.name(), bySource.getOrDefault(s, 0L))).filter(c -> c.value() > 0).toList();
        return new Overview(props.size(), byStatus.getOrDefault(PropertyStatus.AVAILABLE, 0L), byStatus.getOrDefault(PropertyStatus.RESERVED, 0L),
                byStatus.getOrDefault(PropertyStatus.SOLD, 0L) + byStatus.getOrDefault(PropertyStatus.LET, 0L), saleValue, rent, newLeads, open.size(),
                rows.stream().filter(LeadRow::followUpOverdue).count(), viewingsToday, next.size(), ps, ls, src, upcoming, attention, snap.openDeals(), snap.openValue(),
                snap.soldThisMonth(), snap.soldValue(), snap.commissionToPost(), us.available(), us.reserved(), us.soldThisMonth(), us.soldValue(), us.receivable(), documentService.countExpiring(), valuationService.countExpiredCurrent());
    }
}
