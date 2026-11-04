package com.quvii.p2pv2;

/** Callback type the vendor library resolves reflectively. */
public interface QvQueryServiceCallback {
    void onQuery(QvP2PV2ServiceInfo[] infos);
}
