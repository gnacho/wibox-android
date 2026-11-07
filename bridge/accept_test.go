package main

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"
)

const successAnswer = `<envelope><header><flag>tdkcloud</flag><version>v1.24</version><command>accept-nologin-share-invitation</command><seq>0</seq><result>0</result></header><content><account>OX00000000001f28dd003-2021-abcdef</account><id>testdevice01</id><name>bridge-lab</name><data-encode-key>key-32</data-encode-key><dynamic-password>dp</dynamic-password><password-expired>2026-11-30</password-expired><powers>1,3,4,1001,1002,1003</powers><model>IDS7938</model></content></envelope>`

// fakeGateway behaves like the userauth gateway: the first down hands out a session
// cookie, the up channel answers 200 with an empty body and the queued envelope is
// delivered on the next down of the same session.
type fakeGateway struct {
	mu          sync.Mutex
	steps       []string
	cookies     []string
	contentType string
	envelope    string
	answer      string
	upBody      string
	emptyFirst  bool
	emptyPolls  int
}

func (g *fakeGateway) handler() http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		g.mu.Lock()
		defer g.mu.Unlock()
		cookie := r.Header.Get("Cookie")
		g.cookies = append(g.cookies, cookie)
		switch {
		case r.Method == http.MethodGet && strings.Contains(r.URL.Path, ";jus_duplex=down") && cookie == "":
			g.steps = append(g.steps, "down-open")
			http.SetCookie(w, &http.Cookie{Name: "jsessionid", Value: "abc123", Path: "/"})
			w.WriteHeader(http.StatusOK)
		case r.Method == http.MethodPost && strings.Contains(r.URL.Path, ";jus_duplex=up"):
			g.steps = append(g.steps, "up")
			g.contentType = r.Header.Get("Content-Type")
			body, _ := io.ReadAll(r.Body)
			g.upBody = string(body)
			w.WriteHeader(http.StatusOK)
		case r.Method == http.MethodGet && strings.Contains(r.URL.Path, ";jus_duplex=down"):
			g.steps = append(g.steps, "down-answer")
			if g.emptyFirst && g.emptyPolls == 0 {
				g.emptyPolls++
				w.WriteHeader(http.StatusOK)
				return
			}
			_, _ = io.WriteString(w, g.answer)
		default:
			http.NotFound(w, r)
		}
	})
}

func (g *fakeGateway) snapshot() (steps, cookies []string, contentType string) {
	g.mu.Lock()
	defer g.mu.Unlock()
	return append([]string(nil), g.steps...), append([]string(nil), g.cookies...), g.contentType
}

func startGateway(t *testing.T, gateway *fakeGateway) *httptest.Server {
	t.Helper()
	server := httptest.NewServer(gateway.handler())
	t.Cleanup(server.Close)
	return server
}

func testConfig(baseURL string) config {
	return config{
		BaseURL:  baseURL,
		ClientID: "003-2021-0123456789abcdef",
		Content:  testContent(),
		Timeout:  5 * time.Second,
		HTTP:     &http.Client{Timeout: 5 * time.Second},
	}
}

func TestAcceptReusesTheSessionCookieAcrossBothChannels(t *testing.T) {
	gateway := &fakeGateway{answer: successAnswer}
	server := startGateway(t, gateway)

	got, err := enrol(context.Background(), testConfig(server.URL))
	if err != nil {
		t.Fatalf("enrol: %v", err)
	}
	if !got.accepted() {
		t.Fatalf("result = %d, want 0", got.Result)
	}
	steps, cookies, contentType := gateway.snapshot()
	want := []string{"down-open", "up", "down-answer"}
	if strings.Join(steps, ",") != strings.Join(want, ",") {
		t.Fatalf("steps = %v, want %v", steps, want)
	}
	if contentType != "application/xml" {
		t.Errorf("content type = %q, want application/xml without charset", contentType)
	}
	// The up post and the down that carries the answer must use the cookie the first
	// down handed out.
	if len(cookies) != 3 {
		t.Fatalf("requests = %d, want 3", len(cookies))
	}
	for i, cookie := range cookies[1:] {
		if cookie != "jsessionid=abc123" {
			t.Errorf("request %d used cookie %q", i+2, cookie)
		}
	}
	if !strings.Contains(gateway.upBody, "<share-code>ABCD1234</share-code>") {
		t.Errorf("up channel did not carry the accept envelope: %q", gateway.upBody)
	}
}

func TestAcceptReadsTheAnswerThatWaitsForTheNextPoll(t *testing.T) {
	gateway := &fakeGateway{answer: successAnswer, emptyFirst: true}
	server := startGateway(t, gateway)

	got, err := enrol(context.Background(), testConfig(server.URL))
	if err != nil {
		t.Fatalf("enrol: %v", err)
	}
	if got.Account == "" {
		t.Fatal("an empty poll must not lose the queued answer")
	}
}

func TestAcceptFillsTheOutcomeFromTheResponse(t *testing.T) {
	gateway := &fakeGateway{answer: successAnswer}
	server := startGateway(t, gateway)

	got, err := enrol(context.Background(), testConfig(server.URL))
	if err != nil {
		t.Fatalf("enrol: %v", err)
	}
	if got.Account != "OX00000000001f28dd003-2021-abcdef" {
		t.Errorf("account = %q", got.Account)
	}
	if got.AccountID != "testdevice01" || got.DeviceName != "bridge-lab" {
		t.Errorf("device = %q / %q", got.AccountID, got.DeviceName)
	}
	if got.Model != "IDS7938" || got.Powers != "1,3,4,1001,1002,1003" {
		t.Errorf("model = %q powers = %q", got.Model, got.Powers)
	}
	if got.Password != testContent().Password {
		t.Errorf("the password of the new account must be the one the request carried, got %q", got.Password)
	}
	if got.DataEncodeKey != "key-32" {
		t.Errorf("data-encode-key = %q", got.DataEncodeKey)
	}
}

func TestAcceptKeepsAServerVerdict(t *testing.T) {
	answer := `<envelope><header><flag>tdkcloud</flag><command>accept-nologin-share-invitation</command><result>100004</result></header><content><account></account></content></envelope>`
	gateway := &fakeGateway{answer: answer}
	server := startGateway(t, gateway)

	got, err := enrol(context.Background(), testConfig(server.URL))
	if err != nil {
		t.Fatalf("a rejection is not a transport error: %v", err)
	}
	if got.accepted() {
		t.Fatal("result 100004 must not be reported as accepted")
	}
	if got.Result != 100004 {
		t.Fatalf("result = %d, want 100004", got.Result)
	}
	if got.Account != "" {
		t.Errorf("a rejected accept must not report an account, got %q", got.Account)
	}
}

func TestAcceptAcceptsTheAnswerInTheUpBody(t *testing.T) {
	// The gateway sometimes puts the envelope in the POST response instead of queuing
	// it. The probe must not wait for a down that will never answer.
	gateway := &fakeGateway{answer: successAnswer}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.Method {
		case http.MethodGet:
			if r.Header.Get("Cookie") == "" {
				http.SetCookie(w, &http.Cookie{Name: "jsessionid", Value: "abc123", Path: "/"})
				w.WriteHeader(http.StatusOK)
				return
			}
			// No queued answer: block until the client gives up.
			<-r.Context().Done()
		case http.MethodPost:
			w.WriteHeader(http.StatusOK)
			_, _ = io.WriteString(w, successAnswer)
		}
	}))
	t.Cleanup(server.Close)

	got, err := enrol(context.Background(), testConfig(server.URL))
	if err != nil {
		t.Fatalf("enrol: %v", err)
	}
	if !got.accepted() || got.Account == "" {
		t.Fatalf("the envelope of the POST body was ignored: %+v", got)
	}
	_ = gateway
}

func TestAcceptFailsWithoutASessionCookie(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	t.Cleanup(server.Close)

	if _, err := enrol(context.Background(), testConfig(server.URL)); err == nil {
		t.Fatal("a down channel without jsessionid must fail")
	}
}

func TestAcceptFailsWhenTheDownNeverAnswers(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodGet && r.Header.Get("Cookie") == "" {
			http.SetCookie(w, &http.Cookie{Name: "jsessionid", Value: "abc123", Path: "/"})
			w.WriteHeader(http.StatusOK)
			return
		}
		if r.Method == http.MethodGet {
			w.WriteHeader(http.StatusOK)
			return
		}
		w.WriteHeader(http.StatusOK)
	}))
	t.Cleanup(server.Close)

	cfg := testConfig(server.URL)
	cfg.Timeout = 700 * time.Millisecond
	ctx, cancel := context.WithTimeout(context.Background(), cfg.Timeout)
	defer cancel()
	if _, err := enrol(ctx, cfg); err == nil {
		t.Fatal("an exchange without answer must time out")
	}
}

func TestClientIDLooksLikeTheStockOne(t *testing.T) {
	id, err := newClientID()
	if err != nil {
		t.Fatalf("client id: %v", err)
	}
	if !strings.HasPrefix(id, "003-2021-") || len(id) != len("003-2021-")+16 {
		t.Fatalf("client id %q does not match 003-2021-<hex16>", id)
	}
	if _, err := newUUID(); err != nil {
		t.Fatalf("uuid: %v", err)
	}
}
