package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RePhotoBlobRepository extends JpaRepository<RePhotoBlob, Long> {
    Optional<RePhotoBlob> findByPhotoId(Long photoId);

    void deleteByPhotoId(Long photoId);
}
