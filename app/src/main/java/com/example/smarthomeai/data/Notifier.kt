package com.example.smarthomeai.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.smarthomeai.MainActivity

class Notifier(private val context: Context) {
    companion object { const val EXTRA_ALERT_ID = "com.example.smarthomeai.ALERT_ID" }
    init {
        if(Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("smart_home_alerts","Cảnh báo ngôi nhà",NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Chuyển động, khuôn mặt chưa đăng ký và cửa mở lâu" })
    }
    @SuppressLint("MissingPermission")
    fun notify(title: String, text: String, alertId: String) {
        if(Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java)
            .setData(Uri.Builder().scheme("smarthome").authority("events").appendPath(alertId).build())
            .putExtra(EXTRA_ALERT_ID, alertId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=NotificationCompat.Builder(context,"smart_home_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(intent).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(context).notify(alertId, 1, notification) }
    }
}
