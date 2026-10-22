package com.posapp.dataimport;

import com.posapp.catalog.Product;
import com.posapp.catalog.ProductRepository;
import com.posapp.common.ApiException;
import com.posapp.dataimport.dto.ImportResultDto;
import com.posapp.dataimport.dto.SkippedRowDto;
import com.posapp.pricing.PriceList;
import com.posapp.pricing.PriceListEntry;
import com.posapp.pricing.PriceListEntryRepository;
import com.posapp.pricing.PriceListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.posapp.dataimport.ImportSupport.requireDecimal;
import static com.posapp.dataimport.ImportSupport.requireText;
import static com.posapp.dataimport.ImportSupport.text;

/** Fills a price list from a CSV (sku, minQuantity, price). A price already on the list for that product and quantity step is changed; the rest are added. Nothing is ever removed. */
@Service
public class PriceListImportService {

    private final PriceListRepository lists;
    private final PriceListEntryRepository entries;
    private final ProductRepository products;

    public PriceListImportService(PriceListRepository lists, PriceListEntryRepository entries, ProductRepository products) {
        this.lists = lists;
        this.entries = entries;
        this.products = products;
    }

    @Transactional
    public ImportResultDto importEntries(Long listId, MultipartFile file, boolean dryRun) {
        PriceList list = lists.findById(listId).orElseThrow(() -> ApiException.notFound("Price list not found: " + listId));
        List<Map<String, String>> rows = ImportSupport.readRows(file);
        ImportSupport.requireColumns(rows, "sku", "price");
        List<SkippedRowDto> skipped = new ArrayList<>();
        List<SkippedRowDto> accepted = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int n = 0, imported = 0, changed = 0;
        for (Map<String, String> row : rows) {
            n++;
            try {
                String sku = requireText(row, "sku");
                Product p = products.findBySkuIgnoreCase(sku).orElseThrow(() -> ApiException.badRequest("No product with SKU '" + sku + "'"));
                String minText = text(row, "minQuantity");
                int min = 1;
                if (minText != null) {
                    try {
                        min = Integer.parseInt(minText.trim());
                    } catch (NumberFormatException ex) {
                        throw ApiException.badRequest("minQuantity must be a whole number, got '" + minText + "'");
                    }
                }
                if (min < 1) throw ApiException.badRequest("minQuantity must be at least 1");
                BigDecimal price = requireDecimal(row, "price");
                if (price.signum() < 0) throw ApiException.badRequest("The price cannot be negative");
                if (price.scale() > 2 && price.stripTrailingZeros().scale() > 2) throw ApiException.badRequest("The price can have at most two decimals");
                if (!seen.add(p.getId() + ":" + min)) throw ApiException.badRequest("'" + sku + "' from " + min + " appears twice in this file");
                PriceListEntry e = entries.findByPriceListIdAndProductIdAndMinQuantity(listId, p.getId(), min).orElse(null);
                if (e == null) {
                    e = new PriceListEntry();
                    e.setPriceListId(listId);
                    e.setProductId(p.getId());
                    e.setMinQuantity(min);
                } else {
                    changed++;
                }
                e.setPrice(price.setScale(2, RoundingMode.HALF_UP));
                entries.save(e);
                imported++;
                accepted.add(new SkippedRowDto(n, sku + " from " + min + " at " + e.getPrice()));
            } catch (ApiException ex) {
                skipped.add(new SkippedRowDto(n, ex.getMessage()));
            } catch (RuntimeException ex) {
                skipped.add(new SkippedRowDto(n, "Unexpected error: " + ex.getMessage()));
            }
        }
        List<String> notes = new ArrayList<>();
        if (changed > 0) notes.add(changed + " of the prices were already on '" + list.getName() + "' and are changed.");
        return ImportSupport.result(dryRun, n, imported, skipped, accepted, notes);
    }

    public static byte[] templateCsv() {
        return "sku,minQuantity,price\nW-1,1,900\nW-1,10,850\n".getBytes(StandardCharsets.UTF_8);
    }
}
