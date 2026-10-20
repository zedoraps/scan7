package net.moesli.scan7.share

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.moesli.scan7.MainActivity
import net.moesli.scan7.R
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.ui.text.resolve

/**
 * Foreground service while this phone hosts or joins a game over the radio (docs/dev/MULTI_DEVICE.md
 * §6.1): it only keeps the process (and so the [ShareRuntime] that owns session + transport on its
 * single thread) alive with the screen off or the camera open. Started / stopped by [Radio.sessionActive].
 * The notification follows the share state ([ShareText.notice]) and has a Stop sharing / Leave game
 * button; the in-app confirmations don't apply there (a notification action can't ask), and both
 * are easy to undo (share again / scan the code again).
 */
class ShareService : Service() {
    private val scope = MainScope()
    private var observing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runtime = AppGraph.share(this)
        when (intent?.action) {
            ACTION_STOP_SHARING -> runtime.stopHosting()
            ACTION_LEAVE -> runtime.leave()
        }
        // A button of a notification left over from a killed process: nothing to show, just go.
        if (intent?.action != null && ShareText.notice(runtime.state.value) == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.share_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        // Every start must call startForeground; the runtime stops the service once the session ends.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(ShareText.notice(runtime.state.value)), type)
        if (!observing) {
            observing = true
            scope.launch {
                runtime.state.map(ShareText::notice).distinctUntilChanged().collect { notice ->
                    if (notice != null) manager.notify(NOTIFICATION_ID, notification(notice))
                }
            }
        }
        // Not sticky: after the system kills the process there is no session to keep alive, and
        // ShareRuntime starts the service again when it restores one.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(notice: ShareNotice?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(notice?.title?.resolve(resources) ?: getString(R.string.share_notification_title))
            .setContentText(notice?.text?.resolve(resources) ?: getString(R.string.share_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        notice?.let { n ->
            val (action, label) = when (n.action) {
                ShareNotice.Action.STOP_SHARING -> ACTION_STOP_SHARING to R.string.share_stop
                ShareNotice.Action.LEAVE -> ACTION_LEAVE to R.string.menu_leave
            }
            val pending = PendingIntent.getService(
                this, n.action.ordinal + 1, Intent(this, ShareService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, getString(label), pending)
        }
        return builder.build()
    }

    private companion object {
        const val CHANNEL = "multiplayer"
        const val NOTIFICATION_ID = 7
        const val ACTION_STOP_SHARING = "net.moesli.scan7.share.STOP_SHARING"
        const val ACTION_LEAVE = "net.moesli.scan7.share.LEAVE"
    }
}
