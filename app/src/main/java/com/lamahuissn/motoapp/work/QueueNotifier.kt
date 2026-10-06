package com.lamahuissn.motoapp.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.lamahuissn.motoapp.R

/**
 * The two things the phone says without being opened (spec 010).
 *
 * The collector put the phone in his pocket with cash recorded and nothing sent. He should not have to
 * open the app to learn whether his work made it: one notification says what is still waiting, another
 * says what arrived — and both disappear when nothing is owed, because a notification that outlives its
 * fact is noise a man learns to ignore.
 *
 * Android 13 and later ask the person first; a phone that refused still has the queue, the chip on the
 * screen and the card on the house — it simply is not spoken to.
 */
object QueueNotifier {

    private const val CHANNEL = "motoapp_queue"
    private const val WAITING = 41
    private const val DELIVERED = 42

    fun waiting(context: Context, owed: Int) = show(
        context = context,
        id = WAITING,
        title = context.getString(R.string.notif_waiting_title),
        text = context.resources.getQuantityString(R.plurals.notif_waiting_text, owed, owed),
        ongoing = true,
    )

    fun delivered(context: Context, count: Int) = show(
        context = context,
        id = DELIVERED,
        title = context.getString(R.string.notif_delivered_title),
        text = context.resources.getQuantityString(R.plurals.notif_delivered_text, count, count),
        ongoing = false,
    )

    /** Nothing is owed any more: the reminder goes with the fact it described. */
    fun clear(context: Context) {
        manager(context)?.cancel(WAITING)
    }

    private fun show(context: Context, id: Int, title: String, text: String, ongoing: Boolean) {
        val notifications = manager(context) ?: return

        // A phone that has not granted the notification permission is not an error: it just is not spoken
        // to. (The permission does not exist before Android 13, where notifications are allowed by default.)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
        )

        val notification = Notification.Builder(context, CHANNEL)
            // A system drawable on purpose: a notification icon ships as a monochrome asset per shape, and
            // the phone already has one that means «syncing» on every version of Android.
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .build()

        notifications.notify(id, notification)
    }

    private fun manager(context: Context): NotificationManager? =
        context.getSystemService(NotificationManager::class.java)
}
