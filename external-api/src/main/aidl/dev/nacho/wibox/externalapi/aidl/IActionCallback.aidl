package dev.nacho.wibox.externalapi.aidl;

import dev.nacho.wibox.externalapi.aidl.ActionOutcome;
import dev.nacho.wibox.externalapi.aidl.ApiError;

oneway interface IActionCallback {
    void onOutcome(in ActionOutcome result);
    void onError(in ApiError error);
}
