package com.techseven.foldstandby.ui.setup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.techseven.foldstandby.BuildConfig
import com.techseven.foldstandby.FoldStandByApp
import com.techseven.foldstandby.R
import com.techseven.foldstandby.data.Alarm
import com.techseven.foldstandby.data.AppSettings
import com.techseven.foldstandby.posture.PostureMonitorService
import com.techseven.foldstandby.ui.adaptive.contentMaxWidth
import com.techseven.foldstandby.ui.adaptive.rememberAlarmListDetailSplit
import com.techseven.foldstandby.ui.adaptive.rememberAppWidthClass
import com.techseven.foldstandby.ui.adaptive.screenPadding
import com.techseven.foldstandby.ui.alarm.AlarmEditorScreen
import com.techseven.foldstandby.ui.alarm.AlarmListScreen
import com.techseven.foldstandby.ui.alarm.AlarmViewModel
import com.techseven.foldstandby.ui.nightstand.NightstandActivity
import com.techseven.foldstandby.ui.reflection.ReflectionActivity
import com.techseven.foldstandby.ui.theme.FoldStandByTheme
import com.techseven.foldstandby.ui.theme.NightstandAccent
import com.techseven.foldstandby.ui.theme.NightstandBg
import com.techseven.foldstandby.ui.theme.NightstandInk
import com.techseven.foldstandby.ui.theme.NightstandMuted
import kotlinx.coroutines.launch

class SetupActivity : ComponentActivity() {
    private var permissionTick by mutableIntStateOf(0)
    private val alarmViewModel: AlarmViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FoldStandByApp

        setContent {
            FoldStandByTheme {
                val settings by app.settingsRepository.settings.collectAsState(
                    initial = AppSettings()
                )
                @Suppress("UNUSED_EXPRESSION")
                permissionTick

                val navController = rememberNavController()
                val alarms by alarmViewModel.alarms.collectAsState()
                val useAlarmSplit = rememberAlarmListDetailSplit()
                var selectedAlarmId by remember { mutableStateOf<String?>(null) }

                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        SetupHomeScreen(
                            settings = settings,
                            permissionsGranted = hasAllPermissions(),
                            batteryUnrestricted = isBatteryUnrestricted(),
                            onToggleEnabled = { enabled ->
                                lifecycleScope.launch {
                                    app.settingsRepository.setNightstandEnabled(enabled)
                                    if (enabled) {
                                        requestPermissionsIfNeeded()
                                        PostureMonitorService.start(this@SetupActivity)
                                    } else {
                                        PostureMonitorService.stop(this@SetupActivity)
                                    }
                                }
                            },
                            onToggleForce = { enabled ->
                                lifecycleScope.launch {
                                    app.settingsRepository.setForceNightstand(enabled)
                                    if (enabled) {
                                        PostureMonitorService.start(this@SetupActivity)
                                    }
                                }
                            },
                            onToggleNightTint = { enabled ->
                                lifecycleScope.launch {
                                    app.settingsRepository.setNightTintEnabled(enabled)
                                }
                            },
                            onAccentColor = { color ->
                                lifecycleScope.launch {
                                    app.settingsRepository.setAccentColor(color)
                                }
                            },
                            onGrantPermissions = { requestPermissionsIfNeeded() },
                            onBatterySettings = { openBatterySettings() },
                            onPreview = {
                                startActivity(Intent(this@SetupActivity, NightstandActivity::class.java))
                            },
                            onOpenReflection = {
                                startActivity(Intent(this@SetupActivity, ReflectionActivity::class.java))
                            },
                            onOpenAlarms = { navController.navigate("alarms") }
                        )
                    }
                    composable("alarms") {
                        val editing = alarms.firstOrNull { it.id == selectedAlarmId }
                        AlarmListScreen(
                            alarms = alarms,
                            canScheduleExact = alarmViewModel.canScheduleExact(),
                            selectedAlarmId = selectedAlarmId,
                            onBack = { navController.popBackStack() },
                            onAdd = {
                                val created = Alarm()
                                selectedAlarmId = created.id
                                navController.navigate("alarm_edit/${created.id}?new=true")
                            },
                            onEdit = { alarm ->
                                selectedAlarmId = alarm.id
                                if (!useAlarmSplit) {
                                    navController.navigate("alarm_edit/${alarm.id}")
                                }
                            },
                            onToggle = { alarm, enabled ->
                                alarmViewModel.setEnabled(alarm.id, enabled)
                            },
                            editorPane = if (useAlarmSplit && editing != null) {
                                {
                                    AlarmEditorScreen(
                                        initial = editing,
                                        ringtoneTitle = alarmViewModel::ringtoneTitle,
                                        showBack = false,
                                        onBack = { selectedAlarmId = null },
                                        onSave = { alarm ->
                                            alarmViewModel.save(alarm)
                                            selectedAlarmId = alarm.id
                                        },
                                        onDelete = { id ->
                                            alarmViewModel.delete(id)
                                            selectedAlarmId = null
                                        }
                                    )
                                }
                            } else null
                        )
                    }
                    composable(
                        route = "alarm_edit/{alarmId}?new={isNew}",
                        arguments = listOf(
                            navArgument("alarmId") { type = NavType.StringType },
                            navArgument("isNew") {
                                type = NavType.BoolType
                                defaultValue = false
                            }
                        )
                    ) { entry ->
                        val alarmId = entry.arguments?.getString("alarmId") ?: return@composable
                        val isNew = entry.arguments?.getBoolean("isNew") == true
                        val existing = alarms.firstOrNull { it.id == alarmId }
                        val initial = existing ?: if (isNew) Alarm(id = alarmId) else Alarm(id = alarmId)

                        AlarmEditorScreen(
                            initial = initial,
                            ringtoneTitle = alarmViewModel::ringtoneTitle,
                            showBack = true,
                            onBack = { navController.popBackStack() },
                            onSave = { alarm ->
                                alarmViewModel.save(alarm)
                                selectedAlarmId = alarm.id
                                navController.popBackStack()
                            },
                            onDelete = if (!isNew && existing != null) {
                                { id ->
                                    alarmViewModel.delete(id)
                                    navController.popBackStack()
                                }
                            } else null
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionTick++
    }

    private fun hasAllPermissions(): Boolean {
        val required = mutableListOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) {
            required += Manifest.permission.POST_NOTIFICATIONS
        }
        return required.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissionsIfNeeded() {
        val required = mutableListOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) {
            required += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun isBatteryUnrestricted(): Boolean {
        val pm = getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
            .onFailure {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupHomeScreen(
    settings: AppSettings,
    permissionsGranted: Boolean,
    batteryUnrestricted: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleForce: (Boolean) -> Unit,
    onToggleNightTint: (Boolean) -> Unit,
    onAccentColor: (Int) -> Unit,
    onGrantPermissions: () -> Unit,
    onBatterySettings: () -> Unit,
    onPreview: () -> Unit,
    onOpenReflection: () -> Unit,
    onOpenAlarms: () -> Unit
) {
    val widthClass = rememberAppWidthClass()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NightstandBg)
            .verticalScroll(rememberScrollState())
            .padding(screenPadding(widthClass))
            .then(
                contentMaxWidth(widthClass)?.let { Modifier.widthIn(max = it) } ?: Modifier
            ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = stringResource(R.string.setup_title),
            color = NightstandInk,
            fontSize = 36.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.setup_subtitle),
            color = NightstandMuted,
            fontSize = 16.sp
        )
        Text(
            text = stringResource(R.string.permissions_hint),
            color = NightstandMuted,
            fontSize = 14.sp
        )

        SettingRow(
            title = stringResource(R.string.enable_nightstand),
            checked = settings.nightstandEnabled,
            onCheckedChange = onToggleEnabled
        )
        SettingRow(
            title = stringResource(R.string.night_tint),
            checked = settings.nightTintEnabled,
            onCheckedChange = onToggleNightTint
        )
        if (BuildConfig.DEBUG) {
            SettingRow(
                title = stringResource(R.string.force_nightstand),
                checked = settings.forceNightstand,
                onCheckedChange = onToggleForce
            )
        }

        Text(
            text = stringResource(R.string.nightstand_color),
            color = NightstandInk,
            fontSize = 17.sp
        )
        Text(
            text = stringResource(R.string.nightstand_color_hint),
            color = NightstandMuted,
            fontSize = 13.sp
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppSettings.ACCENT_SWATCHES.forEach { color ->
                val selected = color == settings.accentColorArgb
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .then(
                            if (selected) {
                                Modifier.border(2.dp, NightstandInk, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onAccentColor(color) }
                )
            }
        }

        if (!permissionsGranted) {
            Button(
                onClick = onGrantPermissions,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NightstandAccent,
                    contentColor = NightstandBg
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.grant_permissions))
            }
        }

        if (!batteryUnrestricted) {
            OutlinedButton(
                onClick = onBatterySettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.battery_unrestricted), color = NightstandInk)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onOpenAlarms,
            colors = ButtonDefaults.buttonColors(
                containerColor = NightstandAccent,
                contentColor = NightstandBg
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(stringResource(R.string.alarms), fontSize = 16.sp)
        }

        OutlinedButton(
            onClick = onOpenReflection,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(stringResource(R.string.flex_reflection), color = NightstandInk, fontSize = 16.sp)
        }
        Text(
            text = stringResource(R.string.flex_reflection_hint),
            color = NightstandMuted,
            fontSize = 13.sp
        )

        if (BuildConfig.DEBUG) {
            Button(
                onClick = onPreview,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(settings.accentColorArgb),
                    contentColor = NightstandBg
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.preview_nightstand))
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = NightstandInk,
            fontSize = 17.sp,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
