package com.quvii.publico.entity;

/**
 * Result of the vendor LAN discovery (QvP2PV2Api.lanSearchDeviceGet).
 *
 * The native library fills this class by name, so the package, the class name
 * and the public fields must match the stock SDK exactly.
 */
public class QvLanSearchInfo {

    public static final int ABILITY_NOT_SHOW_BIT_POSITION = 1;

    public int ability;
    public int adminPasswordSHA256;
    public int category;
    public int cgiPort;
    public int channel;
    public int directMode;
    public int gateWay;
    public int ip;
    public int mask;
    public int streamPort;
    public int wifiConfig;
    public int wifiConfigDefault;
    public int wifiConfigSupport;

    public String oemId;
    public String type;
    public String uid;
    public String version;

    public QvLanSearchInfo() {
    }
}
