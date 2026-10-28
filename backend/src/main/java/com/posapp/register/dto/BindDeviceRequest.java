package com.posapp.register.dto;

/** Body of "Bind to this device". The device id itself travels in the X-Device-Id header, never in the body. */
public class BindDeviceRequest {
    /** A friendly label ("Front counter PC"); optional. */
    private String deviceName;
    /** True (the default) = also remember the address the device has now and require it. */
    private Boolean checkIp;

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public Boolean getCheckIp() { return checkIp; }
    public void setCheckIp(Boolean checkIp) { this.checkIp = checkIp; }
}
