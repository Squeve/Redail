package com.squeve.redail.service

import android.content.Context
import android.content.Intent
import com.squeve.redail.data.RunStore
import com.squeve.redail.model.RunPlan

/** Single entry point for starting / stopping a run from the UI, shortcuts, tile, widget and alarms. */
object RunLauncher {
    fun start(ctx: Context, plan: RunPlan) {
        if (plan.jobs.isEmpty()) return
        val app = ctx.applicationContext
        RedialService.pendingQueue = plan.jobs
        RedialService.pendingOrder = plan.order
        RedialService.pendingSimSlot = plan.simSlot
        RedialService.pendingDry = plan.dry
        if (!plan.dry) RunStore.saveLast(app, plan)
        app.startForegroundService(Intent(app, RedialService::class.java).setAction(RedialService.ACTION_START))
    }

    fun stop(ctx: Context) {
        val app = ctx.applicationContext
        app.startService(Intent(app, RedialService::class.java).setAction(RedialService.ACTION_STOP))
    }
}
