package com.posapp.realestate;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.realestate.RealEstateAccess.Actor;
import com.posapp.realestate.dto.RealEstateProjectDtos.*;
import com.posapp.realestate.dto.RealEstateProjectRequests.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Developer projects, their plots and units, and the price list (single and bulk changes, with a history of every price change). */
@Service
public class ReProjectService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MAX_BULK = 500;
    private static final Set<UnitStatus> ON_SALE = EnumSet.of(UnitStatus.AVAILABLE, UnitStatus.WITHHELD);
    private static final Set<UnitSaleStage> LIVE = EnumSet.of(UnitSaleStage.RESERVED, UnitSaleStage.SOLD);

    private final ReProjectRepository repository;
    private final ReUnitRepository unitRepository;
    private final ReUnitSaleRepository saleRepository;
    private final ReUnitPriceChangeRepository priceRepository;
    private final ReUnitSaleService saleService;
    private final ReSettingsService settings;
    private final RealEstateAccess access;
    private final AuditLogService auditLogService;
    private final ReDocumentService documentService;

    public ReProjectService(ReProjectRepository repository, ReUnitRepository unitRepository, ReUnitSaleRepository saleRepository, ReUnitPriceChangeRepository priceRepository,
                            ReUnitSaleService saleService, ReSettingsService settings, RealEstateAccess access, AuditLogService auditLogService,
                            ReDocumentService documentService) {
        this.documentService = documentService;
        this.repository = repository;
        this.unitRepository = unitRepository;
        this.saleRepository = saleRepository;
        this.priceRepository = priceRepository;
        this.saleService = saleService;
        this.settings = settings;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ helpers

    private static String clean(String s, int max, String what) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > max) throw ApiException.badRequest(what + " can be at most " + max + " characters.");
        return t.isEmpty() ? null : t;
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private ReProject find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Project not found: " + id));
    }

    private ReUnit findUnit(Long id) {
        return unitRepository.findById(id).orElseThrow(() -> ApiException.notFound("Unit not found: " + id));
    }

    private static BigDecimal perSqm(ReUnit u) {
        return u.getSizeSqm() == null || u.getSizeSqm().signum() <= 0 ? null : u.getListPrice().divide(u.getSizeSqm(), 2, RoundingMode.HALF_UP);
    }

    private static void checkUnitNumbers(Integer floor, Integer bedrooms, Integer bathrooms, BigDecimal size) {
        if (floor != null && (floor < -5 || floor > 200)) throw ApiException.badRequest("The floor is between -5 and 200.");
        if (bedrooms != null && (bedrooms < 0 || bedrooms > 50)) throw ApiException.badRequest("Bedrooms are between 0 and 50.");
        if (bathrooms != null && (bathrooms < 0 || bathrooms > 50)) throw ApiException.badRequest("Bathrooms are between 0 and 50.");
        if (size != null && size.signum() <= 0) throw ApiException.badRequest("The size must be more than 0.");
    }

    // ------------------------------------------------------------------ projects

    private static int count(List<ReUnit> units, UnitStatus st) {
        return (int) units.stream().filter(u -> u.getStatus() == st).count();
    }

    private static BigDecimal stock(List<ReUnit> units) {
        return units.stream().filter(u -> u.getStatus() == UnitStatus.AVAILABLE).map(ReUnit::getListPrice).reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal soldValue(Long projectId) {
        return saleRepository.findByProjectId(projectId).stream().filter(s -> s.getStage() == UnitSaleStage.SOLD || s.getStage() == UnitSaleStage.COMPLETED)
                .map(ReUnitSale::getAgreedPrice).reduce(ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public List<ProjectRow> list(String q, ProjectStatus status) {
        access.on();
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        Map<Long, List<ReUnit>> units = unitRepository.findAll().stream().collect(Collectors.groupingBy(ReUnit::getProjectId));
        Map<Long, BigDecimal> sold = new java.util.HashMap<>();
        for (ReUnitSale s : saleRepository.findByStageIn(EnumSet.of(UnitSaleStage.SOLD, UnitSaleStage.COMPLETED))) sold.merge(s.getProjectId(), s.getAgreedPrice(), BigDecimal::add);
        List<ProjectRow> out = new ArrayList<>();
        for (ReProject p : repository.findAll()) {
            if (status != null && p.getStatus() != status) continue;
            if (needle != null && !(p.getCode() + " " + p.getName() + " " + (p.getArea() == null ? "" : p.getArea()) + " " + (p.getDeveloperName() == null ? "" : p.getDeveloperName()))
                    .toLowerCase(Locale.ROOT).contains(needle)) continue;
            List<ReUnit> us = units.getOrDefault(p.getId(), List.of());
            out.add(new ProjectRow(p.getId(), p.getCode(), p.getName(), p.getArea(), p.getDeveloperName(), p.getStatus(), p.getLaunchDate(), p.getHandoverDate(), us.size(),
                    count(us, UnitStatus.AVAILABLE), count(us, UnitStatus.RESERVED), count(us, UnitStatus.SOLD), count(us, UnitStatus.WITHHELD), stock(us), sold.getOrDefault(p.getId(), ZERO)));
        }
        out.sort(Comparator.comparing((ProjectRow r) -> r.status() == ProjectStatus.ARCHIVED ? 1 : 0).thenComparing(ProjectRow::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Transactional
    public ProjectDto get(Long id) {
        access.on();
        return dto(find(id));
    }

    private ProjectDto dto(ReProject p) {
        List<ReUnit> units = unitRepository.findByProjectIdOrderByBlockAscUnitNoAsc(p.getId());
        Map<Long, ReUnitSale> holding = new java.util.HashMap<>();
        for (ReUnitSale s : saleRepository.findByProjectId(p.getId())) {
            if (LIVE.contains(s.getStage()) || s.getStage() == UnitSaleStage.COMPLETED) holding.put(s.getUnitId(), s);
        }
        List<UnitRow> rows = units.stream().map(u -> unitRow(u, p, holding.get(u.getId()))).sorted(Comparator.comparing((UnitRow r) -> r.block() == null ? "" : r.block(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> naturalKey(r.unitNo()))).toList();
        ReUnitSaleService.Money m = saleService.totals(p.getId());
        BigDecimal min = units.stream().map(ReUnit::getListPrice).min(BigDecimal::compareTo).orElse(null);
        BigDecimal max = units.stream().map(ReUnit::getListPrice).max(BigDecimal::compareTo).orElse(null);
        List<String> blocks = units.stream().map(ReUnit::getBlock).filter(b -> b != null && !b.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        return new ProjectDto(p.getId(), p.getCode(), p.getName(), p.getArea(), p.getAddress(), p.getDeveloperName(), p.getStatus(), p.getLaunchDate(), p.getHandoverDate(), p.getDescription(),
                p.getCreatedByName(), p.getCreatedAt(), units.size(), count(units, UnitStatus.AVAILABLE), count(units, UnitStatus.RESERVED), count(units, UnitStatus.SOLD),
                count(units, UnitStatus.WITHHELD), new ProjectStats(stock(units), m.sold(), m.collected(), m.outstanding(), min, max), blocks, rows);
    }

    /** Sorts "A-2" before "A-10": digits compare as numbers. */
    static String naturalKey(String s) {
        StringBuilder b = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+|\\D+").matcher(s == null ? "" : s.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String t = m.group();
            b.append(Character.isDigit(t.charAt(0)) ? String.format("%012d", Long.parseLong(t.length() > 12 ? t.substring(0, 12) : t)) : t);
        }
        return b.toString();
    }

    @Transactional
    public ProjectDto create(Actor a, ProjectRequest r) {
        access.on();
        String name = clean(r.name(), 200, "The name");
        if (name == null) throw ApiException.badRequest("Give the project a name.");
        ReProject p = new ReProject();
        p.setCode(settings.nextCode("PJ"));
        p.setName(name);
        apply(p, r);
        p.setCreatedByName(RealEstateAccess.name(a.user()));
        repository.save(p);
        auditLogService.record("RE_PROJECT_CREATED", "Created project " + p.getCode() + " " + p.getName(), "ReProject", p.getId());
        return dto(p);
    }

    private void apply(ReProject p, ProjectRequest r) {
        if (r.area() != null) p.setArea(clean(r.area(), 120, "The area"));
        if (r.address() != null) p.setAddress(clean(r.address(), 255, "The address"));
        if (r.developerName() != null) p.setDeveloperName(clean(r.developerName(), 200, "The developer"));
        if (r.status() != null) p.setStatus(r.status());
        if (r.launchDate() != null) p.setLaunchDate(r.launchDate());
        if (r.handoverDate() != null) p.setHandoverDate(r.handoverDate());
        if (p.getLaunchDate() != null && p.getHandoverDate() != null && p.getHandoverDate().isBefore(p.getLaunchDate())) {
            throw ApiException.badRequest("The handover cannot be before the launch.");
        }
        if (r.description() != null) p.setDescription(clean(r.description(), 2000, "The description"));
    }

    @Transactional
    public ProjectDto update(Long id, ProjectRequest r) {
        access.on();
        ReProject p = find(id);
        if (r.name() != null) {
            String name = clean(r.name(), 200, "The name");
            if (name == null) throw ApiException.badRequest("Give the project a name.");
            p.setName(name);
        }
        apply(p, r);
        repository.save(p);
        auditLogService.record("RE_PROJECT_UPDATED", "Updated project " + p.getCode(), "ReProject", id);
        return dto(p);
    }

    @Transactional
    public void delete(Long id) {
        access.on();
        ReProject p = find(id);
        if (unitRepository.countByProjectId(id) > 0) throw ApiException.conflict("Project " + p.getCode() + " still has units. Delete them first, or archive the project.");
        documentService.deleteFor(DocSubject.PROJECT, id);
        repository.delete(p);
        auditLogService.record("RE_PROJECT_DELETED", "Deleted project " + p.getCode() + " " + p.getName());
    }

    // ------------------------------------------------------------------ units

    private UnitRow unitRow(ReUnit u, ReProject p, ReUnitSale sale) {
        return new UnitRow(u.getId(), u.getProjectId(), p == null ? null : p.getCode(), p == null ? null : p.getName(), u.getUnitNo(), u.getBlock(), u.getKind(), u.getFloor(),
                u.getBedrooms(), u.getBathrooms(), u.getSizeSqm(), u.getListPrice(), perSqm(u), u.getStatus(), sale == null ? null : sale.getId(), sale == null ? null : sale.getCode(),
                sale == null ? null : sale.getBuyerName());
    }

    @Transactional(readOnly = true)
    public List<UnitRow> units(String q, Long projectId, UnitStatus status, UnitKind kind, String block, Boolean availableOnly) {
        access.on();
        Map<Long, ReProject> projects = repository.findAll().stream().collect(Collectors.toMap(ReProject::getId, Function.identity()));
        Map<Long, ReUnitSale> holding = new java.util.HashMap<>();
        for (ReUnitSale s : saleRepository.findAll()) {
            if (LIVE.contains(s.getStage()) || s.getStage() == UnitSaleStage.COMPLETED) holding.put(s.getUnitId(), s);
        }
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        List<ReUnit> all = projectId != null ? unitRepository.findByProjectId(projectId) : unitRepository.findAll();
        List<UnitRow> out = new ArrayList<>();
        for (ReUnit u : all) {
            if (status != null && u.getStatus() != status) continue;
            if (kind != null && u.getKind() != kind) continue;
            if (block != null && !block.isBlank() && !block.equalsIgnoreCase(u.getBlock())) continue;
            if (Boolean.TRUE.equals(availableOnly) && u.getStatus() != UnitStatus.AVAILABLE) continue;
            ReProject p = projects.get(u.getProjectId());
            if (needle != null && !(u.getUnitNo() + " " + (u.getBlock() == null ? "" : u.getBlock()) + " " + (p == null ? "" : p.getCode() + " " + p.getName())).toLowerCase(Locale.ROOT).contains(needle)) continue;
            out.add(unitRow(u, p, holding.get(u.getId())));
        }
        out.sort(Comparator.comparing((UnitRow r) -> r.projectName() == null ? "" : r.projectName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> r.block() == null ? "" : r.block(), String.CASE_INSENSITIVE_ORDER).thenComparing(r -> naturalKey(r.unitNo())));
        return out;
    }

    @Transactional
    public UnitDto unit(Actor a, Long id) {
        access.on();
        ReUnit u = findUnit(id);
        ReProject p = repository.findById(u.getProjectId()).orElse(null);
        List<UnitSaleRow> sales = saleService.list(null, null, null, id, false);
        List<PriceChangeDto> history = priceRepository.findByUnitIdOrderByIdDesc(id).stream()
                .map(c -> new PriceChangeDto(c.getId(), c.getCreatedAt(), c.getOldPrice(), c.getNewPrice(), c.getReason(), c.getChangedByName())).toList();
        return new UnitDto(u.getId(), u.getProjectId(), p == null ? null : p.getCode(), p == null ? null : p.getName(), u.getUnitNo(), u.getBlock(), u.getKind(), u.getFloor(),
                u.getBedrooms(), u.getBathrooms(), u.getSizeSqm(), u.getListPrice(), perSqm(u), u.getStatus(), u.getNotes(), u.getCreatedAt(), !ON_SALE.contains(u.getStatus()), sales, history);
    }

    private void logPrice(ReUnit u, BigDecimal oldPrice, BigDecimal newPrice, String reason, Actor a) {
        ReUnitPriceChange c = new ReUnitPriceChange();
        c.setUnitId(u.getId());
        c.setOldPrice(oldPrice);
        c.setNewPrice(newPrice);
        c.setReason(reason);
        c.setChangedByName(RealEstateAccess.name(a.user()));
        priceRepository.save(c);
    }

    private void requireOpen(ReProject p) {
        if (p.getStatus() == ProjectStatus.ARCHIVED) throw ApiException.conflict("Project " + p.getCode() + " is archived.");
    }

    @Transactional
    public UnitDto createUnit(Actor a, Long projectId, UnitRequest r) {
        access.on();
        ReProject p = find(projectId);
        requireOpen(p);
        String no = clean(r.unitNo(), 40, "The unit number");
        if (no == null) throw ApiException.badRequest("Give the unit a number.");
        if (unitRepository.existsByProjectIdAndUnitNoIgnoreCase(projectId, no)) throw ApiException.conflict("Project " + p.getCode() + " already has a unit " + no + ".");
        if (r.listPrice() == null || r.listPrice().signum() <= 0) throw ApiException.badRequest("Enter the list price.");
        checkUnitNumbers(r.floor(), r.bedrooms(), r.bathrooms(), r.sizeSqm());
        ReUnit u = new ReUnit();
        u.setProjectId(projectId);
        u.setUnitNo(no);
        u.setBlock(clean(r.block(), 80, "The block"));
        if (r.kind() != null) u.setKind(r.kind());
        u.setFloor(r.floor());
        u.setBedrooms(r.bedrooms());
        u.setBathrooms(r.bathrooms());
        u.setSizeSqm(r.sizeSqm());
        u.setListPrice(money(r.listPrice()));
        u.setNotes(clean(r.notes(), 1000, "The notes"));
        if (r.status() == UnitStatus.WITHHELD) u.setStatus(UnitStatus.WITHHELD);
        unitRepository.save(u);
        logPrice(u, null, u.getListPrice(), "Unit created", a);
        auditLogService.record("RE_UNIT_CREATED", "Added unit " + no + " to " + p.getCode() + " at " + u.getListPrice(), "ReUnit", u.getId());
        return unit(a, u.getId());
    }

    @Transactional
    public BulkResult bulk(Actor a, Long projectId, BulkUnitsRequest r) {
        access.on();
        ReProject p = find(projectId);
        requireOpen(p);
        if (r.from() == null || r.to() == null) throw ApiException.badRequest("Give the first and the last number.");
        if (r.from() < 0 || r.to() < r.from()) throw ApiException.badRequest("The last number must not be below the first.");
        if (r.to() - r.from() + 1 > MAX_BULK) throw ApiException.badRequest("Create at most " + MAX_BULK + " units at a time.");
        if (r.listPrice() == null || r.listPrice().signum() <= 0) throw ApiException.badRequest("Enter the list price.");
        checkUnitNumbers(r.floor(), r.bedrooms(), r.bathrooms(), r.sizeSqm());
        String prefix = r.prefix() == null ? "" : r.prefix().trim();
        int pad = r.padding() == null ? 0 : Math.max(0, Math.min(r.padding(), 8));
        Set<String> existing = unitRepository.findByProjectId(projectId).stream().map(u -> u.getUnitNo().toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(HashSet::new));
        List<String> numbers = new ArrayList<>();
        List<String> clash = new ArrayList<>();
        for (int n = r.from(); n <= r.to(); n++) {
            String no = prefix + (pad > 0 ? String.format("%0" + pad + "d", n) : String.valueOf(n));
            if (no.length() > 40) throw ApiException.badRequest("A unit number can be at most 40 characters.");
            if (existing.contains(no.toLowerCase(Locale.ROOT))) clash.add(no);
            numbers.add(no);
        }
        if (!clash.isEmpty()) {
            throw ApiException.conflict("These unit numbers already exist: " + String.join(", ", clash.subList(0, Math.min(clash.size(), 8))) + (clash.size() > 8 ? " and " + (clash.size() - 8) + " more" : "") + ".");
        }
        String block = clean(r.block(), 80, "The block");
        BigDecimal price = money(r.listPrice());
        for (String no : numbers) {
            ReUnit u = new ReUnit();
            u.setProjectId(projectId);
            u.setUnitNo(no);
            u.setBlock(block);
            if (r.kind() != null) u.setKind(r.kind());
            u.setFloor(r.floor());
            u.setBedrooms(r.bedrooms());
            u.setBathrooms(r.bathrooms());
            u.setSizeSqm(r.sizeSqm());
            u.setListPrice(price);
            unitRepository.save(u);
            logPrice(u, null, price, "Unit created", a);
        }
        auditLogService.record("RE_UNITS_CREATED", "Added " + numbers.size() + " units (" + numbers.get(0) + " to " + numbers.get(numbers.size() - 1) + ") to " + p.getCode(), "ReProject", projectId);
        return new BulkResult(numbers.size(), numbers);
    }

    @Transactional
    public UnitDto updateUnit(Actor a, Long id, UnitRequest r) {
        access.on();
        ReUnit u = findUnit(id);
        if (r.unitNo() != null) {
            String no = clean(r.unitNo(), 40, "The unit number");
            if (no == null) throw ApiException.badRequest("Give the unit a number.");
            if (!no.equalsIgnoreCase(u.getUnitNo()) && unitRepository.existsByProjectIdAndUnitNoIgnoreCase(u.getProjectId(), no)) throw ApiException.conflict("This project already has a unit " + no + ".");
            u.setUnitNo(no);
        }
        if (r.block() != null) u.setBlock(clean(r.block(), 80, "The block"));
        if (r.kind() != null) u.setKind(r.kind());
        checkUnitNumbers(r.floor(), r.bedrooms(), r.bathrooms(), r.sizeSqm());
        if (r.floor() != null) u.setFloor(r.floor());
        if (r.bedrooms() != null) u.setBedrooms(r.bedrooms());
        if (r.bathrooms() != null) u.setBathrooms(r.bathrooms());
        if (r.sizeSqm() != null) u.setSizeSqm(r.sizeSqm());
        if (r.notes() != null) u.setNotes(clean(r.notes(), 1000, "The notes"));
        if (r.listPrice() != null && r.listPrice().compareTo(u.getListPrice()) != 0) {
            if (r.listPrice().signum() <= 0) throw ApiException.badRequest("Enter the list price.");
            if (!ON_SALE.contains(u.getStatus())) throw ApiException.conflict("Unit " + u.getUnitNo() + " is " + u.getStatus().name().toLowerCase(Locale.ROOT) + ": its price is set by the sale.");
            BigDecimal old = u.getListPrice();
            u.setListPrice(money(r.listPrice()));
            logPrice(u, old, u.getListPrice(), clean(r.reason(), 200, "The reason"), a);
        }
        if (r.status() != null && r.status() != u.getStatus()) {
            if (r.status() != UnitStatus.AVAILABLE && r.status() != UnitStatus.WITHHELD) throw ApiException.badRequest("Reserving and selling happen through a sale. You can only mark a unit available or withheld.");
            if (!ON_SALE.contains(u.getStatus())) throw ApiException.conflict("Unit " + u.getUnitNo() + " is " + u.getStatus().name().toLowerCase(Locale.ROOT) + " by a sale. Cancel the sale first.");
            u.setStatus(r.status());
        }
        unitRepository.save(u);
        auditLogService.record("RE_UNIT_UPDATED", "Updated unit " + u.getUnitNo(), "ReUnit", id);
        return unit(a, id);
    }

    @Transactional
    public void deleteUnit(Long id) {
        access.on();
        ReUnit u = findUnit(id);
        if (saleRepository.countByUnitId(id) > 0) throw ApiException.conflict("Unit " + u.getUnitNo() + " has sales on record, so it cannot be deleted. Withhold it instead.");
        priceRepository.deleteByUnitId(id);
        documentService.deleteFor(DocSubject.UNIT, id);
        unitRepository.delete(u);
        auditLogService.record("RE_UNIT_DELETED", "Deleted unit " + u.getUnitNo());
    }

    // ------------------------------------------------------------------ the price list

    private static BigDecimal roundTo(BigDecimal v, int step) {
        if (step <= 1) return v.setScale(2, RoundingMode.HALF_UP);
        BigDecimal s = BigDecimal.valueOf(step);
        return v.divide(s, 0, RoundingMode.HALF_UP).multiply(s).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public PriceAdjustResult adjustPrices(Actor a, Long projectId, PriceAdjustRequest r) {
        access.on();
        ReProject p = find(projectId);
        requireOpen(p);
        if (r.percent() == null || r.percent().signum() == 0) throw ApiException.badRequest("Enter the percentage to change prices by.");
        if (r.percent().compareTo(BigDecimal.valueOf(-90)) < 0 || r.percent().compareTo(BigDecimal.valueOf(500)) > 0) throw ApiException.badRequest("The change is between -90 and 500 percent.");
        int step = r.roundTo() == null ? 1 : r.roundTo();
        if (step < 1 || step > 10_000_000) throw ApiException.badRequest("Round to a whole number of at least 1.");
        boolean preview = Boolean.TRUE.equals(r.preview());
        Set<Long> only = r.unitIds() == null || r.unitIds().isEmpty() ? null : new HashSet<>(r.unitIds());
        List<PriceLineDto> lines = new ArrayList<>();
        BigDecimal factor = BigDecimal.ONE.add(r.percent().divide(HUNDRED, 8, RoundingMode.HALF_UP));
        for (ReUnit u : unitRepository.findByProjectIdOrderByBlockAscUnitNoAsc(projectId)) {
            if (only != null && !only.contains(u.getId())) continue;
            if (!ON_SALE.contains(u.getStatus())) continue;
            if (r.kind() != null && u.getKind() != r.kind()) continue;
            if (r.block() != null && !r.block().isBlank() && !r.block().trim().equalsIgnoreCase(u.getBlock())) continue;
            BigDecimal np = roundTo(u.getListPrice().multiply(factor), step);
            if (np.signum() <= 0 || np.compareTo(u.getListPrice()) == 0) continue;
            lines.add(new PriceLineDto(u.getId(), u.getUnitNo(), u.getBlock(), u.getListPrice(), np));
            if (!preview) {
                BigDecimal old = u.getListPrice();
                u.setListPrice(np);
                unitRepository.save(u);
                logPrice(u, old, np, clean(r.reason(), 200, "The reason") != null ? clean(r.reason(), 200, "The reason") : (r.percent().signum() > 0 ? "+" : "") + r.percent().stripTrailingZeros().toPlainString() + "%", a);
            }
        }
        lines.sort(Comparator.comparing((PriceLineDto l) -> l.block() == null ? "" : l.block(), String.CASE_INSENSITIVE_ORDER).thenComparing(l -> naturalKey(l.unitNo())));
        if (!preview && !lines.isEmpty()) {
            auditLogService.record("RE_PRICES_ADJUSTED", "Changed " + lines.size() + " prices in " + p.getCode() + " by " + r.percent().stripTrailingZeros().toPlainString() + "%", "ReProject", projectId);
        }
        return new PriceAdjustResult(!preview, lines.size(), lines);
    }
}
