package com.omega.attendance.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omega.attendance.data.local.entity.*
import com.omega.attendance.data.local.dao.ClassOption
import com.omega.attendance.data.repository.SchoolStructureRepository
import com.omega.attendance.data.repository.SchoolCalendarRepository
import com.omega.attendance.data.settings.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import com.omega.attendance.security.AuthRepository
import com.omega.attendance.data.sample.SampleDataGenerator

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
    val years: List<AcademicYearEntity> = emptyList(),
    val halls: List<HallEntity> = emptyList(),
    val classes: List<ClassOption> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val structure: SchoolStructureRepository,
    private val auth: AuthRepository,
    private val samples: SampleDataGenerator,
    private val calendar: SchoolCalendarRepository
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    val state = combine(settings.preferences, structure.years(), structure.halls(), structure.classOptions(), message) { p, y, h, c, m -> SettingsUiState(p, y, h, c, m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            runCatching {
                val preferences = settings.preferences.first()
                val yearId = structure.ensureDefaults(preferences.activeAcademicYearId)
                if (preferences.activeAcademicYearId == 0L) settings.updateSchool(preferences.schoolName, yearId)
            }.onFailure { message.value = "تعذر إعداد الصفوف والقاعات تلقائيًا" }
        }
    }

    fun ensureActiveYear() = viewModelScope.launch {
        val preferences = settings.preferences.first()
        if (preferences.activeAcademicYearId == 0L) {
            structure.years().first().firstOrNull()?.let { year ->
                settings.updateSchool(preferences.schoolName, year.id)
            }
        }
    }

    fun addAcademicYear(name: String) = viewModelScope.launch {
        runCatching {
            val startYear = name.take(4).toInt()
            require(name == "%04d-%04d".format(startYear, startYear + 1))
            val zone = ZoneId.systemDefault()
            val id = structure.createYear(name, LocalDate.of(startYear, 9, 1).atStartOfDay(zone).toInstant().toEpochMilli(), LocalDate.of(startYear + 1, 8, 31).atStartOfDay(zone).toInstant().toEpochMilli())
            settings.updateSchool(settings.preferences.first().schoolName, id)
        }.onSuccess { message.value = "تمت إضافة السنة الدراسية" }.onFailure { message.value = "تعذر الإضافة: تحقق من الاسم أو التكرار" }
    }

    fun addHall(name: String, number: String, capacity: String) = viewModelScope.launch {
        runCatching { require(name.isNotBlank() && number.isNotBlank()); structure.createHall(name, number, capacity.toIntOrNull()) }
            .onSuccess { message.value = "تمت إضافة القاعة" }.onFailure { message.value = "تعذر إضافة القاعة" }
    }
    fun addClass(yearId: Long, grade: String, section: String, name: String, hallId: Long?) = viewModelScope.launch {
        runCatching { require(yearId > 0 && grade.isNotBlank() && section.isNotBlank() && name.isNotBlank()); structure.createClass(yearId, grade, section, name, hallId) }
            .onSuccess { message.value = "تمت إضافة الصف" }.onFailure { message.value = "تعذر إضافة الصف؛ تحقق من البيانات أو التكرار" }
    }

    fun updateYear(id: Long, name: String) = viewModelScope.launch {
        runCatching { structure.updateYear(id, name) }
            .onSuccess { message.value = "تم تعديل السنة الدراسية" }
            .onFailure { message.value = "تعذر التعديل؛ تحقق من الاسم أو التكرار" }
    }
    fun deleteYear(id: Long) = viewModelScope.launch {
        runCatching {
            require(settings.preferences.first().activeAcademicYearId != id) { "غيّر السنة النشطة أولًا" }
            structure.deleteYear(id)
        }.onSuccess { message.value = "تم حذف السنة الدراسية" }
            .onFailure { message.value = "لا يمكن حذف سنة نشطة أو مرتبطة بطلبة أو سجلات حضور. اختر سنة نشطة أخرى أولًا." }
    }
    fun updateHall(id: Long, name: String, number: String, capacity: String) = viewModelScope.launch {
        runCatching { structure.updateHall(id, name, number, capacity.toIntOrNull()) }
            .onSuccess { message.value = "تم تعديل القاعة" }
            .onFailure { message.value = "تعذر تعديل القاعة؛ تحقق من البيانات أو تكرار الرقم" }
    }
    fun deleteHall(id: Long) = viewModelScope.launch {
        runCatching { structure.deleteHall(id) }
            .onSuccess { message.value = "تم حذف القاعة" }
            .onFailure { message.value = "تعذر حذف القاعة" }
    }
    fun updateClass(id: Long, grade: String, section: String, name: String, hallId: Long?) = viewModelScope.launch {
        runCatching { structure.updateClass(id, grade, section, name, hallId) }
            .onSuccess { message.value = "تم تعديل الصف" }
            .onFailure { message.value = "تعذر تعديل الصف؛ تحقق من البيانات أو التكرار" }
    }
    fun deleteClass(id: Long) = viewModelScope.launch {
        runCatching { structure.deleteClass(id) }
            .onSuccess { message.value = "تم حذف الصف" }
            .onFailure { message.value = "لا يمكن حذف صف مرتبط بطلبة؛ انقلهم إلى صف آخر أولًا" }
    }

    fun saveSchool(name: String, yearId: Long) = viewModelScope.launch { settings.updateSchool(name, yearId); message.value = "تم حفظ الإعدادات" }
    fun saveAttendance(start: String, late: String, cooldown: String, sound: Boolean, vibration: Boolean) = viewModelScope.launch {
        runCatching {
            val startMinutes = parseTime(start)
            val lateMinutes = parseTime(late)
            require(lateMinutes >= startMinutes)
            settings.updateAttendance(startMinutes, lateMinutes, cooldown.toLongOrNull() ?: 900, sound, vibration)
        }
            .onSuccess { message.value = "تم حفظ إعدادات الحضور" }.onFailure { message.value = "صيغة الوقت غير صحيحة؛ استخدم HH:mm" }
    }
    fun dismissMessage() { message.value = null }
    fun changePin(pin: String, confirm: String) = viewModelScope.launch { if (pin != confirm || pin.length < 4) message.value = "رمزا PIN غير متطابقين" else runCatching { auth.setAdminPin(pin) }.onSuccess { message.value = "تم تغيير رمز الإدارة" }.onFailure { message.value = "تعذر تغيير الرمز" } }
    fun generateLoadData() = viewModelScope.launch { message.value = "جارٍ إنشاء بيانات اختبار الحمل..."; runCatching { samples.generate() }.onSuccess { message.value = "تم إنشاء 1,000 طالب و100,000 سجل" }.onFailure { message.value = it.message ?: "تعذر إنشاء البيانات" } }
    fun saveCalendarDay(date: String, yearId: Long, type: CalendarDayType, title: String) = viewModelScope.launch {
        runCatching { calendar.save(LocalDate.parse(date), yearId, type, title) }
            .onSuccess { message.value = "تم حفظ يوم التقويم" }
            .onFailure { message.value = "تعذر حفظ اليوم؛ تحقق من التاريخ والسنة النشطة" }
    }
    fun deleteCalendarDay(date: String, yearId: Long) = viewModelScope.launch {
        runCatching {
            calendar.delete(LocalDate.parse(date), yearId)
        }.onSuccess { message.value = "تم حذف تخصيص اليوم" }
            .onFailure { message.value = "لا يوجد يوم مخصص لهذا التاريخ أو أن التاريخ غير صحيح" }
    }
    private fun parseTime(value: String): Int {
        val parts = value.split(":")
        require(parts.size == 2)
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        require(hour in 0..23 && minute in 0..59)
        return hour * 60 + minute
    }
}
