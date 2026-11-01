package dev.nacho.wibox.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeviceCodeParserTest {
    @Test
    fun parsesDeviceQrWithDocumentedFieldsAndWhitespace() {
        val result = DeviceCodeParser.parseDeviceQr("  WIBOX-AP\tDEMO7410001  abc_123\n7410  ")
        assertEquals("WIBOX-AP", result.apName)
        assertEquals("DEMO7410001", result.uid)
        assertEquals("abc_123", result.authCode)
        assertEquals("7410", result.model)
    }

    @Test
    fun rejectsWrongDeviceFieldCount() {
        val error = assertFailsWith<CodeParseException> {
            DeviceCodeParser.parse("WIBOX DEMOUID auth")
        }
        assertEquals(CodeParseError.DEVICE_FIELD_COUNT, error.reason)
    }

    @Test
    fun parsesFiveFieldLabelQrWithSsidAndMac() {
        val result = DeviceCodeParser.parseDeviceQr("IDS1111test u1d2test3 MZNTIVVE IDS1111 A1B2C3D4E5F6")
        assertEquals("IDS1111test", result.apName)
        assertEquals("u1d2test3", result.uid)
        assertEquals("MZNTIVVE", result.authCode)
        assertEquals("", result.model)
        assertEquals("IDS1111", result.ssid)
        assertEquals("A1B2C3D4E5F6", result.mac)
    }

    @Test
    fun acceptsColonSeparatedMacOnLabelQr() {
        val result = DeviceCodeParser.parseDeviceQr("IDS1111test u1d2test3 MZNTIVVE IDS1111 a1:b2:c3:d4:e5:f6")
        assertEquals("a1:b2:c3:d4:e5:f6", result.mac)
    }

    @Test
    fun rejectsLabelQrWithBrokenMac() {
        val error = assertFailsWith<CodeParseException> {
            DeviceCodeParser.parseDeviceQr("IDS1111test u1d2test3 MZNTIVVE IDS1111 NOT-A-MAC")
        }
        assertEquals(CodeParseError.INVALID_MODEL, error.reason)
    }

    @Test
    fun rejectsSixFieldQr() {
        val error = assertFailsWith<CodeParseException> {
            DeviceCodeParser.parseDeviceQr("IDS1111test u1d2test3 MZNTIVVE IDS1111 A1B2C3D4E5F6 extra")
        }
        assertEquals(CodeParseError.DEVICE_FIELD_COUNT, error.reason)
    }

    @Test
    fun rejectsInvalidUidAndAuthCode() {
        assertEquals(
            CodeParseError.INVALID_UID,
            assertFailsWith<CodeParseException> {
                DeviceCodeParser.parseDeviceQr("AP ui authCode 7410")
            }.reason,
        )
        assertEquals(
            CodeParseError.INVALID_AUTH_CODE,
            assertFailsWith<CodeParseException> {
                DeviceCodeParser.parseDeviceQr("AP DEMOUID bad! 7410")
            }.reason,
        )
    }

    @Test
    fun parsesAllInvitationFieldsAndPercentDecoding() {
        val invitation = DeviceCodeParser.parseInvitation(
            "https://example.invalid/invite?InviteCode=CODE%201&OwnerId=owner+one" +
                "&OemId=G0021&AppVersion=2.2&DeviceId=DEMO9440002&From=share&AppId=2021",
        )
        assertEquals("CODE 1", invitation.inviteCode)
        assertEquals("owner one", invitation.ownerId)
        assertEquals("DEMO9440002", invitation.deviceId)
        assertEquals(2021, invitation.appId)
    }

    @Test
    fun rejectsMissingAndDuplicateInvitationFields() {
        assertEquals(
            CodeParseError.MISSING_INVITATION_FIELD,
            assertFailsWith<CodeParseException> {
                DeviceCodeParser.parse("InviteCode=X&OwnerId=O&OemId=G0021&AppVersion=1&DeviceId=D&AppId=2021")
            }.reason,
        )
        assertEquals(
            CodeParseError.DUPLICATE_INVITATION_FIELD,
            assertFailsWith<CodeParseException> {
                DeviceCodeParser.parse(validInvitation() + "&DeviceId=OTHER")
            }.reason,
        )
    }

    @Test
    fun rejectsUnsupportedOemAndApp() {
        assertEquals(
            CodeParseError.UNSUPPORTED_OEM,
            assertFailsWith<CodeParseException> {
                DeviceCodeParser.parse(validInvitation().replace("G0021", "OTHER"))
            }.reason,
        )
        assertEquals(
            CodeParseError.UNSUPPORTED_APP,
            assertFailsWith<CodeParseException> {
                DeviceCodeParser.parse(validInvitation().replace("AppId=2021", "AppId=999"))
            }.reason,
        )
    }

    @Test
    fun rejectsMalformedPercentEncoding() {
        val error = assertFailsWith<CodeParseException> {
            DeviceCodeParser.parse(validInvitation().replace("InviteCode=CODE", "InviteCode=%ZZ"))
        }
        assertEquals(CodeParseError.INVALID_INVITATION, error.reason)
    }

    @Test
    fun localInvitationUrlRoundTrips() {
        val original = ParsedInvitation("CODE 1", "owner one", "G0021", "0.1.0", "DEMO_001", "local", 2021)
        val parsed = DeviceCodeParser.parseInvitation(original.toLocalUrl())
        assertEquals(original, parsed)
    }

    @Test
    fun invitationUrlRoundTripsUnicodeFields() {
        val original = ParsedInvitation("CÓDIGO😀", "Propietario ñ", "G0021", "0.1.0", "DEMO_001", "local", 2021)
        assertEquals(original, DeviceCodeParser.parseInvitation(original.toLocalUrl()))
    }

    @Test
    fun sealedParserDistinguishesDeviceAndInvitation() {
        assertTrue(DeviceCodeParser.parse("AP DEMOUID authCode 7410") is ParsedCode.Device)
        assertTrue(DeviceCodeParser.parse(validInvitation()) is ParsedCode.Invitation)
    }

    private fun validInvitation(): String =
        "InviteCode=CODE&OwnerId=owner&OemId=G0021&AppVersion=2.2&DeviceId=DEMO_001&From=share&AppId=2021"
}
