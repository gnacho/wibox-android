package main

import (
	"encoding/xml"
	"strings"
	"testing"
)

// goldenAccept is the request the stock app sent to the gateway in an accept the
// server answered with result=0, with the invitation data replaced by placeholders.
const goldenAccept = `<?xml version="1.0" encoding="UTF-8"?><envelope>
   <content class="com.quvii.qvweb.userauth.bean.request.NoLoginShareAcceptReqContent">
      <share-code>ABCD1234</share-code>
      <device-id>testdevice01</device-id>
      <share-device-memo>bridge-lab</share-device-memo>
      <need-create>1</need-create>
      <password>11111111-2222-4333-8444-555555555555</password>
      <way-from>QrInvite</way-from>
   </content>
   <header>
      <client>
         <app>2021</app>
         <id>003-2021-0123456789abcdef</id>
         <oem>G0021</oem>
         <type>3</type>
      </client>
      <command>accept-nologin-share-invitation</command>
      <flag>tdkcloud</flag>
      <seq>0</seq>
      <user-data></user-data>
      <version></version>
   </header>
</envelope>`

func testContent() acceptContent {
	return acceptContent{
		ShareCode:  "ABCD1234",
		DeviceID:   "testdevice01",
		Memo:       "bridge-lab",
		NeedCreate: true,
		Password:   "11111111-2222-4333-8444-555555555555",
		WayFrom:    "QrInvite",
	}
}

func TestEnvelopeMatchesTheGoldenShape(t *testing.T) {
	got := buildAcceptEnvelope("003-2021-0123456789abcdef", testContent())
	if got != goldenAccept {
		t.Fatalf("envelope differs from the captured shape\n--- got ---\n%s\n--- want ---\n%s", got, goldenAccept)
	}
}

func TestEnvelopeUsesKebabCaseFields(t *testing.T) {
	got := buildAcceptEnvelope("003-2021-0123456789abcdef", testContent())
	for _, field := range []string{
		"<share-code>", "<device-id>", "<share-device-memo>", "<need-create>", "<way-from>",
	} {
		if !strings.Contains(got, field) {
			t.Errorf("envelope is missing %s", field)
		}
	}
	for _, legacy := range []string{"<code>", "<deviceId>", "<needCreate>", "<way>"} {
		if strings.Contains(got, legacy) {
			t.Errorf("envelope still carries the camelCase field %s", legacy)
		}
	}
}

func TestEnvelopeKeepsContentBeforeTheHeaderWithItsClass(t *testing.T) {
	got := buildAcceptEnvelope("003-2021-0123456789abcdef", testContent())
	content := strings.Index(got, "<content")
	header := strings.Index(got, "<header>")
	if content < 0 || header < 0 || content > header {
		t.Fatalf("content must come before the header, got content=%d header=%d", content, header)
	}
	if !strings.Contains(got, `class="com.quvii.qvweb.userauth.bean.request.NoLoginShareAcceptReqContent"`) {
		t.Error("content lost its XStream class attribute")
	}
	if !strings.Contains(got, "<version></version>") {
		t.Error("the header version must be empty")
	}
	if strings.Contains(got, "<session>") {
		t.Error("the accept request must not carry a session element")
	}
}

func TestEnvelopeEscapesTheAlias(t *testing.T) {
	content := testContent()
	content.Memo = `casa & "<norte>"`
	got := buildAcceptEnvelope("003-2021-0123456789abcdef", content)
	if !strings.Contains(got, "<share-device-memo>casa &amp; &quot;&lt;norte&gt;&quot;</share-device-memo>") {
		t.Fatalf("alias not escaped as XStream does: %s", got)
	}
}

func TestEnvelopeStaysWellFormedXML(t *testing.T) {
	content := testContent()
	content.Memo = "casa <norte> & sur"
	got := buildAcceptEnvelope("003-2021-0123456789abcdef", content)
	var parsed struct {
		XMLName xml.Name `xml:"envelope"`
	}
	if err := xml.Unmarshal([]byte(got), &parsed); err != nil {
		t.Fatalf("envelope is not valid XML: %v", err)
	}
}

func TestParseSuccessResponse(t *testing.T) {
	body := `<?xml version="1.0" encoding="UTF-8"?>
<envelope>
  <header><flag>tdkcloud</flag><version>v1.24</version><command>accept-nologin-share-invitation</command><seq>0</seq><result>0</result></header>
  <content><account>OX00000000001f28dd003-2021-abcdef</account><id>testdevice01</id><name>bridge-lab</name><data-encode-key>key-32</data-encode-key><dynamic-password>dp</dynamic-password><password-expired>2026-11-30</password-expired><powers>1,3,4,1001,1002,1003</powers><model>IDS7938</model></content>
</envelope>`
	parsed, err := parseResponse(body)
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if parsed.result() != 0 {
		t.Fatalf("result = %d, want 0", parsed.result())
	}
	if parsed.Header.Command != acceptCommand {
		t.Errorf("command = %q", parsed.Header.Command)
	}
	if parsed.Content.Account != "OX00000000001f28dd003-2021-abcdef" {
		t.Errorf("account = %q", parsed.Content.Account)
	}
	if parsed.Content.DataEncodeKey != "key-32" {
		t.Errorf("data-encode-key = %q", parsed.Content.DataEncodeKey)
	}
	if parsed.Content.Powers != "1,3,4,1001,1002,1003" {
		t.Errorf("powers = %q", parsed.Content.Powers)
	}
	if parsed.Content.Model != "IDS7938" {
		t.Errorf("model = %q", parsed.Content.Model)
	}
}

func TestParseErrorResponseKeepsTheCode(t *testing.T) {
	body := `<envelope><header><flag>tdkcloud</flag><command>accept-nologin-share-invitation</command><result>100004</result></header><content><account></account></content></envelope>`
	parsed, err := parseResponse(body)
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if parsed.result() != 100004 {
		t.Fatalf("result = %d, want 100004", parsed.result())
	}
}

func TestParseResponseWithoutResult(t *testing.T) {
	parsed, err := parseResponse(`<envelope><header><command>x</command></header></envelope>`)
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if parsed.result() != -1 {
		t.Fatalf("result = %d, want -1", parsed.result())
	}
}

func TestParseResponseDropsDoctype(t *testing.T) {
	body := `<!DOCTYPE envelope SYSTEM "http://example.invalid/x.dtd"><envelope><header><command>accept-nologin-share-invitation</command><result>0</result></header></envelope>`
	parsed, err := parseResponse(body)
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if parsed.result() != 0 {
		t.Fatalf("result = %d, want 0", parsed.result())
	}
}

func TestParseEmptyResponseFails(t *testing.T) {
	if _, err := parseResponse("   "); err == nil {
		t.Fatal("an empty body must be an error")
	}
}
