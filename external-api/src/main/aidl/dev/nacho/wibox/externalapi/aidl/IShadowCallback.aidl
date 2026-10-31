package dev.nacho.wibox.externalapi.aidl;

import dev.nacho.wibox.externalapi.aidl.ApiError;
import dev.nacho.wibox.externalapi.aidl.ShadowSnapshot;

oneway interface IShadowCallback {
    void onSuccess(in ShadowSnapshot result);
    void onError(in ApiError error);
}
