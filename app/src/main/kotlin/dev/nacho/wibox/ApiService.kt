package dev.nacho.wibox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * Mantiene vivo el proceso para que el API HTTP local siga escuchando aunque el
 * usuario cierre la app. No toma wake lock: el movil puede dormir y el API
 * responde igual porque la radio se despierta con el paquete.
 */
class ApiService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        runCatching { (application as? WiboxApplication)?.container?.startLocalApi() }
            .onFailure { android.util.Log.d("WIBOX-API", "startLocalApi fallo: " + it.message) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { (application as? WiboxApplication)?.container?.stopLocalApi() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wibox API",
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.description = "Servidor local para Home Assistant y otras apps"
            manager?.createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Wibox")
            .setContentText("API local activa en el puerto " + BuildConfig.WIBOX_API_PORT)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "wibox_api"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            if (BuildConfig.WIBOX_API_TOKEN.isEmpty()) return
            val intent = Intent(context, ApiService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
