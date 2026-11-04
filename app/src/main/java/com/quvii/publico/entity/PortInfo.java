package com.quvii.publico.entity;

/** Port mapping result returned by the vendor P2P SDK. */
public final class PortInfo {

    public static final int PORT_FAIL_COMMON = -1;
    public static final int PORT_FAIL_DEVICE_BUSY = -2;
    public static final int PORT_FAIL_LOW_POWER = -3;
    public static final int PORT_FAIL_P2P_BUSY = -4;

    private int port;
    private int type;

    public PortInfo() {
    }

    public PortInfo(int port, int type) {
        this.port = port;
        this.type = type;
    }

    public int getPort() {
        return port;
    }

    public int getType() {
        return type;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setType(int type) {
        this.type = type;
    }
}
