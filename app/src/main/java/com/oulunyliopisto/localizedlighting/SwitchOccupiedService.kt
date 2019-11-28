package com.oulunyliopisto.localizedlighting

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.android.volley.toolbox.Volley
import java.util.*
import java.util.Timer
import androidx.core.app.NotificationCompat
import android.app.PendingIntent
import android.app.NotificationManager
import android.widget.RemoteViews



class SwitchOccupiedService : Service() {
    private val myBinder = MyLocalBinder()
    private var mNotificationManager: NotificationManager? = null
    private val timer = Timer()


    override fun onBind(intent: Intent): IBinder? {
        return myBinder
    }

    override fun onCreate() {
        super.onCreate()
        mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    }


    inner class MyLocalBinder : Binder() {
        fun getService() : SwitchOccupiedService {
            return this@SwitchOccupiedService
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        println("Service started.")
        startForeground(999, getNotification(applicationContext))

        return Service.START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        println("Service destroyed.")
        timer.cancel()
        stopForeground(true)
    }


    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }


    companion object {
        const val CHANNEL_ID = "localizedlighting.oulunyliopisto.com.CHANNEL_ID"
        const val CHANNEL_NAME = "Localized lighting foreground service"
    }
    private fun createChannel(context: Context) {
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            //val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val notificationChannel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            notificationChannel.enableVibration(false)
            notificationChannel.setShowBadge(true)
            notificationChannel.enableLights(false)
            //notificationChannel.lightColor = Color.parseColor("#e8334a")
            //notificationChannel.description = "notification channel description"
            notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            mNotificationManager?.createNotificationChannel(notificationChannel)
        }

    }

    private fun getNotification(context: Context): Notification {
        createChannel(context)

        val notifyIntent = Intent(context, MainActivity::class.java)
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        //val pendingIntent = PendingIntent.getActivity(context, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val pendingIntent = PendingIntent.getActivity(context, 0, notifyIntent, PendingIntent.FLAG_NO_CREATE)

        val entryIntent = Intent(this, EntryActivity::class.java)

        entryIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        entryIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        entryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val buttonIntent  = PendingIntent.getActivity(context, 1, entryIntent, PendingIntent.FLAG_UPDATE_CURRENT)


        val notificationBuilder: NotificationCompat.Builder

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
        } else {
            notificationBuilder = NotificationCompat.Builder(this)
        }



        notificationBuilder.apply{

            setContentIntent(pendingIntent)  // Set the intent to be fired when the notification is tapped
            //setContent(remoteViews)  // for Android < API level 16
            //setCustomContentView(remoteViews)
            setStyle(NotificationCompat.DecoratedCustomViewStyle())
            setSmallIcon(R.drawable.ic_lightbulb_on_outline)
            //setAutoCancel(true)
            setContentTitle(getString(R.string.foreground_notification_title))
            //setStyle(NotificationCompat.BigTextStyle()
            //    .bigText(message))
            setContentText(getString(R.string.foreground_notification_text))
            setVibrate(LongArray(0))  // disable vibrate
            setShowWhen(false)
            addAction(
                NotificationCompat.Action(
                    R.drawable.ic_launcher_background,
                    getString(R.string.foreground_notification_button),
                    buttonIntent
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        return notificationBuilder.build()

    }
}