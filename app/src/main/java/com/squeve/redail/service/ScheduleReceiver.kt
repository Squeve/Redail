package com.squeve.redail.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.squeve.redail.data.EventLog
import com.squeve.redail.data.RunStore
import com.squeve.redail.data.Scheduler

/** Fires the scheduled run. Not exported, only our own alarm can reach it. */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Scheduler.ACTION_FIRE) return
        val s = RunStore.loadSchedule(context) ?: return
        RunStore.clearSchedule(context)
        EventLog.add("Scheduled run starting")
        RunLauncher.start(context, s.plan)
    }
}

/** Puts a pending schedule back after the phone restarts. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Scheduler.rearm(context)
    }
}
