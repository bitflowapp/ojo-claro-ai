package com.ojoclaro.android.phone

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import com.ojoclaro.android.external.CommandResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneActionExecutorTest {
    private val executor = PhoneActionExecutor()

    @Test
    fun usaActionDialYNuncaActionCall() {
        val spec = executor.buildDialIntentSpec("1123456789")

        assertEquals(Intent.ACTION_DIAL, spec.action)
        assertNotEquals(Intent.ACTION_CALL, spec.action)
    }

    @Test
    fun usaUriTelCorrecta() {
        val spec = executor.buildDialIntentSpec("11 2345-6789")

        assertEquals("tel:1123456789", spec.dataUri)
    }

    @Test
    fun phoneNumberNullAbreDialerSinNumero() {
        val spec = executor.buildDialIntentSpec(null)

        assertEquals(Intent.ACTION_DIAL, spec.action)
        assertNull(spec.dataUri)
    }

    @Test
    fun noRequierePermisoCallPhone() {
        val spec = executor.buildDialIntentSpec("1123456789")

        assertNull(spec.requiredPermission)
        assertNotEquals(Manifest.permission.CALL_PHONE, spec.requiredPermission)
    }

    @Test
    fun manejaActivityNotFoundException() {
        val executor = PhoneActionExecutor(
            intentStarter = { throw ActivityNotFoundException("dialer missing") }
        )

        val result = executor.openDialer()

        val failed = assertIs<CommandResult.Failed>(result)
        assertTrue(failed.recoverable)
        assertTrue(failed.spokenText.contains("No pude abrir"))
    }

    @Test
    fun manejaSecurityException() {
        val executor = PhoneActionExecutor(
            intentStarter = { throw SecurityException("blocked") }
        )

        val result = executor.openDialer()

        val failed = assertIs<CommandResult.Failed>(result)
        assertTrue(failed.recoverable)
        assertTrue(failed.spokenText.contains("sistema"))
    }
}
