package info.plateaukao.einkbro.proxy.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import info.plateaukao.einkbro.R
import info.plateaukao.einkbro.activity.SettingActivity
import info.plateaukao.einkbro.activity.SettingRoute
import info.plateaukao.einkbro.core.mihomo.api.MihomoTunConfig
import info.plateaukao.einkbro.core.mihomo.api.MihomoTunController
import info.plateaukao.einkbro.core.mihomo.runtime.MihomoSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MihomoVpnService : VpnService() {
    private val tunController: MihomoTunController by inject()
    private val sessionManager: MihomoSessionManager by inject()
    private val runtime: StrictVpnRuntime by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var tunFd: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        promoteToForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (tunFd != null) return START_STICKY

        runtime.starting()
        scope.launch {
            try {
                startTunnel()
                runtime.running()
            } catch (error: Throwable) {
                runtime.failed(error)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startTunnel() {
        check(sessionManager.currentSession() != null) {
            "Mihomo core must be running before Strict VPN starts"
        }

        val descriptor = checkNotNull(
            Builder()
                .setSession(getString(R.string.proxy_vpn_session))
                .setMtu(MTU)
                .addAddress(IPV4_ADDRESS, IPV4_PREFIX)
                .addRoute("0.0.0.0", 0)
                .addAddress(IPV6_ADDRESS, IPV6_PREFIX)
                .addRoute("::", 0)
                .addDnsServer(IPV4_DNS)
                .addDnsServer(IPV6_DNS)
                .addAllowedApplication(packageName)
                .setBlocking(false)
                .establish()
        ) {
            "VPN permission is missing or the TUN interface could not be established"
        }
        tunFd = descriptor

        tunController.startTun(
            fd = descriptor.fd,
            config = MihomoTunConfig(
                address = "$IPV4_ADDRESS/$IPV4_PREFIX,$IPV6_ADDRESS/$IPV6_PREFIX",
                dns = "$IPV4_DNS,$IPV6_DNS",
                mtu = MTU,
            ),
            protect = { fd ->
                if (!protect(fd)) {
                    runtime.failed(
                        IllegalStateException("VpnService.protect() rejected mihomo outbound fd")
                    )
                    stopSelf()
                }
            },
            resolverProcess = { _, _, _, uid ->
                if (uid >= 0) "$uid\n$packageName" else packageName
            },
        )
    }

    override fun onRevoke() {
        runtime.failed(SecurityException("VPN permission was revoked"))
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        runCatching { tunController.stopTun() }
        runCatching { tunFd?.close() }
        tunFd = null
        if (runtime.state.value !is StrictVpnState.Failed) runtime.stopped()
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun promoteToForeground() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            SettingActivity.createIntent(this, SettingRoute.Proxy),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(getString(R.string.proxy_vpn_notification_title))
            .setContentText(getString(R.string.proxy_vpn_notification_text))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.proxy_vpn_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    companion object {
        const val ACTION_START = "info.plateaukao.einkbro.mihomo.VPN_START"
        const val ACTION_STOP = "info.plateaukao.einkbro.mihomo.VPN_STOP"

        private const val CHANNEL_ID = "MIHOMO_VPN"
        private const val NOTIFICATION_ID = 7019
        private const val MTU = 1400
        private const val IPV4_ADDRESS = "172.19.0.1"
        private const val IPV4_PREFIX = 30
        private const val IPV4_DNS = "1.1.1.1"
        private const val IPV6_ADDRESS = "fdfe:dcba:9876::1"
        private const val IPV6_PREFIX = 126
        private const val IPV6_DNS = "2606:4700:4700::1111"
    }
}
