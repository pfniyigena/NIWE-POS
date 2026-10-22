package com.posapp.restaurant.dto;

import java.math.BigDecimal;
import java.util.List;

/** One group in the full list sent when an item's options are saved. A null id means a new group. */
public class OptionGroupRequest {
    private Long id;
    private String name;
    private boolean required;
    private int maxSelect = 1;
    private boolean active = true;
    private List<OptionRequest> options;

    public static class OptionRequest {
        private Long id;
        private String name;
        private BigDecimal priceDelta;
        private boolean active = true;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public BigDecimal getPriceDelta() { return priceDelta; }
        public void setPriceDelta(BigDecimal priceDelta) { this.priceDelta = priceDelta; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public int getMaxSelect() { return maxSelect; }
    public void setMaxSelect(int maxSelect) { this.maxSelect = maxSelect; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<OptionRequest> getOptions() { return options; }
    public void setOptions(List<OptionRequest> options) { this.options = options; }
}
