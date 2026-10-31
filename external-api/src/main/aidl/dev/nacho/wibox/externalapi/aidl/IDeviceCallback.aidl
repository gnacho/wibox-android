package dev.nacho.wibox.externalapi.aidl;

import dev.nacho.wibox.externalapi.aidl.ApiError;
import dev.nacho.wibox.externalapi.aidl.DeviceSnapshot;

oneway interface IDeviceCallback {
    void onSuccess(in DeviceSnapshot result);
    void onError(in ApiError error);
}
