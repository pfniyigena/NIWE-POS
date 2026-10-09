package com.posapp.apps.efris.dto;

/** Body for PUT /api/apps/efris/terminals/{registerId}. No @NotBlank on any field — an admin clearing a terminal's settings back to blank is a valid state, not an error. */
public class UpdateEfrisSettingRequest {
    private String efrisUrl;
    private String efrisDb;
    private Integer efrisUid;
    private String efrisPassword;
    /** Task: "add two logo (URA LOGO, EFRIS LOGO)" — see EfrisSetting.rraLogoDataUrl's javadoc. */
    private String rraLogoDataUrl;
    private String efrisLogoDataUrl;

    public String getEfrisUrl() {
        return efrisUrl;
    }

    public void setEfrisUrl(String efrisUrl) {
        this.efrisUrl = efrisUrl;
    }

    public String getEfrisDb() {
        return efrisDb;
    }

    public void setEfrisDb(String efrisDb) {
        this.efrisDb = efrisDb;
    }

    public Integer getEfrisUid() {
        return efrisUid;
    }

    public void setEfrisUid(Integer efrisUid) {
        this.efrisUid = efrisUid;
    }

    public String getEfrisPassword() {
        return efrisPassword;
    }

    public void setEfrisPassword(String efrisPassword) {
        this.efrisPassword = efrisPassword;
    }

    public String getRraLogoDataUrl() {
        return rraLogoDataUrl;
    }

    public void setRraLogoDataUrl(String rraLogoDataUrl) {
        this.rraLogoDataUrl = rraLogoDataUrl;
    }

    public String getEfrisLogoDataUrl() {
        return efrisLogoDataUrl;
    }

    public void setEfrisLogoDataUrl(String efrisLogoDataUrl) {
        this.efrisLogoDataUrl = efrisLogoDataUrl;
    }
}
