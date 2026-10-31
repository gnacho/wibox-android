package dev.nacho.wibox

import android.app.Application
import dev.nacho.wibox.data.AppContainer

class WiboxApplication : Application() {
    val container: AppContainer by lazy { AppContainer.local(this) }
}
