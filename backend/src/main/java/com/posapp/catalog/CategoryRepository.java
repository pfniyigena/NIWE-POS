package com.posapp.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByNameIgnoreCase(String name);

    /** Fourier: "bulk-load my existing product list" — BulkImportService's product importer looks a CSV row's category name up here, creating one on the fly if it doesn't exist yet (see that service's own javadoc). */
    Optional<Category> findByNameIgnoreCase(String name);
}
