package com.mellefresh13.radio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaControllerAccessPolicyTest {
    @Test
    fun trustedControllerCanCloseRadio() {
        assertTrue(MediaControllerAccessPolicy.canCloseRadio(controllerIsTrusted = true))
    }

    @Test
    fun untrustedControllerCannotCloseRadio() {
        assertFalse(MediaControllerAccessPolicy.canCloseRadio(controllerIsTrusted = false))
    }
}
