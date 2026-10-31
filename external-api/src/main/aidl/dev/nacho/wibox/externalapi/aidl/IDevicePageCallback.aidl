package dev.nacho.wibox.externalapi.aidl;

import dev.nacho.wibox.externalapi.aidl.ApiError;
import dev.nacho.wibox.externalapi.aidl.DevicePage;

oneway interface IDevicePageCallback {
    void onSuccess(in DevicePage result);
    void onError(in ApiError error);
}
