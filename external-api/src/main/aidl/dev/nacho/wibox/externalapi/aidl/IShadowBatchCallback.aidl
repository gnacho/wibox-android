package dev.nacho.wibox.externalapi.aidl;

import dev.nacho.wibox.externalapi.aidl.ApiError;
import dev.nacho.wibox.externalapi.aidl.ShadowBatch;

oneway interface IShadowBatchCallback {
    void onSuccess(in ShadowBatch result);
    void onError(in ApiError error);
}
