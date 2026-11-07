package main

import (
	"encoding/xml"
	"errors"
	"strings"
)

// acceptCommand is the userauth command that enrols an installation. The stock app
// sends it when the user opens an invitation link (see docs/protocol.md section 16).
const acceptCommand = "accept-nologin-share-invitation"

// acceptContentClass is the XStream class marker the server expects inside <content>.
// Without it the payload is rejected even when every field is correct.
const acceptContentClass = "com.quvii.qvweb.userauth.bean.request.NoLoginShareAcceptReqContent"

// acceptContent carries the invitation. The element names are the kebab case aliases
// of the wire, not the camelCase names of the smali beans: the stock app serializes
// with XStream and that is what the server reads.
type acceptContent struct {
	ShareCode  string
	DeviceID   string
	Memo       string
	NeedCreate bool
	Password   string
	WayFrom    string
}

func (c acceptContent) fields() [][2]string {
	needCreate := "0"
	if c.NeedCreate {
		needCreate = "1"
	}
	return [][2]string{
		{"share-code", c.ShareCode},
		{"device-id", c.DeviceID},
		{"share-device-memo", c.Memo},
		{"need-create", needCreate},
		{"password", c.Password},
		{"way-from", c.WayFrom},
	}
}

// buildAcceptEnvelope serializes the request the way the stock app does: the content
// goes before the header, it carries the class attribute, the header version is empty
// and the indentation is three spaces per level. The shape is byte for byte the one
// captured from a live accept that the server answered with result=0.
func buildAcceptEnvelope(clientID string, content acceptContent) string {
	var b strings.Builder
	b.WriteString(`<?xml version="1.0" encoding="UTF-8"?><envelope>`)
	b.WriteString("\n   <content class=\"")
	b.WriteString(escapeXML(acceptContentClass))
	b.WriteString("\">")
	for _, field := range content.fields() {
		b.WriteString("\n      <" + field[0] + ">")
		b.WriteString(escapeXML(field[1]))
		b.WriteString("</" + field[0] + ">")
	}
	b.WriteString("\n   </content>")
	b.WriteString("\n   <header>")
	b.WriteString("\n      <client>")
	b.WriteString("\n         <app>2021</app>")
	b.WriteString("\n         <id>" + escapeXML(clientID) + "</id>")
	b.WriteString("\n         <oem>G0021</oem>")
	b.WriteString("\n         <type>3</type>")
	b.WriteString("\n      </client>")
	b.WriteString("\n      <command>" + escapeXML(acceptCommand) + "</command>")
	b.WriteString("\n      <flag>tdkcloud</flag>")
	b.WriteString("\n      <seq>0</seq>")
	b.WriteString("\n      <user-data></user-data>")
	b.WriteString("\n      <version></version>")
	b.WriteString("\n   </header>")
	b.WriteString("\n</envelope>")
	return b.String()
}

// escapeXML follows the XStream output of the stock app: quotes and apostrophes are
// written as named entities, not as numeric references.
func escapeXML(s string) string {
	r := strings.NewReplacer(
		"&", "&amp;",
		"<", "&lt;",
		">", "&gt;",
		`"`, "&quot;",
		"'", "&apos;",
	)
	return r.Replace(s)
}

// response is the envelope the gateway delivers on the down channel.
type response struct {
	XMLName xml.Name `xml:"envelope"`
	Header  struct {
		Flag    string `xml:"flag"`
		Version string `xml:"version"`
		Command string `xml:"command"`
		Seq     int    `xml:"seq"`
		Result  *int   `xml:"result"`
		Session struct {
			ID string `xml:"id"`
		} `xml:"session"`
	} `xml:"header"`
	Content struct {
		Account         string `xml:"account"`
		ID              string `xml:"id"`
		Name            string `xml:"name"`
		DataEncodeKey   string `xml:"data-encode-key"`
		DynamicPassword string `xml:"dynamic-password"`
		PasswordExpired string `xml:"password-expired"`
		Powers          string `xml:"powers"`
		Model           string `xml:"model"`
	} `xml:"content"`
}

func parseResponse(body string) (response, error) {
	var parsed response
	if strings.TrimSpace(body) == "" {
		return parsed, errors.New("empty envelope")
	}
	safe := stripDoctype(body)
	if err := xml.Unmarshal([]byte(safe), &parsed); err != nil {
		return parsed, err
	}
	return parsed, nil
}

// stripDoctype removes doctype and entity declarations before parsing, the same
// hardening the app applies: the parser must not resolve external references.
func stripDoctype(s string) string {
	for {
		start := strings.Index(strings.ToLower(s), "<!doctype")
		if start < 0 {
			break
		}
		end := strings.Index(s[start:], ">")
		if end < 0 {
			break
		}
		s = s[:start] + s[start+end+1:]
	}
	for {
		start := strings.Index(strings.ToLower(s), "<!entity")
		if start < 0 {
			break
		}
		end := strings.Index(s[start:], ">")
		if end < 0 {
			break
		}
		s = s[:start] + s[start+end+1:]
	}
	return s
}

// result returns the outcome code of the header, or -1 when the header carries none.
func (r response) result() int {
	if r.Header.Result == nil {
		return -1
	}
	return *r.Header.Result
}
