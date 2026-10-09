package com.posapp.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    boolean existsByNameIgnoreCase(String name);

    /** Same "look up or create on the fly" use as CategoryRepository.findByNameIgnoreCase — see BulkImportService. */
    Optional<Brand> findByNameIgnoreCase(String name);
}
