package com.omega.attendance

import com.omega.attendance.domain.model.ImportIssue
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportValidationTest {
    @Test fun issueRetainsSourceRow() { assertEquals(42, ImportIssue(42, "invalid").row) }
}
