package com.squeve.redail.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.squeve.redail.MainActivity
import com.squeve.redail.R

/** Home screen widget with START (last run) and STOP buttons. */
class RedialWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val start = PendingIntent.getActivity(
            context, 21,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_START_LAST)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            flags,
        )
        val stop = PendingIntent.getService(
            context, 22,
            Intent(context, RedialService::class.java).setAction(RedialService.ACTION_STOP),
            flags,
        )
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_redail)
            views.setOnClickPendingIntent(R.id.widget_start, start)
            views.setOnClickPendingIntent(R.id.widget_stop, stop)
            manager.updateAppWidget(id, views)
        }
    }
}
