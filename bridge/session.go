package main

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"strings"
	"time"
)

// The userauth channel is duplex over two URLs of the same session. Without the
// jsessionid cookie the gateway answers a plain 404, and the response to a command
// never comes back on the POST: it arrives on the down channel.
const (
	downPath = "/auth/nologin;jus_duplex=down"
	upPath   = "/auth/nologin;jus_duplex=up"
)

var jsessionCookie = regexp.MustCompile(`jsessionid=([^;]+)`)

// duplexClient drives one enrollment: take a session cookie from the down channel,
// post the command, then read the answer from the down channel again.
//
// The order matters. The gateway refuses to generate a response while a down poll is
// already in flight for that session ("BeginResponseGenerator return failed"), so the
// first down is only used to read the cookie and is closed immediately. The answer is
// queued server side (Cache-Control max-age=120) and the next down returns it.
type duplexClient struct {
	http    *http.Client
	baseURL string
	cookie  string
	pending string
	log     io.Writer
}

func newDuplexClient(baseURL string, client *http.Client, log io.Writer) *duplexClient {
	return &duplexClient{
		http:    client,
		baseURL: strings.TrimRight(baseURL, "/"),
		log:     log,
	}
}

// open asks the down channel for a session cookie.
func (c *duplexClient) open(ctx context.Context) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+downPath, nil)
	if err != nil {
		return err
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("down channel answered %s", resp.Status)
	}
	// The down channel is a long poll: only the headers matter here.
	cookie := ""
	for _, header := range resp.Header.Values("Set-Cookie") {
		if match := jsessionCookie.FindStringSubmatch(header); match != nil {
			cookie = match[1]
			break
		}
	}
	if cookie == "" {
		return errors.New("down channel did not return a jsessionid cookie")
	}
	c.cookie = cookie
	fmt.Fprintf(c.log, "session: jsessionid=%s\n", cookie)
	return nil
}

// send posts the envelope on the up channel with the session cookie.
func (c *duplexClient) send(ctx context.Context, envelope string) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL+upPath, strings.NewReader(envelope))
	if err != nil {
		return err
	}
	// Content-Type without the charset suffix, as the stock app sends it.
	req.Header.Set("Content-Type", "application/xml")
	req.Header.Set("charset", "utf-8")
	req.Header.Set("Accept-Language", "en-US")
	req.Header.Set("Connection", "Keep-Alive")
	req.Header.Set("User-Agent", "okhttp/3.9.0")
	req.Header.Set("Cookie", "jsessionid="+c.cookie)
	resp, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("up channel answered %s: %s", resp.Status, strings.TrimSpace(string(body)))
	}
	fmt.Fprintf(c.log, "up: %s body=%d bytes\n", resp.Status, len(body))
	// The gateway sometimes puts the envelope in the POST body instead of queuing it.
	if strings.TrimSpace(string(body)) != "" {
		c.pending = string(body)
	}
	return nil
}

// receive waits for the queued envelope on the down channel.
func (c *duplexClient) receive(ctx context.Context) (string, error) {
	if c.pending != "" {
		body := c.pending
		c.pending = ""
		return body, nil
	}
	var lastErr error
	for {
		if err := ctx.Err(); err != nil {
			if lastErr != nil {
				return "", fmt.Errorf("%w (last down error: %v)", err, lastErr)
			}
			return "", err
		}
		body, err := c.poll(ctx)
		if err == nil && strings.TrimSpace(body) != "" {
			return body, nil
		}
		if err != nil {
			lastErr = err
		}
		select {
		case <-ctx.Done():
		case <-time.After(500 * time.Millisecond):
		}
	}
}

func (c *duplexClient) poll(ctx context.Context) (string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+downPath, nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("Cookie", "jsessionid="+c.cookie)
	resp, err := c.http.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("down channel answered %s", resp.Status)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return "", err
	}
	return string(body), nil
}
