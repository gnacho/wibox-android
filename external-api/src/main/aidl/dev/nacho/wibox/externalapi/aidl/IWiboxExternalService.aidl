package dev.nacho.wibox.externalapi.aidl;

import dev.nacho.wibox.externalapi.aidl.ClientHello;
import dev.nacho.wibox.externalapi.aidl.HandshakeResult;
import dev.nacho.wibox.externalapi.aidl.AuthorizationRequest;
import dev.nacho.wibox.externalapi.aidl.AuthorizationResult;
import dev.nacho.wibox.externalapi.aidl.ProviderSnapshotResult;
import dev.nacho.wibox.externalapi.aidl.CapabilityQuery;
import dev.nacho.wibox.externalapi.aidl.CapabilityPageResult;
import dev.nacho.wibox.externalapi.aidl.PageRequest;
import dev.nacho.wibox.externalapi.aidl.DeviceRequest;
import dev.nacho.wibox.externalapi.aidl.ShadowQuery;
import dev.nacho.wibox.externalapi.aidl.ShadowBatchQuery;
import dev.nacho.wibox.externalapi.aidl.EventSubscriptionRequest;
import dev.nacho.wibox.externalapi.aidl.EventSubscriptionResult;
import dev.nacho.wibox.externalapi.aidl.ActionRequest;
import dev.nacho.wibox.externalapi.aidl.CancellationResult;
import dev.nacho.wibox.externalapi.aidl.ActionOutcomeResult;
import dev.nacho.wibox.externalapi.aidl.UpgradeCheckRequest;
import dev.nacho.wibox.externalapi.aidl.IDevicePageCallback;
import dev.nacho.wibox.externalapi.aidl.IDeviceCallback;
import dev.nacho.wibox.externalapi.aidl.IShadowCallback;
import dev.nacho.wibox.externalapi.aidl.IShadowBatchCallback;
import dev.nacho.wibox.externalapi.aidl.IWiboxEventCallback;
import dev.nacho.wibox.externalapi.aidl.IActionCallback;
import dev.nacho.wibox.externalapi.aidl.IUpgradeCallback;

interface IWiboxExternalService {
    HandshakeResult handshake(in ClientHello hello);
    AuthorizationResult requestAuthorization(in AuthorizationRequest request);
    AuthorizationResult getAuthorizationState();
    ProviderSnapshotResult getProviderSnapshot();
    CapabilityPageResult getCapabilities(in CapabilityQuery query);
    void listDevices(in PageRequest request, in IDevicePageCallback callback);
    void getDevice(in DeviceRequest request, in IDeviceCallback callback);
    void getDeviceShadow(in ShadowQuery request, in IShadowCallback callback);
    void getDeviceShadows(in ShadowBatchQuery request, in IShadowBatchCallback callback);
    EventSubscriptionResult subscribeEvents(in EventSubscriptionRequest request, in IWiboxEventCallback callback);
    void unsubscribeEvents(in IWiboxEventCallback callback);
    void performAction(in ActionRequest request, in IActionCallback callback);
    CancellationResult cancelOperation(String requestId);
    ActionOutcomeResult getActionOutcome(String requestId);
    void checkAppUpgrade(in UpgradeCheckRequest request, in IUpgradeCallback callback);
}
