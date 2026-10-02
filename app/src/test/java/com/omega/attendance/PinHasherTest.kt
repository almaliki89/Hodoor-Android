package com.omega.attendance

import com.omega.attendance.security.PinHasher
import org.junit.Assert.*
import org.junit.Test

class PinHasherTest {
    @Test fun pinIsSaltedAndVerifiable() { val (salt, hash) = PinHasher.create("1234".toCharArray()); assertTrue(PinHasher.verify("1234".toCharArray(), salt, hash)); assertFalse(PinHasher.verify("4321".toCharArray(), salt, hash)); assertFalse(hash.contains("1234")) }
}
