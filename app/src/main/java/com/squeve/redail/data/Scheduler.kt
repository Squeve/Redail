package com.squeve.redail.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.squeve.redail.model.RunPlan
import com.squeve.redail.service.ScheduleReceiver

/** One scheduled run at a time, kept across reboots. */
object Scheduler {
    const val ACTION_FIRE = "com.squeve.redail.SCHEDULE_FIRE"

    fun canBeExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun schedule(ctx: Context, atMs: Long, plan: RunPlan) {
        RunStore.saveSchedule(ctx, atMs, plan)
        arm(ctx, atMs)
    }

    fun cancel(ctx: Context) {
        RunStore.clearSchedule(ctx)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
    }

    /** Called after a reboot: put a still-future schedule back. */
    fun rearm(ctx: Context) {
        val s = RunStore.loadSchedule(ctx) ?: return
        if (s.atMs > System.currentTimeMillis()) arm(ctx, s.atMs) else RunStore.clearSchedule(ctx)
    }

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 7,
        Intent(ctx, ScheduleReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun arm(ctx: Context, atMs: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (canBeExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending(ctx))
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending(ctx))
        }
    }
}
