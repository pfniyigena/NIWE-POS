package com.posapp.realestate.dto;

import com.posapp.realestate.ListingPurpose;
import com.posapp.realestate.PropertyType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the public website may see. No owner details, no street address, no map position, no internal notes, no staff names other than the agent's. */
public final class RealEstatePublicDtos {

    private RealEstatePublicDtos() {
    }

    public record Info(String title, String intro, String phone, String email, List<String> areas, List<String> types) {
    }

    public record Card(Long id, String code, String title, PropertyType type, ListingPurpose purpose, String area, Integer bedrooms, Integer bathrooms, BigDecimal sizeSqm,
                       BigDecimal salePrice, BigDecimal rentPrice, boolean negotiable, boolean featured, Long coverPhotoId, int photos, LocalDate listedDate) {
    }

    public record Page(List<Card> items, long total, int page, int size) {
    }

    public record Detail(Long id, String code, String title, PropertyType type, ListingPurpose purpose, String description, String area, Integer bedrooms, Integer bathrooms,
                         Integer parkingSpaces, BigDecimal sizeSqm, BigDecimal plotSqm, Integer yearBuilt, boolean furnished, List<String> features, BigDecimal salePrice,
                         BigDecimal rentPrice, boolean negotiable, String agentName, LocalDate listedDate, List<Long> photoIds) {
    }

    public record EnquiryRequest(String name, String phone, String email, String message, String website) {
    }

    public record EnquiryResult(String message) {
    }
}
