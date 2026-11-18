package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface OnlineAdRepository extends JpaRepository<OnlineAd, Long> {

    /** Everything except the picture bytes, for the staff list. */
    @Query("select a.id, a.advertiser.id, a.advertiser.name, a.title, a.placements, a.linkUrl, a.altText, a.startsOn, a.endsOn, a.active, a.fee, a.feeNote, a.feePaid, a.sortOrder, a.fileName, a.sizeBytes, a.advertiser.active "
            + "from OnlineAd a order by a.sortOrder, a.id")
    List<Object[]> overview();

    /** The ads that may be shown on the given day: switched on, advertiser switched on, inside their dates. */
    @Query("select a.id, a.title, a.altText, a.placements from OnlineAd a where a.active = true and a.advertiser.active = true "
            + "and (a.startsOn is null or a.startsOn <= :today) and (a.endsOn is null or a.endsOn >= :today) order by a.sortOrder, a.id")
    List<Object[]> showing(LocalDate today);

    /** The address of an ad that is switched on (its advertiser too), for the click redirect. */
    @Query("select a.linkUrl from OnlineAd a where a.id = :id and a.active = true and a.advertiser.active = true")
    java.util.Optional<String> activeLink(Long id);

    long countByAdvertiserId(Long advertiserId);

    @Query("select coalesce(max(a.sortOrder), 0) from OnlineAd a")
    int maxOrder();
}
