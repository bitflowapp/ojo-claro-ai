package com.ojoclaro.shared.commands

import com.ojoclaro.shared.model.AppCommandType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandParserTest {

    private val parser = CommandParser()

    @Test
    fun detectsReadTextCommand() {
        val command = parser.parse("Leeme este cartel")
        assertEquals(AppCommandType.READ_TEXT, command.type)
        assertTrue(command.requiresCamera)
    }

    @Test
    fun detectsEmergencyCommand() {
        val command = parser.parse("Estoy perdido necesito ayuda")
        assertEquals(AppCommandType.EMERGENCY_HELP, command.type)
        assertTrue(command.requiresLocation)
        assertTrue(command.highRisk)
    }

    @Test
    fun detectsDescribeCommandWithoutAccent() {
        val command = parser.parse("que tengo enfrente")
        assertEquals(AppCommandType.DESCRIBE_SCENE, command.type)
        assertTrue(command.requiresCamera)
    }

    @Test
    fun blankInputIsUnknown() {
        val command = parser.parse("   ")
        assertEquals(AppCommandType.UNKNOWN, command.type)
    }
}
