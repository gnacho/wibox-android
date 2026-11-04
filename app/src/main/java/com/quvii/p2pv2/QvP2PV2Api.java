package com.quvii.p2pv2;

/**
 * Minimal bridge to the vendor P2P SDK (libqv-p2p-v2.so, arm64-v8a only).
 *
 * The native library calls back into this exact class name with
 * GetStaticMethodID, so every callback below must exist with the same
 * signature even when the app ignores its content.
 */
public final class QvP2PV2Api {

    private static final String TAG = "WIBOX-NATIVE";

    /** Last UST connection status reported by the native SDK (3 = connected). */
    public static volatile int lastUstStatus = -1;

    static {
        System.loadLibrary("qv-p2p-v2");
    }

    private QvP2PV2Api() {
    }

    // ---------------------------------------------------------------- natives

    public static native int createP2PClient(
            String serviceUrl,
            String ustAddress,
            String ustTestAddress,
            String caPath,
            String keyPath,
            String certPath,
            String oem,
            String clientId,
            String appType,
            String appId,
            int forceTrans,
            int mqttKeepAliveSec);

    static native int deviceAwaken(String deviceId);

    public static native int getP2PConnectStatus();

    public static native void release();

    public static native void reset();

    public static native com.quvii.publico.entity.PortInfo addPortByP2P(
            String deviceId,
            int p2,
            int p3,
            int p4,
            int p2pType,
            int mtu,
            int timeout);

    public static native int deletePortByP2P(int port);

    static native boolean checkPort(int port);

    static native int getDeviceStatus(String deviceId);

    static native int getDirectDeviceStatus(String deviceId);

    static native int addDeviceStatusQuery(String deviceId);

    static native int getDeviceConnectType(String deviceId);

    /** Must be called before createP2PClient. The stock client sends the same mask. */
    static native void setDefaultServiceMask(int mask);

    /** Drops the P2P leg of a device, as DeviceHelper.CleanPort does. */
    static native void delCurrentDevP2pLink(String deviceId);

    // -------------------------------------------------------------- callbacks

    public static void setDeviceOnlineStatus(String deviceId, int status) {
        String label = status == 0 ? "online" : (status == 1 ? "sleep" : "other");
        android.util.Log.d(TAG, "callback setDeviceOnlineStatus id=" + deviceId + " status=" + status + " (" + label + ")");
    }

    public static void getDeviceDisConnect(String deviceId, int reason) {
        android.util.Log.d(TAG, "callback getDeviceDisConnect id=" + deviceId + " reason=" + reason);
    }

    public static void getServerData(QvP2PV2ServiceInfo[] data) {
        android.util.Log.d(TAG, "callback getServerData size=" + (data == null ? -1 : data.length));
    }

    public static void getUstStatus(int status) {
        lastUstStatus = status;
        android.util.Log.d(TAG, "callback getUstStatus status=" + status);
    }

    public static void jniLog(String message) {
        android.util.Log.d(TAG, "jni " + message);
    }

    public static int setIntState(int first, int second, boolean state) {
        int bit = 1 << second;
        return state ? (first | bit) : (first & (~bit));
    }

    public static void subDevMtuTestResult(String deviceId, int result) {
        android.util.Log.d(TAG, "subDevMtuTestResult " + deviceId + " result=" + result);
    }

    public static void subMqttKeepAliveSec(int sec) {
        android.util.Log.d(TAG, "subMqttKeepAliveSec " + sec);
    }

    public static boolean getIntState(int first, int second) {
        return (first & (1 << second)) > 0;
    }

    public static int generateMask(int... values) {
        int state = 0;
        for (int value : values) {
            state = setIntState(state, value, true);
        }
        return state;
    }

    static native void lanSearchDeviceClear();

    static native com.quvii.publico.entity.QvLanSearchInfo[] lanSearchDeviceGet();

    static native int lanSearchDeviceStart();

    static native int lanSearchDeviceStop();
}
