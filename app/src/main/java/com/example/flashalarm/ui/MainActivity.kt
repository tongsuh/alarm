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
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.flashalarm.data.AlarmRepository
import com.example.flashalarm.data.FlashProfileRepository
import com.example.flashalarm.model.AlarmItem
import com.example.flashalarm.model.FlashProfile
import com.example.flashalarm.scheduler.AlarmScheduler
import com.example.flashalarm.ui.screens.AlarmEditDialog
import com.example.flashalarm.ui.screens.AlarmListScreen
import com.example.flashalarm.ui.screens.FlashProfileManageDialog
import com.example.flashalarm.ui.theme.*
import com.example.flashalarm.util.AlarmAudioHelper

class MainActivity : ComponentActivity() {

    private lateinit var alarmRepo: AlarmRepository
    private lateinit var profileRepo: FlashProfileRepository

    private var currentEditingAlarmId: Long = System.currentTimeMillis()
    private var selectedAudioUriState by mutableStateOf<String?>(null)
    private var selectedAudioTitleState by mutableStateOf("默认闹钟铃声")
    private var hasOverlayPermissionState by mutableStateOf(true)
    private var showOverlayPermissionPromptDialog by mutableStateOf(false)

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
                        ringtone.getTitle(this) ?: "已选音乐"
                    } catch (e: Exception) {
                        "已选音乐"
                    }

                    val localPath = AlarmAudioHelper.saveAudioToInternalStorage(this, uri, currentEditingAlarmId)
                    selectedAudioUriState = localPath ?: uri.toString()
                    selectedAudioTitleState = title
                    Toast.makeText(this, "已锁定音频: $title", Toast.LENGTH_SHORT).show()
                }
            }
        }

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

        updateOverlayPermissionState()
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
                        hasOverlayPermission = hasOverlayPermissionState,
                        onRequestOverlayPermission = { requestOverlayPermission() },
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
                            AlarmAudioHelper.deleteInternalAudio(this@MainActivity, alarm.id)
                            alarmRepo.deleteAlarm(alarm.id)
                            alarms = alarmRepo.getAllAlarms()
                        },
                        onEditAlarm = { alarm ->
                            editingAlarm = alarm
                            currentEditingAlarmId = alarm.id
                            selectedAudioUriState = alarm.ringtoneUri
                            selectedAudioTitleState = alarm.ringtoneTitle
                            isEditDialogOpen = true
                        },
                        onAddNewAlarm = {
                            editingAlarm = null
                            currentEditingAlarmId = System.currentTimeMillis()
                            selectedAudioUriState = null
                            selectedAudioTitleState = "默认闹钟铃声"
                            isEditDialogOpen = true
                        },
                        onOpenProfileManager = {
                            isProfileDialogOpen = true
                        }
                    )

                    // 仿 iOS 闹钟添加 / 编辑弹窗
                    if (isEditDialogOpen) {
                        AlarmEditDialog(
                            initialAlarm = editingAlarm,
                            profiles = profiles,
                            hasOverlayPermission = hasOverlayPermissionState,
                            onRequestOverlayPermission = { requestOverlayPermission() },
                            onSave = { savedAlarm ->
                                alarmRepo.saveAlarm(savedAlarm)
                                if (savedAlarm.isEnabled) {
                                    AlarmScheduler.scheduleAlarm(this@MainActivity, savedAlarm)
                                }
                                alarms = alarmRepo.getAllAlarms()
                                isEditDialogOpen = false
                                if (savedAlarm.isFlashEnabled && !hasOverlayPermissionState) {
                                    Toast.makeText(this@MainActivity, "⚠️ 闹钟已存储。请开启悬浮窗权限，使用其他应用时屏幕才能闪烁！", Toast.LENGTH_LONG).show()
                                    showOverlayPermissionPromptDialog = true
                                } else {
                                    Toast.makeText(this@MainActivity, "闹钟已存储", Toast.LENGTH_SHORT).show()
                                }
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
                            onTestProfile = { profile ->
                                runProfilePreviewTest(profile)
                            },
                            onDismiss = { isProfileDialogOpen = false }
                        )
                    }

                    // 核心权限引导弹窗：在其他应用上层显示 (悬浮窗)
                    if (showOverlayPermissionPromptDialog) {
                        AlertDialog(
                            onDismissRequest = { showOverlayPermissionPromptDialog = false },
                            title = {
                                Text("开启「在其他应用上层显示」", color = IosTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            },
                            text = {
                                Text(
                                    text = "闪屏闹钟的核心机制为屏幕规律闪烁唤醒。\n\n当您在使用微信聊天、刷视频或玩游戏时，必须开启【在其他应用上层显示 / 悬浮窗】权限，闹钟到期时才能直接在屏幕最顶层规律闪烁唤醒您。\n\n（小米/红米手机用户还请在应用权限设置中勾选开启「后台弹出界面」）",
                                    color = IosTextSecondary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showOverlayPermissionPromptDialog = false
                                        requestOverlayPermission()
                                    }
                                ) {
                                    Text("前往设置开启", color = IosOrange, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showOverlayPermissionPromptDialog = false }) {
                                    Text("稍后再说", color = IosTextSecondary)
                                }
                            },
                            containerColor = IosCardSurface
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val wasGranted = hasOverlayPermissionState
        updateOverlayPermissionState()
        if (!wasGranted && hasOverlayPermissionState) {
            Toast.makeText(this, "✅ 悬浮闪屏权限已就绪！在其他应用界面时将正常闪烁", Toast.LENGTH_SHORT).show()
            showOverlayPermissionPromptDialog = false
        }
    }

    private fun updateOverlayPermissionState() {
        hasOverlayPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                Toast.makeText(this, "开启【在其他应用上层显示/悬浮窗】，在使用微信玩游戏时屏幕才能直接闪烁！", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }

    private fun launchAudioPicker() {
        try {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择闹钟音频或本地音乐")
            }
            ringtonePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "打开音频选择器失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 运行固定10秒时长的亮屏效果测试
     */
    private fun runProfilePreviewTest(profile: FlashProfile) {
        val alertIntent = Intent(this, AlarmAlertActivity::class.java).apply {
            putExtra(AlarmAlertActivity.EXTRA_ALARM_ID, 888888L)
            putExtra(AlarmAlertActivity.EXTRA_ALARM_LABEL, "亮屏效果测试")
            putExtra(AlarmAlertActivity.EXTRA_IS_SOUND_ENABLED, false)
            putExtra(AlarmAlertActivity.EXTRA_IS_FLASH_ENABLED, true)
            putExtra(AlarmAlertActivity.EXTRA_IS_PREVIEW_MODE, true)
            putExtra(AlarmAlertActivity.EXTRA_TARGET_COLOR_HEX, profile.targetColorHex)
            putExtra(AlarmAlertActivity.EXTRA_TARGET_BRIGHTNESS, profile.targetBrightness)
            putExtra(AlarmAlertActivity.EXTRA_ON_DURATION_MS, profile.onDurationMs)
            putExtra(AlarmAlertActivity.EXTRA_OFF_DURATION_MS, profile.offDurationMs)
            putExtra(AlarmAlertActivity.EXTRA_AUTO_DISMISS_SEC, 10)
        }
        startActivity(alertIntent)
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showOverlayPermissionPromptDialog = true
        }

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

        // 忽略电池优化申请
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
