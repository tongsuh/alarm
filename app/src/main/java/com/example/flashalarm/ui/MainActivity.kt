package com.example.flashalarm.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
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
import com.example.flashalarm.ui.theme.IosBackground

class MainActivity : ComponentActivity() {

    private lateinit var alarmRepo: AlarmRepository
    private lateinit var profileRepo: FlashProfileRepository

    // 当前选中的自定义音频状态
    private var selectedAudioUriState by mutableStateOf<String?>(null)
    private var selectedAudioTitleState by mutableStateOf("默认闹钟铃声")

    // 系统铃声与本地音乐选择器回调
    private val ringtonePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                }

                if (uri != null) {
                    val ringtone = RingtoneManager.getRingtone(this, uri)
                    val title = try {
                        ringtone.getTitle(this) ?: "已选音频"
                    } catch (e: Exception) {
                        "已选音频"
                    }
                    selectedAudioUriState = uri.toString()
                    selectedAudioTitleState = title
                }
            }
        }

    // 通知权限申请回调
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "开启通知权限以确保锁屏点亮", Toast.LENGTH_LONG).show()
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
                    color = IosBackground
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
                            selectedAudioUriState = alarm.ringtoneUri
                            selectedAudioTitleState = alarm.ringtoneTitle
                            isEditDialogOpen = true
                        },
                        onAddNewAlarm = {
                            editingAlarm = null
                            selectedAudioUriState = null
                            selectedAudioTitleState = "默认闹钟铃声"
                            isEditDialogOpen = true
                        },
                        onOpenProfileManager = {
                            isProfileDialogOpen = true
                        },
                        onQuickTest = {
                            runQuick5SecondTest(profiles.firstOrNull() ?: FlashProfile.PRESET_APPLE_WATCH_RED)
                        }
                    )

                    // 仿 iOS 闹钟添加 / 编辑弹窗
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
                                Toast.makeText(this@MainActivity, "闹钟已存储", Toast.LENGTH_SHORT).show()
                            },
                            onDismiss = { isEditDialogOpen = false },
                            onPickAudio = { launchAudioPicker() },
                            onOpenProfileManager = { isProfileDialogOpen = true },
                            currentSelectedAudioTitle = selectedAudioTitleState,
                            currentSelectedAudioUri = selectedAudioUriState
                        )
                    }

                    // 亮屏闪烁模板管理弹窗
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
     * 启动系统音频与铃声选择器
     */
    private fun launchAudioPicker() {
        try {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择闹钟音频或本地音乐")
                selectedAudioUriState?.let {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                }
            }
            ringtonePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "打开音频选择器失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 5秒快速测试闹钟（方便立即锁屏验证夜视深红与声音效果）
     */
    private fun runQuick5SecondTest(profile: FlashProfile) {
        val testAlarm = AlarmItem(
            id = 999999L,
            hour = 0,
            minute = 0,
            label = "5秒快速测试",
            isEnabled = true,
            repeatDays = emptySet(),
            isSoundEnabled = true,
            isFlashEnabled = true,
            ringtoneUri = null,
            ringtoneTitle = "系统默认铃声",
            autoDismissSec = 30,
            flashProfileId = profile.id
        )
        alarmRepo.saveAlarm(testAlarm)

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

        Toast.makeText(this, "已设定测试闹钟，请在 5 秒内锁屏测试！", Toast.LENGTH_LONG).show()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

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
