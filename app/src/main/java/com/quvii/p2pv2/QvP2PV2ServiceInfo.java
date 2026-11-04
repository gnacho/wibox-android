package com.quvii.p2pv2;

/** Mirrors the vendor service-info bean read by libqv-p2p-v2.so. */
public final class QvP2PV2ServiceInfo {
    public String address;
    public int currentGroup;
    public int group;
    public String param;
    public long reSearchMaxTime;
    public long reSearchMinTime;
    public int type;
    public String uri;

    public QvP2PV2ServiceInfo() {
    }

    public QvP2PV2ServiceInfo(int type, int group, int currentGroup, String address, String uri, String param) {
        this.type = type;
        this.group = group;
        this.currentGroup = currentGroup;
        this.address = address;
        this.uri = uri;
        this.param = param;
    }
}
