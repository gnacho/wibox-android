package dev.nacho.wibox.externalapi.aidl;

import dev.nacho.wibox.externalapi.aidl.AuthorizationSnapshot;
import dev.nacho.wibox.externalapi.aidl.EventBatch;
import dev.nacho.wibox.externalapi.aidl.EventConnectionState;
import dev.nacho.wibox.externalapi.aidl.EventGap;

oneway interface IWiboxEventCallback {
    void onEvents(in EventBatch batch);
    void onConnectionChanged(in EventConnectionState state);
    void onGap(in EventGap gap);
    void onAuthorizationChanged(in AuthorizationSnapshot state);
}
