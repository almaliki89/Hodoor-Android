package com.omega.attendance

import com.omega.attendance.data.local.database.OmegaDatabase
import com.omega.attendance.data.local.dao.StaffDao
import com.omega.attendance.data.local.entity.StaffEntity
import org.junit.Assert.assertNotNull
import org.junit.Test

class StaffSchemaTest {
    @Test fun staffHasIndependentDao() {
        assertNotNull("staffDao is missing", OmegaDatabase::class.java.methods.find { it.name == "staffDao" })
    }

    @Test fun staffHasIndependentQrLookupAndAttendanceDao() {
        assertNotNull("staff qrValue is missing", StaffEntity::class.java.declaredFields.find { it.name == "qrValue" })
        assertNotNull("staff QR lookup is missing", StaffDao::class.java.methods.find { it.name == "byQr" })
        assertNotNull("staff attendance DAO is missing", OmegaDatabase::class.java.methods.find { it.name == "staffAttendanceDao" })
    }
}
