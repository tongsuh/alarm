package com.example.flashalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.flashalarm.data.AlarmRepository
import com.example.flashalarm.scheduler.AlarmScheduler

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val repository = AlarmRepository(context)
            val alarms = repository.getAllAlarms()
            for (alarm in alarms) {
                if (alarm.isEnabled) {
                    AlarmScheduler.scheduleAlarm(context, alarm)
                }
            }
        }
    }
}
