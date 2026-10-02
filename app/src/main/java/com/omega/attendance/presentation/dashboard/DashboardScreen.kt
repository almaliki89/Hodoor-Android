package com.omega.attendance.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omega.attendance.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PresentGreen = Color(0xFF16856B)
private val LateAmber = Color(0xFFE49A22)
private val AbsentRed = Color(0xFFD85858)
private val StudentBlue = Color(0xFF4174D9)
private val HeaderStart = Color(0xFF075E54)
private val HeaderEnd = Color(0xFF16856B)

private data class Stat(
    val title: String,
    val value: String,
    val supportingText: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun DashboardScreen(vm: DashboardViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val fontScale = LocalDensity.current.fontScale
    val locale = Locale("ar")
    val today = LocalDate.now()
    val dateText = today.format(DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", locale))
    val stats = listOf(
        Stat("إجمالي الطلبة", state.total.toString(), "طالب مسجل", Icons.Rounded.Groups, StudentBlue),
        Stat("الحاضرون", state.present.toString(), "حضور في الموعد", Icons.Rounded.CheckCircle, PresentGreen),
        Stat("المتأخرون", state.late.toString(), "بعد وقت الدوام", Icons.Rounded.Schedule, LateAmber),
        Stat("الغائبون", state.absent.toString(), "لم يسجلوا اليوم", Icons.Rounded.PersonOff, AbsentRed)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { DashboardHeader(state.schoolName, dateText) }
        item { AttendanceOverview(state) }
        item {
            BoxWithConstraints {
                val columns = when {
                    maxWidth >= 760.dp && fontScale <= 1.3f -> 4
                    maxWidth >= 360.dp && fontScale <= 1.4f -> 2
                    else -> 1
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    stats.chunked(columns).forEach { rowStats ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowStats.forEach { stat -> StatCard(stat, Modifier.weight(1f)) }
                            repeat(columns - rowStats.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        item { StaffOverview(state) }
        item { WeeklyTrend(state, today, locale) }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun StaffOverview(state: DashboardUiState) {
    val staffStats = listOf(
        Stat("إجمالي الكادر", state.staffTotal.toString(), "موظف نشط", Icons.Rounded.Badge, StudentBlue),
        Stat("حاضرون", state.staffPresent.toString(), "في الموعد", Icons.Rounded.CheckCircle, PresentGreen),
        Stat("متأخرون", state.staffLate.toString(), "بعد وقت الدوام", Icons.Rounded.Schedule, LateAmber),
        Stat("غير مسجلين", state.staffAbsent.toString(), "لم يسجلوا اليوم", Icons.Rounded.PersonOff, AbsentRed)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Badge, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("الكادر التدريسي والوظيفي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("ملخص حضور اليوم", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            BoxWithConstraints {
                val columns = if (maxWidth >= 700.dp && LocalDensity.current.fontScale <= 1.3f) 4 else 2
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    staffStats.chunked(columns).forEach { rowStats ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowStats.forEach { stat -> StaffMiniStat(stat, Modifier.weight(1f)) }
                            repeat(columns - rowStats.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffMiniStat(stat: Stat, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = stat.color.copy(alpha = 0.09f)) {
        Column(Modifier.padding(14.dp)) {
            Icon(stat.icon, stat.title, Modifier.size(22.dp), stat.color)
            Spacer(Modifier.height(8.dp))
            Text(stat.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(stat.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DashboardHeader(schoolName: String, dateText: String) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(HeaderStart, HeaderEnd)))
            .padding(horizontal = 22.dp, vertical = 22.dp)
    ) {
        val showBadge = maxWidth >= 340.dp && LocalDensity.current.fontScale <= 1.3f
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.omega_logo),
                        contentDescription = "شعار حضور",
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(Modifier.width(15.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    schoolName.ifBlank { "مدرستي" },
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Text(dateText, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyMedium)
            }
            if (showBadge) Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.15f)) {
                Text(
                    "اليوم",
                    Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun AttendanceOverview(state: DashboardUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(20.dp)) {
            val compact = maxWidth < 470.dp || LocalDensity.current.fontScale > 1.3f
            if (compact) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AttendanceRing(state.rate)
                    OverviewDetails(state, compact = true)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AttendanceRing(state.rate)
                    Spacer(Modifier.width(22.dp))
                    OverviewDetails(state, compact = false, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OverviewDetails(state: DashboardUiState, compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start) {
        Text("ملخص حضور اليوم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = if (compact) TextAlign.Center else TextAlign.Start)
        Text(
            if (state.arrived == 0) "لم يبدأ تسجيل الحضور بعد" else "سجّل ${state.arrived} طالب حضورهم حتى الآن",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (compact) TextAlign.Center else TextAlign.Start
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem("حاضر", PresentGreen)
            LegendItem("متأخر", LateAmber)
            LegendItem("غائب", AbsentRed)
        }
    }
}

@Composable
private fun AttendanceRing(rate: Double) {
    val progress = (rate / 100.0).toFloat().coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 11.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(trackColor, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (progress > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(HeaderStart, PresentGreen)),
                    startAngle = -90f,
                    sweepAngle = progress * 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "%.0f%%".format(Locale.US, rate),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("نسبة الحضور", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatCard(stat: Stat, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(Modifier.size(42.dp), RoundedCornerShape(14.dp), color = stat.color.copy(alpha = 0.12f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(stat.icon, stat.title, Modifier.size(23.dp), stat.color)
                }
            }
            Spacer(Modifier.height(13.dp))
            Text(stat.value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(stat.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(
                stat.supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WeeklyTrend(state: DashboardUiState, today: LocalDate, locale: Locale) {
    val days = (6L downTo 0L).map { today.minusDays(it) }
    val values = days.map { day -> state.trend.firstOrNull { it.attendanceDate == day.toEpochDay() }?.total ?: 0 }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val dayFormatter = DateTimeFormatter.ofPattern("EE", locale)
    val shortDays = listOf("إث", "ثل", "أر", "خم", "جم", "سب", "أح")
    val compactDays = LocalConfiguration.current.screenWidthDp < 430 || LocalDensity.current.fontScale > 1.3f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("الحضور الأسبوعي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("آخر سبعة أيام", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = CircleShape, color = PresentGreen.copy(alpha = 0.1f)) {
                    Text(
                        "${values.sum()} تسجيل",
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = PresentGreen,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEachIndexed { index, day ->
                    val value = values[index]
                    val fraction = if (value == 0) 0.035f else (value.toFloat() / max).coerceAtLeast(0.12f)
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (value == 0) "" else value.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.48f)
                                    .fillMaxHeight(fraction)
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                    .background(if (day == today) PresentGreen else PresentGreen.copy(alpha = 0.34f))
                            )
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            if (compactDays) shortDays[day.dayOfWeek.value - 1] else day.format(dayFormatter),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (day == today) PresentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
