package com.example

import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.example.data.AttendanceSession
import com.example.data.JalaliCalendar
import com.example.ui.AttendanceViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        AttendanceScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AttendanceScreen(
    modifier: Modifier = Modifier,
    viewModel: AttendanceViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val allSessions by viewModel.allSessions.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val timerText by viewModel.currentTimerText.collectAsState()

    // Real-time ticking Gregorian Calendar to format Shamsi date/time
    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

    val currentPersianDate = JalaliCalendar.getPersianFullDateString(currentTimeMillis)
    val currentPersianTime = viewModel.convertToPersianDigits(JalaliCalendar.getPersianTimeString(currentTimeMillis))

    // Form registration states
    var locationModeIsGps by remember { mutableStateOf(true) }
    var workType by remember { mutableStateOf("WORK") } // WORK, REMOTE, MISSION, VACATION
    var manualLocationText by remember { mutableStateOf("") }
    var attendanceNote by remember { mutableStateOf("") }

    // GPS local coordinate storage
    var gpsLatitude by remember { mutableStateOf<Double?>(null) }
    var gpsLongitude by remember { mutableStateOf<Double?>(null) }
    var gpsAddressResolved by remember { mutableStateOf<String?>(null) }
    var isFetchingGps by remember { mutableStateOf(false) }

    // Backup past registration states
    var showManualHistoryAdd by remember { mutableStateOf(false) }

    // Fused Location Provider Client
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // Permission check
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Location fetcher helper
    fun fetchGpsCoordinates() {
        // Confirm permission
        val fineGranted = locationPermissions.permissions.any { 
            it.permission == Manifest.permission.ACCESS_FINE_LOCATION && it.status is PermissionStatus.Granted 
        }
        val coarseGranted = locationPermissions.permissions.any { 
            it.permission == Manifest.permission.ACCESS_COARSE_LOCATION && it.status is PermissionStatus.Granted 
        }

        if (!fineGranted && !coarseGranted) {
            locationPermissions.launchMultiplePermissionRequest()
            return
        }

        isFetchingGps = true
        scope.launch(Dispatchers.IO) {
            try {
                val cts = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cts.token
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        gpsLatitude = location.latitude
                        gpsLongitude = location.longitude
                        
                        // Address resolution
                        try {
                            val geocoder = Geocoder(context, Locale("fa", "IR"))
                            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                gpsAddressResolved = addresses[0].getAddressLine(0)
                            } else {
                                gpsAddressResolved = "موقعیت جغرافیایی (${"%.4f".format(location.latitude)} , ${"%.4f".format(location.longitude)})"
                            }
                        } catch (e: Exception) {
                            gpsAddressResolved = "موقعیت جغرافیایی (${"%.4f".format(location.latitude)} , ${"%.4f".format(location.longitude)})"
                        }
                    } else {
                        scope.launch(Dispatchers.Main) {
                            Toast.makeText(context, "یافتن مکان ناموفق بود. مجدداً تلاش کنید.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    isFetchingGps = false
                }.addOnFailureListener {
                    isFetchingGps = false
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "خطا در دریافت GPS.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: SecurityException) {
                isFetchingGps = false
            }
        }
    }

    // Trigger instant location fetch on selecting GPS switch or startup
    LaunchedEffect(locationModeIsGps) {
        if (locationModeIsGps && activeSession == null) {
            fetchGpsCoordinates()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = WindowInsets.navigationBars.asPaddingValues()
    ) {
        // 1. Hero Image & Clock Header Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_attendance_banner),
                    contentDescription = "Attendance Banner Banner Illustration",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Dark overlay gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )

                // Date and Time Text Overlays (Persian RTL aligned)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = currentPersianDate,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentPersianTime,
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // 2. Statistics Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Total hours card
                val totalSeconds = allSessions.sumOf { it.durationSeconds ?: 0L }
                val totalHoursText = viewModel.formatDurationPersian(totalSeconds)
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Total Hours Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "مجموع ساعت کاری",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = totalHoursText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Total sessions count card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Total Shifts",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "تعداد شیفت‌ها",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = viewModel.convertToPersianDigits("${allSessions.size} شیفت"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 3. Main Action Punch Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Status Badge with pulse effect indicator
                    val isCheckedIn = activeSession != null
                    val statusColor by animateColorAsState(
                        targetValue = if (isCheckedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        label = "statusColor"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusColor.copy(alpha = 0.1f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = CircleShape,
                            color = statusColor
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCheckedIn) {
                                "شما وارد شده‌اید - در حال محاسبه کارکرد..."
                            } else {
                                "شما خارج شده‌اید - زمان آغاز کار را ثبت کنید."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Active Running Timer display
                    if (isCheckedIn) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = timerText,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.testTag("running_timer")
                        )
                        Text(
                            text = "زمان طی شده دوندگی کارکرد فعلی",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Configuration Options (Only active/available when NOT clocked in)
                    AnimatedVisibility(visible = !isCheckedIn) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Section: Type of Work (Segmented Tabs / Group)
                            Text(
                                text = "نوع حضور کاری:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val types = listOf(
                                    Triple("WORK", "حضوری", Icons.Default.Home),
                                    Triple("REMOTE", "دورکاری", Icons.Default.PlayArrow),
                                    Triple("MISSION", "ماموریت", Icons.Default.LocationOn),
                                    Triple("VACATION", "مرخصی", Icons.Default.DateRange)
                                )
                                types.forEach { (typeVal, typeLabel, typeIcon) ->
                                    val isSelected = workType == typeVal
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable { workType = typeVal }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = typeIcon,
                                                contentDescription = typeLabel,
                                                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = typeLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }

                            // Section: Location Mode (GPS Scan vs Manual typed address)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { locationModeIsGps = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (locationModeIsGps) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
                                    ),
                                    border = ButtonDefaults.outlinedButtonBorder(
                                        enabled = true
                                    ).let {
                                        if (locationModeIsGps) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                        else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                    }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocationOn, contentDescription = "GPS")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("موقعیاب (GPS)")
                                    }
                                }

                                OutlinedButton(
                                    onClick = { locationModeIsGps = false },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (!locationModeIsGps) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
                                    ),
                                    border = if (!locationModeIsGps) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) 
                                             else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = "Manual Site")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("ثبت دستی مکان")
                                    }
                                }
                            }

                            // Dynamic location input block
                            if (locationModeIsGps) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "مکان فعلی شما (GPS):",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            
                                            IconButton(
                                                onClick = { fetchGpsCoordinates() },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LocationOn,
                                                    contentDescription = "Refresh GPS",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = if (isFetchingGps) Modifier.size(24.dp) else Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        if (isFetchingGps) {
                                            Text(
                                                text = "در حال دریافت موقعیت دقیق از ماهواره...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        } else {
                                            Text(
                                                text = gpsAddressResolved ?: "موقعیتی یافت نشد. دکمه موقعیاب بالا را لمس کنید تا موقعیت دقیق ثبت شود.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (gpsAddressResolved != null) MaterialTheme.colorScheme.onSurfaceVariant 
                                                        else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value = manualLocationText,
                                    onValueChange = { manualLocationText = it },
                                    label = { Text("نام محل حضور (مثلا: دفتر مرکزی، خانه)") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("location_manual_input"),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                    )
                                )
                            }

                            // Notes Optional input text block
                            OutlinedTextField(
                                value = attendanceNote,
                                onValueChange = { attendanceNote = it },
                                label = { Text("توضیحات و خلاصه وظایف امروز (اختیاری)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("note_input"),
                                singleLine = false,
                                maxLines = 2,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }

                    // If checked-in (punch conclude options)
                    AnimatedVisibility(visible = isCheckedIn) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeSession?.let { active ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "نوع ورود:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    val typeDisplay = when (active.workType) {
                                        "WORK" -> "حضور فیزیکی"
                                        "REMOTE" -> "دورکاری"
                                        "MISSION" -> "ماموریت"
                                        "VACATION" -> "مرخصی"
                                        else -> "نامشخص"
                                    }
                                    Text(
                                        text = typeDisplay,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "ساعت ورود:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = viewModel.convertToPersianDigits(JalaliCalendar.getPersianTimeString(active.checkInTime)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "محل ورود:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = active.checkInLocation ?: "ثبت نشده",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                active.notes?.let { note ->
                                    if (note.isNotBlank()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = "یادداشت ورود:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                            Text(
                                                text = note,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Dynamic CTA Button
                    if (!isCheckedIn) {
                        Button(
                            onClick = {
                                val locationString = if (locationModeIsGps) {
                                    gpsAddressResolved ?: "ثبت شده با GPS"
                                } else {
                                    manualLocationText.ifBlank { "مکان دستی نامشخص" }
                                }

                                viewModel.punchIn(
                                    locationName = locationString,
                                    latitude = if (locationModeIsGps) gpsLatitude else null,
                                    longitude = if (locationModeIsGps) gpsLongitude else null,
                                    isManual = !locationModeIsGps,
                                    workType = workType,
                                    notes = attendanceNote
                                )

                                Toast.makeText(context, "ورود شما با موفقیت ثبت شد.", Toast.LENGTH_SHORT).show()

                                // reset forms
                                manualLocationText = ""
                                attendanceNote = ""
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("punch_in_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Punch In")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ثبت ورود (آغاز شیفت کاری)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                isFetchingGps = true
                                // When checking out, try fetching location dynamically first if was GPS, or use default coordinates.
                                scope.launch {
                                    val fineGranted = locationPermissions.permissions.any { 
                                        it.permission == Manifest.permission.ACCESS_FINE_LOCATION && it.status is PermissionStatus.Granted 
                                    }
                                    var currentLat: Double? = null
                                    var currentLng: Double? = null
                                    var checkOutAddress: String? = null

                                    if (fineGranted) {
                                        try {
                                            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                                                if (loc != null) {
                                                    currentLat = loc.latitude
                                                    currentLng = loc.longitude
                                                    checkOutAddress = "خروج GPS (عرض: ${"%.4f".format(loc.latitude)} , طول: ${"%.4f".format(loc.longitude)})"
                                                }
                                            }
                                        } catch (e: SecurityException) {}
                                    }

                                    // Let GPS finish or just proceed
                                    delay(600) 
                                    viewModel.punchOut(
                                        locationName = checkOutAddress ?: "مکان خروج نامشخص",
                                        latitude = currentLat,
                                        longitude = currentLng,
                                        isManual = currentLat == null
                                    )
                                    isFetchingGps = false
                                    Toast.makeText(context, "خروج شما با موفقیت ثبت شد. خدا قوت!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("punch_out_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Punch Out")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ثبت خروج (پایان کار)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 4. Retrospective / Missed Entry manual form container
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showManualHistoryAdd = !showManualHistoryAdd },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Back date registry",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ثبت دستی کارکرد گذشته (فراموشی)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Text(
                            text = if (showManualHistoryAdd) "بستن فرم ▲" else "باز کردن فرم ▼",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    AnimatedVisibility(visible = showManualHistoryAdd) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            // Fields
                            var mYear by remember { mutableStateOf("1405") }
                            var mMonth by remember { mutableStateOf("1") }
                            var mDay by remember { mutableStateOf("1") }
                            var mWorkType by remember { mutableStateOf("WORK") }

                            var mInHour by remember { mutableStateOf("8") }
                            var mInMinute by remember { mutableStateOf("0") }
                            var mOutHour by remember { mutableStateOf("16") }
                            var mOutMinute by remember { mutableStateOf("30") }

                            var mPlace by remember { mutableStateOf("دفتر مرکزی") }
                            var mNoteText by remember { mutableStateOf("") }

                            // Date pickers row
                            Text(
                                text = "تاریخ شمسی ثبت:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = mYear,
                                    onValueChange = { mYear = it },
                                    label = { Text("سال") },
                                    modifier = Modifier.weight(1.2f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = mMonth,
                                    onValueChange = { mMonth = it },
                                    label = { Text("ماه (۱-۱۲)") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = mDay,
                                    onValueChange = { mDay = it },
                                    label = { Text("روز") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                            }

                            // Work Type Selection
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val listTypes = listOf(
                                    "WORK" to "حضور",
                                    "REMOTE" to "دورکاری",
                                    "MISSION" to "ماموریت"
                                )
                                listTypes.forEach { (v, l) ->
                                    val active = mWorkType == v
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (active) MaterialTheme.colorScheme.secondary 
                                                else MaterialTheme.colorScheme.surface
                                            )
                                            .clickable { mWorkType = v }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = l,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            // Times Row
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                // Punch in hour
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("ساعت ورود:", style = MaterialTheme.typography.labelSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        OutlinedTextField(
                                            value = mInHour,
                                            onValueChange = { mInHour = it },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("ساعت") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = mInMinute,
                                            onValueChange = { mInMinute = it },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("دقیقه") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                    }
                                }

                                // Punch out hour
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("ساعت خروج:", style = MaterialTheme.typography.labelSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        OutlinedTextField(
                                            value = mOutHour,
                                            onValueChange = { mOutHour = it },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("ساعت") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = mOutMinute,
                                            onValueChange = { mOutMinute = it },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("دقیقه") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                    }
                                }
                            }

                            // Place and annotations
                            OutlinedTextField(
                                value = mPlace,
                                onValueChange = { mPlace = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("مکان ثبت") },
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = mNoteText,
                                onValueChange = { mNoteText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("یادداشت / کارهای انجام شده") },
                                singleLine = true
                            )

                            // Save button
                            Button(
                                onClick = {
                                    val y = mYear.toIntOrNull() ?: 1405
                                    val m = mMonth.toIntOrNull() ?: 1
                                    val d = mDay.toIntOrNull() ?: 1
                                    val inH = mInHour.toIntOrNull() ?: 8
                                    val inM = mInMinute.toIntOrNull() ?: 0
                                    val outH = mOutHour.toIntOrNull() ?: 16
                                    val outM = mOutMinute.toIntOrNull() ?: 30

                                    viewModel.addManualPastSession(
                                        year = y,
                                        month = m,
                                        day = d,
                                        checkInHour = inH,
                                        checkInMinute = inM,
                                        checkOutHour = outH,
                                        checkOutMinute = outM,
                                        locationName = mPlace.ifBlank { "دفتر مرکزی" },
                                        workType = mWorkType,
                                        notes = mNoteText
                                    )

                                    Toast.makeText(context, "ساعت کاری گذشته با موفقیت اضافه شد.", Toast.LENGTH_SHORT).show()
                                    showManualHistoryAdd = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("ثبت ساعت کاری در سوابق", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 5. History List Title
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سوابق حضوروغیاب و کارکرد",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (allSessions.isNotEmpty()) {
                    Text(
                        text = "پاکسازی همه سوابق",
                        modifier = Modifier
                            .clickable { viewModel.clearHistory() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 6. History List Elements
        if (allSessions.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Empty History",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "هیچ موردی ثبت نشده است.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "سوابق ساعت کاری شما پس از ثبت ورود و خروج در اینجا نمایش داده خواهند شد.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                        textAlign = Alignment.CenterHorizontally.let { TextAlign.Center }
                    )
                }
            }
        } else {
            items(allSessions) { session ->
                HistoryRecordCard(
                    session = session,
                    onDelete = { viewModel.deleteSession(session) },
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun HistoryRecordCard(
    session: AttendanceSession,
    onDelete: () -> Unit,
    viewModel: AttendanceViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Date + Work type label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick tag label for work type
                    val (lbl, tagColor) = when (session.workType) {
                        "WORK" -> "حضوری" to MaterialTheme.colorScheme.primary
                        "REMOTE" -> "دورکاری" to MaterialTheme.colorScheme.secondary
                        "MISSION" -> "ماموریت" to MaterialTheme.colorScheme.tertiary
                        "VACATION" -> "مرخصی" to Color.Gray
                        else -> "نامشخص" to Color.DarkGray
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(tagColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = lbl,
                            style = MaterialTheme.typography.labelSmall,
                            color = tagColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = viewModel.convertToPersianDigits(session.dateString),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Record",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.65f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(10.dp))

            // Punch In & Punch Out details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // CheckIn specs
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ورود:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = viewModel.convertToPersianDigits(JalaliCalendar.getPersianTimeString(session.checkInTime)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = session.checkInLocation ?: "مکان نامشخص",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // CheckOut specs
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "خروج:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    val outTimeText = if (session.checkOutTime != null) {
                        viewModel.convertToPersianDigits(JalaliCalendar.getPersianTimeString(session.checkOutTime))
                    } else {
                        "ساعت کاری باز"
                    }
                    Text(
                        text = outTimeText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (session.checkOutTime != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = session.checkOutLocation ?: "مکان نامشخص",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Total Calculated Shift Duration
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "مدت کارکرد:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = viewModel.formatDurationPersian(session.durationSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Annotation note if was supplementary
            session.notes?.let { note ->
                if (note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Note",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}
