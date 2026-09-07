package com.example.flashalarm.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.flashalarm.data.AlarmRepository
import com.example.flashalarm.data.FlashProfileRepository
import com.example.flashalarm.model.AlarmItem
import com.example.flashalarm.model.FlashProfile
import com.example.flashalarm.scheduler.AlarmScheduler
import com.example.flashalarm.ui.screens.AlarmEditDialog
import com.example.flashalarm.ui.screens.AlarmListScreen
import com.example.flashalarm.ui.screens.FlashProfileManageDialog
import com.example.flashalarm.ui.theme.FlashAlarmTheme

class MainActivity : ComponentActivity() {

    private lateinit var alarmRepo: AlarmRepository
    private lateinit var profileRepo: FlashProfileRepository

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "需要通知权限以便在锁屏上唤醒闹钟", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        alarmRepo = AlarmRepository(this)
        profileRepo = FlashProfileRepository(this)

        checkAndRequestPermissions()

        setContent {
            FlashAlarmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var alarms by remember { mutableStateOf(alarmRepo.getAllAlarms()) }
                    var profiles by remember { mutableStateOf(profileRepo.getAllProfiles()) }

                    var editingAlarm by remember { mutableStateOf<AlarmItem?>(null) }
                    var isEditDialogOpen by remember { mutableStateOf(false) }
                    var isProfileDialogOpen by remember { mutableStateOf(false) }

                    AlarmListScreen(
                        alarms = alarms,
                        profiles = profiles,
                        onToggleAlarm = { alarm, enabled ->
                            val updated = alarm.copy(isEnabled = enabled)
                            alarmRepo.saveAlarm(updated)
                            if (enabled) {
                                AlarmScheduler.scheduleAlarm(this@MainActivity, updated)
                            } else {
                                AlarmScheduler.cancelAlarm(this@MainActivity, alarm.id)
                            }
                            alarms = alarmRepo.getAllAlarms()
                        },
                        onDeleteAlarm = { alarm ->
                            AlarmScheduler.cancelAlarm(this@MainActivity, alarm.id)
                            alarmRepo.deleteAlarm(alarm.id)
                            alarms = alarmRepo.getAllAlarms()
                        },
                        onEditAlarm = { alarm ->
                            editingAlarm = alarm
                            isEditDialogOpen = true
                        },
                        onAddNewAlarm = {
                            editingAlarm = null
                            isEditDialogOpen = true
                        },
                        onOpenProfileManager = {
                            isProfileDialogOpen = true
                        },
                        onQuickTest = {
                            runQuick5SecondTest(profiles.firstOrNull() ?: FlashProfile.PRESET_SUNRISE)
                        }
                    )

                    // 闹钟添加 / 编辑对话框
                    if (isEditDialogOpen) {
                        AlarmEditDialog(
                            initialAlarm = editingAlarm,
                            profiles = profiles,
                            onSave = { savedAlarm ->
                                alarmRepo.saveAlarm(savedAlarm)
                                if (savedAlarm.isEnabled) {
                                    AlarmScheduler.scheduleAlarm(this@MainActivity, savedAlarm)
                                }
                                alarms = alarmRepo.getAllAlarms()
                                isEditDialogOpen = false
                                Toast.makeText(this@MainActivity, "闹钟已保存并生效", Toast.LENGTH_SHORT).show()
                            },
                            onDismiss = { isEditDialogOpen = false },
                            onOpenProfileManager = { isProfileDialogOpen = true }
                        )
                    }

                    // 亮屏闪烁模板管理对话框
                    if (isProfileDialogOpen) {
                        FlashProfileManageDialog(
                            profiles = profiles,
                            onSaveProfile = { newProfile ->
                                profileRepo.saveProfile(newProfile)
                                profiles = profileRepo.getAllProfiles()
                                Toast.makeText(this@MainActivity, "模板已保存", Toast.LENGTH_SHORT).show()
                            },
                            onDeleteProfile = { profileId ->
                                profileRepo.deleteProfile(profileId)
                                profiles = profileRepo.getAllProfiles()
                            },
                            onDismiss = { isProfileDialogOpen = false }
                        )
                    }
                }
            }
        }
    }

    /**
     * 5秒快速测试闹钟（方便立即验证锁屏唤醒与闪烁效果）
     */
    private fun runQuick5SecondTest(profile: FlashProfile) {
        val testAlarm = AlarmItem(
            id = 999999L,
            hour = 0,
            minute = 0,
            label = "5秒测试闹钟",
            isEnabled = true,
            repeatDays = emptySet(),
            autoDismissSec = 30,
            flashProfileId = profile.id
        )
        alarmRepo.saveAlarm(testAlarm)

        // 5 秒后唤醒
        val triggerAt = System.currentTimeMillis() + 5000L

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val showIntent = Intent(this, MainActivity::class.java)
        val showPendingIntent = android.app.PendingIntent.getActivity(
            this, 999999, showIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val alertIntent = Intent(this, com.example.flashalarm.receiver.AlarmReceiver::class.java).apply {
            putExtra("EXTRA_ALARM_ID", 999999L)
        }
        val broadcastPendingIntent = android.app.PendingIntent.getBroadcast(
            this, 999999, alertIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showPendingIntent),
            broadcastPendingIntent
        )

        Toast.makeText(this, "测试闹钟将在 5 秒后触发！请立刻按电源键锁屏测试。", Toast.LENGTH_LONG).show()
    }

    /**
     * 适配 Android 12/13/14+ 权限检查
     */
    private fun checkAndRequestPermissions() {
        // 1. Android 13+ 通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. Android 12+ 精确闹钟权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 3. Android 14+ 全屏意图权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.canUseFullScreenIntent()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
