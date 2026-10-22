package com.posapp.restaurant.dto;

import com.posapp.restaurant.RestaurantOption;
import com.posapp.restaurant.RestaurantOptionGroup;

import java.math.BigDecimal;
import java.util.List;

public record OptionGroupDto(Long id, String name, boolean required, int maxSelect, int sortOrder, boolean active, List<OptionDto> options) {

    public record OptionDto(Long id, String name, BigDecimal priceDelta, int sortOrder, boolean active) {
        public static OptionDto from(RestaurantOption o) {
            return new OptionDto(o.getId(), o.getName(), o.getPriceDelta(), o.getSortOrder(), o.isActive());
        }
    }

    public static OptionGroupDto from(RestaurantOptionGroup g) {
        return new OptionGroupDto(g.getId(), g.getName(), g.isRequired(), g.getMaxSelect(), g.getSortOrder(), g.isActive(),
                g.getOptions().stream().map(OptionDto::from).toList());
    }
}
