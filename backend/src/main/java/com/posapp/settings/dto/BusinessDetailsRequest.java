package com.posapp.settings.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Admin > Settings > Business Details (Task: "Add Business Details like name, address, contact,
 * TAX ID, logo, etc"). Only {@code businessName} is required — a store can save this screen while
 * still filling in the rest gradually. {@code logoDataUrl} is nullable/blank-able so "remove logo"
 * is just saving with it empty, same as any other optional field here.
 */
public class BusinessDetailsRequest {

    @NotBlank(message = "Business name is required")
    private String businessName;

    private String address;
    private String phone;
    private String email;
    private String taxId;
    private String logoDataUrl;

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
    public String getLogoDataUrl() { return logoDataUrl; }
    public void setLogoDataUrl(String logoDataUrl) { this.logoDataUrl = logoDataUrl; }
}
