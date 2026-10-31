package dev.nacho.wibox.externalapi.aidl;

import dev.nacho.wibox.externalapi.aidl.ApiError;
import dev.nacho.wibox.externalapi.aidl.UpgradeInfo;

oneway interface IUpgradeCallback {
    void onSuccess(in UpgradeInfo result);
    void onError(in ApiError error);
}
