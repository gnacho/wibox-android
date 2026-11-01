package dev.nacho.wibox.core.cloud

import dev.nacho.wibox.core.cloud.internal.UserAuthEnvelope
import dev.nacho.wibox.core.cloud.internal.UserAuthHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAuthEnvelopeTest {

    @Test
    fun `build serializa el envelope accept con header y content`() {
        val xml = UserAuthEnvelope.build(
            UserAuthHeader(command = "accept-nologin-share-invitation", clientId = "003-2021-abc"),
            linkedMapOf(
                "share-code" to "VVzde8PP",
                "device-id" to "<UDID>",
                "share-device-memo" to "Device1",
                "need-create" to "1",
                "password" to "uuid-value",
                "way-from" to "QrInvite",
            ),
            "com.quvii.qvweb.userauth.bean.request.NoLoginShareAcceptReqContent",
        )
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(xml.contains("<flag>tdkcloud</flag>"))
        // Real stock request sends an empty version element, not a protocol/app version.
        assertTrue(xml.contains("<version></version>"))
        assertTrue(xml.contains("<command>accept-nologin-share-invitation</command>"))
        assertTrue(xml.contains("<id>003-2021-abc</id>"))
        assertTrue(xml.contains("<type>3</type>"))
        assertTrue(xml.contains("<oem>G0021</oem>"))
        assertTrue(xml.contains("<app>2021</app>"))
        assertTrue(xml.contains("<user-data></user-data>"))
        assertTrue(xml.contains("<share-code>VVzde8PP</share-code>"))
        assertTrue(xml.contains("<device-id><UDID></device-id>"))
        assertTrue(xml.contains("<share-device-memo>Device1</share-device-memo>"))
        assertTrue(xml.contains("<need-create>1</need-create>"))
        assertTrue(xml.contains("<way-from>QrInvite</way-from>"))
        // Real stock request serialises content (with the XStream bean class) before the header.
        assertTrue(xml.contains("<content class=\"com.quvii.qvweb.userauth.bean.request.NoLoginShareAcceptReqContent\">"))
        assertTrue(xml.indexOf("<content") < xml.indexOf("<header>"))
    }

    @Test
    fun `build escapa caracteres XML en los valores`() {
        val xml = UserAuthEnvelope.build(
            UserAuthHeader(command = "login", clientId = "cid"),
            linkedMapOf("account" to "a<b&c", "password" to "p\"w'd"),
        )
        assertTrue(xml.contains("<account>a&lt;b&amp;c</account>"))
        assertTrue(xml.contains("<password>p&quot;w&apos;d</password>"))
    }

    @Test
    fun `parse lee la respuesta real de error del accept`() {
        // Respuesta real capturada del canal down (result=100004).
        val xml = """<?xml version="1.0" encoding="UTF-8"?><envelope><header><flag>tdkcloud</flag><version>v1.24</version><command>accept-nologin-share-invitation</command><seq>0</seq><result>100004</result></header><content><account></account><id></id><name></name><data-encode-key></data-encode-key><dynamic-password></dynamic-password><password-expired></password-expired><powers></powers></content></envelope>"""
        val parsed = UserAuthEnvelope.parse(xml)
        assertEquals("tdkcloud", parsed.flag)
        assertEquals("v1.24", parsed.version)
        assertEquals("accept-nologin-share-invitation", parsed.command)
        assertEquals(0, parsed.seq)
        assertEquals(100004, parsed.result)
        assertFalse(parsed.isSuccess)
        assertEquals("", parsed.content["account"])
        assertTrue(parsed.content.containsKey("powers"))
    }

    @Test
    fun `parse lee una respuesta de exito con cuenta creada`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><envelope><header><flag>tdkcloud</flag><version>v1.24</version><command>accept-nologin-share-invitation</command><seq>0</seq><result>0</result></header><content><account>free_abc</account><id>2041769</id><name>WIBOX-lab</name><data-encode-key>key123</data-encode-key><dynamic-password>dp</dynamic-password><password-expired>0</password-expired><powers>all</powers></content></envelope>"""
        val parsed = UserAuthEnvelope.parse(xml)
        assertEquals(0, parsed.result)
        assertTrue(parsed.isSuccess)
        assertEquals("free_abc", parsed.content["account"])
        assertEquals("2041769", parsed.content["id"])
        assertEquals("WIBOX-lab", parsed.content["name"])
    }

    @Test
    fun `parse sin result devuelve null y no es exito`() {
        val xml = """<envelope><header><flag>tdkcloud</flag><command>login</command></header><content/></envelope>"""
        val parsed = UserAuthEnvelope.parse(xml)
        assertNull(parsed.result)
        assertFalse(parsed.isSuccess)
    }
}
