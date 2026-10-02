package com.omega.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "school_staff", indices = [Index(value = ["uuid"], unique = true), Index(value = ["qrValue"], unique = true), Index("fullName"), Index("jobTitle"), Index("status")])
data class StaffEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val uuid: String, val qrValue: String, val fullName: String, val jobTitle: String, val specialty: String? = null, val phone: String? = null, val notes: String? = null, val status: String = "ACTIVE", val createdAt: Long, val updatedAt: Long)
