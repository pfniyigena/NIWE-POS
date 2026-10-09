package com.posapp.apps.efrischad.dto;

/** Body for PUT /api/apps/efris-chad/terminals/{registerId}. No @NotBlank on any field — an admin clearing a terminal's settings back to blank is a valid state, not an error. */
public class UpdateEfrisChadSettingRequest {
    private String url;
    private String basicAuthUsername;
    private String basicPassword;
    private String sdcNumber;
    private String branchCode;
    private String mrc;
    private String integrationKey;
    /** Task 3: "Add also Chad Revenue Authority logo and EFRIS logo on EFRIS Chad setting" — see EfrisChadSetting.craLogoDataUrl's javadoc. */
    private String craLogoDataUrl;
    private String efrisLogoDataUrl;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getBasicAuthUsername() {
        return basicAuthUsername;
    }

    public void setBasicAuthUsername(String basicAuthUsername) {
        this.basicAuthUsername = basicAuthUsername;
    }

    public String getBasicPassword() {
        return basicPassword;
    }

    public void setBasicPassword(String basicPassword) {
        this.basicPassword = basicPassword;
    }

    public String getSdcNumber() {
        return sdcNumber;
    }

    public void setSdcNumber(String sdcNumber) {
        this.sdcNumber = sdcNumber;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }

    public String getMrc() {
        return mrc;
    }

    public void setMrc(String mrc) {
        this.mrc = mrc;
    }

    public String getIntegrationKey() {
        return integrationKey;
    }

    public void setIntegrationKey(String integrationKey) {
        this.integrationKey = integrationKey;
    }

    public String getCraLogoDataUrl() {
        return craLogoDataUrl;
    }

    public void setCraLogoDataUrl(String craLogoDataUrl) {
        this.craLogoDataUrl = craLogoDataUrl;
    }

    public String getEfrisLogoDataUrl() {
        return efrisLogoDataUrl;
    }

    public void setEfrisLogoDataUrl(String efrisLogoDataUrl) {
        this.efrisLogoDataUrl = efrisLogoDataUrl;
    }
}
